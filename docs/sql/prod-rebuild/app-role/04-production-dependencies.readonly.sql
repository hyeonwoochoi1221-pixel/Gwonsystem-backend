-- 89-7 metadata only; execute only after confirming GWONSYSTEM / zsaqpsohhwygrawphnsg in Dashboard.
-- No business rows, queue contents, function bodies, trigger arguments or password values.
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout='20s';
SET LOCAL lock_timeout='3s';
SET LOCAL search_path=pg_catalog;
WITH namespaces AS (
 SELECT n.*, EXISTS(SELECT 1 FROM aclexplode(COALESCE(n.nspacl,acldefault('n',n.nspowner))) x
   WHERE x.grantee=0 AND x.privilege_type='USAGE') AS public_usage,
 EXISTS(SELECT 1 FROM aclexplode(COALESCE(n.nspacl,acldefault('n',n.nspowner))) x
   WHERE x.grantee=0 AND x.privilege_type='CREATE') AS public_create
 FROM pg_namespace n
), functions AS (
 SELECT p.*,n.nspname,n.public_usage,
 EXISTS(SELECT 1 FROM aclexplode(COALESCE(p.proacl,acldefault('f',p.proowner))) x
   WHERE x.grantee=0 AND x.privilege_type='EXECUTE') AS public_execute
 FROM pg_proc p JOIN namespaces n ON n.oid=p.pronamespace
 WHERE n.nspname NOT IN ('pg_catalog','information_schema')
), business AS (
 SELECT c.* FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname='public' AND c.relname IN
 ('company_events','gallery_photos','home_banners','members','notices','test_users','users','departments','schedules','inquiries')
 AND c.relkind IN ('r','p')
)
SELECT jsonb_build_object(
 'context',jsonb_build_object('database',current_database(),'executor',current_user,
 'version',current_setting('server_version'),'expected_project_ref','zsaqpsohhwygrawphnsg',
 'project_ref_is_dashboard_attestation',true,'app_role_exists',EXISTS(SELECT 1 FROM pg_roles WHERE rolname='gwon_prod_app')),
 'executor_role',(SELECT jsonb_build_object('name',rolname,'superuser',rolsuper,'create_role',rolcreaterole,
 'create_db',rolcreatedb,'bypass_rls',rolbypassrls) FROM pg_roles WHERE rolname=current_user),
 'public_schemas',COALESCE((SELECT jsonb_agg(jsonb_build_object('schema',nspname,'usage',public_usage,'create',public_create) ORDER BY nspname)
 FROM namespaces WHERE nspname NOT LIKE 'pg_%' AND (public_usage OR public_create)), '[]'::jsonb),
 'net_relations',COALESCE((SELECT jsonb_agg(jsonb_build_object('name',c.relname,'kind',c.relkind,
 'owner',pg_get_userbyid(c.relowner),'public_schema_usage',n.public_usage,'rls',c.relrowsecurity,
 'acl',(SELECT jsonb_agg(jsonb_build_object('grantee',CASE WHEN x.grantee=0 THEN 'PUBLIC' ELSE pg_get_userbyid(x.grantee) END,
 'privilege',x.privilege_type,'grantable',x.is_grantable)) FROM aclexplode(COALESCE(c.relacl,acldefault('r',c.relowner))) x)) ORDER BY c.relname)
 FROM pg_class c JOIN namespaces n ON n.oid=c.relnamespace WHERE n.nspname='net' AND c.relkind IN ('r','p','v','m')), '[]'::jsonb),
 'callable_function_review',COALESCE((SELECT jsonb_agg(jsonb_build_object('schema',nspname,'name',proname,
 'signature',pg_get_function_identity_arguments(oid),'owner',pg_get_userbyid(proowner),
 'security_definer',prosecdef,'volatility',provolatile,'public_execute',public_execute,
 'public_schema_usage',public_usage,'body_fingerprint',md5(prosrc),
 'search_path_configured',EXISTS(SELECT 1 FROM unnest(proconfig) cfg WHERE cfg LIKE 'search_path=%')) ORDER BY nspname,proname,oid)
 FROM functions WHERE nspname IN ('public','net') OR (prosecdef AND public_execute AND public_usage)), '[]'::jsonb),
 'business_acl',COALESCE((SELECT jsonb_agg(jsonb_build_object('table',relname,'owner',pg_get_userbyid(relowner),
 'rls',relrowsecurity,'force_rls',relforcerowsecurity,
 'acl',(SELECT jsonb_agg(jsonb_build_object('grantee',CASE WHEN x.grantee=0 THEN 'PUBLIC' ELSE pg_get_userbyid(x.grantee) END,
 'privilege',x.privilege_type,'grantable',x.is_grantable)) FROM aclexplode(COALESCE(relacl,acldefault('r',relowner))) x)) ORDER BY relname)
 FROM business), '[]'::jsonb),
 'defaults',COALESCE((SELECT jsonb_agg(jsonb_build_object('table',c.relname,'column',a.attname,
 'expression',CASE WHEN pg_get_expr(d.adbin,d.adrelid) IN ('now()','CURRENT_TIMESTAMP','false','true','0',
 '''ROLE_ASSOCIATE''::character varying') OR pg_get_expr(d.adbin,d.adrelid) ~ '^nextval\(' THEN pg_get_expr(d.adbin,d.adrelid)
 ELSE '<withheld: separately review expression fingerprint/dependencies>' END,
 'fingerprint',md5(pg_get_expr(d.adbin,d.adrelid)),
 'function_dependencies',(SELECT jsonb_agg(jsonb_build_object('schema',n.nspname,'function',p.proname,
 'security_definer',p.prosecdef,'volatility',p.provolatile,'owner',pg_get_userbyid(p.proowner)))
 FROM pg_depend dep JOIN pg_proc p ON p.oid=dep.refobjid JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE dep.classid='pg_attrdef'::regclass AND dep.objid=d.oid AND dep.refclassid='pg_proc'::regclass)) ORDER BY c.relname,a.attnum)
 FROM business c JOIN pg_attribute a ON a.attrelid=c.oid JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum), '[]'::jsonb),
 'business_triggers',COALESCE((SELECT jsonb_agg(jsonb_build_object('table',c.relname,'trigger',t.tgname,
 'enabled',t.tgenabled,'internal',t.tgisinternal,'function_schema',n.nspname,'function',p.proname,
 'security_definer',p.prosecdef,'function_owner',pg_get_userbyid(p.proowner),'function_fingerprint',md5(p.prosrc)) ORDER BY c.relname,t.tgname)
 FROM business c JOIN pg_trigger t ON t.tgrelid=c.oid JOIN pg_proc p ON p.oid=t.tgfoid JOIN pg_namespace n ON n.oid=p.pronamespace), '[]'::jsonb),
 'event_triggers',COALESCE((SELECT jsonb_agg(jsonb_build_object('name',e.evtname,'event',e.evtevent,'enabled',e.evtenabled,
 'owner',pg_get_userbyid(e.evtowner),'function_schema',n.nspname,'function',p.proname,
 'security_definer',p.prosecdef,'function_fingerprint',md5(p.prosrc)) ORDER BY e.evtname)
 FROM pg_event_trigger e JOIN pg_proc p ON p.oid=e.evtfoid JOIN pg_namespace n ON n.oid=p.pronamespace), '[]'::jsonb),
 'realtime_tables',COALESCE((SELECT jsonb_agg(jsonb_build_object('publication',pubname,'schema',schemaname,'table',tablename))
 FROM pg_publication_tables WHERE schemaname='public'), '[]'::jsonb)
) AS dependency_result;
ROLLBACK;
