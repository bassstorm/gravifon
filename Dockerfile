# ── Build stage ──────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-jammy AS build

WORKDIR /build

# Cache dependency layer separately so code changes don't re-download deps
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

ARG PROJECT_VERSION=dev

LABEL org.opencontainers.image.title="Gravifon"
LABEL org.opencontainers.image.description="Local-network audio player"
LABEL org.opencontainers.image.source="https://github.com/gravifon/gravifon"
LABEL org.opencontainers.image.version="${PROJECT_VERSION}"

WORKDIR /app

COPY --from=build /build/target/gravifon-*.jar app.jar

# Music library is mounted here at runtime
VOLUME /music

EXPOSE 8080

ENV GRAVIFON_MUSIC_ROOT=/music
ENV SPRING_PROFILES_ACTIVE=""

ENTRYPOINT ["java", "-jar", "app.jar"]

