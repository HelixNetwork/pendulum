FROM helixnetwork/base16.04:latest as builder
MAINTAINER Dario Tietz

WORKDIR /testnet-1.0
COPY . /testnet-1.0
RUN mvn clean package

FROM openjdk:jre-slim
WORKDIR /testnet-1.0
COPY --from=builder /testnet-1.0/target/helix*.jar helix*.jar
VOLUME /testnet-1.0

EXPOSE 14700/udp
EXPOSE 14700
EXPOSE 14600/udp

ENTRYPOINT ["/usr/bin/java", "-XX:+DisableAttachMechanism", "-Xmx8g", "-Xms256m", "-Dlogback.configurationFile=/testnet-1.0/conf/logback.xml", "-Djava.net.preferIPv4Stack=true", "-jar", "helix*.jar", "-p", "14700", "-u", "14600", "--remote", "$@"]
