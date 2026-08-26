-- 该脚本由现有数据库发布流程执行；社区服务未自动启用 Flyway。
-- 帖子事务与此表的 insert 必须使用同一个 MySQL 事务。
create table if not exists post_index_outbox (
    event_id char(36) not null primary key,
    event_type varchar(32) not null,
    post_id varchar(64) not null,
    occurred_at datetime(3) not null,
    state varchar(16) not null,
    attempt_count int not null default 0,
    next_attempt_at datetime(3) not null,
    locked_until datetime(3) null,
    published_at datetime(3) null,
    delivered_at datetime(3) null,
    last_error varchar(1000) null,
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    index idx_post_index_outbox_retry (state, next_attempt_at),
    index idx_post_index_outbox_lease (state, locked_until)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
