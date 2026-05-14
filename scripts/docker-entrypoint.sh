#!/bin/sh
set -eu

PORT_VALUE="${PORT:-8080}"
APP_JAR="${APP_JAR:-/app/app.jar}"
APP_CDS_PATH="${APP_CDS_PATH:-/app/app-cds.jsa}"
APP_CDS_ENABLED="${APP_CDS_ENABLED:-false}"

BASE_JAVA_OPTS="${JAVA_OPTS:-}"
STARTUP_JAVA_OPTS="${JAVA_STARTUP_OPTS:--XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+AlwaysPreTouch -XX:+UseStringDeduplication -XX:+TieredCompilation -XX:TieredStopAtLevel=1 -Djava.security.egd=file:/dev/./urandom}"

if [ "$APP_CDS_ENABLED" = "true" ] && [ -f "$APP_CDS_PATH" ]; then
  exec sh -c "java -Xshare:on -XX:SharedArchiveFile=$APP_CDS_PATH $STARTUP_JAVA_OPTS $BASE_JAVA_OPTS -Dserver.port=$PORT_VALUE -Dfile.encoding=UTF-8 -jar $APP_JAR"
fi

exec sh -c "java $STARTUP_JAVA_OPTS $BASE_JAVA_OPTS -Dserver.port=$PORT_VALUE -Dfile.encoding=UTF-8 -jar $APP_JAR"
