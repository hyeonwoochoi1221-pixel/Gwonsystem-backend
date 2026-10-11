# 89-3 운영 재구축 SQL 수정 및 격리 검증

2026-10-11. codex/prod-notice-transition / HEAD a16ad2e22ec13800b86fce9ef06005b357b00c22.
시작 작업 트리는 깨끗했다. 이 단계는 docs/sql/prod-rebuild 안의 SQL/문서/검증 자료만 변경했다.
Java 업무 소스, 운영/개발 설정, Dockerfile, main/develop, RND 및 기존 PG 디렉터리는 변경하지 않았다.
운영/개발 Supabase 접근 및 SQL 실행, Render/Vercel 접근/배포, Git commit/push/merge는 수행하지 않았다.

## 환경 및 안전 범위

- 독립 PostgreSQL 17.11 / Windows x64, JDK 21.0.10.
- Hibernate 6.6.53.Final, pgJDBC 42.7.11. Probe에서 실제 버전을 assertion으로 확인했다.
- localhost 127.0.0.1:55489 / postgres / isolated_admin 및 isolated_app.
- 최종 새 디렉터리: 이 폴더의 pgdata-final. 기존 테스트 PG 데이터는 사용하지 않았다.
- localhost trust, sslmode=disable은 격리 fixture 전용이다. 운영 TLS/인증 검증의 근거가 아니다.
- PG 및 JVM 외부 연결 환경변수를 출력 없이 제거하고 종료 후 원복했다.
- 서버 주소/포트/data_directory/빈 public 확인 이후에만 fixture SQL을 실행했다.
- 실제 엔티티 클래스6개를 새로 빌드한 target/classes에서 로딩했다. Jar 의존성은 로컬 패키지에서 추출했다.
- Spring Boot 애플리케이션 전체는 시작하지 않았다. CompanyEventSchemaConfig 및 자동 update는 실행하지 않았다.
- Hibernate 설정은 명시적 hbm2ddl.auto=validate. create/update/create-drop 없음.
- 모든 입력 SQL/Probe/Check/실제 jar SHA256은 input-hashes.json에 보관했다.

## SQL 수정 결과

1. users.id INTEGER 유지, author_id BIGINT NULL -> users.id INTEGER FK/SET NULL 유지.
   JPA Long 및 운영 readiness 검사 때문에 author_id를 INTEGER로 줄이지 않았다.
2. members.phone UNIQUE 유지. author_member_id BIGINT NOT NULL -> members.id RESTRICT FK 추가.
3. company_events.end_date DATE NULL 유지.
4. LocalDateTime 컬럼은 운영 timestamptz 유지, Instant 컬럼은 timestamptz(6) 유지.
5. bigint BY DEFAULT IDENTITY3개 + 소유된 nextval3개 유지. members 시퀀스 bigint,
   notices/test_users 시퀀스 integer 유지. 초기화 값은1부터 시작하며 상한 확장은 하지 않는다.
6. 비-ID DEFAULT를 기존 테이블에서 잠금 후 임시 스냅샷으로 보존/재적용한다.
   실제 표현식 검토가 미완료라 existing_defaults_reviewed 승인값은 false 유지.
7. 추가 role CHECK는 제거했다. 최종63컬럼, PK6+UNIQUE4+FK2=12제약, 인덱스12개.
8. 백업 완료 조건을 제거하고 business_data_reset_approved로 교체했다. 백업은 필수 단계가 아니다.
9. 관리 이벤트 트리거6개는 변경/비활성화하지 않는다. 승인된 인벤토리와 전후 해시/소유권을 대조한다.
10. 확인된 test_users PUBLIC all-row 정책만 초기 정책으로 허용한다. 명시 승인 후 전용 앱 정책으로 좁힌다.
    다른 기존 정책/테이블 트리거/Realtime 연결/외부 FK·뷰는 중단한다. CASCADE 우회 없음.
11. 운영 역할은 미확인 자리표시자와 false 검증 조건으로 남겼다. 운영 역할/비밀번호 생성 없음.
    앱용CRUD+시퀀스USAGE만 부여하며 새6테이블에서만 넓은 권한을 제거한다. 전역default ACL/PUBLIC 변경 없음.

## 격리 재현 범위와 한계

운영 실제 데이터는 사용하지 않았다. 초기6테이블은 검증 대상 DDL에서 신규 작성자와 작성자 인덱스를
제외한62컬럼으로 구성하고, 모형 DEFAULT/기존phone UNIQUE/INTEGER users/기존test_users 정책을 재현했다.
users는 FK 확인용 최소(id INTEGER PK) 구조다. departments/schedules 전체 재현은 하지 않았다.
auth/storage는 sentinel 모형 테이블로 보존을 확인했으며 Supabase 내부 스키마의 전체 복제가 아니다.

관리 이벤트 트리거6개는 실제 이름/event/owner/function schema/SECURITY DEFINER 상태로 모형화했다.
함수 본문은 DDL 이벤트 기록용이다. 실제 Supabase 함수 본문 및 GraphQL/PostgREST/cron/net 내부 동작은
복제하지 않았다. 정의/소유권/활성 상태 보존 및 DDL 실행 중 작동한다는 점만 검증했다.
기존 광범위한 테이블/시퀀스 default ACL을 로컬에 재현해 새 객체에서만 제거되는지 확인했다.
실제 운영 역할·Pooler·TLS·OAuth·SMTP·Storage 파일 연결은 검증하지 않았다.

## 실행 결과

최종 Probe exit0 / TOTAL PASS127. Check exit0 / TOTAL PASS7.

- 원본의 승인값false/자리표시자 실행 차단 P0001, 구조 변화 없음.
- 승인7항목 각각 누락, 관리 역할/없는 역할, 미확인 정책/이벤트 트리거 모두 P0001, 구조 변화 없음.
- 외부 뷰 의존성은2BP01로 차단, DROP RESTRICT 보존. CASCADE 오류 힌트는 따르지 않았다.
- 별도 TEST-ONLY 복사본의 full rebuild ROLLBACK: 기존 행/정의/ACL/관리 이벤트 트리거 복원.
- 별도 TEST-ONLY 복사본의 COMMIT: 재구축 성공. 원본의 false/자리표시자/ROLLBACK은 그대로다.
- 63컬럼/12제약/12인덱스, identity3/owned-nextval3 및 integer시퀀스2개 확인.
- 기존 category/시간 DEFAULT 보존, users 및 auth/storage 모형 데이터 보존.
- 6개 관리 이벤트 트리거 각각 실행 기록 존재, 정의/소유권/활성 상태 전후 동일.
- 실제 JPA6엔티티 Hibernate validate PASS. JDBC members.created_at DATA_TYPE93 / TYPE_NAMEtimestamptz.
- phone 중복23505, 작성자NULL23502, 없는회원23503, 회원삭제RESTRICT23503.
- BIGINT author_id -> INTEGER users FK 정상, 삭제SET NULL 및 테스트rollback 확인.
- 앱DDL CREATE/ALTER/DROP42501, 6개시퀀스 nextval 사용 성공, 배너EXCLUSIVE LOCK 성공.
- 6개테이블RLS ENABLE/FORCE, 앱CRUD 허용, anon/authenticated/service_role의 넓은접근 제거.
- 실제 ProdNoticeAuthorPolicy readiness/JDBCcreate PASS. 위조authorId999 무시, members ID/legacyNULL 저장.
  MemberRepository는fixture proxy이므로 실제회원Repository/HTTP 로그인 통합검증을 의미하지 않는다.
- 실제 Hibernate로 공지조회/조회수1증가/삭제 PASS.
- 재실행 차단 및 구조 동일. 본 SQL은 idempotent한 재초기화 스크립트가 아니다.
- 현재01/02/04 읽기전용SQL도 실행PASS, 구조 변경없음.

schema-before-validate.txt / schema-after-validate.txt SHA256 동일:
7EA0607C83526D9A5691F0B97F2FAA98D59F550C5095A98778025B250549AB70.
범위: 컬럼/타입/NULL/DEFAULT/생성방식/제약/인덱스/테이블ACL/RLS/정책/시퀀스설정/이벤트트리거/default ACL.
시퀀스 현재값과 이벤트 기록 행 수는 DML에 따라 달라지므로 구조 비교에서 제외한다.

## 빌드 및 기존 정책 회귀

명령(저장소 루트):

```powershell
.\mvnw.cmd -o -DskipTests package
.\mvnw.cmd -o '-Dtest=DevNoticeAuthorPolicyTest,ProdNoticeAuthorPolicyTest,NoticeAuthorPolicyProfileTest,DdlSafetyTest' test
```

패키지 BUILD SUCCESS. 선택된19테스트 통과(DDL7/dev3/프로필4/prod5), 실패/오류/skip0.
위험CLI기동거부 테스트의 ERROR는 예상된 차단 결과다. 전체백엔드/프론트/운영서비스 테스트는 실행하지 않았다.
Mockito attach의 Windows 파이프 접근이 샌드박스에서 실패해 해당 자체 실행을 중단하고 제한 밖에서 재실행했다.
DB접속없는4클래스만 재실행했으며 코드/환경변수 수정은 하지 않았다.

## 실행 명령 및 초기 시행착오

```powershell
& './docs/sql/prod-rebuild/validation-89-3/run.ps1'
& './docs/sql/prod-rebuild/validation-89-3/run.ps1' -Resume -Mode Check
```

run.ps1 기본모드는새pgdata-final이있으면초기화거부. 기존 성공fixture에Probe 재실행은 금지.
Check만 이단계pgdata-final을재기동해readonly후속검증 가능. 다른프로젝트PG에사용하지 않는다.
첫새cluster pgdata는준비확인네트워크제한과모형event trigger owner의superuser요구로중단했다.
두번째 pgdata-run2에서는검증도구의char문자열캐스팅/regtype비교오류를수정했다.
이후 완전히 새 pgdata-final에서127assertion전체를처음부터통과했다. 초기 실패cluster도삭제/재초기화하지 않았다.
실패로그는attempt1/attempt2 *.log로로컬보존. 원본DDL은검증중추가수정이필요하지 않았다.

모든 인스턴스정상종료 확인. 최종 stop=0/status=3/pg_isready=2, 55489 미수신 확인.
pgdata*/lib/class/log/err/실행가능TEST-ONLY SQL2개는 .gitignore로제외, Dockercontext도기존validation-*제외규칙 적용.
SQL원본4개/이전보고서/검증소스/최종증거는 보존했다. 커밋/스테이징은 수행하지 않았다.

## 운영 실행 판정: NO-GO

아직 production_app_role_verified/backend_only_rls_approved/existing_defaults_reviewed/
managed_event_triggers_reviewed/project_identity_verified/dependency_review_approved/
business_data_reset_approved가false이며역할/ref는자리표시자,마지막ROLLBACK이다.
운영Render/Vercel실제연결/Auto-Deploy/프로필/TLS및운영DEFAULT/관리함수동작/외부사용검토가필요하다.
전용앱역할의다른스키마CREATE/소유권/실행가능SECURITY DEFINER 등간접DDL권한도별도확인해야한다.
dev작성자nullable/운영NOT NULL차이는여전히의도된환경별조건이며개발DB에이SQL을적용하지 않는다.
실제업무데이터삭제/권한축소/COMMIT/운영코드반영/배포는각각사용자승인후 별도단계로진행한다.
커밋후업무데이터복원은보장하지않고,장애는호환아티팩트/후속마이그레이션으로수정한다.
