#!/bin/bash
# See Dockerfile and DOCKER.md for further info

if [ "${DOCKER_SBX_MONITORING_API_PORT_ENABLE}" == "1" ]; then
  nohup socat -lm TCP-LISTEN:14266,fork TCP:127.0.0.1:${DOCKER_SBX_MONITORING_API_PORT_DESTINATION} &
fi

exec java \
  $JAVA_OPTIONS \
  -Xms$JAVA_MIN_MEMORY \
  -Xmx$JAVA_MAX_MEMORY \
  -Djava.net.preferIPv4Stack=true \
  -jar $DOCKER_SBX_JAR_PATH \
  --remote --remote-limit-api "$DOCKER_SBX_REMOTE_LIMIT_API" \
  "$@"
