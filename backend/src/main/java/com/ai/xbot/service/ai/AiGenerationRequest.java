package com.ai.xbot.service.ai;

/** A fully built prompt pair ready to send. */
public record AiGenerationRequest(String systemPrompt, String userPrompt) {
}
