-- REVIEW DRAFT. SQL FILE ONLY; NOT EXECUTED BY CODE/BOOT/DOCKER.
-- Only gwonsystem-dev, PostgreSQL 17, database postgres; never production.
-- Before approval: independently verify Dashboard project and connection host/ref,
-- take a schema snapshot, review RLS/triggers, and run 04 as a read-only preflight.
-- Use a dev administrator, NOT gwon_dev_app. psql ON_ERROR_STOP=1.
-- Replace the two human-confirmation placeholders. They cannot prove server identity.
-- Fresh preparation only: existing column/constraint aborts for manual review.
-- No data backfill, table deletion, ownership/grant/PUBLIC/managed-schema changes.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
DO $preflight$
DECLARE
    project_name constant text := 'gwonsystem-dev';
    project_ref constant text := 'elddmkyqfmxryemjrxhh';
BEGIN
    IF project_name <> 'gwonsystem-dev' OR project_ref !~ '^[a-z]{20}$'
       OR project_ref = 'zsaqpsohhwygrawphnsg'
       OR pg_catalog.current_database() <> 'postgres'
       OR pg_catalog.current_setting('server_version_num')::integer / 10000 <> 17 THEN
        RAISE EXCEPTION 'Approved development project and PostgreSQL 17 required';
    END IF;
    IF current_user = 'gwon_dev_app' THEN RAISE EXCEPTION 'Use approved dev schema administrator'; END IF;
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_attribute
               WHERE attrelid='public.notices'::regclass AND attname='author_member_id' AND NOT attisdropped) THEN
        RAISE EXCEPTION 'Column already exists; review actual schema before retry';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_attribute a JOIN pg_catalog.pg_constraint c
          ON c.conrelid=a.attrelid AND c.conkey=ARRAY[a.attnum]
        JOIN pg_catalog.pg_attribute u ON u.attrelid=c.confrelid AND c.confkey=ARRAY[u.attnum]
        WHERE a.attrelid='public.notices'::regclass AND a.attname='author_id'
          AND a.atttypid='pg_catalog.int8'::regtype AND NOT a.attnotnull
          AND c.conname='notices_author_id_fkey' AND c.contype='f'
          AND c.confrelid='public.users'::regclass AND u.attname='id'
          AND c.confdeltype='n' AND c.convalidated
    ) THEN RAISE EXCEPTION 'Existing nullable author_id/users SET NULL FK differs from approved schema'; END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_attribute WHERE attrelid='public.members'::regclass
                   AND attname='id' AND atttypid='pg_catalog.int8'::regtype AND NOT attisdropped) THEN
        RAISE EXCEPTION 'Expected members.id BIGINT';
    END IF;
END
$preflight$;
ALTER TABLE public.notices ADD COLUMN author_member_id BIGINT NULL;
ALTER TABLE public.notices ADD CONSTRAINT notices_author_member_id_fkey
    FOREIGN KEY (author_member_id) REFERENCES public.members(id)
    ON DELETE RESTRICT ON UPDATE NO ACTION NOT DEFERRABLE;
CREATE INDEX notices_author_member_id_idx ON public.notices(author_member_id);
COMMIT;
-- Immediately run 04 and confirm both FKs, nullable BIGINT, valid constraint,
-- unchanged legacy data, app DML grants and RLS behavior before any dev rollout.
