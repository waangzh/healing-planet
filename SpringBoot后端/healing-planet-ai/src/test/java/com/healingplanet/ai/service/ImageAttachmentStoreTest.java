package com.healingplanet.ai.service;

import com.healingplanet.ai.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImageAttachmentStoreTest {
    @Test
    void attachmentCanBeReusedUntilTtlExpires() {
        RagProperties properties = new RagProperties();
        properties.getAttachments().setTtlSeconds(60);
        MutableClock clock = new MutableClock(Instant.parse("2026-08-16T00:00:00Z"));
        ImageAttachmentStore store = new ImageAttachmentStore(properties, clock);

        var saved = store.resolve(imagePart(new byte[]{1, 2, 3}), null);
        assertEquals(saved.id(), store.resolve(null, saved.id()).id());

        clock.advance(Duration.ofSeconds(61));
        assertThrows(IllegalArgumentException.class, () -> store.resolve(null, saved.id()));
    }

    private FilePart imagePart(byte[] bytes) {
        FilePart part = mock(FilePart.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        when(part.headers()).thenReturn(headers);
        when(part.filename()).thenReturn("leaf.png");
        when(part.content()).thenReturn(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes)));
        return part;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) { this.instant = instant; }
        private void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
