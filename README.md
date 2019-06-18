<!-- [![doc][1]][2] ![GitHub release][3] [![matrix][12]][13] -->
[![license][4]][5] [![build][6]][7] [![grade][8]][9] [![coverage][10]][11] [![discord][14]][15] 

[1]: https://javadoc-badge.appspot.com/helixnetwork/testnet-1.0.svg?label=javadocs
[2]: https://javadoc-badge.appspot.com/helixnetwork/testnet-1.0
[3]: https://img.shields.io/github/release/helixnetwork/testnet-1.0.svg
[4]: https://img.shields.io/badge/License-GPLv3-blue.svg
[5]: LICENSE
[6]: https://travis-ci.com/HelixNetwork/testnet-1.0.svg?token=iyim5S8NXU1bnHDx8VMr&branch=master
[7]: https://travis-ci.com/HelixNetwork/testnet-1.0
[8]: https://api.codacy.com/project/badge/Grade/0756a1f4690c453e99da9e242695634d
[9]: https://www.codacy.com?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=HelixNetwork/testnet-1.0&amp;utm_campaign=Badge_Grade
[10]: https://codecov.io/gh/helixnetwork/testnet-1.0/branch/dev/graph/badge.svg?token=0IRQbGplCg
[11]: https://codecov.io/gh/helixnetwork/testnet-1.0
[12]: https://img.shields.io/matrix/helixnetwork:matrix.org.svg?label=matrix
[13]: https://riot.im/app/#/room/#helixnetwork:matrix.org
[14]: https://img.shields.io/discord/410771391600656395.svg?label=discord
[15]: https://discord.gg/PjAKR8q

# Helix-1.0
This is the 1.0 implementation of the Helix Protocol based on [**IRI**](https://github.com/iotaledger/iri/).
* **Latest release:** 0.5.6 pre-release
* **License:** GPLv3

## Developers

- Please see the [CONTRIBUTING.md](https://github.com/HelixNetwork/testnet-1.0/blob/dev/CONTRIBUTING.md) and [STYLEGUIDE.md](https://github.com/HelixNetwork/testnet-1.0/blob/dev/STYLEGUIDE.md) if you wish to contribute to this repository!
- Please read the [testnet-1.0-specifications](https://github.com/HelixNetwork/helix-specs/blob/master/specs/testnet-1.0.md) before contributing.
- Disable proof of work in the course of testing by passing `--pow-disabled` flag.

## Installing   
Make sure you have [**Maven**](https://maven.apache.org/) and [**Java 8**](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) installed on your computer.

### Download
```
$ git clone https://github.com/HelixNetwork/testnet-1.0.git
```
### Build
Build an executable jar at the `target` directory using maven.
```
$ cd testnet-1.0
$ mvn clean package
```

### Launch
```
java -jar target/helix-<VERSION>.jar -p 8085
```

## Configuration
Option | Shortened version | Description | Example Input
--- | --- | --- | ---
`--port` | `-p` | This is a *mandatory* option that defines the port to be used to send API commands to your node | `-p 8085`
`--neighbors` | `-n` | Neighbors that you are connected with will be added via this option. | `-n "udp://148.148.148.148:4100 udp://[2001:db8:a0b:12f0::1]:4100"`
`--config` | `-c` | Config INI file that can be used instead of CLI options. See more below | `-c x.ini`
`--udp-receiver-port` | `-u` | UDP receiver port | `-u 4100`
`--tcp-receiver-port` | `-t` | TCP receiver port | `-t 5100`
`--ms-delay`| `-m` | Sets delay for auto-milestones. | `-m 60`
`--testnet` | | Testnet flag, bypasses milestone signature validation and overly high mwm. | `--testnet`
`--remote` | | Remotely access your node and send API commands | `--remote`
`--remote-auth` | | Require authentication password for accessing remotely. Requires a correct `username:hashedpassword` combination passed to the Auth Header. | `--remote-auth helixtoken:a3fcb75bbfc68db05a5207c2afc97fc496ec86e7ecdd6a933be4d1bad8f74c34`
`--remote-limit-api` | | Exclude certain API calls from being able to be accessed remotely | `--remote-limit-api "attachToTangle, addNeighbors"`
`--send-limit`| | Limit the outbound bandwidth consumption. Limit is set to mbit/s | `--send-limit 1.0`
`--max-peers` | | Limit the number of max accepted peers. Default is set to 0 (mutual tethering) | `--max-peers 8`
`--dns-resolution-false` | | Ignores DNS resolution refreshing  | `--dns-resolution-false`
`--savelog-enabled` | | Writes the log to file system | `--savelog-enabled`
`--pow-disabled` | | Disables searching and validation of nonce. A feature for simnet. | `--pow-disabled`

### INI
You can also provide an ini file to store all of your command line options and easily update (especially neighbors) if needed. You can enable it via the `--config` flag. Here is an example INI file:
```
[HLX]
PORT = 8085
UDP_RECEIVER_PORT = 4100
NEIGHBORS = udp://my.favorite.com:5100
HXI_DIR = hxi
HEADLESS = true
DEBUG = true
DB_PATH = db
ZMQ_ENABLED = true
```

## MessageQ

MessageQ is a small zmq wrapper for streaming gathered metrics and statistics of topics, enabling targeted event streams from subscribing clients to processes of the node.
A client interested in real time state updates and notifications could use any desired [zmq-client](https://github.com/zeromq/zeromq.js/) to start listening to topics.

Currently the following topics are covered:

| Topic      | Description                     | Tested?|
| ---------- | ------------------------------- | ------ |
| `dns`      | Neighbor related info           |✓|
| `hmr`      | Hit/miss ration                 |✖|
| `antn`     | Added non-tethered neighbors (testnet only) |✖|
| `rntn`     | Refused non-tethered neighbors  |✖|
| `rtl`      | for transactions randomly removed from the request list|✖|
| `lmi`      | Latest solid milestone index    |✓|
| `lmhs`     | Latest solid milestone hash     |✓|
| `sn`       | Uses solid milestone's child measurement to publish newly confirmed tx.|✓|
| `tx`       | Newly seen transactions         |✓|
| `ct5s2m`   | Confirmed transactions older than 5s and younger than 2m|✓|
| `t5s2m`    | total transactions older than 5s and younger than 2m|✓|
| `<Address>`| Watching all traffic on a specified address|✓|
