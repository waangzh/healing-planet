-- Index state is durable across instances. These migrations are executed by the existing database release process.
alter table rag_embedding_state
    add column source_updated_at datetime(3) null after payload_hash,
    add column indexed_at datetime(3) not null default current_timestamp(3) after source_updated_at,
    add index idx_rag_embedding_state_source_fingerprint (source, index_fingerprint, indexed_at);

-- Preserve the closest pre-V7 index time for existing rows instead of treating migration time as a successful run.
update rag_embedding_state
set indexed_at = updated_at;

create table if not exists rag_index_status (
    source varchar(32) not null primary key,
    last_run_id char(36) not null default '',
    last_operation varchar(32) not null default '',
    last_attempt_started_at datetime(3) null,
    last_attempt_finished_at datetime(3) null,
    last_successful_index_at datetime(3) null,
    last_run_status varchar(16) not null default '',
    last_index_fingerprint char(64) not null default '',
    documents_seen int not null default 0,
    documents_unchanged int not null default 0,
    documents_embedded int not null default 0,
    payload_updates int not null default 0,
    sparse_updates int not null default 0,
    documents_deleted int not null default 0,
    fragments_created int not null default 0,
    logical_evidences_created int not null default 0,
    failed_documents int not null default 0,
    last_error varchar(1000) not null default '',
    updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
