package com.healingplanet.ai.domain;

import java.time.Instant;

/** 视觉模型只输出感知候选，不包含治疗建议。 */
public record DiseaseDetection(
        Integer label,
        String className,
        String diseaseName,
        String cropName,
        Double confidence,
        boolean healthy,
        Instant detectedAt
) {
}
