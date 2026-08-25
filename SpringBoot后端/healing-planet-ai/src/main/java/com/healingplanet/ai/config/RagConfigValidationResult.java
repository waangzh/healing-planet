package com.healingplanet.ai.config;

import java.util.List;

public record RagConfigValidationResult(long revision, boolean valid, List<String> errors) {
}
