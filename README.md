[![Build Status](https://travis-ci.com/HelixNetwork/sbx.svg?token=uwTGeqrvzM3QBFSrvQb6&branch=master)](https://travis-ci.com/HelixNetwork/sbx)
![GitHub release](https://img.shields.io/github/release/helixnetwork/sbx.svg)
[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

# Helix Protocol
This is the testnet-1.0 implementation of the Helix Protocol based on [**IRI**](https://github.com/iotaledger/iri/).
* **Latest release:** 0.4.2 pre-release
* **License:** GPLv3

## Developers

- Please see the [CONTRIBUTING.md](https://github.com/HelixNetwork/testnet-1.0/blob/dev/CONTRIBUTING.md) and [STYLEGUIDE.md](https://github.com/HelixNetwork/testnet-1.0/blob/dev/STYLEGUIDE.md) if you wish to contribute to this repository!
- Please read and update the [testnet-1.0-specifications](https://github.com/HelixNetwork/helix-specs/blob/master/specs/testnet-1.0.md).
- You may enable auto-submission of milestones by passing the `-m`-flag and an integer for the delay.
- Test-Balance:
```
Seed#0: df36d3a5c687106be8c8880ce06117a302bd09fe88355cd4102b901ad9f76ec2
Addr#0: 556a2431d03e57e92b7d4d4d37f98332fce5427d8167e16c0a5cfbe20899d261

Seed#1: 7b6cc72ce82f3e1369b3e62bfc9607853ae607d352de4110a93645d575898bc6
Addr#1: 196a2095205189ad2aa77c1125fc9e5d9c4888fb307a4a16caca6f6d311036e7

Seed#2: 462813e2e99aeb25e94fba849af07bf8927e3a81911c16359f87a6cef1a960c2
Addr#2: 9ac84e8c4df3e51e78f088b2f51408c97333ad982313101cac14ccb03f137e1f

Seed#3: e2ccb4dbaffc70b02f0d1c14bc1214cc2833d157ddcddb8179014bd593861aa1
Addr#3: b662e011dae0a5a554281efc7d858894d797fe92b292c5a29c8d3b2ce648aae0
```
These addresses hold value, you may use the corresponding seeds to issue value-transfers.

## Installing   
Make sure you have [**Maven**](https://maven.apache.org/) and [**Java 8**](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) installed on your computer.

### Download
```
$ git clone https://github.com/HelixNetwork/sbx.git
```
### Compile
```
$ cd testnet-1.0
$ mvn clean compile
$ mvn package
```

This will create a `target` directory in which you will find the executable jar file that you can use for the

### Launch

```
java -jar target/testnet-<VERSION>.jar -p 14700
```

With auto-milestone submission:
```
java -jar target/testnet-<VERSION>.jar -p 14700 -m 30
```
**NOTICE**: The `-m` flag is only temporary and will be removed in future updates. Its current purpose is to ease the process of submitting milestones as a developer. spment purposes
If you are running a node with the milestone flag, you should consider limiting the api.

## CLI

Option | Shortened version | Description | Example Input
--- | --- | --- | ---
`--port` | `-p` | This is a *mandatory* option that defines the port to be used to send API commands to your node | `-p 14800`
`--neighbors` | `-n` | Neighbors that you are connected with will be added via this option. | `-n "udp://148.148.148.148:14700 udp://[2001:db8:a0b:12f0::1]:14700"`
`--config` | `-c` | Config INI file that can be used instead of CLI options. See more below | `-c sbx.ini`
`--udp-receiver-port` | `-u` | UDP receiver port | `-u 14800`
`--tcp-receiver-port` | `-t` | TCP receiver port | `-t 14800`
`--ms-delay`| `-m` | Sets delay for auto-milestones. | `-m 60`
`--testnet` | | Makes it possible to run HCP with the HELIX testnet | `--testnet`
`--remote` | | Remotely access your node and send API commands | `--remote`
`--remote-auth` | | Require authentication password for accessing remotely. Requires a correct `username:hashedpassword` combination | `--remote-auth helixtoken:LL9EZFNCHZCMLJLVUBCKJSWKFEXNYRHHMYS9XQLUZRDEKUUDOCMBMRBWJEMEDDXSDPHIGQULENCRVEYMO`
`--remote-limit-api` | | Exclude certain API calls from being able to be accessed remotely | `--remote-limit-api "attachToTangle, addNeighbors"`
`--send-limit`| | Limit the outbound bandwidth consumption. Limit is set to mbit/s | `--send-limit 1.0`
`--max-peers` | | Limit the number of max accepted peers. Default is set to 0 (mutual tethering) | `--max-peers 8`
`--dns-resolution-false` | | Ignores DNS resolution refreshing  | `-dns-resolution-false`
## INI

You can also provide an ini file to store all of your command line options and easily update (especially neighbors) if needed. You can enable it via the `--config` flag. Here is an example INI file:
```
[SBX]
PORT = 14700
UDP_RECEIVER_PORT = 14700
NEIGHBORS = udp://my.favorite.com:15600
HXI_DIR = hxi
HEADLESS = true
DEBUG = true
DB_PATH = db
ZMQ_ENABLED = true
MS_DELAY = 30
```

## MessageQ

This is a **work in progress**. Things will change.

MessageQ is a small wrapper for ZeroMQ inside HCP to allow streaming
of topics from within a running full node. The goal of this is to allow
for targeted event streams from subscribing clients to the node process.

A client may want to be notified of a change in status of a transaction,
or may want to see incoming transactions, or any number of data points.
These can be filtered by topic, and the aim is for machine-readability
over human readability.

For instance, a light wallet connected to a remote node may want to know
when a transaction is confirmed. It would, perhaps, after querying the API,
subscribe to a topic which publishes on the update of a state.

#### Topics

A client interested in tip selection metrics may subscribe to `mctn`, short for
"monte carlo transaction number", a metric that indicates how many transactions
were traversed in a random walk simulation. It may subscribe to `rts`, for
"reason to stop", to see information about walk terminations.

Other topics currently found in the latest code are
* `dns` for information related to neighbors
* `hmr` for the hit to miss ratio
* `antn` for added non-tethered neighbors ( testnet only )
* `rntn` for refused non-tethered neighbors
* `rtl` for transactions randomly removed from the request list
* `lmi` for the latest milestoneTracker index
* `lmsi` for the latest solid milestoneTracker index
* `lmhs` for the latest solid milestoneTracker hash
* `sn` for newly confirmed transactions ( by solid milestoneTracker children measurement )
* `tx` for newly seen transactions
* `ct5m2h` confirmed transactions older than 5m and younger than 2h
* `t5m2h` total transactions older than 5m and younger than 2h

* `<Address>` to watch for an address to be confirmed

All topic must be lowercase (to not clash with `<Address>` containing the topic title - like `TXCR9...` & `TX`)
All of these topics are subject to change, and more may be added; this is experimental code.

## API
The [**Helix Library**](https://github.com/helixnetwork/helix.api) wraps the primitive api commands, whilst providing the mandatory cryptography to locally sign a transaction and typically do proof of work.
As the latest build is still being tested, you can preliminarily send http requests using [cURL]().  
### getNodeInfo
**Command**
```
curl http://localhost:14700 \
  -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-HELIX-API-Version: 1' \
  -d '{"command": "getNodeInfo"}'
```

**Response**
```
{
  "appName":"SBX",
  "appVersion":"0.3.4",
  "jreAvailableProcessors":8,
  "jreFreeMemory":231182400,
  "jreVersion":"1.8.0_171",
  "jreMaxMemory":3817865216,
  "jreTotalMemory":257425408,
  "latestMilestone":"0000f13be306d278fae139dc4a54deb40389a8d1c3677a872a9a198f57aad343",
  "latestMilestoneIndex":6,
  "latestSolidSubtangleMilestone":"0000f13be306d278fae139dc4a54deb40389a8d1c3677a872a9a198f57aad343",
  "latestSolidSubtangleMilestoneIndex":6,
  "milestoneStartIndex":0,
  "neighbors":0,
  "packetsQueueSize":0,
  "time":1543586964422,
  "tips":0,
  "transactionsToRequest":0,
  "features":["snapshotPruning","dnsRefresher"],
  "coordinatorAddress":"a3fcb75bbfc68db05a5207c2afc97fc496ec86e7ecdd6a933be4d1bad8f74c34",
  "duration":9
}
```

### getNeighbors
**Command**
```
curl http://localhost:14700 \
  -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-HELIX-API-Version: 1' \
  -d '{"command": "getNeighbors"}'
```
**Response**
```
{
  "neighbors":[],
  "duration":0
}
```
### addNeighbors
**Command**
```
curl http://localhost:14700 \
  -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-HELIX-API-Version: 1' \
  -d '{"command": "addNeighbors", "uris": ["udp://8.8.8.8:14700", "udp://8.8.8.5:14700"]}'
```
**Response**
```
{
  "addedNeighbors": 0,
  "duration": 2
}
```
### removeNeighbors
**Command**
```
curl http://localhost:14700 \
  -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-HELIX-API-Version: 1' \
  -d '{"command": "removeNeighbors", "uris": ["udp://8.8.8.8:14700", "udp://8.8.8.5:14700"]}'
```
**Response**
```
{
  "removedNeighbors": 0,
  "duration": 2
}
```
### getTips
**Command**
```
curl http://localhost:14700 \
  -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-HELIX-API-Version: 1' \
  -d '{"command": "getTips"}'
```
**Response**
```
{
  "hashes":["0000f13be306d278fae139dc4a54deb40389a8d1c3677a872a9a198f57aad343", "000006ce531ddf7d7bea39274dac4ba7ee1402ffad754cc68471fc59f9a114aab91174116de83fe0919382c1e587284028da9d7774246cf728de44284c65a6b3"],
  "duration":2
}
```
### findTransactions
**Command**
```
curl http://localhost:14700 \
  -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-HELIX-API-Version: 1' \
  -d '{"command": "findTransactions", "addresses": ["a3fcb75bbfc68db05a5207c2afc97fc496ec86e7ecdd6a933be4d1bad8f74c34"]}'
```
**Response**
```
{
 "hashes":["00006c409915cfd8c16b5f9be2f9108966716f7bf5ea64794f92e7ad9d4b0ac8",
           "0000850174174f28682c09835bd2ae5f18157340a6740c35203361c21eaf81b0",
           "00001a8e1d781318c196df2843d32d5ea2674806c92a26faabe6d1e277fdb143",
           "0000883463bdfba5795ece73d200fa95a7617ff61e43f058297380f8e373dbaa",
           "0000809b36c217e3d00ceffe97302157a885ed28b1a4bc66edd28fdce13004c8",
           "0000f13be306d278fae139dc4a54deb40389a8d1c3677a872a9a198f57aad343"],
 "duration":13
}
```
### getHBytes
**Command**
```
curl http://localhost:14700 \
  -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-HELIX-API-Version: 1' \
  -d '{"command": "getHBytes", "hashes": ["0000f13be306d278fae139dc4a54deb40389a8d1c3677a872a9a198f57aad343"]}'
```
**Response**
```
{
 "hbytes":["0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a3fcb75bbfc68db05a5207c2afc97fc496ec86e7ecdd6a933be4d1bad8f74c34000000000000000000000000000000000000000000000000000000000000000000000000000000000000016764f23c2900000000000000000000000000000000051323892969e8e6d9ee971a79133e3691e1fcee6e133e07e3a87508755f44d30000883463bdfba5795ece73d200fa95a7617ff61e43f058297380f8e373dbaa0000883463bdfba5795ece73d200fa95a7617ff61e43f058297380f8e373dbaa00000000000000060000016764f23c2a0000000000000000000000000000007f94a99460b908004d000000000000000000000000000000000000000000000000"],
 "duration":0
}

```
### getInclusionStates
**Command**
```
curl http://localhost:14700 \
  -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-HELIX-API-Version: 1' \
  -d '{"command": "getInclusionStates", "transactions": ["00001a8e1d781318c196df2843d32d5ea2674806c92a26faabe6d1e277fdb143"], "tips": ["00006c409915cfd8c16b5f9be2f9108966716f7bf5ea64794f92e7ad9d4b0ac8"]}'
```
**Response**
```
{
 "states":[false],"duration":2
}
```
### getBalances
**Command**
```
curl http://localhost:14700 \
  -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-HELIX-API-Version: 1' \
  -d '{"command": "getBalances", "addresses": ["d0e7e549a4ffe5b4f8343973f0237db9ede3597baced22715c22dcd8c76ae738"], "threshold": 100}'
```
**Response**
```
{
  "balances":["536561674354688"],
  "references":["0000cfddd53dc766df017ebbf6691c4b618e86832f2a97e9c59cf1176ffaeef0"],
  "milestoneIndex":2,
  "duration":20
}
```
### getTransactionsToApprove
**Command**
```
curl http://localhost:14700 \
  -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-HELIX-API-Version: 1' \
  -d '{"command": "getTransactionsToApprove", "depth": 15, "reference": "0000850174174f28682c09835bd2ae5f18157340a6740c35203361c21eaf81b0"}'
```
**Response**
```
{
 "trunkTransaction":"0000f13be306d278fae139dc4a54deb40389a8d1c3677a872a9a198f57aad343",
 "branchTransaction":"0000cfddd53dc766df017ebbf6691c4b618e86832f2a97e9c59cf1176ffaeef0",
 "duration":5
}
```
### attachToTangle
**Command**
```
curl http://localhost:14700 \
  -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-HELIX-API-Version: 1' \
  -d '{"command": "attachToTangle", "trunkTransaction": "0000f13be306d278fae139dc4a54deb40389a8d1c3677a872a9a198f57aad343", "branchTransaction": "0000850174174f28682c09835bd2ae5f18157340a6740c35203361c21eaf81b0", "minWeightMagnitude": 2, "hbytes": ["0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000005bdd83fd000000000000000100000000000000027aeb69d7e585744a473e82a61fe6fab06b130757ccd329409f56aff1449e1f8400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000feff0036003600360000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"]}'
```
**Response**
```
{
 "hbytes":["0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000005bdd83fd000000000000000100000000000000027aeb69d7e585744a473e82a61fe6fab06b130757ccd329409f56aff1449e1f840000f13be306d278fae139dc4a54deb40389a8d1c3677a872a9a198f57aad3430000850174174f28682c09835bd2ae5f18157340a6740c35203361c21eaf81b0feff0036003600360000016765232f7f0000000000000000000000000000007faad370633bb3186e000000000000000000000000000000000000000000000000"],
 "duration":1388
}
```
### interruptAttachingToTangle
**Command**
```
curl http://localhost:14700 \
  -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-HELIX-API-Version: 1' \
  -d '{"command": "interruptAttachingToTangle"}'
```
**Response**
```
{
 "duration":1
}
```
### broadcastTransactions
**Command**
```
curl http://localhost:14700 \
  -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-HELIX-API-Version: 1' \
  -d '{"command": "broadcastTransactions", "hbytes": ["0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000005bdd83fd000000000000000100000000000000027aeb69d7e585744a473e82a61fe6fab06b130757ccd329409f56aff1449e1f840000f13be306d278fae139dc4a54deb40389a8d1c3677a872a9a198f57aad3430000850174174f28682c09835bd2ae5f18157340a6740c35203361c21eaf81b0feff0036003600360000016765232f7f0000000000000000000000000000007faad370633bb3186e000000000000000000000000000000000000000000000000"]}'
```
**Response**
```
{
 "duration":1005
}
```
### storeTransactions
**Command**
```
curl http://localhost:14700 \
  -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-HELIX-API-Version: 1' \
  -d '{"command": "storeTransactions", "hbytes": ["0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000005bdd83fd000000000000000100000000000000027aeb69d7e585744a473e82a61fe6fab06b130757ccd329409f56aff1449e1f840000f13be306d278fae139dc4a54deb40389a8d1c3677a872a9a198f57aad3430000850174174f28682c09835bd2ae5f18157340a6740c35203361c21eaf81b0feff0036003600360000016765232f7f0000000000000000000000000000007faad370633bb3186e000000000000000000000000000000000000000000000000"]}'
```
**Response**
```
{
 "duration":1017
 }
```
