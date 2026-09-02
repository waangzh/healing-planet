-- A source lease serializes full scans and incremental writes across AI service instances.
-- A crashed owner is recoverable after lease_until; normal long scans renew before expiry.
create table if not exists rag_ingestion_lease (
    source varchar(32) not null primary key,
    lease_owner varchar(64) not null default '',
    lease_until datetime(3) not null,
    updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
