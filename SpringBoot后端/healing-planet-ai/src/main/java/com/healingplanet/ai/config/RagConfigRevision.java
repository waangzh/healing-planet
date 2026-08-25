package com.healingplanet.ai.config;

import java.time.Instant;

record RagConfigRevision(long revision, RagConfigStatus status, RagRuntimeConfig config, String description,
                         String createdBy, Instant createdAt, String validatedBy, Instant validatedAt,
                         String publishedBy, Instant publishedAt, Long rollbackFromRevision,
                         String failureReason) {
}
