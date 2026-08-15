package com.healingplanet.ai.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.DiseaseDetection;
import com.healingplanet.ai.domain.ImageAttachment;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Locale;

@Component
public class DiseaseDetectorClient {
    private final RestClient client;
    private final RagProperties properties;

    public DiseaseDetectorClient(@Qualifier("diseaseDetectorRestClient") RestClient client,
                                 RagProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public DiseaseDetection detect(ImageAttachment image) {
        try {
            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("image", new NamedByteArrayResource(image.bytes(), image.filename()));
            JsonNode body = client.post().uri(properties.getDiseaseDetector().getPath())
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(parts)
                    .retrieve().body(JsonNode.class);
            if (body == null) throw new IllegalStateException("病害检测服务返回空响应");
            String className = text(body, "class_name", "className");
            String diseaseName = text(body, "disease_name", "diseaseName");
            if (diseaseName.isBlank()) diseaseName = className;
            String cropName = text(body, "crop_name", "cropName");
            Double confidence = number(body, "confidence", "probability", "score");
            if (confidence != null && confidence > 1 && confidence <= 100) confidence /= 100d;
            String normalized = (className + " " + diseaseName).toLowerCase(Locale.ROOT);
            boolean healthy = normalized.contains("healthy") || normalized.contains("健康");
            return new DiseaseDetection(integer(body, "label"), className, diseaseName, cropName,
                    confidence, healthy, Instant.now());
        } catch (RestClientException exception) {
            throw new IllegalStateException("病害检测服务暂时不可用", exception);
        }
    }

    private String text(JsonNode node, String... names) {
        for (String name : names) if (node.hasNonNull(name)) return node.get(name).asText("").trim();
        return "";
    }

    private Double number(JsonNode node, String... names) {
        for (String name : names) if (node.hasNonNull(name) && node.get(name).isNumber()) return node.get(name).asDouble();
        return null;
    }

    private Integer integer(JsonNode node, String name) {
        return node.hasNonNull(name) && node.get(name).canConvertToInt() ? node.get(name).asInt() : null;
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;
        private NamedByteArrayResource(byte[] bytes, String filename) { super(bytes); this.filename = filename; }
        @Override public String getFilename() { return filename; }
    }
}
