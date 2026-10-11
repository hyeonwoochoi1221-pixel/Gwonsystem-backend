# 운영 재구축 검토 초안 — 실행하지 않음

후속 격리 검증 결과는 validation-20261011/REPORT.md에 기록했다. 운영/개발 DB에서는 여전히
실행하지 않았다. PostgreSQL17.11에서 원본 차단, 테스트 복사본 재구축/롤백 및6엔티티 validate가
통과했다. LocalDateTime+timestamptz도 validate 통과했으므로 timestamp로의 변경은 필수 조건이
아니다. 최종 운영 DDL의 시간 타입 선택은 기존 시간 의미를 기준으로 별도 승인해야 한다.

현재 codex/prod-notice-transition / HEAD 9211f453dcabb91b9c43ffab73b0b7a0ba82ad54와
84~85단계 의도된 미커밋 소스를 기준으로 작성했다. Java/설정은 이번 단계에서 수정하지 않았다.
자료는 기존 docs/sql/01~04, ai-lab/reports/jpa-main-validation의 SQL 및 README,
사용자가 확인한 운영 메타데이터(62컬럼/11제약/10인덱스/6시퀀스)를 참고했다.
그 보고서 폴더에는 실제 결과 파일이 없었다. 현재 운영 ACL/RLS/트리거/전체 DDL을 확인했다고 주장하지 않는다.

## 범위 및 코드 근거

- members: entity/Member.java(25컬럼), repository/MemberRepository.java와 인증/회원 서비스.
- notices: entity/Notice.java(9컬럼), service/ProdNoticeAuthorPolicy.java의 JDBC INSERT(추가1컬럼).
  ROLE_REGULAR/SUPERVISOR 재조회, 클라이언트 authorId 무시, 신규 author_id=NULL.
- company_events: entity/CompanyEvent.java(9컬럼), controller/CompanyEventController.java 일정 CRUD.
- gallery_photos: entity/GalleryPhoto.java(8컬럼), GalleryPhotoRepository/갤러리 서비스.
  bytea는 계속 필수; Storage 사진은 빈 bytea+storage_path 방식도 사용한다.
- home_banners: entity/Banner.java(7컬럼), BannerRepository.java의 EXCLUSIVE 테이블 잠금.
  별도 DDL 권한 없이 UPDATE 권한으로 잠금 가능; 실제 앱 역할 회귀 검증 필요.
- test_users: entity/TestUser.java(4컬럼), TestUserController.java에 실제 CRUD가 있어 유지/재구축.
- users: 테이블/데이터/시퀀스/권한 모두 유지. notices.author_id FK 대상이며 members와 통합/ID대응하지 않는다.
- departments/inquiries/schedules: 이 백엔드에서 직접 참조를 찾지 못했다. 외부 사용 미확인으로 모두 유지.
  따라서 이 초안은 운영 업무 데이터 전체 삭제가 아니라 확인된6개 테이블만 초기화한다.
- member_role ENUM: Entity는 STRING varchar(30)를 사용한다. 기존 타입은 삭제하지 않는다.
- auth/storage/관리 스키마, Storage 파일, Supabase Auth 사용자, 역할과 전역 PUBLIC ACL은 유지한다.

## 제안 스키마 (기존 운영 DDL의 복제 아님)

6개 테이블/63컬럼. 모두 bigint BY DEFAULT IDENTITY, 전용시퀀스는 PostgreSQL이 자동 생성/소유한다.
기존 members/notices/test_users의 nextval 방식에서 identity로 바뀌는 **제안**이다.
6 PK, 3 UNIQUE(username/email/test email), 2 FK, role CHECK 1개(총12제약),
6 PK인덱스+3 UNIQUE인덱스+작성자2인덱스(총11개). 이름은 자동 생성된 일부 포함.
기존 추가 UNIQUE/CHECK/인덱스의 업무 의미는 승인 전 비교해야 한다.
member author는 bigint NOT NULL, validated/nondeferrable RESTRICT delete/NO ACTION update FK.
legacy author는 bigint NULL, users.id SET NULL FK 유지(재구축 시 같은 의미로 다시 생성).
공지 있는 회원 삭제는 실패한다. 회원 삭제 서비스 오류 처리/업무 절차 검토 필요.

LocalDate=date, LocalDateTime=timestamp(6) without time zone,
Instant=timestamp(6) with time zone, Long=bigint, byte[]=bytea, String=명시길이/default255,
role=varchar(30)+CHECK, content=text. Entity 초기값/PrePersist와 DB DEFAULT는 별개다.
role/동의/view_count/is_pinned DEFAULT는 명시적 제안이며 앱 입력 NULL을 대체하지 않는다.
시간대 없는 새 컬럼은 기존 timestamptz와 다르다. 기존 데이터는 변환/복제하지 않으므로
시간 변환은 발생하지 않지만, 이후 날짜 의미/JVM 및 DB timezone 정책을 승인해야 한다.
운영의 생략된 다른 컬럼/제약을 사실로 추정하지 않았다.
정적 타입 대조상 validate 호환을 목표로 하지만 이번 단계에서는 SQL/Hibernate를 실행하지 않았다.
실제 버전/방언/naming/search_path/JDBC/최종설정으로 격리6엔티티 validate 및 JDBC INSERT 검증이 선행되어야 한다.
Hibernate validate는 전체 NULL/DEFAULT/FK/RLS/권한/인덱스 의미를 보장하지 않는다.

## 권한/RLS/트리거

운영 전용 앱 역할은 아직 이름/권한 미확인: 개발 gwon_dev_app를 복사하거나 사용하지 않는다.
승인된 별도 LOGIN 역할(비소유자, no superuser/createdb/createrole/replication/bypassrls,
role membership 없음, database/public CREATE 없음)을 별도 준비해야 한다. 이 초안은 역할/비밀번호를 생성하지 않는다.
앱에는 새6테이블 CRUD, public USAGE, 전용시퀀스 USAGE만 부여한다. ALTER/DROP/TRUNCATE/REFERENCES/TRIGGER는 부여하지 않는다.
PUBLIC에서 상속하는 TEMP는 허용; 다른 schema CREATE 및 실행가능 SECURITY DEFINER 함수는 별도 검사한다.
새6테이블에 RLS ENABLE+FORCE, 앱역할 전용 all-row CRUD 정책을 제안한다.
이 정책은 개인별 권한 대신 Spring Security를 신뢰한다. anon/authenticated/service_role의 새테이블 접근을 차단한다.
**기존 PostgREST/Realtime/외부 업무가 의존하면 부적합: 승인 전 접근 경로를 모두 확인해야 한다.**
기존 미확인 정책/사용자트리거가 하나라도 있으면 재구축 guard가 중단한다. 임의로 삭제하지 않는다.
Supabase event trigger가 있으면 중단한다. 이를 끄지 말고 관리 동작을 확인하여 별도 SQL 검토를 받아야 한다.
기존 권한은 백업/승인 비교 대상이며 자동 복제하지 않는다. 새로운 전역 default ACL은 변경하지 않는다.
현재 코드에 필요한 사용자 트리거는 없고 날짜는 앱에서 설정한다. FK 내부트리거만 자동 생성된다.
DROP RESTRICT는 외부 FK/view 의존성을 차단하지만 문자열 기반 함수/외부시스템 의존성은 잡지 못한다.

## 단계별 실행 계획 (현재 실행 금지)

1. 운영 Dashboard 프로젝트명/ref/연결호스트를 두 사람이 대조. 개발 ref elddmkyqfmxryemjrxhh 제외.
   DB명 postgres/IP만으로 프로젝트를 증명할 수 없다. Pooler의 project suffix도 대조한다.
2. 01 사전점검 결과를 로컬에서 검토. ACL/정책/DEFAULT/인덱스 정의는 비밀 리터럴 포함 가능하므로
   공개 채팅에 원문을 올리지 않는다. 실제 데이터/password hash는 조회하지 않는다.
3. DB/역할/외부의존성/기존 정책 및 트리거 승인 후 **초안 수정 재검토**. 현재 SQL은 실행 준비 완료가 아니다.
4. maintenance로 HTTP/백그라운드/외부 writer와 구버전 배포 재시작을 차단한다.
5. 승인된 백업 전용 연결로 PostgreSQL17 pg_dump -Fc --schema=public --file=<암호화 보관 경로>.
   암호는 대화형/비공개 자격증명 저장소만 사용. --enable-row-security로 부분백업을 허용하지 않는다.
   02는 백업 manifest일 뿐 SQL만으로 복구 가능한 백업을 만들 수 없다.
   public dump에는 민감데이터 포함: 저장소/Git/채팅 업로드 금지. 별도 정책/ACL/sequence 상태/역할 메타데이터 보관.
   auth/storage는 손대지 않으며 public 함수의 관리스키마 의존성도 복구 리허설에 반영한다.
6. 독립 PostgreSQL17에서 백업 복구 및 앱 테스트, 새스키마 validate/권한/RLS/회원/OAuth/공지/일정/갤러리/배너 검증.
   기존 로컬 pgdata 사용 금지. 새 관리자 초기화는 별도 승인; 계정 SQL/비밀번호는 제공하지 않았다.
7. 삭제범위/새정책/복구/배포순서에 대한 사용자 명시 승인 후에만 03 확인값을 채운다.
   현재는 placeholders/false guard와 마지막 ROLLBACK으로 차단. COMMIT 변경은 별도 승인 사항.
   SQL Editor 전체 단일실행 또는 psql ON_ERROR_STOP=1. 조각별 실행/오류 무시 금지.
8. 04 실제 관리자와 앱 역할로 사후검증.6테이블0건,63컬럼,새 FK 및 기존 FK 유효,
   PK/UNIQUE/시퀀스, CRUD/RLS/소유권 검증. RLS-filtered count를 전체0건 증거로 쓰지 않는다.
9. DB 준비 뒤 승인된 **운영용** 백엔드 아티팩트 배포, validate 성공/health 확인 후 프론트 배포.
   develop 전체병합/운영dev프로필 금지. OAuth callback/SMTP/Storage/DB/프론트 URL/CORS 운영값 유지.
   Render DB env/JVM CLI/native Hibernate properties에서 update/create/create-drop/none,
   JPA schema-generation action, generate-ddl=true, sql.init.mode=always/embedded,
   Flyway/Liquibase=true, connection-init-sql이 없는지 확인한다. 기본/prod validate, SQL init never.
10. 관리자 재등록/승인된 권한승격 후 실제 기능검증, maintenance 해제. Supabase Auth사용자는
    유지되지만 members가 비면 기존 앱 로그인/연결은 사라진다. 소셜 재가입과 필수 동의 재확인 필요.

## 재실행/실패/복구

03은 idempotent하지 않다. 성공 후 author_member_id 존재 시 재실행 중단한다.
실패 또는 ROLLBACK이면 같은 transaction 내 DROP/CREATE가 취소된다.
한 번에 실행하지 않았거나 COMMIT 여부가 불명확하면 다시 실행하지 말고 메타데이터부터 확인한다.
COMMIT 후에는 구코드만 롤백해도 삭제데이터가 복구되지 않는다. maintenance 유지, 새업무데이터가
생겼으면 먼저 별도 보관, 검증된 백업으로 승인된 public 범위만 복구 후 대응되는 구아티팩트로 복구한다.
--clean/--create/관리스키마 전체복원/역할 전체복원은 사용하지 않는다. 백업 public 객체만으로 완전복구
가능한지 FK/함수/외부스키마 의존성 및 ACL 포함 리허설로 검증해야 한다.
Storage 파일은 유지되지만 gallery 행을 지우면 파일참조가 사라지고 orphan이 생긴다. 파일 삭제는 별도작업.
복구 뒤 구버전의 ddl-auto=update/시작DDL을 재활성화하지 않는 롤백아티팩트가 별도로 필요하다.

공식 참고: https://www.postgresql.org/docs/17/sql-droptable.html
https://www.postgresql.org/docs/17/ddl-rowsecurity.html
https://www.postgresql.org/docs/17/app-pgdump.html
https://www.postgresql.org/docs/17/app-pgrestore.html
