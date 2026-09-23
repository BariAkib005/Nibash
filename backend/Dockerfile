# syntax=docker/dockerfile:1
#
# Nibash API — multi-stage build (plan §Week 6): compile with the JDK, run on a JRE-only image.

# ---------------------------------------------------------------- build
FROM eclipse-temurin:26-jdk AS build
WORKDIR /src

# Dependencies first, so editing source does not re-download the world.
COPY .mvn .mvn
COPY mvnw pom.xml ./
# A Windows checkout gives mvnw CRLF line endings, which /bin/sh cannot run.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/nibash-*.jar /src/app.jar

# ---------------------------------------------------------------- runtime
FROM eclipse-temurin:26-jre

RUN groupadd --system nibash && useradd --system --gid nibash --home /app nibash \
    && mkdir -p /app/media && chown nibash:nibash /app/media
WORKDIR /app
COPY --from=build --chown=nibash:nibash /src/app.jar app.jar

USER nibash
# Uploads live on a volume; the heap is capped as a share of the container's memory so the JVM and
# MySQL can share a small VM (plan §9, "Azure free-tier RAM starves JVM + MySQL").
ENV NIBASH_MEDIA_DIR=/app/media \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
