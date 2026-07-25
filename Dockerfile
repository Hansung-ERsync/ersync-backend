FROM eclipse-temurin:25-jdk-jammy@sha256:0348e7b24ad4479cf35927b750671bb4b78465c303003b08536f6f2fa6f180cd AS builder

WORKDIR /workspace

ADD https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem /tmp/rds-ca/global-bundle.pem
RUN mkdir -p /tmp/rds-ca/certificates \
    && awk 'BEGIN { number = 1 } \
        { print > ("/tmp/rds-ca/certificates/rds-ca-" number ".pem") } \
        /-----END CERTIFICATE-----/ { number++ }' \
        /tmp/rds-ca/global-bundle.pem \
    && keytool -importkeystore \
        -srckeystore "${JAVA_HOME}/lib/security/cacerts" \
        -srcstorepass changeit \
        -destkeystore /tmp/rds-truststore.p12 \
        -deststoretype PKCS12 \
        -deststorepass changeit \
        -noprompt >/dev/null \
    && for certificate in /tmp/rds-ca/certificates/*.pem; do \
        alias="$(basename "${certificate}" .pem)"; \
        keytool -importcert \
            -alias "${alias}" \
            -file "${certificate}" \
            -keystore /tmp/rds-truststore.p12 \
            -storetype PKCS12 \
            -storepass changeit \
            -noprompt >/dev/null; \
    done \
    && keytool -list \
        -keystore /tmp/rds-truststore.p12 \
        -storetype PKCS12 \
        -storepass changeit >/dev/null

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
RUN ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:25-jre-jammy@sha256:b8ba5fca9d88b6ecc3a46c8e75b744f84aca9a9d08587901b5ab480baf641ab5

RUN groupadd --gid 10001 ersync \
    && useradd \
        --uid 10001 \
        --gid ersync \
        --home-dir /app \
        --no-create-home \
        --shell /usr/sbin/nologin \
        ersync \
    && mkdir -p /app/certs /app/config \
    && chmod 0555 /app /app/certs /app/config

WORKDIR /app

COPY --from=builder --chown=root:root --chmod=0444 /workspace/build/libs/*.jar app.jar
COPY --from=builder --chown=root:root --chmod=0444 /tmp/rds-truststore.p12 /app/certs/rds-truststore.p12

ENV JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=/app/certs/rds-truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit -Djavax.net.ssl.trustStoreType=PKCS12"

USER ersync

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
