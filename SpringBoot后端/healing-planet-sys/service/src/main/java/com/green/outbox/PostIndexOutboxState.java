package com.green.outbox;

/** outbox 记录状态；FAILED 需要由运维修复或人工重放，避免静默丢弃。 */
public final class PostIndexOutboxState {
    public static final String PENDING = "PENDING";
    public static final String PUBLISHING = "PUBLISHING";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String DELIVERED = "DELIVERED";
    public static final String FAILED = "FAILED";

    private PostIndexOutboxState() {
    }
}
