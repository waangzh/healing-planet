package com.healingplanet.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagConfigServiceTest {

    @Test
    void shouldRejectInvalidWeightGroupsBeforeSavingDraft() {
        RagProperties defaults = new RagProperties();
        RagRuntimeConfig current = RagRuntimeConfig.from(defaults);
        RagRuntimeConfig invalid = new RagRuntimeConfig(0, current.denseTopK(), current.sparseTopK(),
                current.finalTopK(), current.similarityThreshold(), current.retrievalMode(), current.rrfK(),
                current.rerankerEnabled(), new RagRuntimeConfig.SourceAwareRanking(
                true, 31, 0.8, 0.8, 0.7, 0.2, 0.1, 0.62, 0.15, 0.13, 0.05, 0.05,
                0.2, 0.8, 2, 1.5, 0.05, 1000, 365), true, 3);
        RagConfigRepository repository = mock(RagConfigRepository.class);
        RagConfigService service = service(repository, defaults);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.saveDraft(
                        new RagConfigDraftRequest(invalid, "invalid", "admin")))
                .hasMessageContaining("denseWeight + rrfWeight");
    }

    @Test
    void shouldActivateOnlyThePublishedValidatedSnapshot() {
        RagProperties defaults = new RagProperties();
        RagRuntimeConfig oldConfig = RagRuntimeConfig.from(defaults).withRevision(1);
        RagRuntimeConfig newConfig = new RagRuntimeConfig(2, 40, 35, 8, 0.3,
                RagProperties.RetrievalMode.HYBRID_RRF, 70, true, oldConfig.sourceAwareRanking(), true, 3);
        RagConfigRevision current = revision(1, RagConfigStatus.ACTIVE, oldConfig);
        RagConfigRevision target = revision(2, RagConfigStatus.VALIDATED, newConfig);
        RagConfigRevision activeTarget = revision(2, RagConfigStatus.ACTIVE, newConfig);
        RagConfigRepository repository = mock(RagConfigRepository.class);
        when(repository.findByRevisionForUpdate(2)).thenReturn(Optional.of(target));
        when(repository.findActiveForUpdate()).thenReturn(Optional.of(current));
        when(repository.findByRevision(2)).thenReturn(Optional.of(activeTarget));
        RagRuntimeConfigProvider provider = new RagRuntimeConfigProvider(defaults);
        RagConfigService service = new RagConfigService(repository, provider, new RagRuntimeConfigValidator(),
                defaults, new ObjectMapper());

        RagConfigRevisionView published = service.publish(2, "admin");

        assertThat(published.status()).isEqualTo(RagConfigStatus.ACTIVE);
        assertThat(provider.snapshot()).isEqualTo(newConfig);
        verify(repository).markSuperseded(1);
        verify(repository).markActive(2, "admin", null);
        verify(repository).audit(eq(2L), eq("PUBLISHED"), eq("admin"), eq(1L), any());
    }

    private RagConfigService service(RagConfigRepository repository, RagProperties defaults) {
        RagRuntimeConfigProvider provider = new RagRuntimeConfigProvider(defaults);
        return new RagConfigService(repository, provider, new RagRuntimeConfigValidator(), defaults, new ObjectMapper());
    }

    private RagConfigRevision revision(long version, RagConfigStatus status, RagRuntimeConfig config) {
        return new RagConfigRevision(version, status, config, "test", "admin", Instant.now(), null, null,
                null, null, null, null);
    }
}
