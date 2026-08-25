package com.healingplanet.ai.api;

import com.healingplanet.ai.config.RagConfigDraftRequest;
import com.healingplanet.ai.config.RagConfigRevisionView;
import com.healingplanet.ai.config.RagConfigService;
import com.healingplanet.ai.config.RagConfigValidationResult;
import com.healingplanet.ai.config.RagProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@RestController
@RequestMapping("/internal/rag-config")
public class InternalRagConfigController {
    private final RagConfigService service;
    private final RagProperties properties;

    public InternalRagConfigController(RagConfigService service, RagProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @GetMapping("/current")
    public RagConfigRevisionView current(@RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        authorize(apiKey);
        return service.current();
    }

    @GetMapping("/revisions")
    public List<RagConfigRevisionView> revisions(
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        authorize(apiKey);
        return service.revisions();
    }

    @GetMapping("/revisions/{revision}")
    public RagConfigRevisionView revision(@PathVariable long revision,
                                           @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        authorize(apiKey);
        return service.revision(revision);
    }

    @PostMapping("/drafts")
    public RagConfigRevisionView saveDraft(@RequestBody RagConfigDraftRequest request,
                                           @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        authorize(apiKey);
        return service.saveDraft(request);
    }

    @PostMapping("/drafts/{revision}/validate")
    public RagConfigValidationResult validate(@PathVariable long revision, @RequestBody(required = false) OperatorRequest request,
                                              @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        authorize(apiKey);
        return service.validate(revision, request == null ? null : request.operator());
    }

    @PostMapping("/drafts/{revision}/publish")
    public RagConfigRevisionView publish(@PathVariable long revision, @RequestBody(required = false) OperatorRequest request,
                                         @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        authorize(apiKey);
        return service.publish(revision, request == null ? null : request.operator());
    }

    @PostMapping("/revisions/{revision}/rollback")
    public RagConfigRevisionView rollback(@PathVariable long revision, @RequestBody(required = false) OperatorRequest request,
                                          @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        authorize(apiKey);
        return service.rollback(revision, request == null ? null : request.operator());
    }

    private void authorize(String apiKey) {
        String expected = properties.getInternalApiKey();
        if (expected == null || expected.isBlank() || apiKey == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), apiKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "内部接口鉴权失败");
        }
    }

    public record OperatorRequest(String operator) {
    }
}
