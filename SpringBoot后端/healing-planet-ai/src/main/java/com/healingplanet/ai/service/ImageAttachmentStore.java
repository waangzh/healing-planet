package com.healingplanet.ai.service;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.DiseaseDetection;
import com.healingplanet.ai.domain.ImageAttachment;
import com.healingplanet.ai.domain.VisualObservation;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ImageAttachmentStore {
    private final Map<String, ImageAttachment> attachments = new ConcurrentHashMap<>();
    private final RagProperties properties;
    private final Clock clock;

    public ImageAttachmentStore(RagProperties properties) {
        this(properties, Clock.systemUTC());
    }

    ImageAttachmentStore(RagProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public ImageAttachment resolve(FilePart image, String attachmentId) {
        cleanup();
        if (image != null) return save(image);
        if (attachmentId == null || attachmentId.isBlank()) {
            throw new IllegalArgumentException("请上传图片或提供仍在有效期内的 attachmentId");
        }
        ImageAttachment attachment = attachments.get(attachmentId);
        if (attachment == null || !attachment.expiresAt().isAfter(clock.instant())) {
            attachments.remove(attachmentId);
            throw new IllegalArgumentException("图片附件已过期，请重新上传");
        }
        return attachment;
    }

    public ImageAttachment updateObservation(String id, VisualObservation observation) {
        return attachments.computeIfPresent(id, (key, value) -> value.withObservation(observation));
    }

    public ImageAttachment updateDetection(String id, DiseaseDetection detection) {
        return attachments.computeIfPresent(id, (key, value) -> value.withDetection(detection));
    }

    public long ttlSeconds() {
        return Math.max(1, properties.getAttachments().getTtlSeconds());
    }

    private ImageAttachment save(FilePart image) {
        validate(image);
        try {
            int maxBytes = Math.toIntExact(Math.min(Integer.MAX_VALUE,
                    properties.getDiseaseDetector().getMaxImageBytes()));
            byte[] bytes = DataBufferUtils.join(image.content(), maxBytes)
                    .map(buffer -> {
                        byte[] value = new byte[buffer.readableByteCount()];
                        buffer.read(value);
                        DataBufferUtils.release(buffer);
                        return value;
                    }).block();
            if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("请上传待分析的植物图片");
            evictIfFull();
            String id = UUID.randomUUID().toString();
            MediaType mediaType = image.headers().getContentType();
            ImageAttachment attachment = new ImageAttachment(id, bytes, mediaType.toString(),
                    safeFilename(image.filename()), clock.instant().plusSeconds(ttlSeconds()), null, null);
            attachments.put(id, attachment);
            return attachment;
        } catch (DataBufferLimitException exception) {
            throw new IllegalArgumentException("图片超过允许的最大大小", exception);
        }
    }

    private void validate(FilePart image) {
        MediaType contentType = image.headers().getContentType();
        if (contentType == null || !"image".equalsIgnoreCase(contentType.getType())) {
            throw new IllegalArgumentException("仅支持图片文件");
        }
    }

    private void cleanup() {
        Instant now = clock.instant();
        attachments.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private void evictIfFull() {
        int maxEntries = Math.max(1, properties.getAttachments().getMaxEntries());
        while (attachments.size() >= maxEntries) {
            attachments.entrySet().stream()
                    .min(Comparator.comparing(entry -> entry.getValue().expiresAt()))
                    .map(Map.Entry::getKey)
                    .ifPresent(attachments::remove);
        }
    }

    private String safeFilename(String value) {
        return value == null || value.isBlank() ? "plant-image.jpg" : value;
    }
}
