package com.healingplanet.ai.retrieval;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;
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
            stop(sample, stage, source, status);
        }
    }

    public <T> Flux<T> timeFlux(String stage, String source, Supplier<Flux<T>> operation) {
        return Flux.defer(() -> {
            Timer.Sample sample = Timer.start(registry);
            AtomicReference<String> status = new AtomicReference<>("ok");
            try {
                return operation.get()
                        .doOnError(ignored -> status.set("error"))
                        .doOnCancel(() -> status.set("cancelled"))
                        .doFinally(ignored -> stop(sample, stage, source, status.get()));
            } catch (RuntimeException exception) {
                stop(sample, stage, source, "error");
                throw exception;
            }
        });
    }

    public void recordCandidates(String stage, String source, int count) {
        DistributionSummary.builder(CANDIDATE_SUMMARY)
                .description("RAG retrieval candidate count")
                .tag("stage", stage)
                .tag("source", source)
                .register(registry)
                .record(count);
    }

    private void stop(Timer.Sample sample, String stage, String source, String status) {
        sample.stop(Timer.builder(STAGE_TIMER)
                .description("RAG pipeline stage duration")
                .publishPercentiles(0.5, 0.95)
                .publishPercentileHistogram()
                .tag("stage", stage)
                .tag("source", source)
                .tag("status", status)
                .register(registry));
    }
}
