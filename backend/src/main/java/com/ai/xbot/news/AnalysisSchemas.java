package com.ai.xbot.news;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict JSON schemas for article analysis and claim verification. */
final class AnalysisSchemas {

    static final List<String> CATEGORIES = List.of("GOVERNANCE", "POLICY", "INFRASTRUCTURE", "EDUCATION", "HEALTHCARE",
            "EMPLOYMENT", "TRANSPORT", "CIVIC_ISSUES", "LEGISLATURE", "ELECTIONS", "POLITICS", "TVK", "PUBLIC_INTEREST");

    private AnalysisSchemas() {}

    static Map<String, Object> analysis() {
        Map<String, Object> claimProps = new LinkedHashMap<>();
        claimProps.put("text", Map.of("type", "string"));
        claimProps.put("type", Map.of("type", "string", "enum", List.of("FACT", "STATISTIC", "ATTRIBUTED_STATEMENT", "OPINION")));
        claimProps.put("attributed_to", Map.of("type", List.of("string", "null")));
        Map<String, Object> claim = obj(claimProps);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("summary", Map.of("type", "string"));
        props.put("category", Map.of("type", "string", "enum", CATEGORIES));
        props.put("claims", Map.of("type", "array", "items", claim));
        return obj(props);
    }

    static Map<String, Object> verification() {
        Map<String, Object> intArray = Map.of("type", "array", "items", Map.of("type", "integer"));
        Map<String, Object> aProps = new LinkedHashMap<>();
        aProps.put("claim_index", Map.of("type", "integer"));
        aProps.put("supports", intArray);
        aProps.put("contradicts", intArray);
        aProps.put("note", Map.of("type", "string"));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("assessments", Map.of("type", "array", "items", obj(aProps)));
        return obj(props);
    }

    private static Map<String, Object> obj(Map<String, Object> props) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("type", "object");
        o.put("properties", props);
        o.put("required", List.copyOf(props.keySet()));
        o.put("additionalProperties", false);
        return o;
    }
}
