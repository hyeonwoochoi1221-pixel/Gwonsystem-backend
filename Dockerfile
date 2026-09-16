# 1. 빌드 스테이지: 내 프로젝트 버전인 메이븐 3.9.16과 자바 21을 사용해 빌드 진행
FROM maven:3.9.16-eclipse-temurin-21 AS build
COPY . .
# 테스트 코드를 제외하고 jar 파일 빌드 (Render 무료 플랜 메모리 절약)
RUN mvn clean package -DskipTests

# 2. 실행 스테이지: 빌드된 가벼운 jar 파일만 자바 21 JRE 환경으로 복사
FROM eclipse-temurin:21-jre-jammy
EXPOSE 8080
# 메이븐 빌드 결과물이 나오는 target 폴더에서 jar 복사
COPY --from=build /target/*-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]