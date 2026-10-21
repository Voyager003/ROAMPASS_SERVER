# 세 앱(roampass-api, mock-pg, mock-supplier) 공용 이미지. 빌드할 모듈은 MODULE 인자로 고름
#   docker build --build-arg MODULE=roampass-api -t roampass-api .
# 빌드 컨텍스트는 저장소 루트(settings.gradle.kts가 세 모듈을 모두 포함하므로)

# 1단계: Gradle로 실행 가능한 jar 생성. 테스트는 CI·로컬에서 따로 돌리므로 여기서는 제외
FROM eclipse-temurin:21-jdk AS build
ARG MODULE
WORKDIR /workspace

# 의존성 계층을 소스와 분리해 소스만 바뀌면 다운로드를 다시 하지 않게 함
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY roampass-api/build.gradle.kts roampass-api/
COPY mock-pg/build.gradle.kts mock-pg/
COPY mock-supplier/build.gradle.kts mock-supplier/
RUN ./gradlew --no-daemon -q ":${MODULE}:dependencies" > /dev/null

COPY ${MODULE}/src ${MODULE}/src
RUN ./gradlew --no-daemon ":${MODULE}:bootJar" \
 && find "${MODULE}/build/libs" -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/app.jar \;

# 2단계: JRE와 jar만. root가 아닌 사용자로 실행
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER app

# 컨테이너 메모리 한도의 75%를 힙 상한으로(1GB 인스턴스에서 JVM 둘이 함께 돌 때 대비). 필요하면 JAVA_OPTS로 덮어씀
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
