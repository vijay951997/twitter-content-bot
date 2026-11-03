package com.ai.xbot.service.drafts;

import com.ai.xbot.entity.enums.ContentLanguage;
import com.ai.xbot.entity.enums.PostSlot;
import com.ai.xbot.entity.enums.VerificationStatus;
import com.ai.xbot.service.ai.AiGenerationResult.AiDraft;
import com.ai.xbot.service.text.LanguageDetector;
import com.ai.xbot.service.text.XTextLength;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks one AI draft against the project rules. The AI output is never trusted as-is:
 * <ul>
 *   <li>must fit X's 280 weighted characters</li>
 *   <li>must be written in the requested language</li>
 *   <li>may only cite URLs the user supplied; a draft whose text contains any other link is discarded,
 *       and invented entries in source_urls are dropped and flagged</li>
 *   <li>verification status is capped: no source material → UNVERIFIED; the AI reading supplied notes
 *       is never treated as independent verification, so VERIFIED is lowered to PARTIALLY_VERIFIED
 *       (true verification arrives with the Phase 3 fact-checking engine)</li>
 * </ul>
 */
public final class DraftValidator {

    public static final Set<String> KNOWN_RISK_FLAGS = Set.of(
            "UNVERIFIED_ALLEGATION", "PERSONAL_ATTACK", "UNSUPPORTED_STATISTIC", "UNATTRIBUTED_QUOTE",
            "INFLAMMATORY", "POSSIBLY_OUTDATED", "OPINION_AS_FACT");
    public static final String FLAG_AI_URL_REMOVED = "AI_URL_REMOVED";
    public static final String FLAG_HAS_UNVERIFIED_CLAIMS = "HAS_UNVERIFIED_CLAIMS";

    private static final Pattern URL_IN_TEXT = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern CATEGORY_CHARS = Pattern.compile("[^A-Z0-9_]");

    private DraftValidator() {}

    public sealed interface Outcome permits Accepted, Rejected {}

    public record Accepted(String content, ContentLanguage language, String category, List<String> sourceUrls,
                           List<String> verifiedClaims, List<String> unverifiedClaims,
                           VerificationStatus verificationStatus, List<String> riskFlags,
                           BigDecimal qualityEstimate, int weightedLength) implements Outcome {}

    public record Rejected(String reason) implements Outcome {}

    public static Outcome validate(AiDraft d, ContentLanguage expected, PostSlot slot,
                                   List<String> suppliedUrls, boolean hasSourceMaterial) {
        String content = d.content() == null ? "" : d.content().strip();
        if (content.isEmpty()) return new Rejected("Empty draft");

        int length = XTextLength.weightedLength(content);
        if (length > XTextLength.MAX_WEIGHTED_LENGTH) {
            return new Rejected("Too long for X (" + length + "/" + XTextLength.MAX_WEIGHTED_LENGTH + ")");
        }

        if (!LanguageDetector.matches(content, expected)) {
            return new Rejected("Not written in the requested language (" + expected.code() + ")");
        }

        Set<String> allowed = new HashSet<>();
        for (String u : suppliedUrls) allowed.add(normalizeUrl(u));

        Matcher m = URL_IN_TEXT.matcher(content);
        while (m.find()) {
            if (!allowed.contains(normalizeUrl(m.group()))) {
                return new Rejected("Contains a link that was not supplied: " + m.group());
            }
        }

        List<String> riskFlags = new ArrayList<>();
        for (String f : d.riskFlags() == null ? List.<String>of() : d.riskFlags()) {
            if (KNOWN_RISK_FLAGS.contains(f) && !riskFlags.contains(f)) riskFlags.add(f);
        }

        List<String> urls = new ArrayList<>();
        for (String u : d.sourceUrls() == null ? List.<String>of() : d.sourceUrls()) {
            String original = findSupplied(u, suppliedUrls);
            if (original == null) {
                if (!riskFlags.contains(FLAG_AI_URL_REMOVED)) riskFlags.add(FLAG_AI_URL_REMOVED);
            } else if (!urls.contains(original)) {
                urls.add(original);
            }
        }

        List<String> verified = nonNull(d.verifiedClaims());
        List<String> unverified = nonNull(d.unverifiedClaims());
        VerificationStatus status = capStatus(d.verificationStatus(), hasSourceMaterial);
        if (!hasSourceMaterial && !verified.isEmpty()) {
            // Nothing was supplied, so nothing can be "verified".
            unverified = new ArrayList<>(unverified);
            unverified.addAll(verified);
            verified = List.of();
        }
        if (!unverified.isEmpty()) riskFlags.add(FLAG_HAS_UNVERIFIED_CLAIMS);

        return new Accepted(content, expected, category(d.category(), slot), urls, verified, unverified, status,
                riskFlags, quality(d.qualityEstimate()), length);
    }

    static VerificationStatus capStatus(String aiStatus, boolean hasSourceMaterial) {
        if (!hasSourceMaterial) return VerificationStatus.UNVERIFIED;
        VerificationStatus s;
        try {
            s = aiStatus == null ? VerificationStatus.UNVERIFIED : VerificationStatus.valueOf(aiStatus);
        } catch (IllegalArgumentException e) {
            s = VerificationStatus.UNVERIFIED;
        }
        return s == VerificationStatus.VERIFIED ? VerificationStatus.PARTIALLY_VERIFIED : s;
    }

    static String category(String raw, PostSlot slot) {
        String c = raw == null ? "" : CATEGORY_CHARS.matcher(raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_')).replaceAll("");
        if (c.isEmpty()) {
            return switch (slot) {
                case MORNING -> "GOVERNANCE";
                case AFTERNOON -> "POLITICS";
                case EVENING -> "PUBLIC_INTEREST";
            };
        }
        return c.length() > 40 ? c.substring(0, 40) : c;
    }

    static BigDecimal quality(Double q) {
        if (q == null || q.isNaN()) return null;
        double clamped = Math.max(0, Math.min(1, q));
        return BigDecimal.valueOf(clamped).setScale(3, RoundingMode.HALF_UP);
    }

    static String normalizeUrl(String url) {
        String u = url.strip();
        while (u.endsWith("/") || u.endsWith(".") || u.endsWith(",") || u.endsWith(")")) u = u.substring(0, u.length() - 1);
        return u.toLowerCase(Locale.ROOT);
    }

    private static String findSupplied(String candidate, List<String> supplied) {
        String n = normalizeUrl(candidate);
        for (String s : supplied) if (normalizeUrl(s).equals(n)) return s;
        return null;
    }

    private static List<String> nonNull(List<String> l) {
        return l == null ? List.of() : l;
    }
}
