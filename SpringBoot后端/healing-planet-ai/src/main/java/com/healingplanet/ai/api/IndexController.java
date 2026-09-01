package com.healingplanet.ai.api;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.IndexRunReport;
import com.healingplanet.ai.domain.IndexStatus;
import com.healingplanet.ai.ingestion.IngestionService;
import com.healingplanet.ai.ingestion.IndexStatusService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.function.Supplier;

@RestController
@RequestMapping("/internal/index")
public class IndexController {

    private final IngestionService ingestionService;
    private final IndexStatusService indexStatusService;
    private final RagProperties properties;

    public IndexController(IngestionService ingestionService, IndexStatusService indexStatusService,
                           RagProperties properties) {
        this.ingestionService = ingestionService;
        this.indexStatusService = indexStatusService;
        this.properties = properties;
    }

    @PostMapping("/full")
    public Mono<IndexRunReport> full(@RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        authorize(apiKey);
        return offload(ingestionService::fullIndex);
    }

    @PostMapping("/plants")
    public Mono<IndexRunReport> plants(@RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        authorize(apiKey);
        return offload(ingestionService::indexPlants);
    }

    @PostMapping("/community")
    public Mono<IndexRunReport> community(@RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        authorize(apiKey);
        return offload(ingestionService::indexCommunity);
    }

    @PostMapping("/diseases")
    public Mono<IndexRunReport> diseases(@RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        authorize(apiKey);
        return offload(ingestionService::indexDiseases);
    }

    @PostMapping("/disease/{diseaseId}")
    public Mono<IndexRunReport> disease(@PathVariable String diseaseId,
                                     @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        authorize(apiKey);
        return offload(() -> ingestionService.indexDisease(diseaseId));
    }

    @PostMapping("/post/{postId}")
    public Mono<IndexRunReport> post(@PathVariable String postId,
                                  @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        authorize(apiKey);
        return offload(() -> ingestionService.indexPost(postId));
    }

    @DeleteMapping("/post/{postId}")
    public Mono<IndexRunReport> deletePost(@PathVariable String postId,
                                        @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        authorize(apiKey);
        return offload(() -> ingestionService.deletePost(postId));
    }

    @GetMapping("/status")
    public Mono<IndexStatus> status(@RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        authorize(apiKey);
        return Mono.fromSupplier(indexStatusService::status).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<IndexRunReport> offload(Supplier<IndexRunReport> action) {
        return Mono.fromSupplier(action).subscribeOn(Schedulers.boundedElastic());
    }

    private void authorize(String apiKey) {
        String expected = properties.getInternalApiKey();
        if (expected != null && !expected.isBlank() && !expected.equals(apiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无权调用内部索引接口");
        }
    }
}
