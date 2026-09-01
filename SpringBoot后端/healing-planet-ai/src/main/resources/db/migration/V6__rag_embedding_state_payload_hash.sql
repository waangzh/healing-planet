-- Payload compatibility is independent from embedding compatibility. Existing rows receive one payload-only sync.
alter table rag_embedding_state
    add column payload_hash char(64) not null default '' after index_fingerprint;
