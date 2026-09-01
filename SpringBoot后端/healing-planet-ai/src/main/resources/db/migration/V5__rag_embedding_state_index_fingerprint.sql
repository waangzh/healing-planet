-- 该脚本由现有数据库发布流程执行；项目未自动启用 Flyway。
-- 版本化 embedding 文本与分块契约，任一版本变化都应触发受控重新向量化。
alter table rag_embedding_state
    add column embedding_content_version varchar(255) not null default 'legacy' after embedding_model_version,
    add column chunk_schema_version varchar(255) not null default 'legacy' after embedding_content_version,
    add column index_fingerprint char(64) not null default '' after chunk_schema_version;
