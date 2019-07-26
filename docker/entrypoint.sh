#!/bin/bash
# See Dockerfile and DOCKER.md for further info

if [ "${DOCKER_HLX_MONITORING_API_PORT_ENABLE}" == "1" ]; then
  nohup socat -lm TCP-LISTEN:8086,fork TCP:127.0.0.1:${DOCKER_HLX_MONITORING_API_PORT_DESTINATION} &
fi

HLX_JAR_FILE=$(find "$DOCKER_HLX_JAR_PATH" -type f -name "$DOCKER_HLX_JAR_FILE" -print -quit)
if [[ "${HLX_JAR_FILE}x" == "x" ]]
then
  >&2 echo "ERROR: File '$DOCKER_HLX_JAR_FILE' not found in path '$DOCKER_HLX_JAR_PATH'"
  exit 1
fi

exec java \
  $JAVA_OPTIONS \
  -Xms$JAVA_MIN_MEMORY \
  -Xmx$JAVA_MAX_MEMORY \
  -Djava.net.preferIPv4Stack="$DOCKER_JAVA_NET_PREFER_IPV4_STACK" \
  -jar "$HLX_JAR_FILE" \
  --remote "$DOCKER_HLX_REMOTE" --remote-limit-api "$DOCKER_HLX_REMOTE_LIMIT_API" \
  "$@"