package com.loreweave.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Three Gemini models, each matched to its job (and — usefully on the free tier — each with its own
 * per-model daily request quota, so the guard never starves narration and vice-versa):
 *  - streaming model  → narration streamed to the client via SSE (the turn's prose).
 *  - structured model → the cheap blocking "extract new facts" call (JSON out); also the opening scene.
 *  - guard model      → the contradiction guard's NLI classifier — a small, deterministic yes/no job,
 *                       so it runs on the cheaper flash-lite model on its own quota budget.
 */
@Configuration
public class LlmConfig {

    @Value("${loreweave.llm.api-key}")    private String apiKey;
    @Value("${loreweave.llm.model}")      private String model;
    @Value("${loreweave.guard.model}")    private String guardModel;

    @Bean
    StreamingChatLanguageModel streamingModel() {
        return GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(model)
                .temperature(0.9)     // creative narration
                .build();
    }

    /** @Primary so the narration/extraction path injects this without a qualifier. */
    @Bean
    @Primary
    ChatLanguageModel structuredModel() {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(model)
                .temperature(0.1)     // deterministic extraction
                .build();
    }

    /** The contradiction guard's classifier — cheaper model, its own quota, zero temperature. */
    @Bean("guardModel")
    ChatLanguageModel guardModel() {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(guardModel)
                .temperature(0.0)     // a consistency judgment should be as deterministic as possible
                .build();
    }
}
