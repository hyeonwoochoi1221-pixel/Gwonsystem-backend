# 개발 DB TEMP 권한 허용 검토

기준: Supabase PostgreSQL 17. 사용자가 확인한 PUBLIC 권한은 database CREATE=false,
TEMP=true, public schema CREATE=false다. 이번 작업에서는 실제 DB에 접속하지 않았다.

## 허용과 차단

PUBLIC의 TEMP 상속은 허용한다. 임시 테이블·시퀀스와 그 임시 객체의 ALTER/DROP은
같은 DB 세션 안에서 가능하며, 영구 스키마·업무 테이블에 대한 DDL 권한을 부여하지 않는다.
SQL은 TEMP를 직접 GRANT/REVOKE하지 않으며 PUBLIC/Supabase 관리 역할의 권한도 변경하지 않는다.
검증 SQL의 can_create_temp_objects=true는 정상적인 정보 표시다.

개발 DataSource 검사는 DB CREATE, 모든 영구 스키마 CREATE, 현재 DB 및 공유 객체의
영구 소유권, 다른 역할 멤버십, SUPERUSER/CREATEDB/CREATEROLE/REPLICATION/BYPASSRLS를 거부한다.
임시 관계·타입·스키마 소유권만 소유권 거부 검사에서 제외한다.
기존 Java 검사는 TEMP를 검사하지 않았으므로 이번에는 허용 기준을 명시하고 영구 DDL 검사를 확장했다.
검사 쿼리의 카탈로그와 권한 함수는 pg_catalog로 명시하여 임시 객체의 이름 가리기를 방지한다.
일반 운영 프로필에는 이 검사가 등록되지 않으며 운영 설정은 변경하지 않았다.

## TEMP 우회와 한계

- 임시 테이블은 영구 테이블 이름을 가릴 수 있다. 역할 기본 search_path는
  pg_catalog, public, pg_temp이고, dev JDBC currentSchema는 public,pg_temp다.
  pg_catalog는 JDBC 경로에서도 암묵적으로 먼저 검색된다. Hibernate default_schema=public을
  강제하여 ORM 업무 테이블을 스키마 명시로 조회한다. dev ddl-auto=validate와 시작 DDL 차단은 유지한다.
- search_path는 세션에서 다시 바꿀 수 있다. 기본값은 보안 경계가 아니며 SQL injection을
  해결하지 않는다. 권한 검사는 search_path와 무관한 명시적 카탈로그 조회를 사용한다.
- TEMP 자체는 영구 객체의 CREATE/ALTER/DROP 권한을 주지 않는다. 임시 객체는 세션 종료까지
  남을 수 있고 Session pooler/Hikari에서 재사용될 수 있다. SQL injection 시 조회 가리기,
  세션 오염, 디스크 사용량 증가 가능성이 있어 사용자 임의 SQL 실행 기능은 허용하지 않는다.
- SECURITY DEFINER 함수는 소유자 권한으로 실행한다. 안전하지 않은 함수의 미지정 search_path,
  임시 객체보다 늦은 신뢰 스키마 검색, 동적 SQL은 간접 영구 DDL/데이터 변경 우회가 될 수 있다.
  함수 내부 참조를 스키마 명시하고 신뢰 스키마 뒤에 pg_temp를 둔 함수별 search_path,
  필요한 역할만 EXECUTE할 수 있는 권한을 별도 검토해야 한다. 이번 변경은 기존 함수나 권한을 고치지 않는다.
- 트리거, RLS 표현식, 기본값, 외래키 cascade 등도 앱의 직접 권한 이상의 부작용을
  일으킬 수 있다. 시작 검사는 해당 함수 구현 전체의 안전성을 증명하지 않는다.
  02 검증 SQL은 보안 함수의 proconfig, 트리거와 FK를 읽기 전용으로 나열한다.
  검토 전에는 실제 개발 서버의 데이터 변경 요청이 안전하다고 단정할 수 없다.

허용 범위는 개발 전용 DB 안에서 임시 객체를 사용하는 권한이다. 영구 DDL 및 소유 역할 획득은
계속 차단하며, 승인되지 않은 보안 함수·트리거의 간접 권한 상승은 허용 범위에 포함하지 않는다.
실제 함수·트리거·RLS·TLS 검증은 승인된 후 개발 DB에서 별도로 진행해야 한다.

참고: [PostgreSQL 17 SECURITY DEFINER 안전 지침](https://www.postgresql.org/docs/17/sql-createfunction.html#SQL-CREATEFUNCTION-SECURITY),
[스키마와 search_path](https://www.postgresql.org/docs/17/ddl-schemas.html).

## 검증 범위

새 임시 localhost PostgreSQL에서 TEMP 테이블 생성/변경을 허용하면서 영구 테이블의
CREATE/ALTER/DROP은 거부하는 것을 검증한다. 임시 pg_roles와 변경된 search_path가 있어도
검사가 정상 작동하며, 다른 영구 스키마의 CREATE와 영구 테이블 소유권은 거부한다.
이 로컬 테스트의 TLS 결과만 mock 처리한다. 실제 Supabase 또는 pooler에는 접속하지 않는다.
번들 로컬 PostgreSQL은 14.22이므로 PostgreSQL 17에서 SQL 파일 전체를 실행한 검증은 아니다.

최종 검증: `mvnw.cmd package` 성공, 테스트 73개 / 실패 0 / 오류 0 / 건너뜀 0.
인증·회원·공지·갤러리 등 기존 테스트와 dev 권한/DDL 테스트를 포함한다.
`git diff --check`도 통과했다. 중간 전체 빌드는 샌드박스의 테스트 JVM 정체로 중단했고,
승인된 샌드박스 밖 재실행으로 최종 테스트와 JAR 패키징을 완료했다.
Supabase 접속, SQL 파일 실행, 운영 환경 변경, Push/병합/배포는 수행하지 않았다.
