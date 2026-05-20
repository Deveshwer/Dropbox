ALTER TABLE file_upload_sessions
ADD COLUMN expires_at TIMESTAMP;

UPDATE file_upload_sessions
SET expires_at = created_at + INTERVAL '15 minutes'
WHERE expires_at IS NULL;

ALTER TABLE file_upload_sessions
ALTER COLUMN expires_at SET NOT NULL;