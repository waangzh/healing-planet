package com.healingplanet.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagConfigServiceTest {

    @Test
    void shouldRejectInvalidWeightGroupsBeforeSavingDraft() {
        RagProperties defaults = new RagProperties();
        RagRuntimeConfig current = RagRuntimeConfig.from(defaults);
        RagRuntimeConfig invalid = new RagRuntimeConfig(0, current.denseTopK(), current.sparseTopK(),
                current.finalTopK(), current.similarityThreshold(), current.retrievalMode(), current.rrfK(),
                current.adaptiveRecall(), current.recallQualification(),
                current.rerankerEnabled(), new RagRuntimeConfig.SourceAwareRanking(
                true, 31, 0.8, 0.8, 0.7, 0.2, 0.1, 0.62, 0.15, 0.13, 0.05, 0.05,
                0.2, 0.8, 2, 1.5, 0.05, 1000, 365), true, 3,
                current.contextAssembly(),
                current.answerability(), current.generation(), current.rerankerClient());
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
                RagProperties.RetrievalMode.HYBRID_RRF, 70, oldConfig.adaptiveRecall(), oldConfig.recallQualification(), true,
                oldConfig.sourceAwareRanking(), true, 3,
                oldConfig.contextAssembly(),
                oldConfig.answerability(), oldConfig.generation(), oldConfig.rerankerClient());
        RagConfigRevision current = revision(1, RagConfigStatus.ACTIVE, oldConfig);
        RagConfigRevision target = revision(2, RagConfigStatus.VALIDATED, newConfig);
        RagConfigRevision activeTarget = revision(2, RagConfigStatus.ACTIVE, newConfig);
        RagConfigRepository repository = mock(RagConfigRepository.class);
        when(repository.findByRevisionForUpdate(2)).thenReturn(Optional.of(target));
        when(repository.findActiveForUpdate()).thenReturn(Optional.of(current));
        when(repository.findByRevision(2)).thenReturn(Optional.of(activeTarget));
        RagRuntimeConfigProvider provider = new RagRuntimeConfigProvider(defaults);
        RagExternalClientManager clientManager = mock(RagExternalClientManager.class);
        when(clientManager.prepare(newConfig)).thenReturn(new RagRuntimeSnapshot(newConfig, null));
        RagConfigService service = new RagConfigService(repository, provider, new RagRuntimeConfigValidator(),
                defaults, new ObjectMapper(), clientManager);

        RagConfigRevisionView published = service.publish(2, "admin");

        assertThat(published.status()).isEqualTo(RagConfigStatus.ACTIVE);
        assertThat(provider.snapshot()).isEqualTo(newConfig);
        verify(repository).markSuperseded(1);
        verify(repository).markActive(2, "admin", null);
        verify(repository).audit(eq(2L), eq("PUBLISHED"), eq("admin"), eq(1L), any());
        verify(clientManager).prepare(newConfig);
    }

    @Test
    void shouldKeepCurrentSnapshotWhenCandidateConnectionProbeFails() {
        RagProperties defaults = new RagProperties();
        RagRuntimeConfig candidate = RagRuntimeConfig.from(defaults).withRevision(2);
        RagConfigRevision target = revision(2, RagConfigStatus.VALIDATED, candidate);
        RagConfigRepository repository = mock(RagConfigRepository.class);
        when(repository.findByRevisionForUpdate(2)).thenReturn(Optional.of(target));
        RagExternalClientManager clientManager = mock(RagExternalClientManager.class);
        when(clientManager.prepare(candidate)).thenThrow(new IllegalStateException("外部连接测试未通过"));
        RagRuntimeConfigProvider provider = new RagRuntimeConfigProvider(defaults);
        RagConfigService service = new RagConfigService(repository, provider, new RagRuntimeConfigValidator(),
                defaults, new ObjectMapper(), clientManager);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.publish(2, "admin"))
                .hasMessageContaining("外部连接测试未通过");

        assertThat(provider.snapshot().revision()).isEqualTo(0);
        verify(repository, never()).markActive(eq(2L), eq("admin"), eq(null));
    }

    @Test
    void shouldSynchronizeOnlyWhenDatabaseActiveRevisionIsNewer() {
        RagProperties defaults = new RagProperties();
        RagRuntimeConfig config = RagRuntimeConfig.from(defaults).withRevision(8);
        RagConfigRepository repository = mock(RagConfigRepository.class);
        when(repository.findActive()).thenReturn(Optional.of(revision(8, RagConfigStatus.ACTIVE, config)));
        RagExternalClientManager clientManager = mock(RagExternalClientManager.class);
        when(clientManager.prepare(config)).thenReturn(new RagRuntimeSnapshot(config, null));
        RagRuntimeConfigProvider provider = new RagRuntimeConfigProvider(defaults);
        RagConfigService service = new RagConfigService(repository, provider, new RagRuntimeConfigValidator(),
                defaults, new ObjectMapper(), clientManager);

        assertThat(service.refreshActiveRuntime()).isTrue();
        assertThat(provider.snapshot().revision()).isEqualTo(8);
        assertThat(service.refreshActiveRuntime()).isFalse();
        verify(clientManager).prepare(config);
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
