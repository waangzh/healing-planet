package com.green.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.green.outbox.PostIndexOutboxEvent;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface PostIndexOutboxMapper extends BaseMapper<PostIndexOutboxEvent> {
    @Select("select * from post_index_outbox where (state = 'PENDING' and next_attempt_at <= now()) " +
            "or (state = 'PUBLISHING' and locked_until < now()) order by occurred_at asc limit #{limit}")
    List<PostIndexOutboxEvent> selectPublishable(@Param("limit") int limit);

    @Update("update post_index_outbox set state = 'PUBLISHING', locked_until = #{lockedUntil}, " +
            "attempt_count = attempt_count + 1, updated_at = now() where event_id = #{eventId} " +
            "and ((state = 'PENDING' and next_attempt_at <= now()) or (state = 'PUBLISHING' and locked_until < now()))")
    int claimForPublishing(@Param("eventId") String eventId, @Param("lockedUntil") Date lockedUntil);

    @Update("update post_index_outbox set state = 'PUBLISHED', locked_until = null, published_at = coalesce(published_at, now()), " +
            "last_error = null, updated_at = now() where event_id = #{eventId} and state = 'PUBLISHING'")
    int markPublished(@Param("eventId") String eventId);

    @Update("update post_index_outbox set state = 'PENDING', next_attempt_at = #{nextAttemptAt}, locked_until = null, " +
            "last_error = #{lastError}, updated_at = now() where event_id = #{eventId} " +
            "and state in ('PUBLISHING', 'PUBLISHED')")
    int reschedule(@Param("eventId") String eventId, @Param("nextAttemptAt") Date nextAttemptAt,
                   @Param("lastError") String lastError);

    @Update("update post_index_outbox set state = 'DELIVERED', delivered_at = now(), locked_until = null, " +
            "last_error = null, updated_at = now() where event_id = #{eventId} " +
            "and state in ('PUBLISHING', 'PUBLISHED')")
    int markDelivered(@Param("eventId") String eventId);

    @Update("update post_index_outbox set state = 'FAILED', locked_until = null, last_error = #{lastError}, " +
            "updated_at = now() where event_id = #{eventId} and state <> 'DELIVERED'")
    int markFailed(@Param("eventId") String eventId, @Param("lastError") String lastError);
}
