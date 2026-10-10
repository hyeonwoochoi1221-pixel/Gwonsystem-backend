-- READ ONLY. Run after 01 using the confirmed DEVELOPMENT administrator connection.
-- Run again with the app's actual Session pooler login to check current_user/TLS.
-- No CREATE/DROP probes, DML, nextval/setval, SET ROLE, or password/hash queries.
-- Missing role produces no role rows / NULL checks: that is failure, not a pass.
-- Expected: safe_role=true, permanent DDL flags=false, no permanent owner/membership rows, CRUD=true,
-- can_create_temp_objects=true is permitted and is informational, not a rejection.
-- sequence USAGE/SELECT=true and UPDATE=false. Review ALL policy/function/trigger rows.
-- Effective grants are not proof of RLS-accessible rows or actual network reachability.
-- SECURITY DEFINER routines and trigger routines require manual source review;
-- PUBLIC grants, unowned sequences and restrictive policies are never auto-repaired.

BEGIN TRANSACTION READ ONLY;

SELECT pg_catalog.current_database(), session_user, current_user,
       pg_catalog.current_setting('search_path') AS session_search_path,
       pg_catalog.current_setting('server_version') AS server_version,
       COALESCE((SELECT ssl FROM pg_catalog.pg_stat_ssl WHERE pid = pg_catalog.pg_backend_pid()), false) AS server_tls,
       current_user = 'gwon_dev_app' AS actual_app_role;

SELECT rolname, rolcanlogin,
       NOT (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls) AS safe_role,
       rolinherit,
       pg_catalog.has_database_privilege(oid, pg_catalog.current_database(), 'CONNECT') AS can_connect,
       pg_catalog.has_database_privilege(oid, pg_catalog.current_database(), 'CREATE') AS can_create_schema,
       pg_catalog.has_database_privilege(oid, pg_catalog.current_database(), 'TEMP') AS can_create_temp_objects,
       pg_catalog.has_schema_privilege(oid, 'public', 'USAGE') AS public_usage,
       pg_catalog.has_schema_privilege(oid, 'public', 'CREATE') AS public_create
FROM pg_catalog.pg_roles WHERE rolname = 'gwon_dev_app';

-- Any row permits direct/transitive membership; NOINHERIT alone is insufficient.
SELECT owner_role.rolname AS member_of
FROM pg_catalog.pg_roles app CROSS JOIN pg_catalog.pg_roles owner_role
WHERE app.rolname = 'gwon_dev_app' AND owner_role.oid <> app.oid
  AND pg_catalog.pg_has_role(app.oid, owner_role.oid, 'MEMBER');

SELECT n.nspname AS schema_name
FROM pg_catalog.pg_namespace n JOIN pg_catalog.pg_roles app ON app.rolname = 'gwon_dev_app'
WHERE n.nspname !~ '^pg_(toast_)?temp_'
  AND pg_catalog.has_schema_privilege(app.oid, n.oid, 'CREATE');

-- Ownership gives ALTER/DROP rights independently of ACLs. Expected no permanent rows.
-- Session temporary relations/types/schema ownership is allowed.
SELECT d.classid::regclass AS object_catalog, d.objid, d.objsubid
FROM pg_catalog.pg_shdepend d JOIN pg_catalog.pg_roles app ON app.rolname = 'gwon_dev_app'
WHERE d.refclassid = 'pg_catalog.pg_authid'::regclass AND d.deptype = 'o'
  AND pg_catalog.pg_has_role(app.oid, d.refobjid, 'MEMBER')
  AND (d.dbid = 0 OR d.dbid = (SELECT oid FROM pg_catalog.pg_database WHERE datname = pg_catalog.current_database()))
  AND NOT (d.classid = 'pg_catalog.pg_class'::regclass AND EXISTS (
      SELECT 1 FROM pg_catalog.pg_class c WHERE c.oid = d.objid AND c.relpersistence = 't'))
  AND NOT (d.classid = 'pg_catalog.pg_type'::regclass AND EXISTS (
      SELECT 1 FROM pg_catalog.pg_type t JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace
      WHERE t.oid = d.objid AND n.nspname ~ '^pg_(toast_)?temp_'))
  AND NOT (d.classid = 'pg_catalog.pg_namespace'::regclass AND EXISTS (
      SELECT 1 FROM pg_catalog.pg_namespace n WHERE n.oid = d.objid AND n.nspname ~ '^pg_(toast_)?temp_'));

WITH expected(name) AS (VALUES ('company_events'), ('departments'), ('gallery_photos'),
 ('home_banners'), ('inquiries'), ('members'), ('notices'), ('schedules'), ('test_users'), ('users'))
SELECT e.name, c.oid IS NOT NULL AS table_exists, c.relrowsecurity AS rls_enabled,
 c.relforcerowsecurity AS rls_forced,
 pg_catalog.has_table_privilege(app.oid, c.oid, 'SELECT') AS can_select,
 pg_catalog.has_table_privilege(app.oid, c.oid, 'INSERT') AS can_insert,
 pg_catalog.has_table_privilege(app.oid, c.oid, 'UPDATE') AS can_update,
 pg_catalog.has_table_privilege(app.oid, c.oid, 'DELETE') AS can_delete,
 pg_catalog.has_table_privilege(app.oid, c.oid, 'TRUNCATE') AS unexpected_truncate,
 pg_catalog.has_table_privilege(app.oid, c.oid, 'TRIGGER') AS unexpected_trigger,
 pg_catalog.has_table_privilege(app.oid, c.oid, 'MAINTAIN') AS unexpected_maintain
FROM expected e LEFT JOIN pg_catalog.pg_class c ON c.oid = pg_catalog.to_regclass(pg_catalog.format('public.%I', e.name))
LEFT JOIN pg_catalog.pg_roles app ON app.rolname = 'gwon_dev_app' ORDER BY e.name;

SELECT ns.nspname, s.relname AS sequence_name, t.relname AS owned_by_table,
 pg_catalog.has_sequence_privilege(app.oid, s.oid, 'USAGE') AS can_nextval,
 pg_catalog.has_sequence_privilege(app.oid, s.oid, 'SELECT') AS can_select,
 pg_catalog.has_sequence_privilege(app.oid, s.oid, 'UPDATE') AS unexpected_setval
FROM pg_catalog.pg_class s JOIN pg_catalog.pg_namespace ns ON ns.oid = s.relnamespace
LEFT JOIN pg_catalog.pg_depend d ON d.classid = 'pg_catalog.pg_class'::regclass AND d.objid = s.oid
 AND d.refclassid = 'pg_catalog.pg_class'::regclass AND d.deptype IN ('a', 'i')
LEFT JOIN pg_catalog.pg_class t ON t.oid = d.refobjid
LEFT JOIN pg_catalog.pg_roles app ON app.rolname = 'gwon_dev_app'
WHERE s.relkind = 'S' AND ns.nspname = 'public' ORDER BY s.relname;

-- Detect default expressions referencing sequences not covered by owned-sequence grants.
SELECT t.relname AS table_name, a.attname AS column_name, s.relname AS default_sequence,
 pg_catalog.has_sequence_privilege(app.oid, s.oid, 'USAGE') AS can_nextval
FROM pg_catalog.pg_attrdef ad JOIN pg_catalog.pg_class t ON t.oid = ad.adrelid
JOIN pg_catalog.pg_namespace n ON n.oid = t.relnamespace
JOIN pg_catalog.pg_attribute a ON a.attrelid = t.oid AND a.attnum = ad.adnum
JOIN pg_catalog.pg_depend d ON d.classid = 'pg_catalog.pg_attrdef'::regclass AND d.objid = ad.oid
 AND d.refclassid = 'pg_catalog.pg_class'::regclass
JOIN pg_catalog.pg_class s ON s.oid = d.refobjid AND s.relkind = 'S'
LEFT JOIN pg_catalog.pg_roles app ON app.rolname = 'gwon_dev_app'
WHERE n.nspname = 'public';

SELECT tablename, policyname, permissive, roles, cmd, qual, with_check
FROM pg_catalog.pg_policies WHERE schemaname = 'public' ORDER BY tablename, policyname;

-- Callable SECURITY DEFINER routines can exercise their owner's privileges.
SELECT n.nspname, p.oid::regprocedure AS routine, owner_role.rolname AS owner,
       p.proconfig AS function_settings
FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
JOIN pg_catalog.pg_roles owner_role ON owner_role.oid = p.proowner
JOIN pg_catalog.pg_roles app ON app.rolname = 'gwon_dev_app'
WHERE p.prosecdef AND pg_catalog.has_schema_privilege(app.oid, n.oid, 'USAGE')
 AND pg_catalog.has_function_privilege(app.oid, p.oid, 'EXECUTE') ORDER BY n.nspname, p.proname;

-- Trigger functions execute on CRUD even without app EXECUTE grants. Review side effects.
SELECT c.relname AS table_name, t.tgname, t.tgenabled,
       p.oid::regprocedure AS routine, p.prosecdef AS security_definer,
       pg_catalog.pg_get_triggerdef(t.oid) AS definition
FROM pg_catalog.pg_trigger t JOIN pg_catalog.pg_class c ON c.oid = t.tgrelid
JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace JOIN pg_catalog.pg_proc p ON p.oid = t.tgfoid
WHERE n.nspname = 'public' AND NOT t.tgisinternal ORDER BY c.relname, t.tgname;

-- FK/cascade targets can affect tables outside the approved ten. No rows/data touched.
SELECT c.conname, c.conrelid::regclass AS source_table,
       c.confrelid::regclass AS target_table, pg_catalog.pg_get_constraintdef(c.oid) AS definition
FROM pg_catalog.pg_constraint c JOIN pg_catalog.pg_class t ON t.oid = c.conrelid
JOIN pg_catalog.pg_namespace n ON n.oid = t.relnamespace
WHERE c.contype = 'f' AND n.nspname = 'public';

SELECT t.typname, pg_catalog.has_type_privilege(app.oid, t.oid, 'USAGE') AS enum_usage
FROM pg_catalog.pg_type t JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace
JOIN pg_catalog.pg_roles app ON app.rolname = 'gwon_dev_app'
WHERE n.nspname = 'public' AND t.typname = 'member_role';

ROLLBACK;
