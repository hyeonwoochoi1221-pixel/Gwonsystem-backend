-- Metadata only. Role must already exist. Never reads business rows, passwords or HTTP queues.
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout='15s';
SELECT current_database(), current_user, version(), inet_server_addr(), inet_server_port();
SELECT rolname,rolcanlogin,rolinherit,rolsuper,rolcreatedb,rolcreaterole,rolreplication,rolbypassrls
FROM pg_roles WHERE rolname='gwon_prod_app';
SELECT r.rolname AS member,p.rolname AS granted_role,m.admin_option,m.inherit_option,m.set_option
FROM pg_auth_members m JOIN pg_roles r ON r.oid=m.member JOIN pg_roles p ON p.oid=m.roleid
WHERE r.rolname='gwon_prod_app';
SELECT has_database_privilege('gwon_prod_app',current_database(),'CONNECT') AS connect,
has_database_privilege('gwon_prod_app',current_database(),'CREATE') AS permanent_create,
has_database_privilege('gwon_prod_app',current_database(),'TEMP') AS temporary_tables_allowed;
SELECT nspname,has_schema_privilege('gwon_prod_app',oid,'USAGE') AS usage,
has_schema_privilege('gwon_prod_app',oid,'CREATE') AS create
FROM pg_namespace ORDER BY nspname;
SELECT n.nspname,c.relname,pg_get_userbyid(c.relowner) AS owner,c.relrowsecurity,c.relforcerowsecurity,
has_table_privilege('gwon_prod_app',c.oid,'SELECT') AS select_allowed,
has_table_privilege('gwon_prod_app',c.oid,'INSERT') AS insert_allowed,
has_table_privilege('gwon_prod_app',c.oid,'UPDATE') AS update_allowed,
has_table_privilege('gwon_prod_app',c.oid,'DELETE') AS delete_allowed,
has_table_privilege('gwon_prod_app',c.oid,'TRUNCATE,REFERENCES,TRIGGER') AS extra_privilege,
has_table_privilege('gwon_prod_app',c.oid,'SELECT WITH GRANT OPTION,INSERT WITH GRANT OPTION,UPDATE WITH GRANT OPTION,DELETE WITH GRANT OPTION') AS grant_option
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE c.relkind IN ('r','v','m','p') AND n.nspname NOT IN ('pg_catalog','information_schema') ORDER BY 1,2;
SELECT schemaname,tablename,policyname,permissive,roles,cmd,qual,with_check
FROM pg_policies WHERE schemaname='public' ORDER BY tablename,policyname;
SELECT c.relname,pg_get_userbyid(c.relowner) AS owner,
has_sequence_privilege('gwon_prod_app',c.oid,'USAGE') AS usage,
has_sequence_privilege('gwon_prod_app',c.oid,'SELECT') AS select_allowed,
has_sequence_privilege('gwon_prod_app',c.oid,'UPDATE') AS setval_allowed
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE c.relkind='S' AND n.nspname='public' ORDER BY c.relname;
-- Callable user/extension functions: metadata and body fingerprint only, not definitions.
SELECT n.nspname,p.proname,pg_get_function_identity_arguments(p.oid) AS signature,
pg_get_userbyid(p.proowner) AS owner,p.prosecdef,p.provolatile,
md5(p.prosrc) AS body_fingerprint
FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
WHERE n.nspname NOT IN ('pg_catalog','information_schema')
AND has_schema_privilege('gwon_prod_app',n.oid,'USAGE')
AND has_function_privilege('gwon_prod_app',p.oid,'EXECUTE') ORDER BY 1,2,3;
SELECT classid::regclass,objid,objsubid,dbid FROM pg_shdepend
WHERE refclassid='pg_authid'::regclass AND refobjid=(SELECT oid FROM pg_roles WHERE rolname='gwon_prod_app') AND deptype='o';
ROLLBACK;
