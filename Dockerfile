FROM helixnetwork/base16.04:latest as builder
MAINTAINER Dario Tietz

WORKDIR /helix-1.0
COPY . /helix-1.0
RUN mvn clean package

FROM openjdk:jre-slim
WORKDIR /helix-1.0
COPY --from=builder /testnet-1.0/target/helix*.jar helix*.jar
VOLUME /helix-1.0


EXPOSE 8085
EXPOSE 4100/udp
EXPOSE 5100/tcp
EXPOSE 5556/tcp

WORKDIR /helix/data

ENTRYPOINT ["/usr/bin/java", "-XX:+DisableAttachMechanism", "-Xmx8g", "-Xms256m", "-Dlogback.configurationFile=/helix-1.0/conf/logback.xml", "-Djava.net.preferIPv4Stack=true", "-jar", "helix*.jar", "-p", "8085", "$@"]
