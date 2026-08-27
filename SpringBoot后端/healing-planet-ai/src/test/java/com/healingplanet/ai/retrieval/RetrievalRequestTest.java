package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.RagQuery;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalRequestTest {
    @Test
    void shouldRecognizeHumiditySynonymsAsHumidityTopic() {
        assertThat(List.of(
                "空气凤梨长期放在潮湿又闷的地方养可以吗？",
                "白掌放在太干燥的空调房会怎样？",
                "环境保持湿润还是干爽更合适？"))
                .allSatisfy(query -> assertThat(requiredKnowledgeTypes(query)).containsExactly("HUMIDITY"));
    }

    @Test
    void shouldRecognizeLightExposureSynonymsAsLightTopic() {
        assertThat(List.of(
                "芦荟长期在室内养，能直接搬到烈日下吗？",
                "绿萝能一直放在强光下吗？",
                "虎尾兰暴晒后要怎么缓过来？",
                "白掌可以接受阳台直射吗？"))
                .allSatisfy(query -> assertThat(requiredKnowledgeTypes(query)).containsExactly("LIGHT"));
    }

    @Test
    void shouldClassifyMultipleTopicsWithoutFallingBackToBroadCare() {
        assertThat(requiredKnowledgeTypes("房间空气干燥而且阳光直射"))
                .containsExactly("LIGHT", "HUMIDITY");
        assertThat(requiredKnowledgeTypes("介质偏干和长期积水哪个更容易恢复？"))
                .containsExactly("WATERING", "GENERAL_CARE");
    }

    private List<String> requiredKnowledgeTypes(String query) {
        return KnowledgeTopicClassifier.classify(query).stream().toList();
    }
}
