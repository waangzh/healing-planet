package com.healingplanet.ai.api;

import com.healingplanet.ai.service.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagControllerTest {

    @Test
    void streamEmitsErrorAndTerminatesWhenModelFailsAfterEvidence() {
        RagService ragService = mock(RagService.class);
        when(ragService.stream(any())).thenReturn(new RagService.RagStream(List.of(),
                Flux.just("部分回答").concatWith(Flux.error(new IllegalStateException("上游失败")))));

        List<ServerSentEvent<?>> events = new RagController(ragService)
                .stream(new RagChatRequest(null, null, null, null, "绿萝怎么养？"))
                .collectList()
                .block();

        assertThat(events).extracting(ServerSentEvent::event)
                .containsExactly("evidence", "token", "error", "done");
        assertThat(events.get(2).data()).isEqualTo(Map.of("message", "AI 回答服务暂时不可用，请稍后重试。"));
        assertThat(events.get(3).data()).isEqualTo(Map.of("done", false));
    }
}
