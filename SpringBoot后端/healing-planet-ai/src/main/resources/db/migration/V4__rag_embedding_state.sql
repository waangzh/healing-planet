-- 该脚本由现有数据库发布流程执行；项目未自动启用 Flyway。
-- 该状态是跨实例的向量化去重依据，不能只保存在本地 Lucene 索引中。
create table if not exists rag_embedding_state (
    document_id char(36) not null primary key,
    source varchar(32) not null,
    source_id varchar(64) not null,
    content_hash char(64) not null,
    embedding_model_version varchar(255) not null,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    index idx_rag_embedding_state_source (source, source_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
