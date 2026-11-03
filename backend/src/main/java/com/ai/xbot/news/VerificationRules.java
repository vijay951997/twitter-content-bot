package com.ai.xbot.news;

import com.ai.xbot.entity.enums.ClaimType;
import com.ai.xbot.entity.enums.VerificationStatus;
import java.util.*;

/**
 * Deterministic verification rules. The AI only says which candidate reports state the same fact
 * (or contradict it); this class decides the status from who those reports come from.
 *
 * <ul>
 *   <li>OPINION → UNVERIFIED (opinions aren't facts)</li>
 *   <li>any independent contradiction → CONFLICTING_INFORMATION</li>
 *   <li>stated by an official primary source (the claim's own source or a supporting one) → VERIFIED</li>
 *   <li>reported by the claim's source and at least one other independent reputable publisher → VERIFIED</li>
 *   <li>one reputable publisher only → PARTIALLY_VERIFIED (must be attributed when posted)</li>
 *   <li>otherwise → UNVERIFIED</li>
 * </ul>
 * Publishers are independent when their website domains differ, so two sections of the same newspaper
 * never corroborate each other.
 */
public final class VerificationRules {

    private VerificationRules() {}

    public record SourceInfo(String url, String domain, boolean primary, int tier) {}

    public record Decision(VerificationStatus status, String method, int independentSources,
                           List<String> supportingUrls, List<String> conflictingUrls, String note) {}

    public static Decision decide(ClaimType type, SourceInfo own, List<SourceInfo> supporters,
                                  List<SourceInfo> contradictors, String aiNote) {
        if (type == ClaimType.OPINION) {
            return new Decision(VerificationStatus.UNVERIFIED, "OPINION", 1, List.of(own.url()), List.of(),
                    "Opinion or prediction — not a verifiable fact.");
        }

        List<SourceInfo> indepSupport = independent(own, supporters);
        List<SourceInfo> indepContra = independent(own, contradictors);
        List<String> supportUrls = new ArrayList<>(List.of(own.url()));
        indepSupport.forEach(s -> supportUrls.add(s.url()));
        List<String> contraUrls = indepContra.stream().map(SourceInfo::url).toList();
        int count = 1 + distinctDomains(indepSupport);

        if (!indepContra.isEmpty()) {
            return new Decision(VerificationStatus.CONFLICTING_INFORMATION, "CONFLICT", count, supportUrls, contraUrls,
                    note(aiNote, "Another source reports this differently."));
        }
        boolean primaryInvolved = (own.primary() && own.tier() == 1)
                || indepSupport.stream().anyMatch(s -> s.primary() && s.tier() == 1);
        if (primaryInvolved) {
            return new Decision(VerificationStatus.VERIFIED, "PRIMARY_SOURCE", count, supportUrls, contraUrls,
                    note(aiNote, "Stated by an official primary source."));
        }
        long reputableIndependent = indepSupport.stream().filter(s -> s.tier() <= 2).map(SourceInfo::domain).distinct().count();
        if (own.tier() <= 2 && reputableIndependent >= 1) {
            return new Decision(VerificationStatus.VERIFIED, "INDEPENDENT_SOURCES", count, supportUrls, contraUrls,
                    note(aiNote, "Reported by " + count + " independent publishers."));
        }
        if (own.tier() <= 2) {
            return new Decision(VerificationStatus.PARTIALLY_VERIFIED, "SINGLE_SOURCE", count, supportUrls, contraUrls,
                    note(aiNote, "Reported by one publisher so far — attribute it to them."));
        }
        return new Decision(VerificationStatus.UNVERIFIED, "LOW_RELIABILITY_SOURCE", count, supportUrls, contraUrls,
                note(aiNote, "Only reported by a lower-reliability source."));
    }

    private static List<SourceInfo> independent(SourceInfo own, List<SourceInfo> others) {
        Map<String, SourceInfo> byDomain = new LinkedHashMap<>();
        for (SourceInfo s : others) {
            if (!s.domain().equals(own.domain())) byDomain.putIfAbsent(s.domain(), s);
        }
        return new ArrayList<>(byDomain.values());
    }

    private static int distinctDomains(List<SourceInfo> list) {
        return (int) list.stream().map(SourceInfo::domain).distinct().count();
    }

    private static String note(String ai, String fallback) {
        return ai == null || ai.isBlank() ? fallback : fallback + " " + ai.strip();
    }
}
