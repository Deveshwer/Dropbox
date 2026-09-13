CREATE TABLE file_upload_parts (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    part_number INTEGER NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum VARCHAR(255) NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    uploaded_at TIMESTAMP NULL,
    CONSTRAINT fk_upload_parts_session
        FOREIGN KEY (session_id) REFERENCES file_upload_sessions(id) ON DELETE CASCADE,
    CONSTRAINT uk_upload_parts_session_part UNIQUE (session_id, part_number)
);

CREATE INDEX idx_upload_parts_session_status ON file_upload_parts(session_id, status);
