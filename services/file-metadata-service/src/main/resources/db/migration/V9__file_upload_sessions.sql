CREATE TABLE file_upload_sessions (
    id UUID PRIMARY KEY,
    file_id UUID NOT NULL,
    initiated_by UUID NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NULL
);

ALTER TABLE file_upload_sessions
ADD CONSTRAINT fk_upload_sessions_file
FOREIGN KEY (file_id) REFERENCES files(id);