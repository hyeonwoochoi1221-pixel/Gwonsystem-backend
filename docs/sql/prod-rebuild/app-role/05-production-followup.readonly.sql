-- 89-7 follow-up query executed through the confirmed production SQL Editor.
-- No rows, passwords, function bodies or literal defaults are returned.
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout='15s';
SET LOCAL search_path=pg_catalog;
SELECT jsonb_build_object(
 'net_schema_exists',to_regnamespace('net') IS NOT NULL,
 'pg_net_installed',EXISTS(SELECT 1 FROM pg_extension WHERE extname='pg_net'),
 'public_database_acl',(SELECT jsonb_agg(jsonb_build_object('privilege',x.privilege_type,'grantable',x.is_grantable))
 FROM pg_database d CROSS JOIN LATERAL aclexplode(COALESCE(d.datacl,acldefault('d',d.datdba))) x
 WHERE d.datname=current_database() AND x.grantee=0),
 'role_memberships',(SELECT jsonb_agg(jsonb_build_object('member',mbr.rolname,'role',parent.rolname,
 'admin',m.admin_option,'inherit',m.inherit_option,'set',m.set_option))
 FROM pg_auth_members m JOIN pg_roles mbr ON mbr.oid=m.member JOIN pg_roles parent ON parent.oid=m.roleid
 WHERE mbr.rolname IN ('postgres','anon','authenticated','service_role')),
 'policies',(SELECT jsonb_agg(jsonb_build_object('table',tablename,'name',policyname,'roles',roles,
 'permissive',permissive,'command',cmd,'using_is_true',qual='true','check_is_true',with_check='true',
 'using_hash',md5(qual),'check_hash',md5(with_check))) FROM pg_policies WHERE schemaname='public'),
 'hidden_default_classification',(SELECT jsonb_agg(jsonb_build_object('table',c.relname,'column',a.attname,
 'constant_varchar',pg_get_expr(d.adbin,d.adrelid) ~ '^''([^'']|'''')*''::character varying$',
 'hash',md5(pg_get_expr(d.adbin,d.adrelid))))
 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_attribute a ON a.attrelid=c.oid
 JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum WHERE n.nspname='public'
 AND (c.relname,a.attname) IN (('notices','category'),('inquiries','status'),('users','role'))),
 'managed_event_metadata',(SELECT jsonb_agg(jsonb_build_object('name',e.evtname,'tags',e.evttags,
 'definer',p.prosecdef,'create_extension_guard_present',position('CREATE EXTENSION' in p.prosrc)>0,
 'function_hash',md5(p.prosrc))) FROM pg_event_trigger e JOIN pg_proc p ON p.oid=e.evtfoid)
) AS followup_result;
ROLLBACK;
