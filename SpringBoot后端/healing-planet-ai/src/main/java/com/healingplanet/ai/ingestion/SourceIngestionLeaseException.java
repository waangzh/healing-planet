package com.healingplanet.ai.ingestion;

/** A safe, source-specific failure that can be exposed through the internal index status. */
final class SourceIngestionLeaseException extends IllegalStateException {
    SourceIngestionLeaseException(String message) {
        super(message);
    }

    SourceIngestionLeaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
