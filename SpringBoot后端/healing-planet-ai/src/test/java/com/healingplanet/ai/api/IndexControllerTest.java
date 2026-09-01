package com.healingplanet.ai.api;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.IndexStatus;
import com.healingplanet.ai.ingestion.IndexStatusService;
import com.healingplanet.ai.ingestion.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IndexControllerTest {

    @Test
    void shouldExposeAuthorizedPersistentIndexStatus() {
        RagProperties properties = new RagProperties();
        properties.setInternalApiKey("internal-secret");
        IndexStatusService statusService = mock(IndexStatusService.class);
        when(statusService.status()).thenReturn(new IndexStatus(Instant.parse("2026-09-01T12:00:00Z"), "fingerprint",
                List.of(), List.of()));
        IndexController controller = new IndexController(mock(IngestionService.class), statusService, properties);
        WebTestClient client = WebTestClient.bindToController(controller).build();

        client.get().uri("/internal/index/status")
                .header("X-Internal-Api-Key", "internal-secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.currentFingerprint").isEqualTo("fingerprint");
        client.get().uri("/internal/index/status")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
