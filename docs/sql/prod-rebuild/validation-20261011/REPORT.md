# 운영 재구축 SQL 격리 검증 결과

2026-10-11. 브랜치 codex/prod-notice-transition, HEAD 9211f453dcabb91b9c43ffab73b0b7a0ba82ad54.
84~85단계 Java/설정/기존 미커밋 변경은 수정하지 않았다. 기존 PG 데이터 디렉터리 접근/수정 없음.
운영·개발 Supabase/Render/Vercel 접근, 커밋/Push/PR/merge 없음.

## 환경

- 독립 PostgreSQL17.11 Windows x64 바이너리 사용.
- 이 폴더의 새 pgdata만 initdb. 기존 hibernate-time 테스트 pgdata는 사용하지 않았다.
- listen 127.0.0.1:55483, database postgres, 테스트 관리자 isolated_admin, 앱 isolated_app.
- 테스트-only localhost trust 인증/ssl=disable; 실운영 TLS 설정과 무관하며 외부주소 수신하지 않는다.
- JDK21.0.10, 실제 backend target/classes, Hibernate6.6.53.Final, pgJDBC42.7.11.
  실행 시 버전 불일치는 assertion으로 중단하도록 구현했다.
- 빌드된 backend jar에서 라이브러리만 이 폴더 lib로 추출하여 사용했다. 앱 전체는 시작하지 않았다.
  Config/Runner/CompanyEventSchemaConfig는 로딩하지 않고 MetadataSources로6엔티티만 등록했다.
  명시적 hbm2ddl.auto=validate 및 Boot와 같은 snake_case naming을 지정했다.
- Maven dependency:build-classpath는 offline plugin 미설치로 실패하여 기존 패키지의 lib를 사용했다.
  의존성 다운로드나 버전 변경은 하지 않았다.

## SQL 검증

독립 cluster에 users(id bigint PK)와6개 업무테이블의 최소 기존 fixture(id bigint PK+한 행),
anon/authenticated/service_role 테스트 NOLOGIN 역할을 만들었다. 실제 운영 전체DDL 복제가 아니다.
users는 기존 FK 재현에 필요한 최소 구조만 재현했다. 실제 운영 의존성은 여전히 미확인이다.

- 01-preflight/02-backup-manifest/04-postflight 모두 SQL 문법/실행 성공.
- 원본03 실행: 프로젝트/백업/의존성 승인 guard로 P0001. 명시적 ROLLBACK 후 구조 동일.
- 승인값만 바꾼 rebuild-rollback.TEST-ONLY.sql: DROP/CREATE/권한/RLS까지 실행 성공,
  마지막 ROLLBACK으로 이전 구조와 members fixture row 복원 확인.
- 같은 격리 복사본의 마지막 문장만 COMMIT한 rebuild-commit.TEST-ONLY.sql: 성공.
  실행한 복사본은 localhost 전용이며 운영 실행 승인을 의미하지 않는다.
- 재실행은 기존 RLS 정책 guard에서 차단(P0001), 구조 동일. idempotent한 파괴적 재실행은 허용하지 않는다.
- users의42번 fixture row가 재구축 후 유지됐음을 확인했다.
- guard 후 운영 SQL 수정은 필요하지 않았다. 원본03의 차단값/ROLLBACK은 그대로다.

## 스키마/권한 결과

6테이블63컬럼: company_events9/gallery_photos8/home_banners7/members25/notices10/test_users4.
12제약(PK6/UNIQUE3/FK2/role CHECK1),11인덱스, bigint identity시퀀스6개 확인.
시퀀스 internal ownership6, increment/start1, noncycle 확인.
role/terms/privacy/marketing/view_count/is_pinned의6개 DEFAULT 확인.
컬럼 타입/길이/NULL/DEFAULT/IDENTITY는 postflight 로그에 전체 기록했다.
회원 username UNIQUE 위반23505, role CHECK 위반23514 확인.

author_member_id BIGINT NOT NULL/FK members(id): NULL INSERT23502, 없는 회원23503,
공지 있는 회원 DELETE23503. 새 FK validated/nondeferrable RESTRICT/NO ACTION.
legacy author_id BIGINT NULL/FK users(id): users 삭제 시 SET NULL 확인 후 테스트 transaction rollback.

새6테이블 RLS ENABLE/FORCE 및 isolated_app 전용 all-row CRUD정책6개 확인.
각 테이블의 앱 SELECT/INSERT/UPDATE/DELETE와시퀀스 USAGE 모두 true.
anon/authenticated/service_role의DML 권한 false, SET ROLE anon SELECT는42501로 거부.
앱 CREATE TABLE/ALTER TABLE/DROP TABLE은42501. 배너 native EXCLUSIVE LOCK은 성공.
앱에DDL권한없이실제 회원/공지 INSERT, 조회수 UPDATE, 공지 DELETE 성공.

## Hibernate 및 기능 검증

실제 CompanyEvent/GalleryPhoto/Banner/Member/Notice/TestUser6개 엔티티 validate PASS.
검증 전후 schema-before-validate.txt/schema-after-validate.txt 완전 동일.
비교범위: 컬럼, 제약 정의/valid/deferrable,인덱스,owner/ACL/RLS flags,정책,시퀀스 설정.
시퀀스값은DML에따라달라질수있으므로비교대상아님. OID 변경은재구축의정상결과다.
DDL validate 및DML 뒤같은metadata snapshot 일치 확인.

실제 ProdNoticeAuthorPolicy readiness 및 JDBCcreate 사용: authorId999 무시, 인증된 회원ID 저장,
legacy author NULL. 인증없음 예외, token SUPERVISOR라도 회원 ROLE_ASSOCIATE면 거부.
이 probe의 MemberRepository는 동적 proxy fixture여서 실제회원Repository조회경로 통합검증은 아니다.
Hibernate Notice조회/조회수+1/삭제는 실제 앱역할+PostgreSQL17에서 실행했다.
HTTP401/403 및서비스/세션보안은 아래기존테스트로 별도검증했다.

기존테스트6클래스26개 통과: ProdNoticeAuthorPolicyTest,NoticeAuthorPolicyProfileTest,
NoticeHttpTest,NoticeMemberAuthorIntegrationTest,SecurityHttpTest,SecurityRegressionTest.
NoticeMemberAuthorIntegrationTest는기존새EmbeddedPostgres fixture를사용한다(PG17 probe와별개).
실제 운영회원/세션/이메일/OAuth는사용하지않았다. 전체Spring앱 운영설정기동은 하지 않았다.

## 시간 타입

초안 timestamp(6) LocalDateTime과 timestamptz Instant 모두6엔티티validate PASS.
추가실험에서members의3시간컬럼/notices2/test_users1을격리DB에서만timestamptz로 바꿔
다시6엔티티validate: PASS. JDBCmembers.created_at은DATA_TYPE93/TYPENAMEtimestamptz.
이 실험의validate도구조변경없음. 실험후초안타입복원및전체snapshot동일확인.
따라서기존운영timestamptz를timestamp로바꾸는것은validate를위한필수조건이아니다.
원본DDL은이번단계에서변경하지않았다. 최종타입은운영시간의미/Timezone정책승인후선택해야한다.
UTC/Seoul 데이터의시간의미비교는이번검증으로보장하지않는다.

## 실행/종료 및 재사용 주의

run.ps1은최초빈디렉터리용. Windows pg_ctl 자식의상속pipe때문에대기하여정확한새pgdata만
종료했다. 원본probe의주소비교도inet::text CIDR표현때문에실패해host()로수정했다.
그시점에는업무SQL실행전이었다. resume.ps1은Hidden Start-Process+WaitForExit로해결했다.
추가Audit에서NOLOGIN anon 직접연결실패를SET ROLE검증으로수정했다.
최종Probe34개/Audit62개 assertion PASS. SQL의예상거부ERROR로그는negative tests의성공근거다.
각실행finally에서새pgdata만fast shutdown. 최종stop=0/status=3/pg_isready=2.
서버는종료됐고디렉터리는증거보존을위해남겼다. 자동삭제하지않았다.

Probe.java/Audit.java와실행복사본/metadata파일은증거다. pgdata/lib/class/log는local ignore.
Probe는1회fixture생성용이므로다시실행하면기존역할/테이블에서실패한다. initdb도재실행금지.
Audit만기존이번일회용cluster로재확인가능하며운영서버에전용할수없다.

## 운영 전에 남은 조건

1. 실제운영RLS/정책/사용자·event triggers/Realtime/외부FK/view/stringSQL함수/ACL 및 owner확인.
   Supabase event trigger가있으면현재초안은의도대로중단한다. 끄거나우회하지말고별도검토필요.
2. 실제운영전용앱역할과PUBLIC상속/다른schemaCREATE/SECURITY DEFINER위험확인.
   일반PG의테스트역할은Supabase관리역할/Pooler/TLS동작을재현하지않는다.
3. 실제백업완전성/복구리허설,maintenance와구버전writer차단,삭제대상6테이블과유지대상승인.
4. ROLE초기관리자및회원재가입절차,Storage orphan/별도Auth사용자연결영향승인.
5. 운영환경변수의ddl-auto update 등제거,운영프로필/dev금지,스키마준비후별도배포승인.

운영실행승인이나운영DDL완전호환성확정이아니다. 원본SQL은여전히review draft이다.
