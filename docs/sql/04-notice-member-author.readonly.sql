-- READ ONLY, before and after the approved development migration.
-- No password/data values, migrations, nextval or application calls.
BEGIN TRANSACTION READ ONLY;
SELECT pg_catalog.current_database(),current_user,pg_catalog.current_setting('server_version');
SELECT a.attname,pg_catalog.format_type(a.atttypid,a.atttypmod) AS type,a.attnotnull
FROM pg_catalog.pg_attribute a WHERE a.attrelid='public.notices'::regclass
  AND a.attname IN ('author_id','author_member_id') AND NOT a.attisdropped;
SELECT conname,pg_catalog.pg_get_constraintdef(oid) AS definition,convalidated,condeferrable
FROM pg_catalog.pg_constraint WHERE conrelid='public.notices'::regclass AND contype='f';
SELECT indexname,indexdef FROM pg_catalog.pg_indexes WHERE schemaname='public' AND tablename='notices';
SELECT (SELECT count(*) FROM public.notices) AS notices_count,
       (SELECT count(*) FROM public.members) AS members_count,
       (SELECT count(*) FROM public.users) AS users_count;
SELECT relrowsecurity,relforcerowsecurity FROM pg_catalog.pg_class WHERE oid='public.notices'::regclass;
SELECT policyname,permissive,roles,cmd,qual,with_check FROM pg_catalog.pg_policies
WHERE schemaname='public' AND tablename IN ('members','notices');
SELECT tgname,pg_catalog.pg_get_triggerdef(oid) AS definition FROM pg_catalog.pg_trigger
WHERE tgrelid='public.notices'::regclass AND NOT tgisinternal;
SELECT has_table_privilege('gwon_dev_app','public.notices','INSERT') AS app_insert,
       has_table_privilege('gwon_dev_app','public.notices','UPDATE') AS app_update,
       has_table_privilege('gwon_dev_app','public.members','SELECT') AS member_lookup;
ROLLBACK;
