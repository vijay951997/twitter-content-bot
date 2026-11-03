package com.ai.xbot.integration.openai;

import java.util.List;
import java.util.Map;

/**
 * JSON schema sent to OpenAI Structured Outputs (strict mode). Strict mode requires every property
 * to be listed in "required" and additionalProperties=false on every object.
 * Mirrors the draft JSON format in the project specification; publish time is chosen by the
 * application (from the slot), not by the model.
 */
final class DraftOutputSchema {

    private DraftOutputSchema() {}

    static Map<String, Object> schema() {
        Map<String, Object> stringArray = Map.of("type", "array", "items", Map.of("type", "string"));

        Map<String, Object> draftProps = new java.util.LinkedHashMap<>();
        draftProps.put("content", Map.of("type", "string"));
        draftProps.put("language", Map.of("type", "string", "enum", List.of("ta", "en", "mixed")));
        draftProps.put("category", Map.of("type", "string"));
        draftProps.put("source_urls", stringArray);
        draftProps.put("verified_claims", stringArray);
        draftProps.put("unverified_claims", stringArray);
        draftProps.put("verification_status", Map.of("type", "string", "enum",
                List.of("VERIFIED", "PARTIALLY_VERIFIED", "UNVERIFIED", "CONFLICTING_INFORMATION")));
        draftProps.put("risk_flags", Map.of("type", "array", "items", Map.of("type", "string", "enum",
                List.of("UNVERIFIED_ALLEGATION", "PERSONAL_ATTACK", "UNSUPPORTED_STATISTIC",
                        "UNATTRIBUTED_QUOTE", "INFLAMMATORY", "POSSIBLY_OUTDATED", "OPINION_AS_FACT"))));
        draftProps.put("quality_estimate", Map.of("type", "number"));
        draftProps.put("requires_human_approval", Map.of("type", "boolean"));

        Map<String, Object> draft = Map.of(
                "type", "object",
                "properties", draftProps,
                "required", List.copyOf(draftProps.keySet()),
                "additionalProperties", false);

        return Map.of(
                "type", "object",
                "properties", Map.of("drafts", Map.of("type", "array", "items", draft)),
                "required", List.of("drafts"),
                "additionalProperties", false);
    }
}
