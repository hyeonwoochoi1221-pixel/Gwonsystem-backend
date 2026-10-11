# 자동 DDL 차단 (운영 전환 후속 단계)

기준 브랜치 codex/prod-notice-transition, HEAD 9211f453dcabb91b9c43ffab73b0b7a0ba82ad54.
앞 단계 공지사항 작성자 정책의 미커밋 변경은 유지했다. prod-notice-author-policy.md의
자동 DDL 미변경 기록은 앞 단계 시점의 기록이며, 이번 단계에서 아래와 같이 해소했다.

## 적용

- 기본 및 prod: application.properties의 ddl-auto=validate. generate-ddl=false,
  sql.init.mode=never, Flyway/Liquibase 비활성화.
- dev: 기존 application-dev.properties와 DevEnvironmentGuard를 유지한다.
- CompanyEventSchemaConfig는 빈 설정 클래스로 남기고 ALTER TABLE Runner를 제거했다.
  어떤 프로필에서도 스키마 변경 Bean을 등록하지 않는다.
- DdlSafetyEnvironmentGuard는 ConfigData 로딩 및 DevEnvironmentGuard 이후,
  datasource 생성 전에 실행된다. 비-dev 환경의 자동 DDL/초기화 설정을 거부하고
  validate 설정을 최상위 PropertySource에 고정한다. 오류는 설정 이름만 출력한다.
- env, 명령행, native Hibernate properties 및 JPA database/scripts action을 검사한다.
  JPA action=none도 Hibernate validate를 우회할 수 있으므로 거부한다.
- 최종 Boot Hibernate properties customizer에서도 validate를 강제하고
  스키마 생성 action 또는 다른 hbm2ddl action을 거부한다.
- Hikari connection-init-sql은 비-dev에서 빈 값만 허용하며 dev는 기존대로 빈 값으로 고정한다.

## 확인 범위 및 한계

현재 src/main에서 다른 ApplicationRunner/CommandLineRunner/PostConstruct DDL,
schema.sql/data.sql/import.sql, 마이그레이션 프레임워크 실행 경로는 발견하지 못했다.
Dockerfile/Dockerfile.dev는 java -jar로 실행하며 수정하지 않았다.
테스트 fixture는 별도의 임시 localhost DB에 스키마를 생성하며 운영 실행 경로가 아니다.

운영 실제 환경변수 및 DB는 접근하지 않았다. 배포 전에 update/create/create-drop/none,
generate-ddl=true, sql.init.mode=always/embedded, migration enabled 설정을 제거해야 한다.
기존 환경변수를 이 작업에서 변경하지 않았다. dev 프로필을 운영에서 사용하면 안 된다.

스키마는 DBA가 별도로 준비해야 한다. 특히 운영 작성자 정책의 author_member_id BIGINT
NOT NULL 및 members FK, nullable legacy author_id, company_events.end_date nullable 조건이
필요하다. Hibernate validate는 NULL/DEFAULT/인덱스/모든 FK 의미를 완전 검증하지 않는다.
운영 스키마 불일치는 기동 실패로 처리하며 자동 수정하지 않는다.

이 보호는 현재 Boot 시작 경로의 차단이다. 미래 코드가 별도 EntityManagerFactory를
직접 만들거나 동일 우선순위 customizer/후속 initializer를 추가하면 재검토해야 한다.
DB 역할의 영구 DDL 권한을 제거하는 별도 방어도 권장하며, 현재 운영 권한은 미확인이다.
트리거/함수의 간접 DDL은 이 설정으로 통제할 수 없다.

## 검증

JDK 21 및 캐시 의존성으로 offline 실행:

```powershell
.\mvnw.cmd -o '-DargLine=-javaagent:C:\Users\hwcho\.m2\repository\org\mockito\mockito-core\5.17.0\mockito-core-5.17.0.jar' test
.\mvnw.cmd -o '-Dtest=DdlSafetyTest,DevStartupTest' '-DargLine=-javaagent:C:\Users\hwcho\.m2\repository\org\mockito\mockito-core\5.17.0\mockito-core-5.17.0.jar' test
.\mvnw.cmd -o -DskipTests package
```

전체 97개 테스트 통과 후 env/bracket 및 실제 Boot 명령행 차단 테스트 2개를 추가했고,
해당 프로필/DDL 테스트 12개가 통과했다. 로그: ddl-safety-tests.log,
ddl-safety-focused.log, ddl-safety-build.log (Git ignore 대상).
Supabase/기존 로컬 PG 데이터 디렉터리/배포 환경은 사용하지 않았다.
