#!/usr/bin/env bash
# start.sh - find the bootable jar (not *-plain.jar) and exec java
set -e
JAR=$(ls build/libs | grep -v "-plain" | grep ".jar$" | head -n1)
if [ -z "$JAR" ]; then
  echo "No executable jar found in build/libs" >&2
  exit 1
fi
echo "Starting $JAR"
exec java -Dserver.port=${PORT:-8080} -jar "build/libs/$JAR"

