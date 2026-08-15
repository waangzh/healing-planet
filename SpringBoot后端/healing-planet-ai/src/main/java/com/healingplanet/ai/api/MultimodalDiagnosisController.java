package com.healingplanet.ai.api;

import com.healingplanet.ai.domain.MultimodalRagResponse;
import com.healingplanet.ai.domain.MultimodalRoute;
import com.healingplanet.ai.service.MultimodalDiagnosisService;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/rag")
@Validated
public class MultimodalDiagnosisController {
    private final MultimodalDiagnosisService service;

    public MultimodalDiagnosisController(MultimodalDiagnosisService service) {
        this.service = service;
    }

    @PostMapping(value = "/diagnose", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<MultimodalRagResponse> diagnose(@RequestPart(value = "image", required = false) FilePart image,
                                      @RequestParam(required = false) String attachmentId,
                                      @RequestParam(required = false) Long userId,
                                      @RequestParam(required = false) Long plantInstanceId,
                                      @RequestParam(required = false) String canonicalPlantId,
                                      @RequestParam(required = false) @Size(max = 2000) String query,
                                      @RequestParam(defaultValue = "AUTO") MultimodalRoute requestedRoute) {
        return Mono.fromCallable(() -> service.analyze(image, attachmentId, userId, plantInstanceId,
                        canonicalPlantId, query, requestedRoute))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
