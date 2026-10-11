# 운영 공지 작성자 정책 준비 결과

기준 develop SHA: 9211f453dcabb91b9c43ffab73b0b7a0ba82ad54.
로컬 브랜치: codex/prod-notice-transition. 미커밋 구현이며 배포 준비 완료를 의미하지 않는다.

## 정책 선택

| 프로필 | NoticeAuthorPolicy |
|---|---|
| dev | 기존 DevNoticeAuthorPolicy |
| prod | ProdNoticeAuthorPolicy |
| 기본 프로필 및 dev가 없는 기타 프로필 | ProdNoticeAuthorPolicy |

LegacyNoticeAuthorPolicy와 NoticeService의 legacy JPA 작성 fallback을 제거했다. dev/prod 중복 활성화는 기존 DevEnvironmentGuard가 계속 차단한다. 정책 자체는 dev가 있으면 dev만 등록되며 !dev 정책과 충돌하지 않는다.

생성·삭제는 인증된 사용자명으로 MemberRepository.findByUsername를 호출하여 현재 회원을 재확인한다. 미인증/익명/없는 회원/저장 ID 없는 회원은 401, 현재 ROLE_REGULAR/ROLE_SUPERVISOR가 아니면 403이다. 생성 요청의 authorId는 작성자 결정에 쓰지 않는다. 신규 작성은 JdbcTemplate의 단일 INSERT로 author_member_id에 members.id를 넣고 author_id에는 SQL NULL을 넣는다. 기존 트랜잭션과 Notice.prePersist 초기값을 유지한다.

NoticeService.deleteNotice도 Authentication을 전달받아 정책을 확인한 후 삭제한다. 로그인 회원의 본인 공지만 삭제하는 정책을 새로 추가하지 않았으며 기존 REGULAR/SUPERVISOR 삭제 범위를 유지한다. NoticeAuthExceptionHandler는 공지 Controller에만 적용되며 개발·운영 모두 안전한 401/403 메시지를 반환한다. GET 응답 형식과 조회수 증가 방식은 변경하지 않았다.

## 운영 스키마 필수 조건

ProdNoticeAuthorPolicy는 bean 초기화 시 카탈로그 SELECT만 실행한다. 준비되지 않았으면 기동 실패이며 스키마 자동 보정은 하지 않는다.

- public.notices.author_member_id: BIGINT, **NOT NULL**.
- 위 컬럼에서 public.members.id로 단일 컬럼 FK.
- 참조 members.id는 BIGINT.
- FK는 VALIDATED, NOT DEFERRABLE.
- ON DELETE 및 ON UPDATE는 RESTRICT 또는 NO ACTION. SET NULL/CASCADE는 허용하지 않는다.
- public.notices.author_id: 기존 BIGINT, **NULL 허용**. 기존 users FK는 이 변경에서 수정하지 않는다.

개발 검사는 기존대로 author_member_id NULL 허용을 요구한다. 운영 검사와 별도 클래스이며 DevNoticeAuthorPolicy 원문은 수정하지 않았다. 운영 NOT NULL은 새 공지에 작성자를 필수로 하는 명시적 계약이다. 기존 데이터가 있는 운영 DB에 적용하려면 별도 재구축/데이터 처리 승인 작업이 필요하다. 이 단계는 SQL을 작성하거나 실행하지 않았다.

운영 준비 시 추가 확인: author_member_id 인덱스, ID 생성 DEFAULT/IDENTITY, 앱 INSERT/SELECT/UPDATE/DELETE 및 시퀀스 권한, RLS·트리거, 회원 삭제 RESTRICT 처리. 정책의 readiness SELECT가 이것들을 모두 검증하는 것은 아니다.

## 검증

전체 백엔드 테스트: 92개, 실패0/오류0/스킵0. 오프라인 package 빌드 성공.

- ProdNoticeAuthorPolicyTest: forged authorId, 현재 DB 권한/회원, 미인증·익명, JDBC 작성 파라미터와 NULL legacy, 삭제 권한, 스키마 준비 여부를 mocks로 검증.
- NoticeAuthorPolicyProfileTest: dev/prod/default/test-isolated에서 정책 bean 하나만 선택됨을 mocks로 검증.
- NoticeHttpTest: 생성·삭제 ROLE/CSRF, 401/403 안전 응답 및 principal 전달.
- 기존 dev 보호 및 실제 공지 FK/트랜잭션/조회/삭제 회귀 테스트 포함.
- BackendApplicationTests의 fixture에 새 운영 필수 컬럼/FK 추가. 이 테스트는 새 EmbeddedPostgres 클러스터만 사용한다.

운영 스키마 조건의 단위 테스트는 실제 운영 DB를 사용하지 않았다. 전체 suite의 기존 integration 테스트는 새 임시 localhost DB/TLS/HTTP fixtures만 사용하며 Supabase나 기존 PostgreSQL pgdata를 사용하지 않는다.

첫 테스트 명령은 Mockito 동적 agent 부착 과정에서 지연되어 중단했다. 명시적 Mockito agent 재실행에서는 32개 중 localhost sandbox 오류1개가 발생했다. 승인된 local 테스트 실행으로 전체92개가 통과했다. 기존 파일 설정에 agent 옵션을 추가하지 않고 실행 명령으로만 지정했다.

실행 명령 (JAVA_HOME은 해당 명령 프로세스의 JDK21 경로):

```powershell
.\mvnw.cmd -o '-DargLine=-javaagent:C:\Users\hwcho\.m2\repository\org\mockito\mockito-core\5.17.0\mockito-core-5.17.0.jar' test
.\mvnw.cmd -o -DskipTests package
```

로그: prod-notice-full-tests.log, prod-notice-build.log 및 target/surefire-reports. 로그는 기존 *.log/target Git ignore 대상이다. package는 이미 통과한 tests를 반복하지 않도록 skipTests로 실행했고 test sources도 컴파일됐다.

## 남은 운영 위험

**application.properties의 ddl-auto=update 및 CompanyEventSchemaConfig는 사용자 제한에 따라 변경하지 않았다.** 이 구현을 현재 운영 환경에 배포하면 자동 DDL 위험이 남는다. 운영 배포 전 별도 DDL 차단과 실제 스키마 준비·권한 검증이 필수이다. 정책의 readiness 검사 실행 순서를 자동 DDL 방지 장치로 간주하면 안 된다.

현재 운영에는 author_member_id가 없으므로 이 정책의 요구 스키마를 준비하기 전 비-dev 기동을 하면 안 된다. 기본 프로필에서도 Prod 정책이 선택된다는 점을 배포 설정에서 확인해야 한다.

회원 삭제는 새로운 FK에 의해 막힐 수 있으며 별도 사용자 안내/오류 처리 검토가 남는다. 시간대·동시 조회수 갱신·트랜잭션 DML 권한 등의 기존 운영 의미는 이 작업으로 완전히 검증되지 않았다.

RND 작업 디렉터리에 접근하지 않았고 자동 DDL/보안 설정/환경변수/기존 entity 매핑을 변경하지 않았다. Git commit/push/PR/merge, 운영·개발 DB 접근, 기존 로컬 PostgreSQL 데이터 디렉터리 변경, Render/Vercel 접근·배포를 수행하지 않았다.
