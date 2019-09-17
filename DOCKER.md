# Docker

To run a Helix docker container you have two options:

-   Build your own image using the provided dockerfile
-   Run container from provided dockerhub image

Both options require that you have [docker](https://www.docker.com/get-started) (>=17.05) installed on your machine.

The provided dockerfile only contains the bare minimum of configuration parameters, as to enable higher degree of customization in terms of configuration and deployment to the node operator.

**Build stages**:

1.  java: installs Oracle Java on top of Ubuntu
2.  build: installs Maven on top of the java stage and compiles Helix
3.  final container: copies the helix jar file using the java stage as base

The built container assumes the WORKDIR inside the container is /helix/data: this means that the database directory will be written inside that directory by default. If a system administrator wants to retain the database across restarts, it is his/her job to mount a docker volume in the right folder

## Getting Started

This section will cover usage information for the provided docker container.

### Prerequisities

In order to run this container you'll need docker installed.

-   [Windows](https://docs.docker.com/windows/started)
-   [OS X](https://docs.docker.com/mac/started/)
-   [Linux](https://docs.docker.com/linux/started/)

### Example

```shell
sudo docker run helixnetwork/helix:latest -p 8085
```

This will run the helix with its API listening on port 8085, with no peers and a fresh database.
The helix docker container is configured to read data from /helix/data. Use the -v option of the docker run command to mount volumes so to have persistent data.
You can also pass more command line options to the docker run command and those will be passed to Helix. Please refer to the [README.md](<>) for all command line and ini options.

### Load options from INI

If you want to use a `<conf_name>`.ini file with the docker container, supposing it's stored under /path/to/conf/`<conf_name>`.ini on your docker host, then pass -v /path/to/conf:/helix/conf and add -c /helix/conf/`<conf_name>`.ini as docker run arguments. So for example the docker run command above would become:

```shell
docker run -v /path/to/conf:/helix/conf -v /path/to/data:/helix/data helixnetwork/helix:latest -p 8085 -c /helix/conf/<conf_name>.ini
```

## Security

Helix-1.0 should be run as a non-administrative user with no root privileges! An unprivileged user can be created on the host and the UID passed to the docker command (e.g. --user 1001). Directories that are mounted to the container from the host should be owned by this user. In addition the --cap-drop=ALL passed to docker restricts process capabilities and adheres to the principle of least privilege. See [runtime-privilege-and-linux-capabilities](https://docs.docker.com/engine/reference/run/#runtime-privilege-and-linux-capabilities) for more information.
