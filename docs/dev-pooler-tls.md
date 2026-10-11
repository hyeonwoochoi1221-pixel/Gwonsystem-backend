# 개발 Session Pooler TLS 검사 수정

## 판단 근거

클라이언트와 Supavisor Pooler의 연결, Pooler와 PostgreSQL의 연결은 별도 구간이다.
Supavisor의 tenant 설정에서도 enforce_ssl(client)과 upstream_ssl/database TLS CA가 분리된다.
pg_stat_ssl은 PostgreSQL backend 프로세스의 연결 상태를 나타내므로 Pooler 경유 세션에서는
Pooler→DB 구간을 관찰한다. 클라이언트→Pooler의 TLS 또는 인증서/호스트명 검증 성공을 증명하지 않는다.
사용자가 보고한 verify-full 연결 성공과 pg_stat_ssl.ssl=false는 모순이 아니다.
실제 해당 Supabase 네트워크 구성을 이번 작업에서 접속하여 확인한 것은 아니다.

확인한 pgJDBC 버전은 42.7.11이다. DefaultJavaSSLFactory는 기본 JSSE SSLSocketFactory를
사용한다. MakeSSL.convert는 startHandshake를 실행한 뒤 VERIFY_FULL 모드에서 기본
PGjdbcHostnameVerifier로 호스트명을 검증한다. CA 또는 호스트명 검증 실패는 SQLException으로
연결 생성을 실패시킨다. verify-full에서는 prefer/allow 같은 평문 재시도가 허용되지 않는다.

## 최종 검사 기준

- 모든 dev 연결: 승인된 host/ref/port/DB 경로/로그인 라우팅, URL의 verify-full 및
  DefaultJavaSSLFactory를 검사한다. Hikari SSL 설정도 기존대로 강제한다.
  URL의 커스텀 verifier/factory/socket 옵션은 허용하지 않고, Hikari의 커스텀
  sslhostnameverifier/socketFactory 설정도 시작 전에 거부한다.
- 승인된 Session pooler: DB-side pg_stat_ssl 값은 진단 정보이며 false만으로 기동을
  차단하지 않는다. 실제 DB 이름·current_user·영구 DDL 권한·역할 멤버십 검사는 동일하게 수행한다.
- Direct 연결: 위 기준에 더하여 PostgreSQL-side TLS=true 검사도 유지한다.
  Pooler처럼 처리하는 예외는 승인된 pooler 호스트와 라우팅 검사를 통과한 연결에만 적용한다.
- 읽기 전용 SQL의 TLS 표시를 database_peer_tls로 변경하여 client TLS와 구분한다.
- 운영 프로필에는 DevDataSourceGuard가 등록되지 않는다. 운영 Dockerfile/main은 수정하지 않았다.

클라이언트 TLS 보장은 검증된 pgJDBC/JSSE 연결 생성 과정에 의존한다. metadata 문자열만으로
암호화된 소켓을 독립적으로 관측하는 것이 아니며, 비공개 driver 필드의 reflection은 사용하지 않는다.
신뢰 저장소를 임의의 TrustManager로 교체하거나 가짜 DataSource/코드를 주입하는 관리자의
행위까지 방어하는 장치는 아니다. JDBC 드라이버 업그레이드 때 TLS 테스트를 다시 수행해야 한다.

## 남은 위험

이 변경은 모든 구간의 TLS를 보장하지 않는다. Pooler→DB 구간의 비암호화 여부/보호 경계는
Supabase 운영 설정과 네트워크에 속하며 클라이언트의 verify-full로 그 구간을 검증할 수 없다.
해당 구간까지 TLS가 필수인 보안 정책이면 공급자 설정 확인 또는 Direct 연결 설계를 별도 검토한다.
CA 신뢰 및 pooler 호스트명도 프로젝트 ID 자체를 증명하지 않으므로 기존 개발 ref 라우팅,
실제 DB 이름·전용 계정·권한 검사와 독립적으로 검토한 허용 목록을 유지한다.
RLS/SECURITY DEFINER/트리거 간접 권한 위험은 기존 TEMP 검토 문서의 한계대로 남아 있다.
실제 Supabase/Render 접속·설정 변경·배포는 수행하지 않았다.

## 테스트

mock guard 테스트는 pooler에서 DB-side TLS=false/true 성공, Direct의 false 거부,
잘못된 DB/계정/프로젝트, 과도한 역할·DDL 권한 거부와 unverified SSL 옵션 거부를 확인한다.
새 localhost PostgreSQL 및 forked Java 프로세스의 실제 pgJDBC/JSSE 테스트는 신뢰된 CA와
일치하는 hostname의 연결 성공, 미신뢰 CA/hostname 불일치/SSL 비지원 서버의 연결 거부를 확인한다.
테스트 인증서와 개인 키는 JUnit 임시 디렉터리에서 생성·정리하며 저장소에 포함하지 않는다.
JSSE 기본 소켓 팩토리 캐시와 trustStore 변경이 다른 테스트에 영향을 주지 않도록 프로세스를 분리한다.
로컬 PostgreSQL은 14.22이며 Supabase PostgreSQL 17의 실제 연결 테스트를 대신하지 않는다.

최종 `mvnw.cmd package` 성공: 테스트 77개, 실패 0, 오류 0, 건너뜀 0.
인증·회원·공지·갤러리 등 기존 회귀 테스트를 포함하며 실행 JAR도 생성했다.
`git diff --check` 통과. 작업 브랜치는 codex/dev-deployment-guards다.

참고:

- [Supavisor tenant TLS 설정](https://github.com/supabase/supavisor/blob/main/docs/configuration/tenants.md)
- [PostgreSQL pg_stat_ssl](https://www.postgresql.org/docs/17/monitoring-stats.html#MONITORING-PG-STAT-SSL-VIEW)
- [pgJDBC SSL 문서](https://jdbc.postgresql.org/documentation/ssl/)
- [pgJDBC 42.7.11 MakeSSL](https://github.com/pgjdbc/pgjdbc/blob/REL42.7.11/pgjdbc/src/main/java/org/postgresql/ssl/MakeSSL.java)
