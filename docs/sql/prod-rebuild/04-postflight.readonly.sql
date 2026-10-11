-- Replace role placeholder after approved production role identification. Never use dev role.
BEGIN READ ONLY;
SET LOCAL statement_timeout='30s';
SELECT table_name,count(*) AS column_count FROM information_schema.columns
WHERE table_schema='public' AND table_name IN
 ('members','notices','company_events','gallery_photos','home_banners','test_users') GROUP BY table_name;
-- Expected: 25,10,9,8,7,4 respectively (total63).
-- Expected constraints: PK6/UNIQUE4/FK2=12; indexes=12. No new role CHECK.
-- members.phone UNIQUE preserved; author_id BIGINT still references users.id INTEGER.
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
SELECT c.conname,c.conrelid::regclass AS source,c.confrelid::regclass AS target,
 format_type(a.atttypid,a.atttypmod) AS source_type,format_type(b.atttypid,b.atttypmod) AS target_type,
 c.confdeltype,c.confupdtype,c.convalidated,c.condeferrable
FROM pg_constraint c JOIN pg_attribute a ON a.attrelid=c.conrelid AND c.conkey=ARRAY[a.attnum]
JOIN pg_attribute b ON b.attrelid=c.confrelid AND c.confkey=ARRAY[b.attnum]
WHERE c.conrelid='public.notices'::regclass AND c.contype='f';
-- 3 identity(bigint) and 3 owned-nextval IDs: members bigint sequence, notices/test_users integer sequences.
SELECT c.relname,format_type(a.atttypid,a.atttypmod) AS id_type,a.attidentity,
 pg_get_serial_sequence(format('public.%I',c.relname),'id') AS sequence,
 format_type(s.seqtypid,NULL) AS sequence_type,s.seqstart,s.seqincrement,s.seqmax,s.seqcycle
FROM pg_class c JOIN pg_attribute a ON a.attrelid=c.oid AND a.attname='id' AND NOT a.attisdropped
LEFT JOIN pg_sequence s ON s.seqrelid=pg_get_serial_sequence(format('public.%I',c.relname),'id')::regclass
WHERE c.relnamespace='public'::regnamespace AND c.relname IN
 ('members','notices','company_events','gallery_photos','home_banners','test_users');
-- Compare with preflight inventory; never disable/change these managed event triggers.
SELECT e.evtname,e.evtevent,e.evtenabled,e.evttags,pg_get_userbyid(e.evtowner) AS owner,
 e.evtfoid::regprocedure AS function,pg_get_userbyid(p.proowner) AS function_owner,
 p.prosecdef,md5(pg_get_functiondef(e.evtfoid)) AS definition_hash
FROM pg_event_trigger e JOIN pg_proc p ON p.oid=e.evtfoid ORDER BY e.evtname;
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
