-- PostgreSQL 17 / gwonsystem-dev ONLY. REVIEWED DRAFT; NEVER RUN ON PRODUCTION.
-- Execution: first inspect the dev project's Dashboard and connection endpoint.
-- Replace ONLY the two confirmation placeholders below. These are human attestations,
-- NOT cryptographic proof of project identity: pg_catalog.current_database() is usually postgres
-- in both projects. Do not execute unless the connection is independently confirmed.
-- Run as a development administrator with CREATEROLE and grant/policy authority.
-- With psql use ON_ERROR_STOP=1. If any error occurs, ROLLBACK; do not continue.
-- No existing role, owner, managed schema, PUBLIC ACL, or existing policy is modified.
-- No default privileges are changed; future tables require separate reviewed grants.
-- Password deliberately absent. After approval and successful COMMIT, set it using
-- an administrator's interactive psql command: \password gwon_dev_app
-- Do not place the password in SQL, shell arguments, logs, or this repository.
-- Until that separate step this LOGIN role has no password and password auth fails.
-- Inherited TEMP is permitted: temporary objects are session-local, not permanent DDL.
-- CREATE in permanent schemas/database and ownership/membership remain forbidden.
-- PostgreSQL cannot deny PUBLIC privileges for just one role. If this script aborts
-- because of PUBLIC grants, obtain a separately reviewed design; DO NOT revoke PUBLIC
-- or modify Supabase-managed roles to force this script through.
-- RLS: only a new policy TO gwon_dev_app is added where RLS is already enabled.
-- Spring backend authorization remains responsible for user-level access. The role
-- intentionally has all-row CRUD on these ten tables. Existing policies, including
-- restrictive PUBLIC policies, remain intact and can still restrict this new role.
-- Verify functions/triggers/foreign keys using 02 before starting Render. SQL privilege
-- checks alone cannot prove that callable functions or triggers never perform DDL,
-- external requests, or operations on other objects.
-- References: https://www.postgresql.org/docs/17/ddl-priv.html
-- https://supabase.com/docs/guides/database/connecting-to-postgres

BEGIN;

DO $provision$
DECLARE
    confirmed_project_name constant text := 'gwonsystem-dev';
    approved_project_ref constant text := 'elddmkyqfmxryemjrxhh';
    table_names constant text[] := ARRAY[
        'company_events', 'departments', 'gallery_photos', 'home_banners',
        'inquiries', 'members', 'notices', 'schedules', 'test_users', 'users'];
    table_name text;
    table_oid oid;
    app_oid oid;
    sequence_record record;
BEGIN
    IF confirmed_project_name <> 'gwonsystem-dev'
       OR approved_project_ref !~ '^[a-z]{20}$'
       OR approved_project_ref = 'zsaqpsohhwygrawphnsg' THEN
        RAISE EXCEPTION 'Confirm the approved development project before execution';
    END IF;
    IF pg_catalog.current_setting('server_version_num')::integer / 10000 <> 17
       OR pg_catalog.current_database() <> 'postgres' THEN
        RAISE EXCEPTION 'Expected PostgreSQL 17 and database postgres';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'gwon_dev_app') THEN
        RAISE EXCEPTION 'Role already exists: review it; this script never rewrites roles';
    END IF;
    FOREACH table_name IN ARRAY table_names LOOP
        table_oid := pg_catalog.to_regclass(pg_catalog.format('public.%I', table_name));
        IF table_oid IS NULL OR NOT EXISTS (
            SELECT 1 FROM pg_catalog.pg_class WHERE oid = table_oid AND relkind IN ('r', 'p')) THEN
            RAISE EXCEPTION 'Required ordinary/partitioned table missing: public.%', table_name;
        END IF;
    END LOOP;

    CREATE ROLE gwon_dev_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE
        NOINHERIT NOREPLICATION NOBYPASSRLS PASSWORD NULL;
    SELECT oid INTO app_oid FROM pg_catalog.pg_roles WHERE rolname = 'gwon_dev_app';

    -- Effective privileges include PUBLIC. Do not attempt ineffective per-role REVOKEs.
    IF pg_catalog.has_database_privilege(app_oid, pg_catalog.current_database(), 'CREATE')
       OR EXISTS (SELECT 1 FROM pg_catalog.pg_namespace n
                  WHERE n.nspname !~ '^pg_(toast_)?temp_'
                    AND pg_catalog.has_schema_privilege(app_oid, n.oid, 'CREATE'))
       OR EXISTS (SELECT 1 FROM pg_catalog.pg_auth_members WHERE member = app_oid)
       -- A newly created role cannot yet own session temporary objects.
       OR EXISTS (SELECT 1 FROM pg_catalog.pg_shdepend d
                  WHERE d.refclassid = 'pg_catalog.pg_authid'::regclass AND d.deptype = 'o'
                    AND pg_catalog.pg_has_role(app_oid, d.refobjid, 'MEMBER')
                    AND (d.dbid = 0 OR d.dbid = (SELECT oid FROM pg_catalog.pg_database
                         WHERE datname = pg_catalog.current_database()))) THEN
        RAISE EXCEPTION 'Unsafe permanent DDL/ownership/membership privileges; provisioning rolled back';
    END IF;
    GRANT CONNECT ON DATABASE postgres TO gwon_dev_app;
    GRANT USAGE ON SCHEMA public TO gwon_dev_app;
    -- Explicit pg_temp last avoids implicit temporary-table precedence by default.
    -- It is not immutable: a session can SET search_path. Functions must secure theirs.
    ALTER ROLE gwon_dev_app IN DATABASE postgres SET search_path = pg_catalog, public, pg_temp;

    FOREACH table_name IN ARRAY table_names LOOP
        table_oid := pg_catalog.to_regclass(pg_catalog.format('public.%I', table_name));
        EXECUTE pg_catalog.format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.%I TO gwon_dev_app', table_name);
        -- No TRUNCATE, REFERENCES, TRIGGER, MAINTAIN or grant option.
        IF EXISTS (SELECT 1 FROM pg_catalog.pg_class WHERE oid = table_oid AND relrowsecurity) THEN
            EXECUTE pg_catalog.format('CREATE POLICY gwon_dev_app_crud ON public.%I AS PERMISSIVE FOR ALL TO gwon_dev_app USING (true) WITH CHECK (true)', table_name);
        END IF;
    END LOOP;

    -- Find actual owned SERIAL/IDENTITY sequences; never guess names or grant all sequences.
    FOR sequence_record IN
        SELECT DISTINCT ns.nspname AS schema_name, s.relname AS sequence_name
        FROM pg_catalog.pg_class s JOIN pg_catalog.pg_namespace ns ON ns.oid = s.relnamespace
        JOIN pg_catalog.pg_depend d ON d.classid = 'pg_catalog.pg_class'::regclass AND d.objid = s.oid
        JOIN pg_catalog.pg_class t ON d.refclassid = 'pg_catalog.pg_class'::regclass AND d.refobjid = t.oid
        JOIN pg_catalog.pg_namespace nt ON nt.oid = t.relnamespace
        WHERE s.relkind = 'S' AND d.deptype IN ('a', 'i')
          AND ns.nspname = 'public' AND nt.nspname = 'public'
          AND t.relname = ANY(table_names)
    LOOP
        EXECUTE pg_catalog.format('GRANT USAGE, SELECT ON SEQUENCE %I.%I TO gwon_dev_app',
                       sequence_record.schema_name, sequence_record.sequence_name);
    END LOOP;
    -- USAGE supports nextval/currval. No UPDATE/setval, ownership or sequence DDL.
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_type t JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace
               WHERE n.nspname = 'public' AND t.typname = 'member_role' AND t.typtype = 'e') THEN
        GRANT USAGE ON TYPE public.member_role TO gwon_dev_app;
    END IF;
END
$provision$;

COMMIT;
