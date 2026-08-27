package com.healingplanet.ai.evaluation;

/** Evidence-grounded outcome evaluated after retrieval and selection. */
public enum Answerability {
    ANSWERABLE,
    INSUFFICIENT_EVIDENCE,
    ENTITY_AMBIGUOUS,
    ENTITY_CONFLICT,
    ENTITY_UNKNOWN,
    STATE_UNAVAILABLE,
    STATE_STALE,
    OUT_OF_SCOPE
}
