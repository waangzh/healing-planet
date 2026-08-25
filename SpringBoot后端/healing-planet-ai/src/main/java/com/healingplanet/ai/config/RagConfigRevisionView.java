package com.healingplanet.ai.config;

import java.time.Instant;

public record RagConfigRevisionView(long revision, RagConfigStatus status, RagRuntimeConfig config,
                                    String description, String createdBy, Instant createdAt,
                                    String validatedBy, Instant validatedAt, String publishedBy,
                                    Instant publishedAt, Long rollbackFromRevision, String failureReason) {
    static RagConfigRevisionView from(RagConfigRevision revision) {
        return new RagConfigRevisionView(revision.revision(), revision.status(), revision.config(),
                revision.description(), revision.createdBy(), revision.createdAt(), revision.validatedBy(),
                revision.validatedAt(), revision.publishedBy(), revision.publishedAt(),
                revision.rollbackFromRevision(), revision.failureReason());
    }
}
