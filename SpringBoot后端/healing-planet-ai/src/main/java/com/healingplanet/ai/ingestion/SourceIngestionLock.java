package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.domain.KnowledgeSource;

/** Serializes writes for one knowledge source across every AI service instance. */
@FunctionalInterface
public interface SourceIngestionLock {
    void execute(KnowledgeSource source, Runnable action);

    static SourceIngestionLock noOp() {
        return (source, action) -> action.run();
    }
}
