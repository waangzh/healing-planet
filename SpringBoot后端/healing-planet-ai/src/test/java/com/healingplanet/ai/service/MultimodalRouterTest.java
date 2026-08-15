package com.healingplanet.ai.service;

import com.healingplanet.ai.domain.MultimodalRoute;
import com.healingplanet.ai.domain.VisualObservation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultimodalRouterTest {
    private final MultimodalRouter router = new MultimodalRouter();

    @Test
    void manualDiagnosisAlwaysWins() {
        assertEquals(MultimodalRoute.DISEASE_DIAGNOSIS,
                router.route(MultimodalRoute.DISEASE_DIAGNOSIS, "标签写了什么", observation(MultimodalRoute.OCR)));
    }

    @Test
    void queryCanOverrideGenericVisualRecommendation() {
        assertEquals(MultimodalRoute.OCR,
                router.route(MultimodalRoute.AUTO, "请读取这个仪表的读数", observation(MultimodalRoute.GENERAL_VISION)));
        assertEquals(MultimodalRoute.DISEASE_DIAGNOSIS,
                router.route(MultimodalRoute.AUTO, "叶片黄叶并且有斑点", observation(MultimodalRoute.GENERAL_VISION)));
    }

    @Test
    void observationRoutesOtherwiseAmbiguousQuestions() {
        assertEquals(MultimodalRoute.DISEASE_DIAGNOSIS,
                router.route(MultimodalRoute.AUTO, "帮我看看", observation(MultimodalRoute.DISEASE_DIAGNOSIS)));
    }

    private VisualObservation observation(MultimodalRoute route) {
        return new VisualObservation(route, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }
}
