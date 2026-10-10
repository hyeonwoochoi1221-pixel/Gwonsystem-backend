# 개발 Render 배포 안전장치

작업 기준은 GitHub `develop`과 동일한 `67f7d542e4acd617e7c6d6e5cf4accdcbaee47ff`다. 작업 브랜치는 `codex/dev-deployment-guards`, 작업 디렉터리는 `D:/dev/GWON_System/backend/dev-deployment-guards`다. 기존 `backend/backend` 및 frontend 미커밋 작업은 수정하지 않았다. Supabase 접속/변경, 환경변수 변경, Push, 병합, 배포는 수행하지 않았다.

## 개발 환경의 강제 조건

`SPRING_PROFILES_ACTIVE=dev`와 `APP_DEPLOYMENT_ENV=dev`를 함께 설정한다. dev와 다른 프로필을 동시에 활성화하면 실패한다. 배포 환경 표시가 dev이거나 Render 서비스 이름이 `-dev`로 끝나는데 dev 프로필이 없으면 실패한다. 배포 표시와 서비스 이름까지 모두 누락된 경우 일반 운영 실행과 구분할 수 없으므로, Render에서 두 변수를 반드시 설정해야 한다.

EnvironmentPostProcessor는 ConfigData 로딩 다음, DataSource/SMTP/OAuth Bean 생성 전에 실행한다. 오류에는 설정 항목 이름/실패 종류만 포함하며 URL·비밀번호·키를 출력하지 않는다.

- `DEV_SUPABASE_PROJECT_REF`: 승인된 개발 Supabase 프로젝트의 20자리 영문 소문자 ref. 알려진 운영 ref `zsaqpsohhwygrawphnsg`는 허용 목록에 넣어도 거부한다.
- `DEV_DB_NAME`: 실제 PostgreSQL DB 이름. Supabase 프로젝트 표시 이름 `gwonsystem-dev`와 다르며 보통 `postgres`다.
- `DEV_DB_ROLE`: 별도 개발 앱 로그인 역할. postgres/supabase_admin/service_role/anon/authenticated는 거부한다.
- Direct host: `db.<개발 ref>.supabase.co`, 포트 5432, 로그인명은 DEV_DB_ROLE과 동일해야 한다.
- Session pooler: `DEV_DB_POOLER_HOST`에 정확한 `.pooler.supabase.com` 호스트를 추가 승인해야 한다. 포트 5432, 로그인명은 `<DEV_DB_ROLE>.<개발 ref>`여야 한다. 서버의 current_user는 DEV_DB_ROLE이어야 한다. Transaction pooler 6543은 세션/트랜잭션 검사와 JPA 호환성 이유로 지원하지 않는다.
- JDBC URL은 단일 host와 정확한 DB 경로만 허용한다. URL 안의 계정/비밀번호, multi-host, 임의 socket factory, options 등은 거부한다.
- `sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory`가 필수다. 인증서 체인과 hostname을 검증한다. 신뢰할 수 없는 인증서는 실패한다. JVM truststore가 Supabase 인증서 체인을 신뢰하지 않는 경우 개발용 신뢰 저장소를 별도 검토해야 하며 SSL 검증을 낮춰 우회하면 안 된다.
- Storage URL은 정확히 `https://<개발 ref>.supabase.co`여야 한다. 버킷/서버 키도 명시적으로 필요하다. Legacy service_role JWT는 ref/role을 오프라인으로 검사한다. JWT 서명 검증이나 Storage API 인증을 대신하는 검사는 아니다. `sb_secret_*` 키는 opaque 값이어서 코드만으로 프로젝트 소유자를 증명할 수 없다. 개발 프로젝트에서 발급한 키인지 담당자가 확인해야 한다.

## 실제 DB 연결 검사와 한계

개발 DataSource를 감싸서 Hibernate 및 업무 코드에 연결을 넘기기 전에 JDBC metadata URL을 다시 승인 목록과 대조한다. 읽기 전용 트랜잭션의 SELECT로 actual `current_database()`, `current_user`, TLS 상태, 역할/DDL 권한을 확인하고 rollback 후 연결 상태를 복원한다. 다른 DB/역할, TLS 없음, SUPERUSER/CREATEDB/CREATEROLE/BYPASSRLS, public CREATE, public 객체 소유 역할의 멤버십, DB CREATE 권한은 거부한다. 대체 계정으로 getConnection을 호출하는 것도 거부한다. 검증 실패 연결은 닫는다.

이 검사는 문자열 검사보다 강하지만 DB 이름/역할은 서버 고유 프로젝트 ID가 아니다. Direct host의 인증서 검증과 project ref, pooler의 ref가 포함된 로그인 라우팅 및 실제 역할/DB 검사를 조합한다. 공유 pooler의 pg_stat_ssl 값은 DB 서버 쪽 연결 상태이며 client→pooler TLS는 JDBC verify-full로 보호한다. 악의적으로 설정/코드를 바꾸는 관리자까지 막는 시스템은 아니다. 승인 목록 자체는 독립적으로 검토해야 하며, 향후 새로운 운영 프로젝트가 생기면 denylist도 업데이트해야 한다.

개발 DB 앱 역할에는 필요한 업무 테이블의 SELECT/INSERT/UPDATE/DELETE와 전용 ID 시퀀스 사용 권한만 별도 승인 절차로 준비해야 한다. 앱 역할을 테이블·스키마 소유자로 만들거나 public/DB CREATE를 부여하면 기동하지 않는다. 이번 작업에서 역할/권한/스키마를 생성하거나 변경하지 않았다. 개발 스키마와 전용 역할이 아직 없으면 기동 실패가 정상이다.

## 자동 DDL 차단

dev에서는 Hibernate action을 `validate`로 강제한다. 환경변수로 create/update를 주어도 덮어쓴다. JPA schema-generation 우회 설정은 거부하며 SQL init/Flyway/Liquibase, Hikari connection-init-sql은 차단한다. JDBC 기본 업무 스키마도 public로 고정한다. CompanyEventSchemaConfig는 `@Profile("!dev")`로 제외되어 기존 시작 시 ALTER TABLE이 실행되지 않는다. 스키마가 없거나 Entity와 호환되지 않으면 생성/수정하지 않고 실패한다. Hibernate validate는 모든 트리거/RLS/FK/업무 의미까지 검증하는 도구는 아니므로 별도 승인된 스키마 검토가 필요하다.

## Storage, 이메일, CORS

dev Storage 설정은 빈 기본값이며 필수 값이 없으면 기동하지 않는다. `EmailService`는 `app.email.logo-url`을 읽는다. dev EMAIL_LOGO_URL이 비어 있으면 img 태그를 생략한다. 지정하는 경우 개발 프로젝트의 지정한 Storage 버킷 공개 URL만 허용한다. URL을 HTML에 삽입할 때 escape한다.

FRONTEND_URL과 CORS_ALLOWED_ORIGINS는 DEV_ALLOWED_FRONTEND_ORIGINS에 포함되어야 한다. Origin은 경로/쿼리 없는 HTTPS 주소만 허용한다. 알려진 운영 gwonsystem.com 및 그 하위 도메인, 운영 백엔드 Render 도메인은 승인 목록에 넣어도 거부한다. 쿠키 Secure=true, SameSite=none, Domain 미지정 및 forwarded headers framework 처리를 강제한다. Render가 전달하는 proxy header 신뢰 경계를 고려하여 직접 외부 접근 경로를 추가할 때 재검토해야 한다.

## 외부 연동은 기본 비활성화

전체 변수와 예시는 `render-dev.env.example`에 있다. 운영 자격증명을 가져오거나 출력하지 않았다.

- SMTP: DEV_MAIL_ENABLED=false 기본값. 발송·OTP 저장 전에 503으로 거부한다. 활성화 시 DEV_MAIL_HOST/PORT/USERNAME/PASSWORD 및 DEV_MAIL_ALLOWED_RECIPIENTS가 모두 필요하고 승인된 정확한 이메일에만 발송한다. 서버는 LOCAL/SMTP 환경에서 개발용 계정인지 암호학적으로 판별할 수 없으므로 발급 주체 확인이 필요하다. 비활성화 시 회원가입 이메일 인증/계정찾기/비밀번호 재설정 메일도 사용할 수 없으며 테스트용 인증 우회는 추가하지 않았다. 기존 계정 로그인은 유지한다.
- OAuth: DEV_OAUTH_ENABLED=false 기본값. OAuth 자동설정을 제외하고 로그인 시작/콜백 경로 접근을 거부한다. 활성화 시 DEV_GOOGLE_CLIENT_ID/SECRET, DEV_NAVER_CLIENT_ID/SECRET, DEV_BACKEND_ORIGIN이 모두 필요하다. 공급자 앱에서 개발 백엔드 콜백을 별도 등록한다. 일반 운영 GOOGLE/NAVER 변수를 복사해도 dev 등록값에 사용되지 않는다.
- Places: DEV_PLACES_ENABLED=false 기본값, 요청 시 503. 활성화 시 DEV_GOOGLE_PLACES_API_KEY만 사용한다. 별도 개발 키의 API 제한/할당량을 검토한다.
- 전용 외부 자격증명도 값 자체만으로 개발 발급 여부를 증명할 수는 없다. 운영 키를 DEV_* 이름으로 복사하는 행위는 금지하며 운영자 검토 대상이다.

## 운영 호환성

dev가 아닌 기존 운영 실행에서 기존 DB 설정, ddl-auto=update, CompanyEvent 시작 시 DDL, OAuth/SMTP/Places 사용은 유지된다. 운영 Storage 기본값은 기존 호환성을 위해 application.properties에 남아 있지만 dev에서는 사용되지 않는다.

운영에 보이는 변경은 EmailService 로고를 EMAIL_LOGO_URL로 재정의할 수 있게 된 점이다. 미설정 시 기존 운영 로고가 동일하게 사용된다. IntegrationDisabledException 전용 503 응답은 새 비활성화 기능에만 적용된다. `/internal/health` Controller는 dev에서만 등록되며 DB·자격증명 정보를 반환하지 않는다. Docker WORKDIR를 명시하고 .dockerignore로 .env/로컬 설정/로그/Git/기존 빌드 산출물을 이미지 입력에서 제외했다. 기존 운영용 JAR 실행 방식은 동일하다.

## 검증 및 배포 준비

- 전체 백엔드 테스트는 실제 Supabase 대신 새 임시 localhost PostgreSQL과 mock HTTP/SMTP를 사용한다. 기본 BackendApplicationTests도 test-isolated 프로필과 임시 DB로 변경했다.
- DevStartupTest는 실제 SpringApplication/factories 순서에서 운영 주소 거부가 Bean 생성 전 발생함을 검증한다. 유효한 dev 설정의 Servlet Boot 시험은 datasource/JPA 자동설정을 제외하여 SMTP/OAuth 비활성화 및 health/security를 검증한다. 실제 Supabase dev 연결 성공을 의미하지 않는다.
- DevDataSourceGuardTest는 JDBC mock으로 metadata, actual DB/역할/TLS/권한 불일치 시 연결 거부 및 read-only/rollback을 검증한다.
- DevSchemaValidationTest는 실제 임시 PostgreSQL에서 dev 설정의 validate가 기존 컬럼 구조를 변경하지 않으며 누락 컬럼을 자동 복구하지 않고 실패함을 검증한다.
- 기존 인증/회원/공지/갤러리/배너/일정 회귀 테스트를 실행한다. 실 SMTP/OAuth/Storage와 운영/개발 Supabase는 호출하지 않는다.
- Docker CLI가 이 환경에 없어 Docker 이미지 빌드·실컨테이너 기동은 실행하지 못했다. Dockerfile의 Maven build 명령, 실행 JAR 및 profile/factories 포함 여부는 별도로 확인한다. Render에서는 Dockerfile이 있는 저장소 루트, develop 반영 후 승인된 커밋, health 경로 `/internal/health`, Render 주입 PORT를 사용해야 한다.
- 스키마·역할·버킷·인증서·실제 Render 변수의 존재와 정확성은 이번에 확인하지 않았다. 코드 검증 완료가 배포 승인을 뜻하지 않는다.

## develop 반영 절차 — 사용자 승인 후만 수행

1. 이 작업 디렉터리의 diff와 변수 목록을 검토한다. RND가 있는 기존 작업 디렉터리에서 checkout/reset/clean/stash하지 않는다.
2. 승인 후 작업 브랜치의 명시된 파일만 commit하고, 최신 origin/develop을 fetch하여 차이를 검토한다. 충돌은 이 격리 작업 디렉터리에서 해결하고 테스트한다.
3. 승인된 경우에만 codex/dev-deployment-guards를 Push하고 base=develop의 PR을 만든다. main 직접 변경·Push는 하지 않는다.
4. PR 검토/승인 후 develop에 병합한다. 운영 main과 운영 Render 설정은 변경하지 않는다.
5. 개발 스키마/권한/Storage 준비와 Render 변수 설정, 실제 배포는 별도 사용자 승인을 받은 후 진행한다. 자동 배포가 연결되어 있다면 Push/병합 전에 그 트리거까지 함께 확인한다.

## 변경 파일 전체 목록

작업 디렉터리 기준 상대 경로다. 이 목록은 기존 RND 작업 파일을 포함하지 않는다.

```text
.dockerignore
Dockerfile
pom.xml
docs/dev-deployment-safety.md
docs/render-dev.env.example
src/main/resources/application.properties
src/main/resources/application-dev.properties
src/main/resources/META-INF/spring.factories
src/main/java/com/gwonsystem/backend/config/CompanyEventSchemaConfig.java
src/main/java/com/gwonsystem/backend/config/DevEnvironmentGuard.java
src/main/java/com/gwonsystem/backend/config/DevDataSourceGuard.java
src/main/java/com/gwonsystem/backend/config/SecurityConfig.java
src/main/java/com/gwonsystem/backend/controller/HealthController.java
src/main/java/com/gwonsystem/backend/exception/IntegrationDisabledException.java
src/main/java/com/gwonsystem/backend/exception/GlobalExceptionHandler.java
src/main/java/com/gwonsystem/backend/service/EmailService.java
src/main/java/com/gwonsystem/backend/service/GooglePlacesService.java
src/test/resources/application-test-isolated.properties
src/test/java/com/gwonsystem/backend/BackendApplicationTests.java
src/test/java/com/gwonsystem/backend/DevIntegrationsTest.java
src/test/java/com/gwonsystem/backend/DevSecurityHttpTest.java
src/test/java/com/gwonsystem/backend/NoticeHttpTest.java
src/test/java/com/gwonsystem/backend/config/DevEnvironmentGuardTest.java
src/test/java/com/gwonsystem/backend/config/DevDataSourceGuardTest.java
src/test/java/com/gwonsystem/backend/config/DevSchemaValidationTest.java
src/test/java/com/gwonsystem/backend/config/DevStartupTest.java
```

## 최종 검증 결과 — 2026-10-11

| 항목 | 결과 |
| --- | --- |
| BE `mvnw.cmd clean package` | 성공. 71 tests, failures=0, errors=0, skipped=0. 실행 JAR 생성 |
| FE `npm run build` | 성공. 기존 로컬 RND 작업을 포함한 frontend이며 수정하지 않음 |
| FE `npm test` | 21 tests 성공 |
| `git diff --check` | 성공 |
| 패키징 내용 | dev 프로필, EnvironmentPostProcessor 등록, 두 guard 클래스 포함. test-isolated 설정/embedded PostgreSQL 테스트 의존성은 실행 JAR에 포함되지 않음 |
| 실행 JAR에 운영 JDBC 주소 지정 | 예상대로 종료 코드 1. EnvironmentPostProcessor 단계에서 거부, DB Bean 생성/연결 전에 종료 |
| 실제 Supabase/SMTP/OAuth/Places 호출 | 하지 않음 |
| Docker 이미지 빌드/기동 | Docker CLI 없음으로 미실행. Dockerfile 명령·JAR·변수는 검토 |

Windows 샌드박스에서 임시 PostgreSQL 실행 파일이 차단된 clean build 시도는 중단했고, 승인된 샌드박스 밖 재실행으로 전체 clean package가 통과했다. 테스트를 skip하거나 제품의 안전장치를 낮추지 않았다.

실제 Render/Supabase dev TLS 연결과 역할 권한, RLS·버킷 정책·OAuth callback·SMTP 수신은 아직 검증하지 않았다. 전용 역할/승인된 스키마가 준비되지 않았거나 권한/TLS가 부적합하면 의도적으로 기동 실패한다. 이 작업 브랜치는 미커밋 변경 상태로 보존했고 사용자 승인 전 commit/Push/병합/배포를 수행하지 않았다.

## TEMP 허용 기준 변경

PUBLIC에서 상속되는 TEMP 권한을 허용한다. 영구 DDL 권한과 역할 멤버십은 계속 거부하며 JDBC 경로는 public,pg_temp, Hibernate 기본 스키마는 public로 강제한다. 기존 public 객체에 한정된 소유권 검사보다 현재 검사가 넓은 범위를 차단한다. 자세한 허용 범위·임시 객체 이름 가리기·보안 함수 위험은 [dev-temp-privilege-review.md](dev-temp-privilege-review.md)를 참고한다. 이 절의 기준이 위의 이전 검사 범위 설명보다 우선한다.
