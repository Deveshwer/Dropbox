alter table folders add column deleted_at timestamp null;

alter table files add column deleted_at timestamp null;