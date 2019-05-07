# 0.5.1 
    - Writing log to filesystem is now covered in `utils/HelixIOUtils` class. `SAVELOG` variable is removed from entry class and added as a config variable. Now `SAVELOG`can be utilized as a boolean flag, that indicates whether to export logs.
    - ZMQ Provider now supports listening to addresses. (WORK IN PROGRESS)
    - "Resetting corrupted milestone" fix. (WORK IN PROGRESS)

# 0.5.0
    - Finished Miner/PoW impl. and added Docs (#2)
    - Remote authentication is no longer done by passing credentials to URL, but rather adding and Authorization header that contains the relevant information. Added a small RempoteAuth class to keep the API class tidy.
    - Fixed snapshot issues (#3)
    - Value Transfer working as expected (#3)
    - Bundles working as expected (#3)
    - Logs may be exported using the `SAVELOG` variable.
    - Signed Milestones 
    
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

