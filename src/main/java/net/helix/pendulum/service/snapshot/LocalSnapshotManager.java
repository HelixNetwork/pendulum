package net.helix.pendulum.service.snapshot;

import net.helix.pendulum.conf.SnapshotConfig;
import net.helix.pendulum.service.milestone.MilestoneTracker;
import net.helix.pendulum.service.transactionpruning.TransactionPruner;

/**
 * Represents the manager for local {@link Snapshot}s that takes care of periodically creating a new {@link Snapshot}
 * when the configured interval has passed.
 */
public interface LocalSnapshotManager {
    /**
     * Starts the automatic creation of local {@link Snapshot}s by spawning a background {@link Thread}, that
     * periodically checks if the last snapshot is older than
     * {@link net.helix.pendulum.conf.SnapshotConfig#getLocalSnapshotsIntervalSynced()}.
     *
     * When we detect that it is time for a local snapshot we internally trigger its creation.
     *
     * Note: If the node is not fully synced we use
     * {@link net.helix.pendulum.conf.SnapshotConfig#getLocalSnapshotsIntervalUnsynced()} instead.
     *
     * @param milestoneTracker tracker for the milestones to determine when a new local snapshot is due
     */
    void start(MilestoneTracker milestoneTracker);

    /**
     * Stops the {@link Thread} that takes care of creating the local {@link Snapshot}s and that was spawned by the
     * {@link #start(MilestoneTracker)} method.
     */
    void shutdown();
    
    LocalSnapshotManager init(SnapshotProvider snapshotProvider, SnapshotService snapshotService,
            TransactionPruner transactionPruner, SnapshotConfig config);
    
}