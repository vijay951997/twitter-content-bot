package com.ai.xbot.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Sends a prompt and returns the model's output, constrained to the given JSON schema. */
public interface AiJsonClient {

    JsonNode complete(String systemPrompt, String userPrompt, String schemaName, Map<String, Object> schema);

    String modelName();
}
