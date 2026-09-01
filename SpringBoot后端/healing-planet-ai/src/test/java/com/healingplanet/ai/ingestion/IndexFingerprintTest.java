package com.healingplanet.ai.ingestion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IndexFingerprintTest {

    @Test
    void shouldChangeWhenAnyEmbeddingCompatibilityVersionChanges() {
        IndexFingerprint baseline = new IndexFingerprint("BAAI/bge-m3", "embedding-content-v2", "chunk-schema-v2");

        assertThat(baseline.value()).hasSize(64);
        assertThat(new IndexFingerprint("BAAI/bge-m3-v2", "embedding-content-v2", "chunk-schema-v2").value())
                .isNotEqualTo(baseline.value());
        assertThat(new IndexFingerprint("BAAI/bge-m3", "embedding-content-v3", "chunk-schema-v2").value())
                .isNotEqualTo(baseline.value());
        assertThat(new IndexFingerprint("BAAI/bge-m3", "embedding-content-v2", "chunk-schema-v3").value())
                .isNotEqualTo(baseline.value());
    }
}
