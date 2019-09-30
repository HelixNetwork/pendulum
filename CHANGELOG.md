## 1.0.0
-   Added new implementation of `TransactionTestUtils.buildTransaction()`
-   Added test for DAGHelper
-   Added test for SpentAddressesProvider
-   Update SpentAddressesProviderImpl for multi-testing
-   Uniform random tip selection with config param TIPSELECTION_ALPHA = 0

## 0.6.9
-   Set `GENESIS_TIME` to [mainnet-genesis-time](https://github.com/HelixNetwork/helix-dao-data#genesis-time)
-   Update Undertow from 1.4.6 to 2.0.26
-   Removed obsolete nominee tracking
-   Renamed curator->ValidatorManager
-   Renamed nominee -> Validator
-   Modified the following configs:
    - `--curator` => `--validator-manager`
    - `--update-nominee` => `--update-validator`
    - `--start-nominee` => `--start-validator`
    - `--nominee` => `--validator`
    - `--testnet-no-coo-validation` => `--testnet-no-milestone-sign-validation`
-   Fixed Logback configuration

## 0.6.8
-   Remove Graphstream
-   Snapshots are saved to specifiable dir within of root
-   Updated Global Mainnet Snapshot

## 0.6.7
-   Fixed #142 Negative value for address detected in the running testnet

## 0.6.6
-   Fixed RVM.getRandomMilestone: returns null if round has empty set
-   Fixed for RVM.delete: if round is deleted from DB, it's removed from cache
-   Added test for RoundViewModel

## 0.6.5
-   getNodeInfo now only reflects relevant information
-   Store nominees in local db and fix getNomineesOfRound()
-   Deleted empty HelixTest
-   Fix debug info and javadocs in Tests
-   Fix for debug info, javadoc and variable names in src

## 0.6.4
-   Renaming/Refactoring
-   `round_duration` and `round_pause` was increased for testing

## 0.6.3
-   Added test for Round model
-   Update Round Model
-   Add test for hashes
-   Sha3 getStandardHash() returns zeros for 0-length input array
-   Hashes model was updated to support reading with offset
-   HashPrefix length set to 32
-   Fix #143 Entry point failed consistency check exception at node start-up

## 0.6.2
-   Fix replayMilestonesInconsistentTest
-   Nominee setting (#135)
-   Key generation with a prebuilt jar (#136)

## 0.6.1
-   Curator-less implementation
-   Set initial nominees
-   Set testnet genesis time

## 0.6.0
-   Finality Update integration. For more info see the [specifications](https://github.com/HelixNetwork/helix-specs/blob/master/specs/1.0/finality.md).

## pre-0.6.0

These change-logs have been removed to keep the file concise, if there are specific interests, feel free to reach out to the contributors.
