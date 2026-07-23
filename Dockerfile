FROM eclipse-temurin:25-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
RUN ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:25-jre-jammy

RUN groupadd --system ersync \
    && useradd --system --gid ersync --home-dir /app --shell /usr/sbin/nologin ersync

WORKDIR /app

COPY --from=builder --chown=ersync:ersync /workspace/build/libs/*.jar app.jar

USER ersync

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
