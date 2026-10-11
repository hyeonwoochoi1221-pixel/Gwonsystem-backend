# 89-7 운영 전환 차단 조건 점검 (2026-10-11)

**판정: 실제 실행 NO-GO. 코드/SQL 준비 및 로컬 검증은 진행 가능.**
운영에는 READ ONLY 트랜잭션의 카탈로그 SELECT만 실행했다. 역할/ACL/데이터/스키마 변경 없음.
Render/Vercel 설정·배포, Git commit/push/merge, 개발망 변경 없음. 기존 RND 디렉터리에 접근하지 않았다.

## 기준과 실제 운영 조회

- 작업 브랜치 codex/prod-notice-transition / HEAD a16ad2e22ec13800b86fce9ef06005b357b00c22.
- GitHub ls-remote: backend main 67f7d542e4acd617e7c6d6e5cf4accdcbaee47ff,
  develop d6fbc8c0b724953685741921897708f0b9d8da1e.
- backend develop은 PR #6 merge이며 부모는 9211f453...와 a16ad2e...이다.
  원격 develop tree 60a91e064c121674513f17fa29f63fe51fea7ce5는 로컬 HEAD tree와 동일.
- frontend main c47d4b2074b6826349e7064423e2fe1363c30393,
  develop d93cd2a35843a4b86404fd9cf11272317dd8a61a.
  develop tree 0aa9e68e3b121c0fab66029fe6f924a55f60b17c는 공지 전용 프론트 HEAD e7bd6495...와 동일.
  두 저장소 모두 로컬 remote ref가 오래됐지만 fetch/checkout 없이 ls-remote와 GitHub GET으로 대조했다.
- SQL Editor 제목 GWONSYSTEM, URL project/zsaqpsohhwygrawphnsg, main PRODUCTION 확인 후 실행.
- PostgreSQL 17.6, postgres DB, postgres 실행자. DB명만으로 프로젝트를 확정하지 않았다.
- READ ONLY / statement_timeout 20초 또는 15초 / ROLLBACK. 업무 행, 비밀번호, HTTP 큐, 함수 본문, 트리거 인자는 출력하지 않았다.
- production-dependencies.csv/json 및 production-followup.csv/json은 로컬에 보존하고 Git 제외.
  첫 실행 SQL은 04-production-dependencies.readonly.sql과 동일 조사 범위의 SQL Editor용 축약형이며 JSON 키 이름이 다르다.
  두 번째 실행 SQL은 05-production-followup.readonly.sql에 정리했다.

## 확인된 사실과 해소된 조건

| 항목 | 이번 실제 조회 | 의미 |
|---|---|---|
| net / pg_net | 스키마 없음 / 확장 미설치 | 현재 PUBLIC net 큐/HTTP 함수 위험은 없음. 향후 확장 설치 시 재점검 |
| PUBLIC 스키마 권한 | public, information_schema USAGE; CREATE 없음 | 영구 스키마 CREATE 상속 없음 |
| PUBLIC DB 권한 | CONNECT, TEMPORARY; CREATE 없음 | TEMP 허용 설계와 일치 |
| 사용자 함수 | public/net 함수 및 PUBLIC 접근 가능한 비시스템 SECURITY DEFINER 결과 없음 | 현재 01 역할 생성의 함수 위험 차단에 걸릴 객체는 이 범위에서 없음 |
| DEFAULT | 27개; nextval, now, false, 0, varchar 상수 | 사용자 정의 함수 의존 없음. notices.category도 상수로 확인(실제 문자열은 출력 안 함) |
| 업무 트리거 | 12개 전부 내부 FK 트리거 | 사용자 정의 DML 트리거 없음 |
| 관리 이벤트 트리거 | 기존 6개 유지/활성, SECURITY DEFINER=false | 변경·비활성화 금지 유지 |
| 관리 이벤트 태그 | cron/net/graphql 접근 트리거는 CREATE EXTENSION, placeholder는 DROP EXTENSION | 테이블 재구축에 확장 접근 트리거를 임의 해제할 필요 없음 |
| Realtime | public publication table 결과 없음 | 현재 public 등록 테이블을 통한 연동 발견 안 됨 |
| postgres | SUPERUSER=false, CREATEROLE=true, CREATEDB=true, BYPASSRLS=true | 고권한 앱 연결 교체 필요. 실제 역할 생성 실행 성공을 검증한 것은 아님 |
| gwon_prod_app | 없음 | 현재 Pooler 로그인 성공 테스트 불가능; 역할을 생성하지 않음 |
| RLS 정책 | test_users PUBLIC ALL / USING true / WITH CHECK true 한 개 | 신규 backend 전용 정책 전환의 명시적 승인 필요 |
| 업무 ACL | 10개 테이블에 anon/authenticated/service_role의 넓은 권한(각 8개, MAINTAIN 포함) | 기존 광범위 권한을 신규 앱에 복사하지 않음 |

관리 pgrst 이벤트 함수는 여전히 재구축 DDL에 실행될 수 있다. 함수 지문/속성 확인은 내부 부작용의 완전 증명이 아니다.
순수 PostgreSQL fixture가 실제 Supabase 이벤트 함수 동작을 재현한다고 주장하지 않는다.
pg_catalog 시스템 함수의 PUBLIC EXECUTE 전체를 제거하거나 함수 실행이 전혀 없다고 주장하지 않는다.

## 코드 직접 호출 / 권한 축소 영향

검색: 프론트 src/public/package.json 및 백엔드 src/main Java/설정.

| 기능 | 확인된 경로 | 6개 업무 테이블의 API 역할 ACL 축소 영향 |
|---|---|---|
| 회원/OAuth/관리 | MemberRepository + Spring 인증 | 앱 역할 members CRUD와 ID USAGE가 유지되면 직접 영향 없음. 실제 운영 OAuth 회귀는 별도 |
| 공지 생성/조회/조회수/삭제 | NoticeRepository, ProdNoticeAuthorPolicy JDBC | 앱 전용 RLS CRUD로 격리 성공. 작성자는 members 재조회, client authorId 무시 |
| 일정 | company_events Repository | 앱 CRUD/IDENTITY USAGE로 격리 성공 |
| 갤러리 메타데이터 | GalleryPhotoRepository | 앱 CRUD/USAGE로 격리 성공 |
| 갤러리 파일 쓰기/삭제 | SupabaseStorageService POST/DELETE /storage/v1/object | DB 사용자와 별도 SUPABASE_SECRET_KEY. storage 스키마/키/파일/버킷 정책을 건드리지 않으면 이번 public ACL 축소가 직접 대상이 아님 |
| 갤러리 파일 읽기 | public Storage URL / GalleryController 302; HomeGallery img src | 브라우저의 직접 Storage HTTP 읽기 있음. public REST 테이블 권한과 구분. 버킷 공개 접근 유지 필요 |
| 이메일 로고 | EmailService / EMAIL_LOGO_URL -> Storage 공개 이미지 | 메일 수신기의 직접 이미지 요청. 파일/버킷 공개 접근 보존 필요 |
| 배너 | BannerRepository와 LOCK TABLE ... EXCLUSIVE | 앱 UPDATE/CRUD로 잠금 성공, CREATE 불필요 |
| /api/users | TestUserController -> test_users | users 테이블을 쓰는 것으로 오인하지 않음. 앱 CRUD/USAGE/RLS로 검증 |
| REST/RPC/Realtime | /rest/v1, RPC, SDK createClient/channel, WebSocket/EventSource 직접 호출 미발견 | 저장소 범위에서 직접 소비자 없음. 외부 스크립트/다른 앱 부재는 증명할 수 없음 |

프론트는 axiosInstance -> 백엔드 API, 갤러리 img src만 Storage URL을 직접 읽는다.
RLS/API 역할 축소는 대상 6개 테이블/시퀀스에만 적용한다. service_role 글로벌 멤버십, storage/auth 권한은 유지한다.
직접 PostgREST/GraphQL 외부 소비자는 명시적 사용자 확인 또는 제한된 역할/뷰 설계 전까지 미확인이다.
members 재구축은 기존 앱 회원/역할을 없앤다. Supabase Auth 사용자가 보존돼도 앱 관리자 자동 복구를 의미하지 않는다.
공지 작성 회원 삭제는 RESTRICT로 차단된다. 공지가 있는 회원 삭제의 UI/오류 안내는 별도 위험이다.

## 앱 역할과 검증 결과 대조

89-6 재구축 SQL/역할 SQL/Probe/jar 입력 해시는 현재 파일과 모두 동일했다.
89-7에서는 새 pgdata-89-7 / 127.0.0.1:55497, PostgreSQL 17.11, Hibernate 6.6.53.Final, pgJDBC 42.7.11 사용.
기존 PostgreSQL 데이터 디렉터리는 수정하지 않았다. 실제 Spring Boot 전체는 시작하지 않았다.

- 164개 assertion PASS, 프로세스 exit 0.
- NOSUPERUSER + CREATEROLE인 fixture_creator가 DB/public 소유자로서 역할 생성 SQL 실행 성공.
  실제 Supabase postgres의 확장/보안 제약을 완전히 재현한 결과는 아님.
- 기존 재구축의 승인 차단, ROLLBACK/COMMIT, 63컬럼/12제약/12인덱스, FK/NOT NULL/UNIQUE/RLS/시퀀스 성공.
- 전용 앱 역할로 실제 JPA 6개 validate, 공지 작성자 JDBC INSERT RETURNING, 조회수/삭제, 배너 잠금 성공.
- 영구 DDL/권한 상승/관리 스키마 직접 접근/setval/TRUNCATE/RLS 우회 차단, TEMP 허용.
- 회원 INSERT/SELECT 및 FK에 의한 DELETE 차단; 전체 회원 HTTP CRUD 회귀를 새로 실행했다고 주장하지 않음.
- 신규 읽기 전용 SQL 04/05 문법 실행 PASS.
- 스키마 전후 SHA256 동일: 7A052802E552395232FD2C34DEA57EFE7DF46DAA6A076EE721C972DCA7F53422.
- 종료 stop=0 / pg_ctl status=3 / pg_isready=2, 포트 해제.

## TLS 최소 로컬 수정안

현재 Dockerfile과 main Dockerfile 모두 CA 등록 단계 없음. 현재 사용자 확인 운영 URL은 sslmode=require.
require는 기본적으로 서버 CA/호스트명을 검증하지 않는다. 서버 인증을 보장하는 verify-full 전환은 별도 검증 필요.
[pgJDBC SSL](https://jdbc.postgresql.org/documentation/ssl/)

`Dockerfile.production-tls.DRAFT`만 추가했다. 현재 ./Dockerfile 및 Dockerfile.dev는 변경하지 않았다.
기존 공개 CA/검증기를 재사용하고 SHA256 확인 후 runtime Java cacerts에 등록한다. dev ENV는 포함하지 않는다.
CA는 공개 인증서이며 개인 키/실제 비밀번호 없음. changeit은 공개 JDK truststore 기본값이다.

- Windows JDK21에서 지문/유효기간/자기 서명/CA 조건 검증 PASS.
- 시스템 저장소가 아닌 복사 cacerts에 등록 후 동일 CA 지문 확인 PASS.
- 변조된 공개 CA 복사본은 fingerprint mismatch와 비영 종료로 거부 PASS.
- Docker 없음: Linux JRE 이미지 빌드, registry tag 다운로드, 실제 서버 인증서 체인/호스트 검증은 미수행.
- CA를 등록해도 sslmode=require가 자동으로 verify-full이 되지 않는다.
- 승인 후 JDBC에는 운영 Dashboard Session Pooler host, verify-full, DefaultJavaSSLFactory를 함께 사용해야 한다.
- 현재 Render 서비스의 실제 런타임 truststore/SSL factory는 이번 단계에서 재조회하지 않았다.

## 커밋 / PR 범위

현재 a16ad2e의 84~88 코드/DDL 보호는 이미 develop과 같은 tree이다. 다시 구현하거나 중복 커밋할 필요 없음.

1. SQL/권한 PR: 기존 89-3 변경 4개(02 manifest,03 rebuild,04 postflight,README),
   app-role의 01~05 SQL/README, 검증 89-3/89-6/89-7의 소스·문서·해시·구조 스냅샷·종료 결과.
   이 PR은 승인 차단/ROLLBACK을 유지하고 SQL 실행 자체를 포함하지 않는다.
2. 배포 TLS PR: 실제 Dockerfile로 보완안 승격은 별도 검토/승인 후. 현재 draft와 보고서, .dockerignore 보완 1줄만 준비.
   Linux Docker build와 서버 CA/호스트 검증 결과를 확보한 뒤 런타임 변경을 승인한다.
3. 운영 코드 동기화 PR: 두 저장소 develop 기능을 main에 반영하는 것은 별도 최종 승인 단계.
   main의 자동 배포를 통제하기 전 merge/push하지 않는다. 프론트 새 authorId 제거를 구 backend에 먼저 배포하지 않는다.

제외: TEST-ONLY SQL 2개씩, pgdata, 라이브러리, classes, 로그, 테스트 truststore/변조 CA,
원시 운영 metadata CSV/JSON. 로컬 보존, 삭제 없음. git add -f 금지 유지.
기존 89-3/89-6 파일을 변경하지 않고 신규 자료/SQL만 추가했다. .dockerignore에 review 자료 제외 1줄을 추가했다.
관련 산출물에 비밀번호/API 키/개인 키를 포함하지 않았다.

## 남은 실행 차단 조건

1. 전용 역할 부재: 실제 역할 생성/비밀번호 입력/Pooler 인증은 별도 승인 후 수행해야 한다.
2. TLS: Docker build와 승인된 운영 Pooler의 verify-full/CA/호스트 검증 완료 필요.
3. 실제 관리 이벤트 동작: 관리 객체를 그대로 보존하는 계획 유지. 이벤트 함수 내부의 완전한 격리 재현은 없음.
4. 외부 직접 API 소비자: 6개 테이블의 anon/authenticated/service_role ACL 축소 영향에 대한 사용자 확인 필요.
5. 승인된 재구축 실행본: 모든 차단값은 false/placeholder/ROLLBACK 상태. 현재 6개 테이블만 초기화 대상임을 재확인.
6. Auto-Deploy 통제/유지보수/관리자 재등록 및 운영 기능 회귀 계획 승인 필요.

## 최종 실행 순서 — 이번에는 실행하지 않음

1. SQL/역할 및 TLS 보완안을 별도 커밋·PR로 검토/승인. 검증된 정확한 SHA/이미지/SQL 지문 고정.
2. 승인된 자동 배포 통제와 유지보수 절차 마련. old main의 update 백엔드와 다른 writer를 중지하고 재시작 차단.
3. 운영 프로젝트/Pooler 연결/6개 초기화 범위/외부 API 영향 재확인. OAuth/SMTP/Storage/도메인 운영값 보존.
4. 관리자 연결로 승인된 역할 생성 SQL 실행. 비밀번호는 사용자가 안전한 비밀 입력 경로에서 설정.
5. 사용자명 gwon_prod_app.zsaqpsohhwygrawphnsg로 실제 Session Pooler read-only 연결,
   current_user/프로젝트 연결 대상/Java verify-full 검증. 역할 부재 상태에서는 이 검증을 앞당길 수 없음.
6. 승인된 03 재구축 실행본을 단일 트랜잭션으로 적용하고 역할/스키마 postflight 실행.
   기존 스키마 전환용 역할 02를 재구축 전에 먼저 실행하지 않는다.
7. 자동 배포가 억제된 상태에서 승인된 운영 코드 main 반영 및 TLS 이미지 준비.
   구 코드가 새 스키마를 대상으로 시작하지 않게 하고, 새 코드도 DB 준비 전에 시작하지 않게 한다.
8. 승인된 Render 연결 사용자/비밀번호/verify-full 변경 후 새 백엔드 기동.
   prod 또는 기본 정책, validate/SQL init never, current_user=gwon_prod_app, 운영 연결 대상을 확인.
9. 신규 앱 관리자 등록/승격은 별도 승인 후. 공지/회원/일정/갤러리/Storage/배너/OAuth/SMTP 회귀 확인.
10. 운영 API/쿠키/CORS가 확인된 뒤 프론트 main 반영·배포. 안정 확인 후 자동 배포 정책 복원.

실패 시 모든 writer를 중지하고 스키마에 호환되는 이전/수정 이미지로 복구한다.
연결 자격증명의 postgres 복원은 고권한 위험을 다시 도입하므로 긴급 별도 승인 필요.
이미 재구축 COMMIT한 업무 데이터를 역할/코드 롤백으로 복구할 수 없다. 재초기화로 장애를 우회하지 않는다.
향후 신규 데이터는 보존하고 버전 관리된 수정 마이그레이션을 사용한다.
