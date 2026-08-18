package com.healingplanet.ai.retrieval;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class RetrievalMetrics {

    static final String STAGE_TIMER = "healing.planet.rag.retrieval.stage";
    static final String CANDIDATE_SUMMARY = "healing.planet.rag.retrieval.candidates";

    private final MeterRegistry registry;

    public RetrievalMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public <T> T time(String stage, String source, Supplier<T> operation) {
        Timer.Sample sample = Timer.start(registry);
        String status = "ok";
        try {
            return operation.get();
        } catch (RuntimeException exception) {
            status = "error";
            throw exception;
        } finally {
            sample.stop(Timer.builder(STAGE_TIMER)
                    .description("RAG retrieval stage duration")
                    .tag("stage", stage)
                    .tag("source", source)
                    .tag("status", status)
                    .register(registry));
        }
    }

    public void recordCandidates(String stage, String source, int count) {
        DistributionSummary.builder(CANDIDATE_SUMMARY)
                .description("RAG retrieval candidate count")
                .tag("stage", stage)
                .tag("source", source)
                .register(registry)
                .record(count);
    }
}
