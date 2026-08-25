package com.healingplanet.ai.config;

import java.time.Instant;
import java.util.List;

public record RagConnectionTestResult(long revision, boolean successful, List<Check> checks, Instant checkedAt) {
    public record Check(String name, boolean successful, String message) {
    }
}
