package com.ai.xbot.service.ai;

/** Generates draft posts from a prompt. The production implementation calls OpenAI. */
public interface AiDraftGenerator {

    AiGenerationResult generate(AiGenerationRequest request);

    /** Model identifier recorded on each draft. */
    String modelName();
}
