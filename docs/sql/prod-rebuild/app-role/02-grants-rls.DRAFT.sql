-- Optional existing-schema path; when rebuilding use 01 -> rebuild 03 -> verification instead.
-- Preserves existing RLS state, other-role grants and policies. No default privileges changed.
BEGIN;
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='30s';
SET LOCAL search_path=pg_catalog,public;
DO $guard$
DECLARE
  approved_project_ref text := 'REPLACE_APPROVED_PRODUCTION_REF';
  grants_and_rls_approved boolean := false;
  inherited_and_external_dependencies_reviewed boolean := false;
  app oid; t text; seq text; r record;
BEGIN
  SELECT oid INTO app FROM pg_roles WHERE rolname='gwon_prod_app' AND rolcanlogin
    AND NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolinherit
    AND NOT rolreplication AND NOT rolbypassrls;
  IF app IS NULL OR approved_project_ref<>'zsaqpsohhwygrawphnsg'
    OR NOT grants_and_rls_approved OR NOT inherited_and_external_dependencies_reviewed
    OR current_database()<>'postgres' OR current_setting('server_version_num')::int/10000<>17
    OR EXISTS(SELECT 1 FROM pg_auth_members WHERE member=app)
    OR has_database_privilege(app,current_database(),'CREATE')
    OR EXISTS(SELECT 1 FROM pg_namespace WHERE nspname !~ '^pg_temp_' AND nspname !~ '^pg_toast_temp_'
      AND has_schema_privilege(app,oid,'CREATE'))
    OR EXISTS(SELECT 1 FROM pg_shdepend WHERE refclassid='pg_authid'::regclass AND refobjid=app AND deptype='o') THEN
    RAISE EXCEPTION 'Identity, approvals or least privilege role checks failed';
  END IF;
  FOR t IN SELECT unnest(ARRAY['company_events','gallery_photos','home_banners','members','notices','test_users']) LOOP
    IF NOT EXISTS(SELECT 1 FROM pg_class WHERE oid=to_regclass('public.'||t) AND relkind='r') THEN
      RAISE EXCEPTION 'Missing ordinary table %',t;
    END IF;
    IF EXISTS(SELECT 1 FROM pg_policy WHERE polrelid=to_regclass('public.'||t)
      AND NOT polpermissive AND (0=ANY(polroles) OR app=ANY(polroles))) THEN
      RAISE EXCEPTION 'Applicable restrictive policy requires review on %',t;
    END IF;
    EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON TABLE public.%I TO gwon_prod_app',t);
    seq:=pg_get_serial_sequence('public.'||t,'id');
    IF seq IS NULL THEN RAISE EXCEPTION 'Owned ID sequence missing on %',t; END IF;
    EXECUTE format('GRANT USAGE ON SEQUENCE %s TO gwon_prod_app',seq);
    SELECT relrowsecurity INTO r FROM pg_class WHERE oid=to_regclass('public.'||t);
    IF r.relrowsecurity THEN
      -- Reuse exact reviewed policy from rebuild 03, or our own earlier invocation.
      IF EXISTS(SELECT 1 FROM pg_policy WHERE polrelid=to_regclass('public.'||t)
        AND polname IN ('backend_crud','gwon_prod_app_crud')) THEN
        IF NOT EXISTS(SELECT 1 FROM pg_policy WHERE polrelid=to_regclass('public.'||t)
          AND polname IN ('backend_crud','gwon_prod_app_crud') AND polcmd='*' AND polpermissive
          AND polroles=ARRAY[app] AND pg_get_expr(polqual,polrelid)='true'
          AND pg_get_expr(polwithcheck,polrelid)='true') THEN
          RAISE EXCEPTION 'Existing app policy differs on %',t;
        END IF;
      ELSE
        EXECUTE format('CREATE POLICY gwon_prod_app_crud ON public.%I FOR ALL TO gwon_prod_app USING (true) WITH CHECK (true)',t);
      END IF;
    END IF;
  END LOOP;
  GRANT USAGE ON SCHEMA public TO gwon_prod_app;
END $guard$;
ROLLBACK;
