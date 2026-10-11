# 개발 공지 작성자 준비 결과

실제 Supabase/Render/Vercel에 접속하거나 DB SQL을 실행하지 않았다. 이 작업에서 commit, Push,
PR, Merge, 배포는 하지 않았다. 원격 develop 읽기와 로컬 작업 브랜치 생성만 수행했다.

- 백엔드: D:/dev/GWON_System/backend/notice-member-author-backend,
  codex/dev-notice-member-author-develop, develop 기준 721d4390d1c213c059e79d36656aa243c7735d86.
- 프론트엔드: D:/dev/GWON_System/backend/notice-member-author-frontend,
  codex/dev-notice-member-author, develop과 동일한 c47d4b2074b6826349e7064423e2fe1363c30393 기준.
- 기존 backend/backend, backend/dev-deployment-guards와 frontend의 작업 내용은 보존했다.

## 설계 및 운영 호환성

author_id와 users FK(ON DELETE SET NULL)는 유지한다. dev 스키마에만 nullable BIGINT
author_member_id와 members(id) FK(ON DELETE RESTRICT / ON UPDATE NO ACTION / NOT DEFERRABLE),
인덱스를 추가하는 SQL을 준비했다. 데이터 backfill이나 기존 작성자 변경은 없다.

dev에서는 Authentication.getName()을 MemberRepository.findByUsername으로 조회한다.
일반 로그인 및 현재 OAuth 성공 처리 모두 세션 principal에 members.username을 넣는다.
미인증/anonymous/조회되지 않는 회원은 거부하며 DB의 현재 role이 REGULAR/SUPERVISOR인 경우만 생성한다.
세션이 관리자여도 DB에서 강등된 계정은 작성할 수 없다. 동시 권한 변경까지 직렬화하는 설계는 아니다.
입력 authorId는 dev 작성자 결정에 사용하지 않는다. author_member_id라는 임의 JSON도 입력 매핑이 없다.

기존 Notice JPA 엔티티는 수정하지 않았다. 새 컬럼을 공통 엔티티에 추가하면 기존 운영
ddl-auto=update가 운영 스키마를 자동 변경할 수 있으므로 이를 피했다. dev 전용 정책이
같은 JpaTransactionManager/DataSource의 JdbcTemplate으로 작성자와 공지 필드를 한 번의
INSERT ... RETURNING id에 기록한다. 기존 prePersist 기본값을 적용하므로 날짜/조회수/고정값도 유지한다.
작성자가 INSERT부터 존재하여 트리거/RLS가 중간의 작성자 없는 행을 보는 문제를 피한다.
전체 생성 작업은 NoticeService의 트랜잭션 안에 있으며 FK 실패 시 rollback한다.

dev 정책 초기화는 카탈로그 SELECT만 사용하여 새 컬럼의 BIGINT/nullable 상태와 유효한 비지연
members FK를 확인한다. 준비되지 않으면 기동을 거부하며 자동 DDL은 실행하지 않는다.
Hibernate validate, CompanyEvent DDL 차단, TLS/CA/프로젝트/역할/권한 보호 설정은 그대로다.

비-dev 프로필에서는 LegacyNoticeAuthorPolicy가 기존 author_id/JPA 저장을 유지하고 새 컬럼을
조회하거나 요구하지 않는다. 생성 응답은 기존 200 + 숫자 ID, 목록/상세 DTO와 삭제 경로는 그대로다.
REGULAR/SUPERVISOR 정책, CSRF, CORS는 변경하지 않았다. dev 전용 Notice 예외 처리만 401/403을
안전한 메시지로 반환한다. main, 운영 Dockerfile 및 운영 설정은 수정하지 않았다.

프론트엔드 고정 authorId 및 요청 타입의 authorId를 제거했다. 이 프론트엔드 변경은 개발 배포에만
적용해야 한다. 비-dev legacy writer와 결합하면 author_id 없이 저장될 수 있으므로 운영 반영은
별도 작성자 구조 전환 검토가 필요하다. 기존 운영의 클라이언트 authorId 사용도 이번에 고치지 않는다.

## 사전 검증과 실행 순서 — 별도 승인 후만

1. 실제 개발 Dashboard/ref/host를 독립 확인하고 스키마 스냅샷을 확보한다. SQL 내부의 프로젝트명/ref
   입력은 사람의 확인 표시이며 실제 서버 프로젝트 ID를 증명하지 않는다.
2. 04-notice-member-author.readonly.sql로 컬럼/FK/인덱스/행 수/RLS/트리거/앱 CRUD 권한을 확인한다.
   author_id BIGINT nullable, 기존 named FK -> users(id) SET NULL, members.id BIGINT/PK를 확인한다.
   업무 INSERT/UPDATE 트리거, SECURITY DEFINER 및 외부 webhook 부작용은 사람이 검토한다.
3. 개발 관리자 계정으로 03-notice-member-author.dev.sql의 두 확인용 플레이스홀더를 교체한 사본을
   실행한다. 원본 파일의 placeholder는 승인 전 실행을 차단한다. gwon_dev_app으로 DDL을 실행하지 않는다.
   PostgreSQL 17/database postgres/기존 구조 검증과 lock_timeout=5s, statement_timeout=30s를 포함한다.
   psql ON_ERROR_STOP=1 사용, 오류 시 ROLLBACK. 기존 column이 있으면 덮어쓰지 않고 중단한다.
4. 04를 다시 실행한다. 기존 FK와 데이터가 보존됐고 새 nullable 컬럼/FK/index가 올바른지 확인한다.
   기존 테이블 단위 INSERT/SELECT/UPDATE 권한은 새 컬럼에도 적용되지만 열 단위 ACL/RLS는 별도 확인한다.
   새 INSERT RETURNING id에는 SELECT 권한, ID 생성에는 기존 시퀀스 USAGE가 필요하다.
5. 승인된 개발 백엔드 코드를 먼저 반영/배포한다. 자동 배포 트리거를 확인하고 개발 service/branch만
   대상으로 한다. 스키마 전 배포는 새 dev startup check 실패가 정상이다. 새 백엔드는 이전 FE의
   authorId:1도 무시하여 호환된다. SQL은 저장소 docs에 두며 이미지 시작/자동 마이그레이션에 연결하지 않는다.
6. 개발 프론트엔드의 새 payload를 반영/배포한다. 본 작업에서는 배포하지 않았다.
7. 별도 승인된 테스트에서 users=0인 채 dev_admin_test의 공지 생성, 실제 author_member_id,
   author_id=NULL, 목록/상세/삭제와 일반회원 거부를 확인한다. 상세 GET은 조회수 UPDATE를 수행한다.

## 회원 삭제 및 남은 위험

작성한 공지가 있으면 신규 FK가 members 삭제/ID 변경을 막는다. 작성자를 자동 해제하거나 공지를
CASCADE 삭제하지 않는다. 관리자 회원 삭제 API는 기존 일반 예외 처리로 500을 반환할 수 있다.
409 응답/계정 보존 정책 개선은 별도 검토 대상이며 실제 회원 삭제 테스트를 운영/개발 DB에서 하지 않는다.
공지가 삭제되면 회원 삭제에 대한 이 FK의 제한은 사라지지만 다른 참조 제약은 별개다.

기존 공지 author_member_id는 NULL로 남는다. 과거 users ID와 members ID를 같다고 가정하거나,
임의 users.id=1을 만들거나, 모든 과거 공지를 관리자에게 재귀속하지 않는다.
실제 Supabase 17의 RLS/트리거/앱 계정/Storage/Render 환경은 확인하지 않았다.
JDBC dev INSERT 필드 목록은 앞으로 Notice 컬럼/기본값이 바뀌면 함께 갱신해야 한다.
동시 회원 삭제는 FK로 무결성을 보장하지만 API 실패로 나타날 수 있다.

## 실패 및 롤백

- SQL commit 전 실패: 전체 ROLLBACK, 새 컬럼/FK/index도 취소. 기존 스키마와 데이터는 그대로다.
- SQL commit 후 앱 실패: 먼저 개발 쓰기 요청을 중단하고 원인을 확인한다. 컬럼/FK/index와 작성자
  데이터는 보존한다. 기능 롤백은 승인된 이전 개발 백엔드/프론트엔드 커밋의 조합으로 수행한다.
- 이전 백엔드도 새 nullable 컬럼을 무시하므로 조회/삭제는 가능하지만 기존 작성자 등록 문제가
  다시 발생할 수 있다. 롤백 기간에는 공지 생성을 중단하는 것이 안전하다.
- DROP COLUMN/CASCADE, 신규 작성자 데이터 삭제, 공지를 users 작성자로 자동 변환하는 SQL은 준비하지
  않았다. 스키마 제거가 필요하면 새 데이터 유무/참조를 확인하고 별도 백업·승인을 받아야 한다.

## 자동 검증

격리된 localhost PostgreSQL 14.22에서 03의 ALTER/INDEX 본문을 그대로 실행했다. PostgreSQL 17
가드와 프로젝트 확인용 DO 블록은 원본 SQL 실행 시 중단되는 부정 테스트로 확인했다.
성공 경로 FK 테스트에서는 이 블록을 제외한 DDL 본문만 실행했다.
따라서 실제 Supabase 17에서 SQL 전체를 실행한 검증은 아니다.

- users=0 + 관리자 members=1에서 서버 결정 작성자로 생성 성공, 위조 authorId 무시.
- members FK 삭제 제한, 실제 FK 실패 후 전체 생성 트랜잭션 rollback.
- 기존 users SET NULL FK 유지, legacy/null 작성자 공지의 목록·상세 조회수·삭제 회귀.
- 미인증/anonymous/삭제된 회원/DB 권한 강등 거부, REGULAR/SUPERVISOR 및 CSRF 유지.
- 개발 새 스키마 미준비 거부, 비-dev legacy 정책은 새 컬럼 불필요.
- 프론트엔드 모달 실제 submit handler를 transpile/실행하여 작성자 없는 payload 확인.

프론트엔드 빌드의 초기 실패는 기존 저장소에서 제외된 lines-and-columns/build 의존성 파일 누락이었다.
원래 frontend의 설치 파일을 새 작업 디렉터리에 복사하여 해결했다. 원래 의존성/작업 파일은 수정하지 않았다.

## 변경 파일 목록

백엔드 작업 디렉터리 기준:

- src/main/java/com/gwonsystem/backend/controller/NoticeController.java
- src/main/java/com/gwonsystem/backend/dto/NoticeCreateRequestDto.java
- src/main/java/com/gwonsystem/backend/service/NoticeService.java
- src/main/java/com/gwonsystem/backend/service/NoticeAuthorPolicy.java
- src/main/java/com/gwonsystem/backend/service/DevNoticeAuthorPolicy.java
- src/main/java/com/gwonsystem/backend/service/LegacyNoticeAuthorPolicy.java
- src/main/java/com/gwonsystem/backend/exception/DevNoticeAuthExceptionHandler.java
- src/test/java/com/gwonsystem/backend/NoticeHttpTest.java
- src/test/java/com/gwonsystem/backend/NoticeMemberAuthorIntegrationTest.java
- src/test/java/com/gwonsystem/backend/service/DevNoticeAuthorPolicyTest.java
- docs/sql/03-notice-member-author.dev.sql
- docs/sql/04-notice-member-author.readonly.sql
- docs/dev-notice-member-author.md

프론트엔드 작업 디렉터리 기준:

- src/pages/notice/NoticeCreateModal.tsx
- src/types/index.ts
- tests/notice-author.test.mjs

최종 검증: 백엔드 전체 83개 테스트 통과(실패/오류/건너뜀 0), Maven package 성공.
프론트엔드 작성자 payload 테스트 1개 및 TypeScript/Vite build 성공.
