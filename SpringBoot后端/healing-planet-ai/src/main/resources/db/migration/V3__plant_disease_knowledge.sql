create table if not exists plant_disease_knowledge (
    id varchar(64) not null primary key,
    canonical_plant_id varchar(64) null,
    plant_name varchar(128) not null,
    disease_name varchar(255) not null,
    aliases varchar(1000) null,
    symptoms text not null,
    visual_symptoms text not null,
    trigger_conditions text null,
    environment_conditions text null,
    treatment text not null,
    prevention text null,
    source varchar(1000) not null,
    source_level varchar(32) not null default 'REVIEWED',
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    index idx_disease_plant (canonical_plant_id),
    index idx_disease_name (disease_name)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- 不在迁移中填充未审核的病害建议。生产数据必须填写可追溯 source，审核后再触发索引。
