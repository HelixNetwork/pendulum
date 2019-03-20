FROM helixnetwork/base16.04:latest as builder
MAINTAINER Dario Tietz

WORKDIR /sbx
COPY . /sbx
RUN mvn clean package

FROM openjdk:jre-slim
WORKDIR /sbx
COPY --from=builder /sbx/target/sbx*.jar sbx.jar
VOLUME /sbx

EXPOSE 14700/udp
EXPOSE 14700
EXPOSE 14600/udp
EXPOSE 5556

CMD ["/usr/bin/java", "-XX:+DisableAttachMechanism", "-Xmx8g", "-Xms256m", "-Dlogback.configurationFile=/sbx/conf/logback.xml", "-Djava.net.preferIPv4Stack=true", "-jar", "sbx.jar", "-p", "14700", "-u", "14600", "--zmq-enabled=true", "--remote", "--remote-auth", "helix:LW59AG75A84GSEES", "-m", "30", "$@"]