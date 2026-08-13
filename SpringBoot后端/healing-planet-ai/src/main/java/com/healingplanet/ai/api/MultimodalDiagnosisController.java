package com.healingplanet.ai.api;

import com.healingplanet.ai.domain.RagResponse;
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

@RestController
@RequestMapping("/api/rag")
@Validated
public class MultimodalDiagnosisController {
    private final MultimodalDiagnosisService service;

    public MultimodalDiagnosisController(MultimodalDiagnosisService service) {
        this.service = service;
    }

    @PostMapping(value = "/diagnose", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RagResponse diagnose(@RequestPart("image") FilePart image,
                                @RequestParam Long userId,
                                @RequestParam Long plantInstanceId,
                                @RequestParam(required = false) String canonicalPlantId,
                                @RequestParam(required = false) @Size(max = 2000) String query) {
        return service.diagnose(image, userId, plantInstanceId, canonicalPlantId, query);
    }
}
