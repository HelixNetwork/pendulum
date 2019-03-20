# Docker
The Dockerfile included in this repo builds a working SBX docker container whilst trying to stay the least opinionated as possible. This allows system administrators the option to deploy and configure SBX based on their own individual circumstances and needs.

When building SBX via the Dockerfile provided, Docker 17.05 minimum is required, due to the use of Docker build stages. During docker build, these are the stages invoked:
- java: installs Oracle Java on top of Ubuntu
- build: installs Maven on top of the java stage and compiles SBX
- final container: copies the SBX jar file using the java stage as base

The built container assumes the WORKDIR inside the container is /sbx/data: this means that the database directory will be written inside that directory by default. If a system administrator wants to retain the database across restarts, it is his/her job to mount a docker volume in the right folder.

The docker conatiner supports the env variables to configure advanced options. These variables can be set but are not required to run SBX.

`JAVA_OPTIONS`: these are the java options to pass right after the java command. It must not contain -Xms nor -Xmx. Defaults to a safe value
`JAVA_MIN_MEMORY`: the value of -Xms option. Defaults to 2G
`JAVA_MAX_MEMORY`: the value of -Xmx option. Defaults to 4G
`DOCKER_SBX_JAR_PATH`: defaults to /sbx/target/sbx*.jar as pushed by the Dockerfile. This is useful if custom SBX binaries want to be executed and the default path needs to be overridden
`DOCKER_SBX_REMOTE_LIMIT_API`: defaults to "interruptAttachToTangle, attachToTangle, addNeighbors, removeNeighbors, getNeighbors"
`DOCKER_SBX_MONITORING_API_PORT_ENABLE`: defaults to 0. If set to 1, a socat on port 14266 directed to 127.0.0.1:DOCKER_SBX_MONITORING_API_PORT_DESTINATION  will be open in order to allow all API calls regardless of the DOCKER_SBX_REMOTE_LIMIT_API setting. This is useful to give access to restricted API calls to local tools and still denying access to restricted API calls to the internet. It is highly recommended to use this option together with docker networks (docker run --net).

The container entry point is a shell script that performs few additional steps before launching SBX:
- verifies if `DOCKER_SBX_MONITORING_API_PORT_ENABLE` is set to 1
- launches SBX with all parameters passed as desired

It is important to note that other than --remote and --remote-limit-api "$DOCKER_SBX_REMOTE_LIMIT_API", neither the entrypoint nor the Dockerfile are aware of any SBX configuration option. This is to not tie the Dockerfile and its container to a specific set of SBX options. Instead, this contain still allows the use of an INI file or command line options. Please refer to the SBX documentation to learn what are the allowed options at command line and via the INI file.

**At the time of writing, SBX requires -p to be passed either via INI or via command line. The entrypoint of this docker container does not do that for you.**

Here is a systemd unit example you can use with this Docker container. This is just an example and customisation is possible and recommended. In this example the docker network sbx must be created and the paths /mnt/sbx/conf and /mnt/sbx/data are used on the docker host to serve respectively the neighbors file and the data directory. No INI files are used in this example, instead options are passed via command line options, such as --testnet and --zmq-enabled.

```
[Unit]
Description=SBX
After=docker.service
Requires=docker.service

[Service]
TimeoutStartSec=0
Restart=always
ExecStartPre=-/usr/bin/docker rm %n
ExecStart=/usr/bin/docker run \
--name %n \
--hostname sbx \
--net=sbx \
-v /mnt/sbx/conf:/sbx/conf \
-v /mnt/sbx/data:/sbx/data \
-p 14265:14265 \
-p 15600:15600 \
-p 14600:14600/udp  \
helixnetwork/sbx:v1.5.0 \
-p 14700 \
--zmq-enabled \
--testnet

ExecStop=/usr/bin/docker stop %n
ExecReload=/usr/bin/docker restart %n

[Install]
WantedBy=multi-user.target
```
