# Stage 1: Build stage
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Windows 줄바꿈(CRLF)을 Linux 스타일(LF)로 변환하고 실행 권한 부여
RUN chmod +x ./gradlew && sed -i 's/\r$//' ./gradlew
RUN ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# 빌드 결과물을 app.jar로 복사
COPY --from=build /app/build/libs/*.jar app.jar

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -Dfile.encoding=UTF-8 -jar app.jar"]
