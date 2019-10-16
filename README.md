<!-- [![doc][1]][2] [![matrix][12]][13] -->

![GitHub release][3] [![build][6]][7] [![license][4]][5] [![grade][8]][9] [![coverage][10]][11] [![discord][14]][15]

# Pendulum

Pendulum is a quorum based [Tangle](https://github.com/iotaledger/iri/) implementation designed towards reliable timekeeping and high-throughput messaging.

-   **Latest release:** 1.0.3 release
-   **License:** GPLv3

Special thanks to all of the [IOTA Contributors](https://github.com/iotaledger/iri/graphs/contributors)!

## Hardware requirements

**Minimal** (~t2.small AWS instance)
  - 2GB RAM
  - 1 GHz CPU
  - 10 GB storage
  - 10Mbit/s WAN, static IP

**Optimal** (~t2.medium AWS instance)
  - 4GB RAM or more
  - 2 or more 2GHz CPU cores (~ t2.medium AWS instance)
  - 50GB SSD
  - 1Gbit/s WAN, static IP

**Enterprise-grade**
  - Four or more instances with Optimal specs
    - two or more instances with `--remote` API enabled
    - two or more "relayer" instances connected to multiple peers  
  - For validators: additional dedicated instance with Optimal specification for the validator node
  - HA loadbalancer proxing API instances. Can be hardware or software based (e.g. [Nginx cluster sample config](#nginx-cluster-sample-config) below)


## Developers

-   Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests to us.
-   Please read the [pendulum-1.0-specifications](https://github.com/HelixNetwork/helix-specs/tree/master/specs/1.0) before contributing.

## Installing

Make sure you have [**Maven**](https://maven.apache.org/) and [**Java 8**](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) installed on your computer.

### Download

    $ git clone https://github.com/HelixNetwork/pendulum.git

### Build

Build an executable jar at the `target` directory using maven.

    $ cd pendulum
    $ mvn clean package

### Launch Full node

    java -jar target/pendulum-<VERSION>.jar -p 8085

### Launch Validator node
Launching a node as a validator first requires to generate a 64 character hex string, that is used as a seed for key generation. You will find the public key in the last line of the `validator.key` file contained in the resources directory. If you wish to act as a validator, please send a request to dt@hlx.ai containing your public key.

    java -jar target/pendulum-<VERSION>.jar -p 8085 --validator <pathToValidatorSeed>


### Nginx cluster sample config

For production-level applications we recommend exposing a single public API endpoint reverse-proxing multiple fullnode instances. Additionally, we highly recommend obtaining an SSL certificate from a trusted authority (e.g. from Let’s Encrypt).

Below is a sample configuration file for the popular Nginx webserver (typically put into `/etc/nginx/conf.d/` ). For more information please consult the official Nginx [documentation](https://docs.nginx.com/nginx/admin-guide/web-server/reverse-proxy/)

```
upstream pendulum {
        ip_hash;
        Server fullnode1.ip.address:8085;
        Server fullnode2.ip.address:8085;
}


server {
        listen 443 ssl;
        listen [::]:443 ssl;
        server_name my.api.endpoint.com;

        server_tokens off;

        ssl_certificate /path/to/cert.pem;
        ssl_certificate_key /path/to/key.pem;
        ssl_session_timeout 5m;
        ssl_protocols TLSv1 TLSv1.1 TLSv1.2;
        ssl_ciphers 'EECDH+AESGCM:EDH+AESGCM:AES256+EECDH:AES256+EDH';
        ssl_prefer_server_ciphers on;

        ssl_ecdh_curve secp384r1;
        ssl_session_tickets off;

        # OCSP stapling
        ssl_stapling on;

        ssl_stapling_verify on;
        resolver 8.8.8.8;


        location / {
                ## CORS
                proxy_hide_header Access-Control-Allow-Origin;
                add_header 'Access-Control-Allow-Origin' '*' always;
                add_header 'Access-Control-Allow-Credentials' 'true';
                add_header 'Access-Control-Allow-Headers' 'Authorization,Accept,Origin,DNT,X-HELIX-API-Version,Keep-Alive,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Content-Range,Range';
                add_header 'Access-Control-Allow-Methods' 'GET,POST,OPTIONS,PUT,DELETE,PATCH';

                if ($request_method = 'OPTIONS') {
                        add_header 'Access-Control-Allow-Origin' '*';
                        add_header 'Access-Control-Allow-Credentials' 'true';
                        add_header 'Access-Control-Allow-Headers' 'Authorization,Accept,Origin,DNT,X-HELIX-API-Version,Keep-Alive,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Content-Range,Range';
                        add_header 'Access-Control-Allow-Methods' 'GET,POST,OPTIONS,PUT,DELETE,PATCH';
                        add_header 'Access-Control-Max-Age' 1728000;
                        add_header 'Content-Type' 'text/plain charset=UTF-8';
                        add_header 'Content-Length' 0;
                        return 204;
                }

                proxy_redirect off;
                proxy_set_header host $host;
                proxy_set_header X-real-ip $remote_addr;
                proxy_set_header X-forward-for $proxy_add_x_forwarded_for;
                proxy_set_header X-Forwarded-Proto $scheme;
                proxy_pass http://pendulum;
        }
}

```


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
| `--remote-auth`          |       | Require authentication password for accessing remotely. Requires a correct `username:hashedpassword` combination passed to the Auth Header. | `--remote-auth token:<your_token>`                             |
| `--remote-limit-api`     |       | Exclude certain API calls from being able to be accessed remotely                                                                           | `--remote-limit-api "attachToTangle, addNeighbors"`                 |
| `--send-limit`           |       | Limit the outbound bandwidth consumption. Limit is set to mbit/s                                                                            | `--send-limit 1.0`                                                  |
| `--max-peers`            |       | Limit the number of max accepted peers. Default is set to 0.                                                                                | `--max-peers 8`                                                     |
| `--dns-resolution-false` |       | Ignores DNS resolution refreshing                                                                                                           | `--dns-resolution-false`                                            |
| `--savelog-enabled`      |       | Writes the log to file system                                                                                                               | `--savelog-enabled`                                                 |                                                                      | `--pow-disabled`                                                    |
| `--validator`            |       | Flag that enables applying as a validator in the network. A path to a file containing the seed has to be passed.                                                                                                                | `--savelog-enabled`                                                 |                                                                      | `--pow-disabled`                                                    |
| `--update-validator`     |       | The desired delay for updating validators in seconds.                                                                                                            | `--savelog-enabled`                                                 |                                                                      | `--pow-disabled`                                                    |
| `--start-validator`      |       | The number of rounds between validators are published and the round they start to operate.                                                                                                                | `--savelog-enabled`                                                 |                                                                      | `--pow-disabled`                                                    |
| `--genessis`             |       | Time when the ledger started.                                                                                                                | `--savelog-enabled`                                                 |                                                                      | `--pow-disabled`                                                    |
| `--round`                |       | Duration of a round in milli secounds.                                                                                                                | `--savelog-enabled`                                                 |                                                                      | `--pow-disabled`                                                    |
| `--round-pause`          |       | Duration of time to finalize the round in milli secounds.                                                                                                                | `--savelog-enabled`                                                 |                                                                      | `--pow-disabled`                                                    |
                                                                                                          | `--dns-resolution-false`     |


### INI

You can also provide an ini file to store all of your command line options and easily update (especially neighbors) if needed. You can enable it via the `--config` flag. Here is an example INI file:

```text
[HLX]
PORT = 8085
UDP_RECEIVER_PORT = 4100
NEIGHBORS = udp://my.favorite.com:5100
HXI_DIR = XI
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

[3]: https://img.shields.io/github/release/helixnetwork/pendulum.svg 

<!-- [12]: https://img.shields.io/matrix/helixnetwork:matrix.org.svg?label=matrix -->

<!-- [13]: https://riot.im/app/#/room/#helixnetwork:matrix.org -->

[4]: https://img.shields.io/badge/License-GPLv3-blue.svg

[5]: LICENSE

[6]: https://travis-ci.com/HelixNetwork/pendulum.svg?token=iyim5S8NXU1bnHDx8VMr&branch=master

[7]: https://travis-ci.com/HelixNetwork/pendulum

[8]: https://api.codacy.com/project/badge/Grade/f90eeaff3b1c4e9fb324c74100ed7b3a

[9]: https://www.codacy.com?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=HelixNetwork/pendulum&amp;utm_campaign=Badge_Grade

[10]: https://codecov.io/gh/HelixNetwork/pendulum/branch/dev/graph/badge.svg

[11]: https://codecov.io/gh/HelixNetwork/pendulum

[14]: https://img.shields.io/discord/410771391600656395.svg?label=discord

[15]: https://discord.gg/PjAKR8q
