# 0.4.2
    - New PoW Class: Miner.java from Eth java impl. (still wip)
    - Added a Merkle class relevant for milestone signing and verification.
    - Resolved issues from 0.4.1 as described in (#3).
    - Local snapshot files are now written properly.
    - Remove spentAddressesDBEmptyException for testnet scope.
    - Docs update (#7).

# 0.4.1
    - Added `Graphstream`.
    - Added `TransactionStatsPublisher`.

# 0.4.0
    - API and core functionality milestone.
    - Added comments to _almost_ every method.

# 0.3.4
    - Resolved major issues from 1.5.5-patch. 
    - Added services: Solidifier, Ledger, Pruner, LocalSnapshot, Snapshot, Milestone.

# 0.3.3
    - Patched conf, api, dto, tipselection to 1.5.5.

# 0.3.2
    - Removed redundant debug verbosity.
    - Renamed main class HCP -> SBX.
    - Renamed MSAgent -> MSS.
    - Removed boolean flag for milestones. `MS_DELAY` > 0 is sufficient condition to start `mss`.
    - Added InitResources` class to write own resource files for testing.

# 0.3.1
    - Refactor Model
    - Adapting sizes in `TransactionViewModel` (sbx#7).
    - Added `storeAndBroadcastMilestoneStatement`.
    - Added MilestoneScheduledService(MSS).
    - Added optional `MS_DELAY` config parameter and input argument
    - Divepearler (provisionary pow engine) uses multithreading
    - Fixes related to binary model
