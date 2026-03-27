alter table users
add column password_hash varchar(255) not null default '';

alter table users
add column updated_at timestamp not null default now();