package com.healingplanet.ai.domain;

import java.util.List;

public record RagResponse(String answer, List<Evidence> evidence) {
}
