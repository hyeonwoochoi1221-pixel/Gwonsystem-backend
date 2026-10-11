# 89-6 운영 앱 역할 설계 — 실행 전 승인 필요

## 대상과 경계

제안 역할은 `gwon_prod_app`이다. 실제 운영에 이미 존재하는 역할로 간주하지 않는다.
운영 프로젝트는 GWONSYSTEM / `zsaqpsohhwygrawphnsg`, 개발 프로젝트는 `elddmkyqfmxryemjrxhh`이다.
DB 이름과 서버 주소만으로 Supabase 프로젝트를 확정할 수 없다. Dashboard의 프로젝트 및 Connect 정보와 별도로 대조한다.
이 단계에서는 어느 Supabase에도 접속하지 않았으며 Render도 변경하지 않았다.

SQL은 PostgreSQL 17용이며 기본 상태는 승인 검사 실패 및 최종 ROLLBACK이다.
비밀번호를 포함하지 않는다. PASSWORD NULL은 비밀번호 접속을 활성화하지 않는다.
CREATE ROLE 권한은 실행자가 실제로 보유하는지 확인해야 한다. Supabase postgres를 진짜 SUPERUSER로 가정하지 않는다.
역할이 이미 존재하면 생성 SQL은 중단한다. 재실행을 위해 기존 역할을 자동 삭제하거나 덮어쓰지 않는다.

## 코드 기준 권한

| 객체 | 기능 / 코드 | 직접 부여 |
|---|---|---|
| postgres DB | JDBC 연결 | CONNECT |
| public 스키마 | 6개 JPA Entity / Repository | USAGE |
| members | MemberRepository, 인증/회원 및 ProdNoticeAuthorPolicy 회원 재조회 | SELECT, INSERT, UPDATE, DELETE |
| notices | NoticeRepository, ProdNoticeAuthorPolicy JDBC INSERT / 조회수 | SELECT, INSERT, UPDATE, DELETE |
| company_events | CompanyEventRepository, 일정 | SELECT, INSERT, UPDATE, DELETE |
| gallery_photos | GalleryPhotoRepository, 갤러리 | SELECT, INSERT, UPDATE, DELETE |
| home_banners | BannerRepository, 배너 및 EXCLUSIVE LOCK | SELECT, INSERT, UPDATE, DELETE |
| test_users | TestUserRepository 및 실제 CRUD Controller | SELECT, INSERT, UPDATE, DELETE |
| 위 6개 id 소유 시퀀스 | IDENTITY/nextval / RETURNING | USAGE만 |

USAGE는 nextval/currval에 충분하다. setval용 UPDATE, 시퀀스 SELECT는 부여하지 않는다.
배너 LOCK TABLE은 UPDATE 권한으로 가능하며 별도 DDL 권한을 필요로 하지 않는다.
users는 legacy FK 대상일 뿐 앱 직접 CRUD 권한이 필요하지 않다. FK 검사는 앱의 users SELECT를 요구하지 않는다.
departments, inquiries, schedules에는 현재 확인된 백엔드 직접 접근이 없어 권한을 추가하지 않는다.
auth/storage 스키마 권한, anon/authenticated/service_role 멤버십, pg_monitor 및 서버 파일 권한을 부여하지 않는다.
SupabaseStorageService는 HTTP Storage API를 사용한다. DB 역할 전환과 Storage API Key/JWT는 별개다.
새 테이블 자동 GRANT를 위한 ALTER DEFAULT PRIVILEGES도 사용하지 않는다. 향후 마이그레이션에 객체별 GRANT를 포함한다.

## RLS와 기존 공개 기능

NOINHERIT는 PUBLIC 권한을 제거하지 않는다. 모든 역할이 PUBLIC의 CONNECT/TEMP/함수 EXECUTE 등을 받을 수 있다.
새 역할은 NOINHERIT, NOSUPERUSER, NOBYPASSRLS, NOCREATEDB, NOCREATEROLE, NOREPLICATION이다.
역할 멤버십과 업무 객체 소유권이 없어야 한다. NOINHERIT만으로 SET ROLE 상승이 방지되는 것은 아니다.

확보된 운영 조사: 소유자는 postgres, company_events/gallery_photos/home_banners RLS 비활성,
그 외 업무 테이블 RLS 활성, FORCE 비활성. 알려진 정책은 test_users의 PUBLIC FOR ALL true 정책이다.
anon/authenticated/service_role에 넓은 기존 테이블 ACL이 있다. 이 사실을 신규 역할의 권한으로 복사하지 않는다.
현재 역할 postgres는 소유자로서 보통 RLS를 우회한다. 앱 역할 전환 시 기존 정책만으로 members/notices 접근은 실패할 수 있다.

`02-grants-rls.DRAFT.sql`은 기존 스키마 전환 선택지다. 기존 RLS 활성 여부와 다른 역할 ACL/정책을 유지하고,
RLS가 활성인 대상에만 앱 전용 permissive FOR ALL true 정책을 추가한다. 해당 역할에 적용되는 restrictive 정책은 중단한다.
역할 제한 true 정책은 모든 업무 행 접근을 허용하므로 회원별 권한은 기존 Spring Security/서비스가 책임진다.
Supabase JWT를 직접 사용하는 클라이언트 역할에 이 정책을 공유하지 않는다.

현재 계획된 재구축에서는 `01`로 역할 생성 후 기존 재구축 `03`이 6개 테이블의 ENABLE/FORCE RLS,
backend_crud 정책, 앱 CRUD/시퀀스 USAGE를 적용한다. 역할 SQL `02`를 재구축 전에 실행하지 않는다.
기존 재구축의 미확인 정책 차단과 충돌할 수 있기 때문이다. 재구축 이후 02는 동일 backend_crud를 재사용한다.
재구축 03은 6개 테이블의 anon/authenticated/service_role/PUBLIC 접근을 제거한다.
이는 별도 승인 대상이며 직접 PostgREST 소비자가 있다면 깨질 수 있다. 프론트의 백엔드 경유 공개 API는 앱 역할로 접근한다.
외부 직접 소비자가 없다는 사실은 이 단계에서 확인하지 못했다.
Supabase 관리 이벤트 트리거 6개, 관리 스키마 및 글로벌 PUBLIC/default ACL은 변경하지 않는다.

## TEMP / 간접 권한 한계

PUBLIC에서 상속되는 TEMP는 허용한다. 임시 객체를 만드는 권한은 영구 업무 테이블 ALTER/DROP 권한이 아니다.
그러나 임시 객체/함수 이름으로 신뢰되지 않은 search_path를 이용할 수 있다.
역할 search_path는 public,pg_catalog,pg_temp: public CREATE가 없어야 하고 pg_temp는 마지막이다.
Hibernate 기본 스키마 식별을 위해 public을 먼저 둔다. 민감한 SQL은 public/pg_catalog로 명시한다.
역할이 자신의 search_path를 변경하는 것은 막지 못하므로 SECURITY DEFINER 함수는 별도의 안전한 search_path와 EXECUTE 검토가 필요하다.
트리거·DEFAULT·함수·확장·이벤트 트리거가 다른 소유자의 권한으로 간접 작업을 수행할 수 있다.
전용 역할과 DDL 안전 코드만으로 모든 간접 DDL/정보 접근이 불가능하다고 보장하지 않는다.

Supabase 공식 안내에 따르면 PUBLIC에 pg_net 큐/응답 테이블 및 HTTP 함수 권한이 있을 수 있다.
큐에는 HTTP 헤더 등이 들어갈 수 있으므로 데이터는 조회하지 않는다. 01은 접근 가능한 net 관계 또는
비시스템 SECURITY DEFINER 함수가 있으면 중단한다. 관리 PUBLIC 권한을 바꾸어 우회하지 않는다.
SECURITY INVOKER 함수, 사용자 트리거/DEFAULT, PUBLIC large-object 권한 및 추가 확장 접근은 메타데이터/정의 별도 검토가 필요하다.
01의 검토 승인 플래그는 이 검토가 끝났다는 수동 확인이지 자동적인 완전 증명이 아니다.
실제 pg_net 권한 등으로 차단되면 별도 지원/분리 설계가 필요하며 이 SQL을 무조건 승인하는 방법으로 해결하지 않는다.

## 실행 순서 / Render / 롤백

1. 운영 Connect 정보, 사용자명, 프로젝트, postgres DB를 대조한다. 관리 권한/기존 역할 부재/메타데이터 및 외부 소비자를 확인한다.
2. 현재 원본 01/02/재구축 03은 실행 승인 전이다. 별도 검토 실행본에 승인 플래그/프로젝트/역할과 최종 COMMIT을 적용한다.
3. 승인 후 01로 역할을 생성한다. 비밀번호는 SQL 파일·명령 인수·로그에 넣지 않는다.
   신뢰된 관리자 psql에서 대화형 `\password gwon_prod_app` 등 승인된 비밀 입력 경로를 사용한다.
4. 재구축 경로: 모든 쓰기 중단 및 자동 배포 제어 -> 01 -> 기존 03 재구축(앱 역할 gwon_prod_app) -> 기존 postflight 및 역할 03.
   데이터 재구축 없이 전환하는 경우에만 역할 02 적용 후 권한/RLS/인증 회귀 테스트를 수행한다. 이 경로는 운영 스키마 준비 여부를 별도 확인한다.
5. 실제 Pooler를 통한 별도 승인 연결 테스트로 current_user=gwon_prod_app 및 프로젝트를 확인한다. 이 단계에서는 미수행.
6. Render 비밀 저장소에서 DB 사용자/비밀번호만 승인된 운영 역할로 전환한다. URL은 동일 운영 프로젝트의 Dashboard Session Pooler 값을 유지한다.
   shared Pooler username: `gwon_prod_app.zsaqpsohhwygrawphnsg`; PostgreSQL current_user는 gwon_prod_app이다.
   JDBC 형식: `jdbc:postgresql://<운영 Dashboard Session Pooler host>:5432/postgres?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory`
   Java CA/호스트 검증이 준비·검증된 경우의 권장 형식이다. 현재 운영 sslmode=require와 TLS 신뢰 문제를 이 작업에서 변경/해결한 것으로 간주하지 않는다.
   실제 application.properties: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
   비밀번호는 Render의 비밀 입력란에서만 설정하고 보고서/실행 파일에는 저장하지 않는다.
   direct/dedicated Pooler는 사용자명 형식이 달라 role만 사용하므로 shared 형식을 무조건 복사하지 않는다.
7. DDL validate/SQL 초기화 차단 유지, 로그인·권한·공지 CRUD/조회수·일정·갤러리·배너·외부 연동을 운영 승인 후 확인한다.
8. 연결 변경 실패: 앱을 중지하고 승인된 이전 자격증명으로 되돌리는 것은 별도 긴급 승인 작업이다.
   이전 postgres 연결 복원은 고권한 위험을 재도입한다. 자동 실행하지 않는다. 역할/정책은 즉시 DROP 하지 않는다.
   기존 스키마 경로에서 다른 역할 ACL/정책은 그대로여서 연결 롤백이 가능하다.
   재구축 후 옛 backend 코드로 되돌리면 작성자 NOT NULL 등과 충돌할 수 있어 호환 코드/스키마 검증이 선행되어야 한다.
   재구축 데이터 손실은 역할 롤백으로 복원되지 않는다. 이 역할 작업은 데이터 삭제를 승인하지 않는다.

비밀번호 및 Pooler 인증, 실제 Supabase 이벤트 트리거/확장 함수, 운영 역할 생성 권한은 로컬 검증 범위 밖이다.

## 검증 근거 / 공식 출처

격리 결과: ../validation-89-6/REPORT.md. 실제 JPA 6개 validate 및 재구축/역할 SQL 검증을 포함한다.
- [Supabase Pooler username / endpoint](https://supabase.com/docs/guides/troubleshooting/tenant-or-user-not-found)
- [Supabase custom-role PUBLIC inheritance and pg_net](https://supabase.com/docs/guides/troubleshooting/custom-role-inherits-privileges-that-were-not-explicitly-granted-ddaa1c)
- [PostgreSQL 17 RLS](https://www.postgresql.org/docs/17/ddl-rowsecurity.html)
- [PostgreSQL 17 role membership](https://www.postgresql.org/docs/17/role-membership.html)
