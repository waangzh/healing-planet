package com.healingplanet.ai.domain;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.time.Instant;

public record ImageAttachment(
        String id,
        byte[] bytes,
        String contentType,
        String filename,
        Instant expiresAt,
        VisualObservation observation,
        DiseaseDetection detection
) {
    public Resource resource() {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    public ImageAttachment withObservation(VisualObservation value) {
        return new ImageAttachment(id, bytes, contentType, filename, expiresAt, value, detection);
    }

    public ImageAttachment withDetection(DiseaseDetection value) {
        return new ImageAttachment(id, bytes, contentType, filename, expiresAt, observation, value);
    }
}
