package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class PlantEntityDisambiguatorTest {

    @Test
    void shouldAcceptKnownCandidateReturnedByLlmWhenConfidenceIsHighEnough() {
        RagProperties properties = new RagProperties();
        PlantEntityDisambiguator disambiguator = new PlantEntityDisambiguator(properties,
                (systemPrompt, userPrompt) -> new PlantEntityDisambiguator.LlmDecision("KNOWN", "1", 0.91, "typo"));

        var result = disambiguator.disambiguate("绿箩能一直晒大太阳不？", "绿箩", List.of(
                new PlantEntityDisambiguator.CandidateOption("1", Set.of("绿萝"), 0.44, 0.50),
                new PlantEntityDisambiguator.CandidateOption("10", Set.of("芦荟"), 0.39, 0.21)
        ));

        assertThat(result.attempted()).isTrue();
        assertThat(result.known()).isTrue();
        assertThat(result.canonicalPlantId()).isEqualTo("1");
    }

    @Test
    void shouldPreserveAmbiguousDecisionWhenContextCannotChooseACandidate() {
        RagProperties properties = new RagProperties();
        PlantEntityDisambiguator disambiguator = new PlantEntityDisambiguator(properties,
                (systemPrompt, userPrompt) -> new PlantEntityDisambiguator.LlmDecision(
                        "AMBIGUOUS", "", 0.76, "insufficient context"));

        var result = disambiguator.disambiguate("万年青的叶片为什么发黄？", "万年青", List.of(
                new PlantEntityDisambiguator.CandidateOption("30", Set.of("广东万年青", "万年青"), 0, 1),
                new PlantEntityDisambiguator.CandidateOption("31", Set.of("花叶万年青", "万年青"), 0, 1)
        ));

        assertThat(result.attempted()).isTrue();
        assertThat(result.ambiguous()).isTrue();
        assertThat(result.reason()).isEqualTo("llm_ambiguous");
    }

    @Test
    void shouldRejectInvalidCandidateReturnedByLlm() {
        RagProperties properties = new RagProperties();
        PlantEntityDisambiguator disambiguator = new PlantEntityDisambiguator(properties,
                (systemPrompt, userPrompt) -> new PlantEntityDisambiguator.LlmDecision("KNOWN", "999", 0.95, "bad"));

        var result = disambiguator.disambiguate("绿箩能一直晒大太阳不？", "绿箩", List.of(
                new PlantEntityDisambiguator.CandidateOption("1", Set.of("绿萝"), 0.44, 0.50)
        ));

        assertThat(result.attempted()).isTrue();
        assertThat(result.known()).isFalse();
        assertThat(result.reason()).isEqualTo("llm_returned_invalid_candidate");
    }

    @Test
    void shouldRejectLowConfidenceLlmResult() {
        RagProperties properties = new RagProperties();
        PlantEntityDisambiguator disambiguator = new PlantEntityDisambiguator(properties,
                (systemPrompt, userPrompt) -> new PlantEntityDisambiguator.LlmDecision("KNOWN", "1", 0.61, "weak"));

        var result = disambiguator.disambiguate("绿箩能一直晒大太阳不？", "绿箩", List.of(
                new PlantEntityDisambiguator.CandidateOption("1", Set.of("绿萝"), 0.44, 0.50)
        ));

        assertThat(result.attempted()).isTrue();
        assertThat(result.known()).isFalse();
        assertThat(result.reason()).isEqualTo("llm_confidence_too_low");
    }

    @Test
    void shouldFailClosedWhenStructuredCallThrows() {
        RagProperties properties = new RagProperties();
        PlantEntityDisambiguator disambiguator = new PlantEntityDisambiguator(properties,
                (systemPrompt, userPrompt) -> {
                    throw new IllegalStateException("boom");
                });

        var result = disambiguator.disambiguate("绿箩能一直晒大太阳不？", "绿箩", List.of(
                new PlantEntityDisambiguator.CandidateOption("1", Set.of("绿萝"), 0.44, 0.50)
        ));

        assertThat(result.attempted()).isTrue();
        assertThat(result.known()).isFalse();
        assertThat(result.reason()).isEqualTo("llm_disambiguation_failed");
    }

    @Test
    void shouldCacheValidatedDecisionsForTheSameQueryAndCandidates() {
        RagProperties properties = new RagProperties();
        AtomicInteger calls = new AtomicInteger();
        PlantEntityDisambiguator disambiguator = new PlantEntityDisambiguator(properties,
                (systemPrompt, userPrompt) -> {
                    calls.incrementAndGet();
                    return new PlantEntityDisambiguator.LlmDecision("KNOWN", "1", 0.91, "typo");
                });
        List<PlantEntityDisambiguator.CandidateOption> candidates = List.of(
                new PlantEntityDisambiguator.CandidateOption("1", Set.of("绿萝"), 0.44, 0.50));

        disambiguator.disambiguate("绿箩能一直晒大太阳不？", "绿箩", candidates);
        disambiguator.disambiguate("绿箩能一直晒大太阳不？", "绿箩", candidates);

        assertThat(calls).hasValue(1);
    }

    @Test
    void shouldNotCacheFailedDecisionSoSameQueryCanRetry() {
        RagProperties properties = new RagProperties();
        AtomicInteger calls = new AtomicInteger();
        PlantEntityDisambiguator disambiguator = new PlantEntityDisambiguator(properties,
                (systemPrompt, userPrompt) -> {
                    if (calls.getAndIncrement() == 0) {
                        throw new IllegalStateException("boom");
                    }
                    return new PlantEntityDisambiguator.LlmDecision("KNOWN", "1", 0.91, "retry-ok");
                });
        List<PlantEntityDisambiguator.CandidateOption> candidates = List.of(
                new PlantEntityDisambiguator.CandidateOption("1", Set.of("绿萝"), 0.44, 0.50));

        assertThat(disambiguator.disambiguate("绿箩能一直晒大太阳不？", "绿箩", candidates).reason())
                .isEqualTo("llm_disambiguation_failed");

        var retried = disambiguator.disambiguate("绿箩能一直晒大太阳不？", "绿箩", candidates);

        assertThat(retried.known()).isTrue();
        assertThat(retried.canonicalPlantId()).isEqualTo("1");
        assertThat(calls).hasValue(2);
    }

    @Test
    void shouldOpenCircuitAfterConfiguredConsecutiveFailures() {
        RagProperties properties = new RagProperties();
        properties.getEntityResolution().setCircuitBreakerFailureThreshold(2);
        AtomicInteger calls = new AtomicInteger();
        PlantEntityDisambiguator disambiguator = new PlantEntityDisambiguator(properties,
                (systemPrompt, userPrompt) -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("boom");
                });
        List<PlantEntityDisambiguator.CandidateOption> candidates = List.of(
                new PlantEntityDisambiguator.CandidateOption("1", Set.of("绿萝"), 0.44, 0.50));

        assertThat(disambiguator.disambiguate("绿箩1", "绿箩", candidates).reason())
                .isEqualTo("llm_disambiguation_failed");
        assertThat(disambiguator.disambiguate("绿箩2", "绿箩", candidates).reason())
                .isEqualTo("llm_disambiguation_failed");
        assertThat(disambiguator.disambiguate("绿箩3", "绿箩", candidates).reason())
                .isEqualTo("llm_disambiguation_circuit_open");
        assertThat(calls).hasValue(2);
    }

    @Test
    void shouldAllowOneHalfOpenProbeAndCloseCircuitAfterRecovery() {
        AtomicLong clock = new AtomicLong();
        PlantEntityDisambiguator.FailureCircuitBreaker breaker =
                new PlantEntityDisambiguator.FailureCircuitBreaker(1, 100, clock::get);

        try {
            breaker.execute(() -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException ignored) {
            // Expected first failure opens the circuit.
        }
        assertThatThrownByCircuitOpen(breaker);

        clock.addAndGet(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(101));
        assertThat(breaker.execute(() -> "recovered")).isEqualTo("recovered");
        assertThat(breaker.execute(() -> "closed")).isEqualTo("closed");
    }

    @Test
    void shouldRetryAfterCircuitCooldownForRealDisambiguator() throws InterruptedException {
        RagProperties properties = new RagProperties();
        properties.getEntityResolution().setCircuitBreakerFailureThreshold(2);
        properties.getEntityResolution().setCircuitBreakerOpenMillis(20);
        AtomicInteger calls = new AtomicInteger();
        PlantEntityDisambiguator disambiguator = new PlantEntityDisambiguator(properties,
                (systemPrompt, userPrompt) -> {
                    if (calls.getAndIncrement() < 2) {
                        throw new IllegalStateException("boom");
                    }
                    return new PlantEntityDisambiguator.LlmDecision("KNOWN", "1", 0.93, "recovered");
                });
        List<PlantEntityDisambiguator.CandidateOption> candidates = List.of(
                new PlantEntityDisambiguator.CandidateOption("1", Set.of("绿萝"), 0.44, 0.50));

        assertThat(disambiguator.disambiguate("绿箩1", "绿箩", candidates).reason())
                .isEqualTo("llm_disambiguation_failed");
        assertThat(disambiguator.disambiguate("绿箩2", "绿箩", candidates).reason())
                .isEqualTo("llm_disambiguation_failed");
        assertThat(disambiguator.disambiguate("绿箩3", "绿箩", candidates).reason())
                .isEqualTo("llm_disambiguation_circuit_open");

        Thread.sleep(30);

        var recovered = disambiguator.disambiguate("绿箩4", "绿箩", candidates);
        assertThat(recovered.known()).isTrue();
        assertThat(recovered.canonicalPlantId()).isEqualTo("1");
    }

    private void assertThatThrownByCircuitOpen(PlantEntityDisambiguator.FailureCircuitBreaker breaker) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> breaker.execute(() -> "blocked"))
                .isInstanceOf(PlantEntityDisambiguator.CircuitOpenException.class);
    }
}
