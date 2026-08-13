package com.healingplanet.ai.api;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.IndexReport;
import com.healingplanet.ai.ingestion.IngestionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/index")
public class IndexController {

    private final IngestionService ingestionService;
    private final RagProperties properties;

    public IndexController(IngestionService ingestionService, RagProperties properties) {
        this.ingestionService = ingestionService;
        this.properties = properties;
    }

    @PostMapping("/full")
    public IndexReport full(@RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        authorize(apiKey);
        return ingestionService.fullIndex();
    }

    @PostMapping("/plants")
    public IndexReport plants(@RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        authorize(apiKey);
        return ingestionService.indexPlants();
    }

    @PostMapping("/community")
    public IndexReport community(@RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        authorize(apiKey);
        return ingestionService.indexCommunity();
    }

    @PostMapping("/post/{postId}")
    public IndexReport post(@PathVariable String postId,
                            @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        authorize(apiKey);
        return ingestionService.indexPost(postId);
    }

    @DeleteMapping("/post/{postId}")
    public IndexReport deletePost(@PathVariable String postId,
                                  @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        authorize(apiKey);
        return ingestionService.deletePost(postId);
    }

    private void authorize(String apiKey) {
        String expected = properties.getInternalApiKey();
        if (expected != null && !expected.isBlank() && !expected.equals(apiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无权调用内部索引接口");
        }
    }
}
