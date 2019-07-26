FROM iotacafe/maven:3.5.4.oracle8u181.1.webupd8.1.1-1@sha256:5e30eb28d778a65af2498bf1b7ef594adf046d44a8e4f7b32b326d8d10626e93 as local_stage_build
MAINTAINER dt@hlx.ai

WORKDIR /helix-1.0

COPY . /helix-1.0
RUN mvn clean package

# execution image
FROM helixnetwork/base16.04:latest as builder

RUN apt-get update && apt-get install -y --no-install-recommends \
        socat \
    && rm -rf /var/lib/apt/lists/*

COPY --from=local_stage_build /helix-1.0/target/helix-1.0*.jar /helix-1.0/target/
COPY docker/entrypoint.sh /

# Default environment variables configuration. See DOCKER.md for details.
# Override these variables if required (e.g. docker run -e JAVA_OPTIONS="myoptions" ...)
# `JAVA_OPTIONS`                                 Java related options
# `JAVA_MIN_MEMORY` and `JAVA_MAX_MEMORY`        Settings for -Xms and -Xmx respectively.
#                                                See https://docs.oracle.com/cd/E21764_01/web.1111/e13814/jvm_tuning.htm#PERFM161
# `DOCKER_HLX_JAR_PATH`                          Path where the HLX jar file is located.
# `DOCKER_HLX_JAR_FILE`                          HLX jar file.
# `DOCKER_HLX_REMOTE_LIMIT_API`                  Sets the --remote-limit-api options.
#                                                (Deprecation warning, see https://github.com/iotaledger/iri/issues/1500)
# `DOCKER_HLX_MONITORING_API_PORT_ENABLE`        When using a docker bridged network setting this to 1 will have
#                                                socat exposing 8086 and pointing it on localhost. See /entrypoint.sh
#                                                Do not enable this option when running HLX's container on host network.
#                                                !!! DO NOT DOCKER EXPOSE (-p) 8086 as the remote api settings will
#                                                not be applied on that port !!!
#                                                You also have to maintain $DOCKER_HLX_MONITORING_API_PORT_DESTINATION
#                                                based on the actual API port exposed via HLX
# `DOCKER_HLX_MONITORING_API_PORT_DESTINATION`   Set this to the actual HLX API port. This is used to map port 14266.
# `DOCKER_HLX_REMOTE`                            When using a docker bridged network set this to true. Using host network
#                                                you may choose to set it to false to make sure the API port listens on
#                                                localhost only. If you want to bind your API (--api-host) to a specific interface
#                                                you will have to set this option to false.
# `DOCKER_JAVA_NET_PREFER_IPV4_STACK`            If set to true will allow usage of IPv4 only. Set to false to be able to use IPv6.
#                                                See https://docs.oracle.com/javase/7/docs/api/java/net/doc-files/net-properties.html
ENV JAVA_OPTIONS="-XX:+UnlockExperimentalVMOptions -XX:+DisableAttachMechanism -XX:InitiatingHeapOccupancyPercent=60 -XX:G1MaxNewSizePercent=75 -XX:MaxGCPauseMillis=10000 -XX:+UseG1GC" \
    JAVA_MIN_MEMORY=2G \
    JAVA_MAX_MEMORY=4G \
    DOCKER_HLX_JAR_PATH="/helix-1.0/target" \
    DOCKER_HLX_JAR_FILE="helix-1.0*.jar" \
    DOCKER_HLX_REMOTE_LIMIT_API="interruptAttachToTangle, attachToTangle, addNeighbors, removeNeighbors, getNeighbors" \
    DOCKER_HLX_MONITORING_API_PORT_ENABLE=0 \
    DOCKER_HLX_MONITORING_API_PORT_DESTINATION=8085 \
    DOCKER_HLX_REMOTE=true \
    DOCKER_JAVA_NET_PREFER_IPV4_STACK=true

WORKDIR /helix-1.0/data
ENTRYPOINT [ "/entrypoint.sh" ]