package com.ai.xbot.service.ai;

import java.util.List;

/**
 * Parsed structured output from the model.
 *
 * @param drafts      draft variations returned by the model
 * @param rawJson     the model's JSON output, stored for audit/debugging (never contains secrets)
 * @param promptTokens / completionTokens usage reported by the API, null if not reported
 */
public record AiGenerationResult(List<AiDraft> drafts, String rawJson, Integer promptTokens, Integer completionTokens) {

    /** One draft as returned by the model. Nothing here is trusted until validated. */
    public record AiDraft(
            String content,
            String language,
            String category,
            List<String> sourceUrls,
            List<String> verifiedClaims,
            List<String> unverifiedClaims,
            String verificationStatus,
            List<String> riskFlags,
            Double qualityEstimate) {
    }
}
