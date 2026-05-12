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
RUN chmod +x /usr/local/bin/healthcheck-adaptive.sh

COPY build/libs/app.jar app.jar

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -Dfile.encoding=UTF-8 -jar app.jar"]
