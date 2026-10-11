-- DRAFT: independently verify production Dashboard project/connection. NOT EXECUTED.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '30s';
SET LOCAL lock_timeout = '5s';
SELECT current_database(), current_user, version(), inet_server_addr(), inet_server_port(),
       current_setting('search_path') AS search_path;
-- Neither database name nor server address proves the Supabase project identity.
SELECT c.relname, c.relkind, pg_get_userbyid(c.relowner) AS owner,
       c.relrowsecurity, c.relforcerowsecurity, c.relacl
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' ORDER BY c.relname;
SELECT table_name,column_name,data_type,udt_name,character_maximum_length,
       is_nullable,column_default,is_identity,identity_generation
FROM information_schema.columns WHERE table_schema='public'
AND table_name IN ('company_events','gallery_photos','home_banners','members','notices','test_users','users')
ORDER BY table_name,ordinal_position;
SELECT conrelid::regclass AS source,confrelid::regclass AS target,conname,
       contype,convalidated,condeferrable,pg_get_constraintdef(oid) AS definition
FROM pg_constraint WHERE connamespace='public'::regnamespace
ORDER BY conrelid,conname;
SELECT schemaname,tablename,indexname,indexdef FROM pg_indexes WHERE schemaname='public';
SELECT schemaname,tablename,policyname,roles,cmd,permissive,qual,with_check
FROM pg_policies WHERE schemaname='public';
SELECT t.tgrelid::regclass AS target,t.tgname,t.tgenabled,p.pronamespace::regnamespace AS function_schema,
       p.proname,p.prosecdef,pg_get_triggerdef(t.oid) AS definition
FROM pg_trigger t JOIN pg_proc p ON p.oid=t.tgfoid
WHERE NOT t.tgisinternal AND t.tgrelid IN
 (SELECT oid FROM pg_class WHERE relnamespace='public'::regnamespace);
SELECT evtname,evtevent,evtenabled,evtfoid::regprocedure FROM pg_event_trigger;
SELECT schemaname,sequencename,sequenceowner,data_type,start_value,min_value,max_value,increment_by,cycle
FROM pg_sequences WHERE schemaname='public'; -- no last_value/nextval/setval
SELECT d.refobjid::regclass AS referenced_object,d.classid::regclass AS dependent_catalog,
       d.objid,d.objsubid,d.deptype
FROM pg_depend d WHERE d.refclassid='pg_class'::regclass AND d.refobjid IN
 (SELECT oid FROM pg_class WHERE relnamespace='public'::regnamespace
 AND relname IN ('company_events','gallery_photos','home_banners','members','notices','test_users'));
SELECT pubname,puballtables FROM pg_publication;
SELECT * FROM pg_publication_tables WHERE schemaname='public';
SELECT grantee,table_name,privilege_type FROM information_schema.table_privileges
WHERE table_schema='public' ORDER BY table_name,grantee,privilege_type;
SELECT rolname,rolcanlogin,rolsuper,rolinherit,rolcreatedb,rolcreaterole,rolreplication,rolbypassrls
FROM pg_roles WHERE rolname NOT LIKE 'pg_%'; -- never pg_authid/passwords
SELECT roleid::regrole,member::regrole,admin_option,inherit_option,set_option FROM pg_auth_members;
SELECT defaclrole::regrole,defaclnamespace::regnamespace,defaclobjtype,defaclacl FROM pg_default_acl;
SELECT n.nspname,p.proname,p.prosecdef,p.proacl FROM pg_proc p
JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public'; -- no function body secrets
ROLLBACK;
-- Definitions/default/policy expressions may contain literal secrets. Review locally before sharing.
