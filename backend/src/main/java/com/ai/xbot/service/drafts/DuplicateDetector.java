package com.ai.xbot.service.drafts;

import com.ai.xbot.service.text.TextSimilarity;
import java.time.Instant;
import java.util.List;

/**
 * Compares a candidate with recent drafts. Exact (normalised-hash) matches and very close wording are
 * discarded; moderately similar drafts are kept with a warning so the reviewer can decide.
 */
public final class DuplicateDetector {

    public static final double DISCARD_THRESHOLD = 0.85;
    public static final double WARN_THRESHOLD = 0.6;

    private DuplicateDetector() {}

    public record Existing(String content, String contentHash, Instant createdAt) {}

    public sealed interface Verdict permits Unique, Similar, Duplicate {}
    public record Unique() implements Verdict {}
    public record Similar(double score, Instant otherCreatedAt) implements Verdict {}
    public record Duplicate(String reason) implements Verdict {}

    public static Verdict check(String content, String contentHash, List<Existing> recent) {
        Similar best = null;
        for (Existing e : recent) {
            if (e.contentHash().equals(contentHash)) {
                return new Duplicate("Identical to an earlier draft");
            }
            double score = TextSimilarity.jaccard(content, e.content());
            if (score >= DISCARD_THRESHOLD) {
                return new Duplicate("Nearly identical to an earlier draft (" + Math.round(score * 100) + "% similar)");
            }
            if (score >= WARN_THRESHOLD && (best == null || score > best.score())) {
                best = new Similar(score, e.createdAt());
            }
        }
        return best == null ? new Unique() : best;
    }
}
