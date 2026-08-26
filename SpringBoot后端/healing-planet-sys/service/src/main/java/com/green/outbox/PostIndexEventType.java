package com.green.outbox;

/** 帖子变更写入 RAG 索引的稳定事件类型。 */
public enum PostIndexEventType {
    POST_UPSERT,
    POST_DELETE
}
