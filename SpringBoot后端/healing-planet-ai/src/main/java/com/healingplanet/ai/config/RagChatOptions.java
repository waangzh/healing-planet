package com.healingplanet.ai.config;

import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

/** 将不可变运行时配置转为单次 ChatClient 调用的 options，不修改共享 ChatModel。 */
@Component
public class RagChatOptions {
    public OpenAiChatOptions from(RagRuntimeConfig config) {
        RagRuntimeConfig.Generation generation = config.generation();
        return OpenAiChatOptions.builder().model(generation.model()).temperature(generation.temperature())
                .maxTokens(generation.maxTokens()).build();
    }
}
