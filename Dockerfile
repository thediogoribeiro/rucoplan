FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests clean package \
    && JAR_FILE="$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*.original' -print -quit)" \
    && test -n "${JAR_FILE}" \
    && cp "${JAR_FILE}" /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system productionplanning \
    && useradd --system --gid productionplanning --home-dir /app --shell /usr/sbin/nologin productionplanning

WORKDIR /app

COPY --from=build --chown=productionplanning:productionplanning /workspace/app.jar /app/app.jar

USER productionplanning

EXPOSE 8082

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=10 \
    CMD curl --fail --silent --show-error http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
