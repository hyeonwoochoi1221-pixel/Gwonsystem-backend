-- Replace role placeholder after approved production role identification. Never use dev role.
BEGIN READ ONLY;
SET LOCAL statement_timeout='30s';
SELECT table_name,count(*) AS column_count FROM information_schema.columns
WHERE table_schema='public' AND table_name IN
 ('members','notices','company_events','gallery_photos','home_banners','test_users') GROUP BY table_name;
-- Expected: 25,10,9,8,7,4 respectively (total63).
SELECT table_name,column_name,data_type,character_maximum_length,is_nullable,column_default,is_identity
FROM information_schema.columns WHERE table_schema='public' AND table_name IN
 ('members','notices','company_events','gallery_photos','home_banners','test_users') ORDER BY table_name,ordinal_position;
SELECT conrelid::regclass,conname,convalidated,condeferrable,pg_get_constraintdef(oid)
FROM pg_constraint WHERE conrelid IN(SELECT oid FROM pg_class WHERE relnamespace='public'::regnamespace
AND relname IN('members','notices','company_events','gallery_photos','home_banners','test_users'));
SELECT schemaname,tablename,indexname,indexdef FROM pg_indexes WHERE schemaname='public';
SELECT c.relname,c.relrowsecurity,c.relforcerowsecurity,pg_get_userbyid(c.relowner) AS owner,c.relacl,
 pg_get_serial_sequence(format('public.%I',c.relname),'id') AS sequence,
 has_table_privilege('REPLACE_PRODUCTION_APP_ROLE',c.oid,'SELECT,INSERT,UPDATE,DELETE') AS any_dml,
 has_table_privilege('REPLACE_PRODUCTION_APP_ROLE',c.oid,'SELECT') AS can_select,
 has_table_privilege('REPLACE_PRODUCTION_APP_ROLE',c.oid,'INSERT') AS can_insert,
 has_table_privilege('REPLACE_PRODUCTION_APP_ROLE',c.oid,'UPDATE') AS can_update,
 has_table_privilege('REPLACE_PRODUCTION_APP_ROLE',c.oid,'DELETE') AS can_delete,
 has_sequence_privilege('REPLACE_PRODUCTION_APP_ROLE',pg_get_serial_sequence(format('public.%I',c.relname),'id'),'USAGE') AS sequence_usage
FROM pg_class c WHERE c.relnamespace='public'::regnamespace AND c.relname IN
 ('members','notices','company_events','gallery_photos','home_banners','test_users');
SELECT policyname,tablename,roles,cmd,qual,with_check FROM pg_policies WHERE schemaname='public';
SELECT rolname,rolsuper,rolcreatedb,rolcreaterole,rolreplication,rolbypassrls,
 has_database_privilege(rolname,current_database(),'CREATE') AS database_create,
 has_schema_privilege(rolname,'public','CREATE') AS public_create
FROM pg_roles WHERE rolname='REPLACE_PRODUCTION_APP_ROLE';
SELECT c.relname,t.tgname,t.tgisinternal FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
WHERE c.relnamespace='public'::regnamespace;
-- Exact row counts (may scan; run only while maintenance writers are stopped).
SELECT 'members' AS table_name,count(*) FROM public.members UNION ALL
SELECT 'notices',count(*) FROM public.notices UNION ALL
SELECT 'company_events',count(*) FROM public.company_events UNION ALL
SELECT 'gallery_photos',count(*) FROM public.gallery_photos UNION ALL
SELECT 'home_banners',count(*) FROM public.home_banners UNION ALL
SELECT 'test_users',count(*) FROM public.test_users;
-- Use approved privileged read-only auditor; RLS-filtered count is not total-count evidence.
ROLLBACK;
