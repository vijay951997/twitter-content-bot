package com.ai.xbot.service.drafts;

import com.ai.xbot.entity.FactualClaim;
import com.ai.xbot.entity.NewsArticle;
import com.ai.xbot.entity.NewsSource;
import com.ai.xbot.entity.enums.ClaimType;
import com.ai.xbot.entity.enums.PostSlot;
import com.ai.xbot.entity.enums.VerificationStatus;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Turns an analysed, verified article into grounded source material for drafting.
 * <ul>
 *   <li>VERIFIED facts may be stated as fact</li>
 *   <li>PARTIALLY_VERIFIED facts are included only with an instruction to attribute them to the publisher</li>
 *   <li>statements by people are always attributed to the speaker</li>
 *   <li>UNVERIFIED, OPINION and CONFLICTING_INFORMATION claims are left out entirely</li>
 * </ul>
 */
public record ArticleBrief(String topic, String notes, List<SourceLink> links, VerificationStatus ceiling,
                           PostSlot suggestedSlot, int usableClaims) {

    public record SourceLink(String url, UUID articleId, UUID claimId) {}

    static final Set<String> MORNING = Set.of("GOVERNANCE", "POLICY", "INFRASTRUCTURE", "TRANSPORT", "EDUCATION",
            "HEALTHCARE", "EMPLOYMENT");
    static final Set<String> AFTERNOON = Set.of("POLITICS", "TVK", "LEGISLATURE", "ELECTIONS");

    public static Optional<ArticleBrief> build(NewsArticle a, NewsSource source, List<FactualClaim> claims, ZoneId zone) {
        List<String> verified = new ArrayList<>();
        List<String> attributedToPublisher = new ArrayList<>();
        List<String> statements = new ArrayList<>();
        List<SourceLink> links = new ArrayList<>();
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        urls.add(a.getOriginalUrl());
        links.add(new SourceLink(a.getOriginalUrl(), a.getId(), null));
        boolean allVerified = true;

        for (FactualClaim c : claims) {
            VerificationStatus st = c.getVerificationStatus();
            if (c.getClaimType() == ClaimType.OPINION) continue;
            if (st != VerificationStatus.VERIFIED && st != VerificationStatus.PARTIALLY_VERIFIED) continue;

            if (c.getClaimType() == ClaimType.ATTRIBUTED_STATEMENT) {
                statements.add(c.getClaimText() + (st == VerificationStatus.VERIFIED
                        ? " (confirmed by multiple sources)"
                        : " (as reported by " + source.getName() + ")"));
            } else if (st == VerificationStatus.VERIFIED) {
                verified.add(c.getClaimText());
            } else {
                attributedToPublisher.add(c.getClaimText());
            }
            if (st != VerificationStatus.VERIFIED) allVerified = false;
            links.add(new SourceLink(a.getOriginalUrl(), a.getId(), c.getId()));
            urls.addAll(c.getSupportingUrls());
        }

        int usable = verified.size() + attributedToPublisher.size() + statements.size();
        if (usable == 0) return Optional.empty();

        StringBuilder n = new StringBuilder();
        n.append("Report: ").append(a.getTitle()).append(" (").append(source.getName()).append(", ")
                .append(DateTimeFormatter.ofPattern("d MMM yyyy").format(a.effectiveTime().atZone(zone))).append(")\n");
        if (a.getSummary() != null) n.append("Summary: ").append(a.getSummary()).append("\n");
        if (!verified.isEmpty()) {
            n.append("\nVERIFIED FACTS (confirmed by an official source or independent publishers; may be stated as fact):\n");
            verified.forEach(v -> n.append("- ").append(v).append("\n"));
        }
        if (!attributedToPublisher.isEmpty()) {
            n.append("\nREPORTED BY ONE PUBLISHER ONLY (must be attributed, e.g. \"According to ")
                    .append(source.getName()).append(", ...\"):\n");
            attributedToPublisher.forEach(v -> n.append("- ").append(v).append("\n"));
        }
        if (!statements.isEmpty()) {
            n.append("\nSTATEMENTS (always attribute to the speaker; never present as established fact):\n");
            statements.forEach(v -> n.append("- ").append(v).append("\n"));
        }
        n.append("\nUse only the facts above. Anything not listed here must not appear in the post.");

        // Keep the article itself plus up to 4 corroborating links.
        List<String> limited = urls.stream().limit(5).toList();
        List<SourceLink> keptLinks = new ArrayList<>(links);
        for (String u : limited) {
            if (keptLinks.stream().noneMatch(l -> l.url().equals(u))) keptLinks.add(new SourceLink(u, null, null));
        }

        String cat = a.getTopicCategory() == null ? "" : a.getTopicCategory();
        PostSlot slot = MORNING.contains(cat) ? PostSlot.MORNING : AFTERNOON.contains(cat) ? PostSlot.AFTERNOON : PostSlot.EVENING;
        String topic = a.getTitle().length() > 300 ? a.getTitle().substring(0, 300) : a.getTitle();
        return Optional.of(new ArticleBrief(topic, n.toString(), keptLinks,
                allVerified ? VerificationStatus.VERIFIED : VerificationStatus.PARTIALLY_VERIFIED, slot, usable));
    }

    public List<String> urls() {
        return links.stream().map(SourceLink::url).distinct().limit(5).toList();
    }

    public static Set<String> categoriesFor(PostSlot slot) {
        return switch (slot) {
            case MORNING -> MORNING;
            case AFTERNOON -> AFTERNOON;
            case EVENING -> Set.of("CIVIC_ISSUES", "PUBLIC_INTEREST", "POLICY", "GOVERNANCE");
        };
    }
}
