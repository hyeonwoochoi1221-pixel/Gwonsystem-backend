# 운영 최초 재구축 초안 — 89-3단계 (운영 실행 금지)

현재 브랜치 codex/prod-notice-transition / HEAD a16ad2e22ec13800b86fce9ef06005b357b00c22 기준.
89-2단계에서 직접 확인한 운영 프로젝트 GWONSYSTEM(zsaqpsohhwygrawphnsg)의 메타데이터를 반영했다.
이 단계에서는 운영/개발 DB에 접속하지 않았고, 새 localhost PostgreSQL에서만 SQL을 실행했다.
최신 검증은 validation-89-3/REPORT.md. validation-20261011은 이전 초안의 역사적 기록이며 최신 결과가 아니다.

## 파일과 범위

- 01-preflight.readonly.sql: 운영 구조/소유권/권한/정책/트리거/의존성 조회. 실행 전 대상 확인 필요.
- 02-backup-manifest.readonly.sql: 기존 선택적 크기/행 수 추정 자료. 백업 자체가 아니며 현재 필수 단계가 아니다.
- 03-rebuild.DESTRUCTIVE-DRAFT.sql: 6개 업무 테이블 초기화 초안. 승인값 false/자리표시자/최종 ROLLBACK 유지.
- 04-postflight.readonly.sql: 63컬럼, 제약/인덱스/권한/RLS/ID/FK/관리 이벤트 트리거 사후 확인.
- validation-89-3/: 소스, 실행 방법, 입력 해시, 구조 스냅샷과 결과 보고서.

재구축: members, notices, company_events, gallery_photos, home_banners, test_users.
유지: users, departments, inquiries, schedules, 기존 member_role ENUM, 모든 auth/storage/관리 객체.
Supabase Auth 계정과 Storage 파일을 복제하거나 삭제하지 않는다. 개발 테스트 데이터도 복사하지 않는다.
users 데이터도 유지한다. users 삭제는 schedules_user_id_fkey의 CASCADE에 영향을 주므로 별도 승인 없이는 금지.
SQL은 DROP RESTRICT만 사용하며 외부 FK/뷰를 CASCADE로 우회하지 않는다.

## 실제 조사 결과와 보존 사항

운영 PostgreSQL 17.6. 대상6테이블62컬럼/11제약/10인덱스. 전체public10테이블89컬럼.
users.id INTEGER, notices.author_id BIGINT NULL의 기존 교차 타입 FK를 그대로 유지한다.
PostgreSQL은 이 조합의 FK를 허용하며, JPA Long/운영 정책의 BIGINT 요구에도 호환된다.
users.id를 BIGINT로 바꾸거나 author_id를 INTEGER로 바꾸지 않는다.
author_member_id BIGINT NOT NULL -> members.id BIGINT, DELETE RESTRICT/UPDATE NO ACTION, validated/nondeferrable.
신규 공지는 서버가 인증된 members를 조회해 작성자를 저장하고 author_id는 NULL로 둔다.
공지 있는 회원 삭제는 FK 오류가 발생한다. users와 members의 ID를 동일인으로 대응하지 않는다.

members.phone UNIQUE를 유지한다. 최종 PK6+UNIQUE4+FK2=12제약, 인덱스12개(작성자 인덱스2개 포함).
기존 운영에 없던 role CHECK를 새로 강제하지 않는다. 역할 검사는 애플리케이션에서 유지된다.
company_events.end_date DATE NULL을 유지한다.
members/notices/test_users 날짜는 기존 timestamptz, gallery/home_banners는 timestamptz(6) 유지.
company/gallery/banner의 bigint BY DEFAULT IDENTITY 3개 유지.
members bigint nextval 시퀀스, notices/test_users integer nextval 시퀀스 유지(컬럼은 모두 BIGINT).
초기화된 시퀀스는 1부터 시작한다. INTEGER 시퀀스의 상한은 유지되며 확대는 후속 버전 마이그레이션 사항이다.
기존 비-ID DEFAULT는 잠금 후 pg_attrdef에서 임시 스냅샷을 만들어 그대로 재적용한다.
실제 DEFAULT 표현식의 리터럴/함수 의미는 아직 별도 검토 필요: existing_defaults_reviewed=false가 실행을 막는다.
Hibernate validate는 NULL/DEFAULT/UNIQUE/FK/권한/RLS의 전체 의미를 보장하지 않으므로 별도 테스트한다.

## Supabase 이벤트 트리거 및 정책

관리 이벤트 트리거6개를 그대로 실행 상태로 둔다. owner와 function owner는 supabase_admin,
function schema는 extensions, SECURITY DEFINER=false이며 이름/event/함수/인자 수/활성 상태까지 대조한다.
issue_graphql_placeholder, pgrst_ddl_watch, pgrst_drop_watch, issue_pg_cron_access,
issue_pg_net_access, issue_pg_graphql_access 이외 항목/변경이 있으면 중단한다.
실행 전후 OID/정의 해시/소유권/ACL/설정/이벤트 태그를 대조해 변경되면 롤백한다.
트리거/함수를 DROP/ALTER/DISABLE하지 않는다. 관리 함수의 실제 내부 동작은 격리 모형으로 보장하지 않는다.

기존 test_users의 FOR ALL TO PUBLIC USING(true) WITH CHECK(true) 정책만 확인된 정책으로 허용한다.
이 정책의 제거와 접근 축소는 backend_only_rls_approved의 별도 승인이 필요하다. 다른 정책은 중단한다.
새6테이블 RLS ENABLE+FORCE, 승인된 업무 앱 역할 전용 all-row CRUD 정책.
익명/일반 Supabase API 역할의 광범위한 접근은 재현하지 않는다.
기존 전역 PUBLIC 권한과 ALTER DEFAULT PRIVILEGES는 변경하지 않는다.
새 테이블/시퀀스에서만 PUBLIC/anon/authenticated/service_role 권한을 제거하고 예상 밖 ACL은 중단한다.
앱 역할에는 CRUD/public USAGE/시퀀스 USAGE만 부여하며 CREATE/ALTER/DROP/TRUNCATE 권한은 부여하지 않는다.
이 구조는 최종 사용자 권한을 Spring Security가 담당한다. 외부 PostgREST 사용자 기능이 있으면 재설계 필요.

## 미확정 사항과 실행 차단

운영 Render 앱 접속 역할은 미확인. 역할/비밀번호를 생성하지 않고 특정 관리 역할을 앱으로 가정하지 않는다.
approved_app_role은 자리표시자. 실제 역할/회원권한/DB·public CREATE/소유권/관리권한/RLS 경로 확인 필요.
PUBLIC TEMP 상속은 허용한다. 다른 스키마 CREATE/실행가능 SECURITY DEFINER 함수도 별도 확인 필요.
production_app_role_verified/backend_only_rls_approved/managed_event_triggers_reviewed/existing_defaults_reviewed는 false 유지.
business_data_reset_approved/project_identity_verified/dependency_review_approved도 false 유지.
운영 프로젝트 식별은 Dashboard 및 실제 연결 정보로 확인해야 한다. DB명/SQL에 적힌 ref만으로는 증명되지 않는다.
기존 데이터 백업 완료 조건은 제거했다. 업무 데이터 삭제는 COMMIT 후 복구 불가임을 승인해야 한다.

## 실제 실행 순서 (이번에는 실행하지 않음)

1. 운영 Render/Vercel 연결 대상, 브랜치/프로필/TLS/Auto-Deploy/운영 OAuth·SMTP·도메인 보존 확인.
2. 운영 최신 메타데이터/DEFAULT/관리 함수 및 외부 사용을 검토하고, 실제 운영 역할과 RLS 접근 설계 확정.
3. 승인된 최종 SQL을 격리 PostgreSQL17에서 다시 검증. 원본의 차단값을 임의로 일괄 true로 변경하지 않는다.
4. main 코드 반영은 자동 배포가 통제된 상태에서 별도 승인 후 수행. 실제 운영 실행/환경 변경은 각각 별도 승인.
5. maintenance로 모든 writer와 구버전 재시작을 차단. 승인 대상6테이블과 운영 프로젝트를 재확인.
6. SQL 전체를 한 트랜잭션으로 실행. 오류 무시/조각 실행 금지. 원본의 COMMIT 변경도 별도 실행 승인 필요.
7. 04와 실제 앱 역할로 사후 점검 후 운영용 백엔드 validate 기동, 이후 프론트 배포/실제 기능 확인.
8. 관리자 신규 등록/별도 승인된 승격 필요. Supabase Auth가 남아도 members 초기화로 기존 앱 회원 연결은 사라진다.
9. 이후 버전 관리된 증분 마이그레이션만 사용. 앱은 validate만 유지하고 DDL은 별도 승인된 실행 주체가 담당.

원본은 재실행용이 아니다. author_member_id나 새 정책이 이미 있으면 중단한다.
커밋 전 실패/ROLLBACK은 해당 트랜잭션의 변경을 취소한다. 커밋 뒤 업무 데이터 복구는 제공하지 않는다.
커밋 후 장애는 새 스키마 호환 아티팩트/수정 마이그레이션으로 해결하며 무조건 재초기화하지 않는다.
구코드 롤백으로 ddl-auto=update나 시작 DDL을 되살리지 않는다.
Storage 파일은 유지되지만 갤러리 행 삭제 후 참조 없는 파일이 남을 수 있다. 파일 삭제는 이번 범위가 아니다.