package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.domain.KnowledgeSource;

/** Serializes writes for one knowledge source across every AI service instance. */
@FunctionalInterface
public interface SourceIngestionLock {
    void execute(KnowledgeSource source, LeaseAction action);

    @FunctionalInterface
    interface LeaseAction {
        void run(LeaseGuard leaseGuard);
    }

    /**
     * A cooperative boundary for long indexing runs. It stops a source run before it starts another batch after the
     * background heartbeat has failed or the database lease has expired.
     */
    @FunctionalInterface
    interface LeaseGuard {
        void assertStillHeld();

        static LeaseGuard noOp() {
            return () -> { };
        }
    }

    static SourceIngestionLock noOp() {
        return (source, action) -> action.run(LeaseGuard.noOp());
    }
}
