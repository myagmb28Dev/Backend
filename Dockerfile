FROM eclipse-temurin:17-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY build/libs/app.jar app.jar

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -Dfile.encoding=UTF-8 -jar app.jar"]
