FROM eclipse-temurin:17-jre AS jre

FROM debian:bookworm-slim
WORKDIR /app

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=jre /opt/java/openjdk ${JAVA_HOME}
COPY scripts/healthcheck-adaptive.sh /usr/local/bin/healthcheck-adaptive.sh
COPY scripts/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/healthcheck-adaptive.sh /usr/local/bin/docker-entrypoint.sh

COPY build/libs/app.jar app.jar

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
