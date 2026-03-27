create table shares (
    id uuid primary key,
    resource_type varchar(20) not null,
    resource_id uuid not null,
    owner_id uuid not null,
    shared_with_user_id uuid not null,
    permission varchar(20) not null,
    status varchar(20) not null,
    expires_at timestamp null,
    created_at timestamp not null,

    constraint fk_shares_owner
        foreign key (owner_id) references users(id),

    constraint fk_shares_shared_with_user
        foreign key (shared_with_user_id) references users(id),

    constraint uq_shares_resource_user
        unique (resource_type, resource_id, shared_with_user_id)
);