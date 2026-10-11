# 개발 Render Java SSL 신뢰 저장소

운영 Dockerfile은 이번 변경에서 수정하지 않았다. 새 Dockerfile.dev만 Supabase Root 2021 CA를
Java 21 기본 cacerts에 추가한다. 개발 이미지의 JSSE 신뢰 범위가 확장되며 운영 이미지에는 추가되지 않는다.

## 인증서와 검증

- docker/dev/prod-ca-2021.crt: 사용자가 Downloads에 내려받은 공개 CA 인증서.
- Subject/Issuer: Supabase Root 2021 CA. 유효기간: 2021-04-28 ~ 2031-04-26 (UTC 날짜).
- SHA-256 (파일 바이트 해시가 아닌 X.509 DER 지문):
  807025ad50d4ed219d2c9c7d299c004f824eb00cf7f65afef607d07b72e6cafa
- VerifySupabaseCa.java는 위 지문을 고정하여 빌드·런타임 이미지 양쪽에서 import 전에 검사한다.
  개인 키 표시, 복수 인증서, 만료, CA 제약/용도 불일치, 자기 서명 검증 실패도 빌드를 중단한다.
- keytool의 changeit은 JDK 기본 공개 cacerts의 표준 암호다. DB/Storage 자격증명이나 개인 키가
  아니다. cacerts에는 공개 인증서만 있으며 이 변경에 애플리케이션 비밀번호/키는 포함하지 않는다.

JDBC의 sslmode=verify-full과 org.postgresql.ssl.DefaultJavaSSLFactory는 유지한다.
sslrootcert를 JDBC에 추가하거나 SSL 검증을 낮추지 않는다. DEV_* 허용 목록, 실제 DB 역할/TLS/DDL
검사, TEMP 허용 기준, Hibernate validate 및 시작 DDL 차단도 변경하지 않는다.
Supabase CA는 개발/운영 프로젝트 구분 수단이 아니다. 기존 프로젝트 ref/DB 계정 검사가 그 분리를 담당한다.
JAVA_TOOL_OPTIONS/JDK_JAVA_OPTIONS로 다른 javax.net.ssl.trustStore를 지정하면 기본 cacerts 등록이
사용되지 않을 수 있으므로 개발 서비스에 별도 trustStore 재정의가 있는지 확인해야 한다.

## 로컬 검증 및 한계

Java 21에서 공개 인증서 지문/CA/자기 서명 검증 및 cacerts 복사본 import/list가 성공했다.
복사본을 사용하는 기본 JSSE TrustManager의 CA 신뢰 검사도 성공했다.
다른 공개 Root CA를 전달하면 지문 불일치로 거부됐다. 실제 Supabase DB 접속은 하지 않았다.
호스트의 Java 설치 신뢰 저장소는 변경하지 않고 target 아래 복사본만 사용했다.
Docker CLI가 없어 eclipse-temurin:21-jre-jammy 컨테이너 빌드·실행은 이 환경에서 수행할 수 없다.
따라서 수정은 인증서 누락 원인을 보완하지만 실제 Render 연결 성공까지 검증한 것은 아니다.
백엔드 회귀 테스트 73개가 모두 통과했고 실패/오류/건너뜀은 0개다. 테스트 후 별도 JAR 패키징과
git diff --check도 성공했다. 첫 샌드박스 테스트는 임시 PostgreSQL 실행 차단으로 중단했고,
승인된 샌드박스 밖 재실행으로 전체 회귀 검증을 완료했다.

Docker가 있는 환경의 승인된 로컬 검증 명령:

```sh
docker build --pull -f Dockerfile.dev -t gwonsystem-backend-dev:ca-root-2021 .
docker run --rm --entrypoint keytool gwonsystem-backend-dev:ca-root-2021 \
  -list -cacerts -storepass changeit -alias gwon-dev-supabase-root-2021
```

두 번째 명령은 앱을 시작하지 않으며 DB 연결도 하지 않는다. 실제 앱 시작은 승인된 개발 변수와
개발 DB만 준비된 후 별도 수행한다. CA 만료/교체 시 새 지문을 독립적으로 승인받아 갱신한다.

## Render 변경 절차 — 승인 후 사용자가 수행

1. 검토된 변경을 승인 후 develop에 반영한다. 자동 배포가 켜져 있다면 반영 전에 개발 서비스의
   배포 트리거를 확인한다. 이번 작업에서는 commit/Push/병합을 하지 않았다.
2. gwonsystem-backend-dev만 선택한다. Branch=develop, Dockerfile Path=Dockerfile.dev,
   Docker build context=저장소 루트(.)로 지정한다. Root Directory를 사용한다면 경로는 해당
   서비스 루트를 기준으로 확인한다. 운영 서비스와 main의 Dockerfile 설정은 바꾸지 않는다.
3. SPRING_PROFILES_ACTIVE=dev, APP_DEPLOYMENT_ENV=dev와 기존 개발 전용 변수를 유지한다.
   DEV_DB_POOLER_HOST=aws-0-ap-northeast-1.pooler.supabase.com, 포트 5432 및 해당 개발 ref의
   gwon_dev_app.<ref> 로그인 라우팅을 확인한다. DB 비밀번호/Storage 키는 이미지에 넣지 않는다.
4. 승인된 커밋을 대상으로 Manual Deploy의 Clear build cache & deploy를 선택하여 새 이미지로
   빌드한다. 지문 검증과 keytool 등록 성공, verify-full/TLS/역할 검사 통과 및 /internal/health를
   확인한다. 자동 DDL이나 마이그레이션 명령을 추가하지 않는다.
5. 실패하면 SSL 검증을 낮추지 말고 실제 사용 trustStore, 인증서 체인, 호스트/ref 및 역할을
   확인한다. 운영 이미지로 개발 서버를 되돌려 운영 기본 설정을 사용하지 않는다.

참고: [Supabase SSL](https://supabase.com/docs/guides/platform/ssl-enforcement),
[Render Docker 설정](https://render.com/docs/docker), [Render 배포](https://render.com/docs/deploys).
