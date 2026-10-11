# 89-6 isolated role validation — 2026-10-11

## Inputs and boundary

- Branch codex/prod-notice-transition, HEAD a16ad2e22ec13800b86fce9ef06005b357b00c22.
- Existing intentional 89-3 uncommitted SQL and reports preserved. No business source/configuration changes.
- PostgreSQL 17.11 independent binaries; brand-new clusters only under this directory.
- Final data directory pgdata-89-6-final; localhost 127.0.0.1:55496 / postgres.
- Hibernate 6.6.53.Final, pgJDBC 42.7.11, six real entities loaded from existing target/classes.
- Full Spring Boot application and CompanyEventSchemaConfig NOT started.
- hbm2ddl.auto=validate fixed. Local SSL disabled only for this localhost fixture; no production TLS claim.
- Local trust authentication, no passwords. Password authentication, Supavisor and Java TLS were NOT tested.
- input-hashes.json records original rebuild SQL, Probe and packaged backend hash.
- Test schema uses users INTEGER PK, dummy managed event functions/triggers, auth/storage sentinels.
  These are test fixtures, not a replica of complete Supabase extensions or managed schemas.

## Result

Final run: TOTAL PASS 160, exit 0. Probe.log contains raw results (ignored local artifact).
- Unapproved role SQL aborted atomically, approved ROLLBACK removed new role, approved local COMMIT created role.
- Inherited net-table access and callable SECURITY DEFINER fixture both blocked provisioning.
- NOINHERIT/no SUPERUSER/CREATEDB/CREATEROLE/BYPASSRLS, zero upward memberships.
- Existing rebuild safety flags, dependency rejection, full ROLLBACK and COMMIT paths passed using gwon_prod_app.
- 63 columns / 12 constraints / 12 indexes, 6 ID generators, 3 identity IDs, two integer sequences preserved.
- Six real entity Hibernate validate passed with dedicated role; metadata unchanged before/after validate/DML.
- CREATE permanent table/schema, ALTER/DROP business table, CREATE ROLE, SUPERUSER alteration,
  SET ROLE/admin session authorization, grant administrator membership, TRUNCATE, setval denied (42501).
- No table SELECT WITH GRANT OPTION. Grant redistribution is checked through effective grant-option absence,
  not a claim that every GRANT statement must raise (PostgreSQL may return a warning).
- TEMP create/drop allowed; row_security=off access to protected table denied.
- No users/auth/storage direct SELECT; 6 sequences usable with USAGE.
- CRUD exercised for company_events/gallery_photos/home_banners/test_users, members insert/read and restricted deletion,
  notice create/read/viewcount/delete, legacy users FK SET NULL and member FK RESTRICT, member phone UNIQUE.
- Banner EXCLUSIVE LOCK passed without DDL privilege.
- RLS ENABLE/FORCE for six rebuilt tables, only dedicated app policy; broad mock API-role ACL removed by rebuild as designed.
- New optional grant SQL reused existing backend_crud without changing other ACL/RLS/schema after rebuild.
- Read-only role audit executed successfully.
- Shutdown stop=0, pg_ctl status=3, pg_isready=2. Every created cluster stopped; existing clusters untouched.

## Failures and corrections retained

Initial sandbox localhost readiness failed; server stopped. Elevated localhost run used only this new test directory.
First functional run: pg_catalog first in role search_path caused Hibernate missing company_events.
Role SQL corrected to public,pg_catalog,pg_temp; public CREATE must remain false.
Second functional run: dummy event-trigger audit tried writing its fixture log as app during a GRANT test.
Dummy logger now records only isolated_admin; production managed triggers were never modified.
Final verification used a new pgdata-89-6-final directory. Earlier directories retained and stopped, never reinitialized.

## Limits / release gate

The existing-schema optional grant path (before rebuild) was statically reviewed; local execution covered reuse after rebuild.
Existing anonymous/authenticated/service-role preservation for that optional pre-rebuild path is not a live integration result.
No live Supabase access. Actual inherited PUBLIC/extension rights, Pooler username/password authentication,
managed event-trigger behavior, security-definer/invoker/default expressions and external PostgREST consumers remain release gates.
Provisioning deliberately blocks net exposure/SECURITY DEFINER; actual Supabase may therefore reject this draft.
No global PUBLIC or managed ACL changes are proposed as a workaround.

## Reproduction

PowerShell from repository: `& ./docs/sql/prod-rebuild/validation-89-6/run.ps1`
Existing final pgdata causes deliberate stop. Do not delete/reinitialize it; use a reviewed new test directory and port for reruns.
Runner clears inherited PG/JVM connection options, verifies versions, exact data directory and localhost endpoint,
compiles Probe.java, performs tests, and stops only its exact data directory in finally.
Execution-enabled rebuild copies, pgdata, extracted libraries, classes and logs are ignored local artifacts.
No commits/push/deployment/Render changes or production data changes were performed.
