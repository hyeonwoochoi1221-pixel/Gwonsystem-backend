-- NOT APPROVED FOR EXECUTION. No password. Existing PUBLIC/managed ACLs remain unchanged.
BEGIN;
SET LOCAL statement_timeout='30s';
SET LOCAL search_path=pg_catalog,public;
DO $guard$
DECLARE
  approved_project_ref text := 'REPLACE_APPROVED_PRODUCTION_REF';
  role_creation_approved boolean := false;
  public_and_function_risk_reviewed boolean := false;
BEGIN
  IF approved_project_ref<>'zsaqpsohhwygrawphnsg' OR NOT role_creation_approved
    OR NOT public_and_function_risk_reviewed OR current_database()<>'postgres'
    OR current_setting('server_version_num')::int/10000<>17 THEN
    RAISE EXCEPTION 'Project identity, role creation and inherited/function risk approvals required';
  END IF;
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='gwon_prod_app') THEN
    RAISE EXCEPTION 'Role exists: inspect; never overwrite credentials or privileges';
  END IF;
  CREATE ROLE gwon_prod_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS PASSWORD NULL;
  IF has_database_privilege('gwon_prod_app',current_database(),'CREATE')
    OR EXISTS(SELECT 1 FROM pg_namespace WHERE nspname !~ '^pg_temp_' AND nspname !~ '^pg_toast_temp_'
      AND has_schema_privilege('gwon_prod_app',oid,'CREATE')) THEN
    RAISE EXCEPTION 'Inherited permanent CREATE cannot be denied with role-specific REVOKE';
  END IF;
  -- Fail closed for callable non-system SECURITY DEFINER code. Do not print function bodies.
  IF EXISTS(SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
    WHERE p.prosecdef AND n.nspname NOT IN ('pg_catalog','information_schema')
    AND has_schema_privilege('gwon_prod_app',n.oid,'USAGE')
    AND has_function_privilege('gwon_prod_app',p.oid,'EXECUTE')) THEN
    RAISE EXCEPTION 'Callable SECURITY DEFINER function requires separate design review';
  END IF;
  -- PUBLIC may expose managed pg_net queues, including sensitive HTTP headers.
  IF EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE n.nspname='net' AND c.relkind IN ('r','v','m','p')
    AND has_schema_privilege('gwon_prod_app',n.oid,'USAGE')
    AND has_table_privilege('gwon_prod_app',c.oid,'SELECT,INSERT,UPDATE,DELETE')) THEN
    RAISE EXCEPTION 'Inherited pg_net relation access: stop, do not change managed PUBLIC ACLs';
  END IF;
  GRANT CONNECT ON DATABASE postgres TO gwon_prod_app;
  GRANT USAGE ON SCHEMA public TO gwon_prod_app;
  ALTER ROLE gwon_prod_app IN DATABASE postgres SET search_path=public,pg_catalog,pg_temp;
END $guard$;
-- TEMP inherited from PUBLIC is permitted, not a permanent schema DDL permission.
-- After separate approval only: change final ROLLBACK to COMMIT in a reviewed execution copy.
ROLLBACK;
