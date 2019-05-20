# 0.5.3
    - PoW Integration (Replace divepearler with Miner/GreedyMiner) 
    
# 0.5.2
    - New coo public key. Generated a new merkle key file with ~130.000 keys for signing.
    - Fixed issue, where InvalidTransactionTimestamp was thrown on validating the nullByte txvm, due to Hash(nullBytes) not corresponding to null_hash and thus not being a solid entry point of the initial snapshot. (#27)
    - Added IS_POW_DISABLED to config. This parameter is determined for testing and simulation. 
    - Refactor(rename): 
        - SBX->HLX, 
        - helix-testnet-* -> helix-*, 
        - testnet-* -> helix-*
    - Listening on zmq address topic, will return json objects, for better parsability. For now only address topic is affected, we might consider updating all topics in a future update. The advantages are, that strings don't have to be stripped and the listener has corresponding keys to each value.  

# 0.5.1
    - Writing log to filesystem is now covered in `utils/HelixIOUtils` class. `SAVELOG` variable is removed from entry class and added as a config variable `SAVELOG_ENABLED`. Introduced `--savelog-enabled` flag, that indicates whether to export logs.
    - ZMQ:
        - Various ZMQ bugs fixed, where messageQ publishes unreadable data.
        - Provider now supports listening to addresses.
        - Refactored ZMQ. We can now publish a message to zero message queue using the the method `tangle.publish()` and do not need to pass a MessageQ object to every class that is involved in publishing messages.
    - Various ZMQ bugs fixed, where messageQ publishes unreadable data.
    - Fix consistency validation

# 0.5.0
    - Finished Miner/PoW impl. and added Docs (#2)
    - Remote authentication is no longer done by passing credentials to URL, but rather adding and Authorization header that contains the relevant information. Added a small RempoteAuth class to keep the API class tidy.
    - Fixed snapshot issues (#3)
    - Value Transfer working as expected (#3)
    - Bundles working as expected (#3)
    - Logs may be exported using the `SAVELOG` variable.
    - Signed Milestones

# 0.4.2
    - New PoW Class: Miner.java from Eth java impl.
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
