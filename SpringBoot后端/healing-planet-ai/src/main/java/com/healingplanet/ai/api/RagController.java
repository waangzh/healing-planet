package com.healingplanet.ai.api;

import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.domain.RagResponse;
import com.healingplanet.ai.service.RagService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Validated
public class RagController {

    private static final Logger log = LoggerFactory.getLogger(RagController.class);
    private static final String STREAM_ERROR_MESSAGE = "AI 回答服务暂时不可用，请稍后重试。";

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/rag/chat")
    public Mono<RagResponse> chat(@Valid @RequestBody RagChatRequest request) {
        return Mono.fromCallable(() -> ragService.chat(toQuery(request)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping(value = "/rag/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<?>> stream(@Valid @RequestBody RagChatRequest request) {
        return Mono.fromCallable(() -> ragService.stream(toQuery(request)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(stream -> {
                    ServerSentEvent<List<Evidence>> evidence = ServerSentEvent.builder(stream.evidence())
                            .event("evidence").build();
                    Flux<ServerSentEvent<?>> diagnostics = stream.entityResolution() == null
                            ? Flux.empty()
                            : Flux.just(ServerSentEvent.builder(stream.entityResolution())
                            .event("entity_resolution").build());
                    Flux<ServerSentEvent<?>> tokens = stream.content().map(content ->
                            ServerSentEvent.builder(Map.of("content", content)).event("token").build());
                    return Flux.concat(Flux.just(evidence), diagnostics, tokens,
                            Flux.just(ServerSentEvent.builder(Map.of("done", true)).event("done").build()));
                })
                .onErrorResume(exception -> {
                    log.error("RAG 流式回答失败", exception);
                    return Flux.just(
                            ServerSentEvent.builder(Map.of("message", STREAM_ERROR_MESSAGE)).event("error").build(),
                            ServerSentEvent.builder(Map.of("done", false)).event("done").build());
                });
    }

    @GetMapping("/search")
    public Mono<List<Evidence>> search(@RequestParam("q") @NotBlank @Size(max = 2000) String query,
                                       @RequestParam(required = false) String canonicalPlantId) {
        return Mono.fromCallable(() -> ragService.search(new RagQuery(query, null, null, canonicalPlantId,
                        null, List.of(), Map.of())))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private RagQuery toQuery(RagChatRequest request) {
        return new RagQuery(request.query(), request.userId(), request.plantInstanceId(),
                request.canonicalPlantId(), request.intent(), List.of(), Map.of());
    }
}
