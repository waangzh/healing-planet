package com.healingplanet.ai.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagChatOptionsTest {

    @Test
    void shouldBuildRequestLevelOptionsFromRuntimeSnapshot() {
        RagProperties properties = new RagProperties();
        properties.getGeneration().setModel("test-chat-model");
        properties.getGeneration().setTemperature(0.7);
        properties.getGeneration().setMaxTokens(1536);

        var options = new RagChatOptions().from(RagRuntimeConfig.from(properties));

        assertThat(options.getModel()).isEqualTo("test-chat-model");
        assertThat(options.getTemperature()).isEqualTo(0.7);
        assertThat(options.getMaxTokens()).isEqualTo(1536);
    }
}
