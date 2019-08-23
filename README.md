<!-- [![doc][1]][2] ![GitHub release][3] [![matrix][12]][13] -->

[![license][4]][5] [![build][6]][7] [![grade][8]][9] [![coverage][10]][11] [![discord][14]][15]

# Helix-1.0

A Quorum based Tangle implementation forked from [**IRI**](https://github.com/iotaledger/iri/).

-   **Latest release:** 0.5.9 pre-release
-   **License:** GPLv3

Special thanks to all of the [IOTA Contributors](https://github.com/iotaledger/iri/graphs/contributors)!

## Developers

-   Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests to us.
-   Please read the [helix-1.0-specifications](https://github.com/HelixNetwork/helix-specs/blob/master/specs/helix-1.0.md) before contributing.

## Installing

Make sure you have [**Maven**](https://maven.apache.org/) and [**Java 8**](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) installed on your computer.

### Download

    $ git clone https://github.com/HelixNetwork/helix-1.0.git

### Build

Build an executable jar at the `target` directory using maven.

    $ cd helix-1.0
    $ mvn clean package

### Launch

    java -jar target/helix-<VERSION>.jar -p 8085

## Configuration

| Option                   | Short | Description                                                                                                                                 | Example Input                                                       |
| ------------------------ | ----- | ------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| `--port`                 | `-p`  | This is a _mandatory_ option that defines the port to be used to send API commands to your node                                             | `-p 8085`                                                           |
| `--neighbors`            | `-n`  | Neighbors that you are connected with will be added via this option.                                                                        | `-n "udp://148.148.148.148:4100 udp://[2001:db8:a0b:12f0::1]:4100"` |
| `--config`               | `-c`  | Config INI file that can be used instead of CLI options. See more below                                                                     | `-c x.ini`                                                          |
| `--udp-receiver-port`    | `-u`  | UDP receiver port                                                                                                                           | `-u 4100`                                                           |
| `--tcp-receiver-port`    | `-t`  | TCP receiver port                                                                                                                           | `-t 5100`                                                           |
| `--ms-delay`             | `-m`  | Sets delay for auto-milestones.                                                                                                             | `-m 60`                                                             |
| `--testnet`              |       | Testnet flag, bypasses milestone signature validation and pow difficulty.                                                                   | `--testnet`                                                         |
| `--remote`               |       | Remotely access your node and send API commands                                                                                             | `--remote`                                                          |
| `--remote-auth`          |       | Require authentication password for accessing remotely. Requires a correct `username:hashedpassword` combination passed to the Auth Header. | `--remote-auth helixtoken:<your_token>`                             |
| `--remote-limit-api`     |       | Exclude certain API calls from being able to be accessed remotely                                                                           | `--remote-limit-api "attachToTangle, addNeighbors"`                 |
| `--send-limit`           |       | Limit the outbound bandwidth consumption. Limit is set to mbit/s                                                                            | `--send-limit 1.0`                                                  |
| `--max-peers`            |       | Limit the number of max accepted peers. Default is set to 0.                                                                                | `--max-peers 8`                                                     |
| `--dns-resolution-false` |       | Ignores DNS resolution refreshing                                                                                                           | `--dns-resolution-false`                                            |
| `--savelog-enabled`      |       | Writes the log to file system                                                                                                               | `--savelog-enabled`                                                 |
| `--pow-disabled`         |       | Disables searching and validation of nonce. A feature for simnet.                                                                           | `--pow-disabled`                                                    |

### INI

You can also provide an ini file to store all of your command line options and easily update (especially neighbors) if needed. You can enable it via the `--config` flag. Here is an example INI file:

```text
[HLX]
PORT = 8085
UDP_RECEIVER_PORT = 4100
NEIGHBORS = udp://my.favorite.com:5100
HXI_DIR = XI
HEADLESS = true
DEBUG = true
DB_PATH = db
ZMQ_ENABLED = true
```

## MessageQ

MessageQ is a small zmq wrapper for streaming gathered metrics and statistics of topics, enabling targeted event streams from subscribing clients to processes of the node.
A client interested in real time state updates and notifications could use any desired [zmq-client](https://github.com/zeromq/zeromq.js/) to start listening to topics.

Currently the following topics are covered:

| Topic       | Description                                                             |
| ----------- | ----------------------------------------------------------------------- |
| `dns`       | Neighbor related info                                                   |
| `hmr`       | Hit/miss ration                                                         |
| `antn`      | Added non-tethered neighbors (testnet only)                             |
| `rntn`      | Refused non-tethered neighbors                                          |
| `rtl`       | for transactions randomly removed from the request list                 |
| `lmi`       | Latest solid milestone index                                            |
| `lmhs`      | Latest solid milestone hash                                             |
| `sn`        | Uses solid milestone's child measurement to publish newly confirmed tx. |
| `tx`        | Newly seen transactions                                                 |
| `ct5s2m`    | Confirmed transactions older than 5s and younger than 2m                |
| `t5s2m`     | total transactions older than 5s and younger than 2m                    |
| `<Address>` | Watching all traffic on a specified address                             |

<!-- [1]: https://javadoc-badge.appspot.com/helixnetwork/helix-1.0.svg?label=javadocs -->

<!-- [2]: https://javadoc-badge.appspot.com/helixnetwork/helix-1.0 -->

<!-- [3]: https://img.shields.io/github/release/helixnetwork/helix-1.0.svg -->

<!-- [12]: https://img.shields.io/matrix/helixnetwork:matrix.org.svg?label=matrix -->

<!-- [13]: https://riot.im/app/#/room/#helixnetwork:matrix.org -->

[4]: https://img.shields.io/badge/License-GPLv3-blue.svg

[5]: LICENSE

[6]: https://travis-ci.com/HelixNetwork/helix-1.0.svg?token=iyim5S8NXU1bnHDx8VMr&branch=master

[7]: https://travis-ci.com/HelixNetwork/helix-1.0

[8]: https://api.codacy.com/project/badge/Grade/0756a1f4690c453e99da9e242695634d

[9]: https://www.codacy.com?utm_source=github.com&utm_medium=referral&utm_content=HelixNetwork/helix-1.0&utm_campaign=Badge_Grade

[10]: https://codecov.io/gh/helixnetwork/helix-1.0/branch/dev/graph/badge.svg?token=0IRQbGplCg

[11]: https://codecov.io/gh/helixnetwork/helix-1.0

[14]: https://img.shields.io/discord/410771391600656395.svg?label=discord

[15]: https://discord.gg/PjAKR8q
