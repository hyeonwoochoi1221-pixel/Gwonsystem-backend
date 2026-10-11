-- NOT EXECUTED. Removes ALL data in SIX tables. Not an approved execution artifact yet.
-- psql ON_ERROR_STOP=1; independently confirm production connection and stop all writers.
-- Replace review attestations ONLY after separate explicit execution approval.
-- No CASCADE, DROP SCHEMA, managed-schema operations, role creation/passwords or global ACL changes.
BEGIN;
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='60s';
SET LOCAL search_path=pg_catalog,public;
DO $guard$
DECLARE
  approved_project_ref text := 'REPLACE_APPROVED_PRODUCTION_REF';
  approved_app_role text := 'REPLACE_PRODUCTION_APP_ROLE';
  business_data_reset_approved boolean := false;
  project_identity_verified boolean := false;
  production_app_role_verified boolean := false;
  backend_only_rls_approved boolean := false;
  managed_event_triggers_reviewed boolean := false;
  existing_defaults_reviewed boolean := false;
  dependency_review_approved boolean := false;
  names text[] := ARRAY['company_events','gallery_photos','home_banners','members','notices','test_users'];
  r record;
BEGIN
  IF approved_project_ref<>'zsaqpsohhwygrawphnsg'
    OR NOT business_data_reset_approved OR NOT project_identity_verified OR NOT dependency_review_approved
    OR NOT production_app_role_verified OR NOT backend_only_rls_approved
    OR NOT managed_event_triggers_reviewed OR NOT existing_defaults_reviewed
    OR current_database()<>'postgres' OR current_setting('server_version_num')::int/10000<>17 THEN
    RAISE EXCEPTION 'Production identity, business reset, role, RLS, defaults and dependency approvals required';
  END IF;
  IF approved_app_role IN ('postgres','anon','authenticated','service_role','gwon_dev_app')
    OR approved_app_role LIKE 'supabase_%' OR approved_app_role=current_user
    OR NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname=approved_app_role AND rolcanlogin
      AND NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication AND NOT rolbypassrls)
    OR EXISTS(SELECT 1 FROM pg_auth_members WHERE member=(SELECT oid FROM pg_roles WHERE rolname=approved_app_role))
    OR has_database_privilege(approved_app_role,current_database(),'CREATE')
    OR has_schema_privilege(approved_app_role,'public','CREATE') THEN
    RAISE EXCEPTION 'Reviewed non-owner production application role required; never copy dev grants';
  END IF;
  -- Reviewed Supabase managed objects are preserved, NOT disabled. Name alone is insufficient.
  IF (SELECT count(*) FROM pg_event_trigger)<>6 OR EXISTS (
    SELECT 1 FROM pg_event_trigger e JOIN pg_proc p ON p.oid=e.evtfoid
    JOIN pg_namespace n ON n.oid=p.pronamespace
    LEFT JOIN (VALUES
      ('issue_graphql_placeholder','sql_drop','set_graphql_placeholder'),
      ('pgrst_ddl_watch','ddl_command_end','pgrst_ddl_watch'),
      ('pgrst_drop_watch','sql_drop','pgrst_drop_watch'),
      ('issue_pg_cron_access','ddl_command_end','grant_pg_cron_access'),
      ('issue_pg_net_access','ddl_command_end','grant_pg_net_access'),
      ('issue_pg_graphql_access','ddl_command_end','grant_pg_graphql_access')
    ) approved(name,event,function_name) ON approved.name=e.evtname
    WHERE approved.name IS NULL OR e.evtevent<>approved.event OR e.evtenabled<>'O'
      OR pg_get_userbyid(e.evtowner)<>'supabase_admin'
      OR pg_get_userbyid(p.proowner)<>'supabase_admin' OR n.nspname<>'extensions'
      OR p.proname<>approved.function_name OR p.pronargs<>0 OR p.prosecdef
  ) THEN
    RAISE EXCEPTION 'Managed event trigger inventory differs from reviewed production snapshot';
  END IF;
  IF EXISTS(SELECT 1 FROM pg_publication WHERE puballtables)
    OR EXISTS(SELECT 1 FROM pg_publication_tables WHERE schemaname='public' AND tablename=ANY(names)) THEN
    RAISE EXCEPTION 'Publication/Realtime dependency needs separate reviewed plan';
  END IF;
  FOR r IN SELECT c.* FROM pg_class c WHERE c.relnamespace='public'::regnamespace AND c.relname=ANY(names) LOOP
    IF r.relkind<>'r' OR pg_get_userbyid(r.relowner)<>current_user THEN
      RAISE EXCEPTION 'Ordinary table owned by approved schema executor required';
    END IF;
    IF EXISTS(SELECT 1 FROM pg_trigger WHERE tgrelid=r.oid AND NOT tgisinternal) THEN
      RAISE EXCEPTION 'Unreviewed table trigger blocks rebuild';
    END IF;
    IF EXISTS(SELECT 1 FROM pg_policy p WHERE p.polrelid=r.oid AND NOT (
      r.relname='test_users' AND p.polname='Allow public insert and select'
      AND p.polcmd='*' AND p.polpermissive AND p.polroles=ARRAY[0::oid]
      AND pg_get_expr(p.polqual,p.polrelid)='true'
      AND pg_get_expr(p.polwithcheck,p.polrelid)='true')) THEN
      RAISE EXCEPTION 'Unreviewed existing policy blocks rebuild';
    END IF;
  END LOOP;
  IF (SELECT count(*) FROM pg_class WHERE relnamespace='public'::regnamespace AND relname=ANY(names))<>6 THEN
    RAISE EXCEPTION 'Expected six existing tables; no partial or repeated rebuild';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM pg_attribute WHERE attrelid='public.users'::regclass
    AND attname='id' AND atttypid='int4'::regtype AND NOT attisdropped) THEN
    RAISE EXCEPTION 'Retained users.id INTEGER required (legacy author_id remains BIGINT)';
  END IF;
  PERFORM set_config('gwon.rebuild_app_role',approved_app_role,true);
  IF EXISTS(SELECT 1 FROM pg_attribute WHERE attrelid='public.notices'::regclass
    AND attname='author_member_id' AND NOT attisdropped) THEN
    RAISE EXCEPTION 'Member-author column already exists; refuse destructive repeat';
  END IF;
END $guard$;
-- Lock the approved tables before capturing defaults. External dependencies still fail DROP RESTRICT.
LOCK TABLE public.notices,public.company_events,public.gallery_photos,
 public.home_banners,public.test_users,public.members IN ACCESS EXCLUSIVE MODE;
-- Transaction-local snapshots only. Never write to auth/storage/extensions/managed schemas.
CREATE TEMP TABLE gwon_rebuild_defaults ON COMMIT DROP AS
SELECT c.relname AS table_name,a.attname AS column_name,pg_get_expr(d.adbin,d.adrelid) AS expression
FROM pg_class c JOIN pg_attribute a ON a.attrelid=c.oid
JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum
WHERE c.relnamespace='public'::regnamespace AND c.relname IN
 ('members','notices','company_events','gallery_photos','home_banners','test_users') AND a.attname<>'id';
CREATE TEMP TABLE gwon_rebuild_events ON COMMIT DROP AS
SELECT e.oid,e.evtname,e.evtowner,e.evtevent,e.evtfoid,e.evtenabled,e.evttags,
 md5(pg_get_functiondef(e.evtfoid)) AS function_definition_hash,p.proowner,p.prosecdef,p.proacl,p.proconfig
FROM pg_event_trigger e JOIN pg_proc p ON p.oid=e.evtfoid;
-- RESTRICT aborts on dependent external FKs/views. Never change to CASCADE to bypass errors.
DROP TABLE public.notices RESTRICT;
DROP TABLE public.company_events RESTRICT;
DROP TABLE public.gallery_photos RESTRICT;
DROP TABLE public.home_banners RESTRICT;
DROP TABLE public.test_users RESTRICT;
DROP TABLE public.members RESTRICT;

CREATE TABLE public.members (
 id bigint PRIMARY KEY,
 username varchar(50) NOT NULL UNIQUE,password varchar(255),email varchar(100) NOT NULL UNIQUE,
 provider varchar(20),provider_id varchar(100),last_name varchar(20) NOT NULL,first_name varchar(50) NOT NULL,
 phone varchar(20) UNIQUE,gender varchar(10),birth_date date,zipcode varchar(10),address varchar(255),
 detail_address varchar(255),workplace_name varchar(255),department_name varchar(255),
 position varchar(255),workplace_phone varchar(255),
 role varchar(30) NOT NULL,
 terms_agreed boolean NOT NULL DEFAULT true,privacy_agreed boolean NOT NULL DEFAULT true,
 marketing_agreed boolean NOT NULL DEFAULT false,
 privacy_agreed_at timestamp with time zone,
 created_at timestamp with time zone,updated_at timestamp with time zone
);
CREATE SEQUENCE public.members_id_seq AS bigint;
ALTER SEQUENCE public.members_id_seq OWNED BY public.members.id;
ALTER TABLE public.members ALTER COLUMN id SET DEFAULT nextval('public.members_id_seq'::regclass);
CREATE TABLE public.notices (
 id bigint PRIMARY KEY,
 author_id bigint NULL,author_member_id bigint NOT NULL,category varchar(30),title varchar(200) NOT NULL,
 content text NOT NULL,view_count integer DEFAULT 0,is_pinned boolean DEFAULT false,
 created_at timestamp with time zone,updated_at timestamp with time zone,
 CONSTRAINT notices_author_id_fkey FOREIGN KEY(author_id) REFERENCES public.users(id)
 ON DELETE SET NULL ON UPDATE NO ACTION NOT DEFERRABLE,
 CONSTRAINT notices_author_member_id_fkey FOREIGN KEY(author_member_id) REFERENCES public.members(id)
 ON DELETE RESTRICT ON UPDATE NO ACTION NOT DEFERRABLE
);
CREATE SEQUENCE public.notices_id_seq AS integer;
ALTER SEQUENCE public.notices_id_seq OWNED BY public.notices.id;
ALTER TABLE public.notices ALTER COLUMN id SET DEFAULT nextval('public.notices_id_seq'::regclass);
CREATE INDEX notices_author_member_id_idx ON public.notices(author_member_id);
CREATE INDEX notices_author_id_idx ON public.notices(author_id);
CREATE TABLE public.company_events (
 id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,title varchar(80) NOT NULL,
 category varchar(20) NOT NULL,start_date date NOT NULL,end_date date NULL,
 location varchar(120) NOT NULL,location_detail varchar(120),place_id varchar(200),description varchar(1000) NOT NULL
);
CREATE TABLE public.gallery_photos (
 id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,title varchar(80) NOT NULL,
 description varchar(300) NOT NULL,content_type varchar(30) NOT NULL,image bytea NOT NULL,
 storage_path varchar(160),created_at timestamp(6) with time zone NOT NULL,created_by varchar(255) NOT NULL
);
CREATE TABLE public.home_banners (
 id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,title varchar(80) NOT NULL,
 description varchar(300) NOT NULL,content_type varchar(30) NOT NULL,image bytea NOT NULL,
 created_at timestamp(6) with time zone NOT NULL,created_by varchar(255) NOT NULL
);
CREATE TABLE public.test_users (
 id bigint PRIMARY KEY,username varchar(50) NOT NULL,
 email varchar(100) NOT NULL UNIQUE,created_at timestamp with time zone
);
CREATE SEQUENCE public.test_users_id_seq AS integer;
ALTER SEQUENCE public.test_users_id_seq OWNED BY public.test_users.id;
ALTER TABLE public.test_users ALTER COLUMN id SET DEFAULT nextval('public.test_users_id_seq'::regclass);
-- Preserve reviewed non-ID defaults verbatim; no guessed category/time/role defaults.
-- Guard existing_defaults_reviewed requires prior review of expressions/functions they call.
DO $defaults$
DECLARE r record;
BEGIN
 FOR r IN SELECT * FROM pg_temp.gwon_rebuild_defaults LOOP
  EXECUTE format('ALTER TABLE public.%I ALTER COLUMN %I SET DEFAULT %s',r.table_name,r.column_name,r.expression);
 END LOOP;
END $defaults$;
-- New-object ACLs only; existing Supabase roles, PUBLIC schema/database grants untouched.
-- All-row backend policy delegates end-user authorization to Spring Security.
DO $acl$
DECLARE app text := current_setting('gwon.rebuild_app_role'); t text; seq text;
BEGIN
 FOR t IN SELECT unnest(ARRAY['members','notices','company_events','gallery_photos','home_banners','test_users']) LOOP
  EXECUTE format('REVOKE ALL ON TABLE public.%I FROM PUBLIC, anon, authenticated, service_role',t);
  EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON TABLE public.%I TO %I',t,app);
  EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY',t);
  EXECUTE format('CREATE POLICY backend_crud ON public.%I FOR ALL TO %I USING (true) WITH CHECK (true)',t,app);
  seq := pg_get_serial_sequence(format('public.%I',t),'id');
  EXECUTE format('REVOKE ALL ON SEQUENCE %s FROM PUBLIC, anon, authenticated, service_role',seq);
  EXECUTE format('GRANT USAGE ON SEQUENCE %s TO %I',seq,app);
  IF EXISTS(SELECT 1 FROM pg_class c CROSS JOIN LATERAL
    aclexplode(coalesce(c.relacl,acldefault('S',c.relowner))) a
    WHERE c.oid=seq::regclass
    AND a.grantee NOT IN(c.relowner,(SELECT oid FROM pg_roles WHERE rolname=app))) THEN
    RAISE EXCEPTION 'Unexpected sequence ACL; do not change global default privileges';
  END IF;
  IF EXISTS(SELECT 1 FROM pg_class c CROSS JOIN LATERAL
    aclexplode(coalesce(c.relacl,acldefault('r',c.relowner))) a
    WHERE c.oid=format('public.%I',t)::regclass
    AND a.grantee NOT IN(c.relowner,(SELECT oid FROM pg_roles WHERE rolname=app))) THEN
    RAISE EXCEPTION 'Unexpected default ACL on new table; review without changing global default privileges';
  END IF;
 END LOOP;
 EXECUTE format('GRANT USAGE ON SCHEMA public TO %I',app);
END $acl$;
DO $preserve_events$
BEGIN
 IF EXISTS (
   (SELECT * FROM pg_temp.gwon_rebuild_events EXCEPT
    SELECT e.oid,e.evtname,e.evtowner,e.evtevent,e.evtfoid,e.evtenabled,e.evttags,
     md5(pg_get_functiondef(e.evtfoid)),p.proowner,p.prosecdef,p.proacl,p.proconfig
    FROM pg_event_trigger e JOIN pg_proc p ON p.oid=e.evtfoid)
   UNION ALL
   (SELECT e.oid,e.evtname,e.evtowner,e.evtevent,e.evtfoid,e.evtenabled,e.evttags,
     md5(pg_get_functiondef(e.evtfoid)),p.proowner,p.prosecdef,p.proacl,p.proconfig
    FROM pg_event_trigger e JOIN pg_proc p ON p.oid=e.evtfoid
    EXCEPT SELECT * FROM pg_temp.gwon_rebuild_events)
 ) THEN RAISE EXCEPTION 'Managed event triggers/functions changed; abort'; END IF;
END $preserve_events$;
-- No application triggers needed by current code. No INSERT/admin seed/auth/storage deletion.
-- Intentionally ROLLBACK. COMMIT replacement requires final explicit execution approval.
ROLLBACK;
