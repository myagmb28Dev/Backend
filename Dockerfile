FROM eclipse-temurin:17-jre AS jre

FROM jre AS layers
WORKDIR /layers
COPY build/libs/app.jar /tmp/app.jar
RUN java -Djarmode=tools -jar /tmp/app.jar extract --layers --launcher --destination /layers

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

COPY --from=layers /layers/dependencies/ ./
COPY --from=layers /layers/spring-boot-loader/ ./
COPY --from=layers /layers/snapshot-dependencies/ ./
COPY --from=layers /layers/application/ ./

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
