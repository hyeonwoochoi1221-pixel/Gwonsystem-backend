# 89-7 local verification

See ../release-review-89-7/REPORT.md for production read-only findings, commit scopes and release gates.

New cluster only: pgdata-89-7, 127.0.0.1:55497, PostgreSQL 17.11, Hibernate 6.6.53.Final, pgJDBC 42.7.11.
Probe.java extends the unchanged 89-6 probe with non-superuser CREATEROLE provisioning and metadata queries 04/05.
164 assertions PASS, exit 0. Business Java/configuration and older clusters unchanged.
Mock creator owns test DB/public but has neither SUPERUSER nor BYPASSRLS; no production role was created.
Local trust authentication and SSL disable are fixtures only, not proof of Pooler credentials/TLS.
No complete Spring Boot server started. Uses previously built unchanged business classes.
Raw logs and execution-enabled test copies stay ignored locally.
Schema snapshots before/after have identical SHA256 7A052802E552395232FD2C34DEA57EFE7DF46DAA6A076EE721C972DCA7F53422.
Shutdown: stop 0, status 3, readiness 2. Only this exact test data directory stopped.

TLS local test: compile existing VerifySupabaseCa and TrustProbe into this directory,
verify source public CA, clone JDK cacerts to cacerts-test, keytool import alias gwon-supabase-root-2021,
run TrustProbe, then run VerifySupabaseCa on its corrupted public-certificate copy and expect nonzero exit.
All passed. No system truststore changed, no private key created, no DB/API password stored.
Docker unavailable; Linux Dockerfile and real server hostname/chain validation remain untested.

Full build/tests were not repeated because business code/settings and verified SQL 01~03 were unchanged.
CA tools were compiled/tested and all six actual JPA entities were revalidated in a new cluster.
