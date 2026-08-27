package com.healingplanet.ai.service;

import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EvidenceType;

import java.util.List;

/** Facts about selected evidence used to choose generation safeguards. */
public record EvidenceProfile(boolean hasFormalKnowledge, boolean hasCommunity, boolean hasCurrentState,
                              boolean hasHistory, boolean stateFresh) {
    public static EvidenceProfile from(List<Evidence> evidence) {
        boolean formal = evidence.stream().anyMatch(item -> item.type() == EvidenceType.CARE_GUIDE);
        boolean community = evidence.stream().anyMatch(item -> item.type() == EvidenceType.COMMUNITY_POST);
        boolean current = evidence.stream().anyMatch(item -> item.type() == EvidenceType.LIVE_STATE);
        boolean history = evidence.stream().anyMatch(item -> item.type() == EvidenceType.SENSOR_HISTORY);
        boolean fresh = evidence.stream().filter(item -> item.type() == EvidenceType.LIVE_STATE)
                .noneMatch(item -> Boolean.TRUE.equals(item.metadata().get("stale")));
        return new EvidenceProfile(formal, community, current, history, fresh);
    }
}
