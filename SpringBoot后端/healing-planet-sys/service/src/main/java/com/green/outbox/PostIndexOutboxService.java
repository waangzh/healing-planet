package com.green.outbox;

import com.green.mapper.PostIndexOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

@Service
public class PostIndexOutboxService {
    private final PostIndexOutboxMapper outboxMapper;

    public PostIndexOutboxService(PostIndexOutboxMapper outboxMapper) {
        this.outboxMapper = outboxMapper;
    }

    /** MANDATORY 防止未来有人绕过帖子事务直接写事件，破坏事务外盒保证。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordUpsert(String postId) {
        record(PostIndexEventType.POST_UPSERT, postId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDelete(String postId) {
        record(PostIndexEventType.POST_DELETE, postId);
    }

    private void record(PostIndexEventType type, String postId) {
        Date now = new Date();
        outboxMapper.insert(PostIndexOutboxEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(type.name())
                .postId(postId)
                .occurredAt(now)
                .state(PostIndexOutboxState.PENDING)
                .attemptCount(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }
}
