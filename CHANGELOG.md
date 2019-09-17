## 0.6.1
-   Curator-less implementation
-   Set initial nominees
-   Set testnet genesis time

## 0.6.0
-   Finality Update integration. For more info see the [specifications](https://github.com/HelixNetwork/helix-specs/blob/master/specs/1.0/finality.md).

## 0.5.9

-   Added security levels according to specifications
-   Added and fixed validation unit tests
-   Sha3_512 returns 0s if it gets only 0s, getStandardHash was added
-   Added calculateAllZerosTest to HashTest
-   Added test for BundleValidator

## 0.5.8

-   Cleanup Converter
-   Refactor hexString() (#83)
-   Refactor hbytes: new param name, `txs`, for `attachToTangle`, `storeTransaction`, `broadcastTransaction`.
-   Added test for TailFinderImpl
-   Added test for WalkerAlpha
-   Added test for WalkValidatorImpl
-   Added test for CumulativeWeightCalculator

## 0.5.7

-   Added APIIntegrationTest
-   Added SnapshotMockUtils
-   Added SnapshotStateDiffImplTest
-   Added SnapshotMetaDataImplTest
-   Added SnapshotImplTest
-   Added SnapshotStateImplTest
-   Added SnapshotProviderImplTest
-   Updated LocalSnapshotManagerImpl
-   Added LocalSnapshotManagerImplTest
-   Added methods `hashCode()` and `equals()` for IntegerIndex
-   Added methods to get transaction with trunk and branch
-   Added mockMilestone and mockStateDiff methods
-   Added test for EntryPointSelectorImpl
-   Added test for RatingOne
-   Added test for SnapshotServiceImpl
-   Fixed TangleTest: delete temp folders
-   Fixed APIIntegrationTest: delete temp folders

## 0.5.6

-   TransactionRequesterWorkerImpl refactoring
-   Added New util functions
-   Added build tx from bytes
-   Added TangleMockUtils
-   Added Test for TransactionRequesterWorkerImpl
-   Updated ConfigFactory and added corresponding unit tests

## 0.5.5

-   Added Unit Tests:
    -   NodeTest
    -   APICallTest
    -   APITest
    -   using MainnetConfig for TangleTest
    -   TransactionTestUtils
    -   TransactionRequesterTest

-   TransactionRequester now drops old transactions when toRequest queue is full

-   Logger: Also log line numbers

-   Added DB Benchmark

-   Added crypto Benchmarks

## 0.5.4

-   API refactoring:
    -   API constructor only needs specific objects from helix instance
    -   Separate http server from api backend to improve readability, maintainability and enable multiple impl.
    -   Resteasy undertow integration

-   Fixed subseed() in Winternitz class

-   Added Unit Tests:
    -   RocksDB
    -   Tangle
    -   Winternitz

-   Fixed: Timestamp conversion in API deviates from Node

-   Stats Publisher:
    -   MIN_TRANSACTION_AGE_THRESHOLD set to 5 seconds
    -   MAX_TRANSACTION_AGE_THRESHOLD set to 2 minutes

-   Fix: Timestamp conversion in Node / API

-   Fix SignedFiles: line separator should not be part of digest

-   New Resource files

-   Added configurable spammer for testing/experiments

## 0.5.3

-   PoW Integration (Replace divepearler with GreedyMiner)
-   PoW: difficulty is a number of zero bytes instead of a power of 2
-   Added SHA3_512
-   Updated Miner for tests
-   Updated HashTest
-   Added MinerTest, SpongeTest
-   ZMQ: dedicated topic for the proposed oracle setup. All relevant data of a bundle is published as a json array. The topic is named: `ORACLE_<VAULT_ADDRESS>`.
-   Set PACKET_SIZE to 800 to avoid additional overhead (#29)
-   Add `MINIMUM_DELAY` to config (#33)

## 0.5.2

-   New coo public key. Generated a new merkle key file with ~130.000 keys for signing.

-   Fixed issue, where InvalidTransactionTimestamp was thrown on validating the nullByte txvm, due to Hash(nullBytes) not corresponding to null_hash and thus not being a solid entry point of the initial snapshot. (#27)

-   Added IS_POW_DISABLED to config. This parameter is determined for testing and simulation.

-   Refactor(rename):
    -   SBX->HLX,
    -   `helix-testnet-*` -> `helix-*`
    -   `testnet-*` -> `helix-*`

-   Listening on zmq address topic, will return json objects, for better parsability. For now only address topic is affected, we might consider updating all topics in a future update. The advantages are, that strings don't have to be stripped and the listener has corresponding keys to each value.  

## 0.5.1

-   Writing log to filesystem is now covered in `utils/HelixIOUtils` class. `SAVELOG` variable is removed from entry class and added as a config variable `SAVELOG_ENABLED`. Introduced `--savelog-enabled` flag, that indicates whether to export logs.

-   ZMQ:
    -   Various ZMQ bugs fixed, where messageQ publishes unreadable data.
    -   Provider now supports listening to addresses.
    -   Refactored ZMQ. We can now publish a message to zero message queue using the the method `tangle.publish()` and do not need to pass a MessageQ object to every class that is involved in publishing messages.

-   Various ZMQ bugs fixed, where messageQ publishes unreadable data.

-   Fix consistency validation

## 0.5.0

-   Finished Miner/PoW impl. and added Docs (#2)
-   Remote authentication is no longer done by passing credentials to URL, but rather adding and Authorization header that contains the relevant information. Added a small RempoteAuth class to keep the API class tidy.
-   Fixed snapshot issues (#3)
-   Value Transfer working as expected (#3)
-   Bundles working as expected (#3)
-   Logs may be exported using the `SAVELOG` variable.
-   Signed Milestones

## 0.4.2

-   New PoW Class: Miner.java from Eth java impl.
-   Added a Merkle class relevant for milestone signing and verification
-   Resolved issues from 0.4.1 as described in (#3)
-   Local snapshot files are now written properly
-   Remove spentAddressesDBEmptyException for testnet scope
-   Docs update (#7).

## 0.4.1

-   Added `Graphstream`.
-   Added `TransactionStatsPublisher`.

## 0.4.0

-   API and core functionality milestone
-   Added comments to _almost_ every method

## 0.3.4

-   Resolved major issues from 1.5.5-patch.
-   Added services: Solidifier, Ledger, Pruner, LocalSnapshot, Snapshot, Milestone

## 0.3.3

-   Patched conf, api, dto, tipselection to 1.5.5

## 0.3.2

-   Removed redundant debug verbosity.
-   Renamed main class HCP -> SBX.
-   Renamed MSAgent -> MSS.
-   Removed boolean flag for milestones. `MS_DELAY` > 0 is sufficient condition to start `mss`
-   Added InitResources\` class to write own resource files for testing

## 0.3.1

-   Refactor Model
-   Adapting sizes in `TransactionViewModel` (sbx#7)
-   Added `storeAndBroadcastMilestoneStatement`
-   Added MilestoneScheduledService(MSS)
-   Added optional `MS_DELAY` config parameter and input argument
-   Divepearler (provisionary pow engine) uses multithreading
-   Fixes related to binary model
