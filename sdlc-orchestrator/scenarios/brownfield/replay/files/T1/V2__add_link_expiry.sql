-- Additive and nullable: existing rows keep NULL (never expire) and the previous release keeps working.
ALTER TABLE link ADD COLUMN expires_at TIMESTAMP(6) WITH TIME ZONE;
