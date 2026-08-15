package com.healingplanet.ai.service;

import com.healingplanet.ai.domain.MultimodalRoute;
import com.healingplanet.ai.domain.VisualObservation;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class MultimodalRouter {
    private static final Set<String> OCR_TERMS = Set.of(
            "文字", "写了什么", "标签", "说明书", "包装", "仪表", "读数", "截图", "识别字", "ocr"
    );
    private static final Set<String> DISEASE_TERMS = Set.of(
            "病", "病斑", "斑点", "黄叶", "发黄", "虫", "虫害", "霉", "腐烂", "烂根", "枯萎", "焦边", "诊断"
    );

    public MultimodalRoute route(MultimodalRoute requested, String query, VisualObservation observation) {
        if (requested != null && requested != MultimodalRoute.AUTO) return requested;
        String text = query == null ? "" : query.toLowerCase(Locale.ROOT);
        if (OCR_TERMS.stream().anyMatch(text::contains)) return MultimodalRoute.OCR;
        if (DISEASE_TERMS.stream().anyMatch(text::contains)) return MultimodalRoute.DISEASE_DIAGNOSIS;
        if (observation != null && observation.recommendedRoute() != null
                && observation.recommendedRoute() != MultimodalRoute.AUTO) {
            return observation.recommendedRoute();
        }
        return MultimodalRoute.GENERAL_VISION;
    }
}
