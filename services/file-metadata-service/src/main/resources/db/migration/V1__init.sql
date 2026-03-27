-- TODO: add initial metadata schema migration.
  create table users (
      id uuid primary key,
      email varchar(255) not null unique,
      created_at timestamp not null
  );

  create table folders (
      id uuid primary key,
      name varchar(255) not null,
      parent_folder_id uuid null,
      owner_id uuid not null,
      created_at timestamp not null,
      updated_at timestamp not null,
      constraint fk_folders_parent
          foreign key (parent_folder_id) references folders(id),
      constraint fk_folders_owner
          foreign key (owner_id) references users(id)
  );

  create table files (
      id uuid primary key,
      name varchar(255) not null,
      folder_id uuid not null,
      owner_id uuid not null,
      current_version_id uuid null,
      created_at timestamp not null,
      updated_at timestamp not null,
      constraint fk_files_folder
          foreign key (folder_id) references folders(id),
      constraint fk_files_owner
          foreign key (owner_id) references users(id)
  );

  create table file_versions (
      id uuid primary key,
      file_id uuid not null,
      version_number bigint not null,
      status varchar(50) not null,
      created_by uuid not null,
      created_at timestamp not null,
      constraint fk_file_versions_file
          foreign key (file_id) references files(id),
      constraint fk_file_versions_created_by
          foreign key (created_by) references users(id),
      constraint uq_file_versions_file_version
          unique (file_id, version_number)
  );

  alter table files
  add constraint fk_files_current_version
  foreign key (current_version_id) references file_versions(id);