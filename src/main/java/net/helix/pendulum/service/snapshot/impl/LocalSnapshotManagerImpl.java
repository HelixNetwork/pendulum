package net.helix.pendulum.service.snapshot.impl;

import net.helix.pendulum.conf.SnapshotConfig;
import net.helix.pendulum.service.milestone.MilestoneTracker;
import net.helix.pendulum.service.snapshot.LocalSnapshotManager;
import net.helix.pendulum.service.snapshot.SnapshotException;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.service.snapshot.SnapshotService;
import net.helix.pendulum.service.transactionpruning.TransactionPruner;
import net.helix.pendulum.utils.thread.ThreadIdentifier;
import net.helix.pendulum.utils.thread.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates a manager for the local snapshots, that takes care of automatically creating local snapshots when the defined
 * intervals have passed.<br />
 * <br />
 * It incorporates a background worker that periodically checks if a new snapshot is due (see {@link
 * #start(MilestoneTracker)} and {@link #shutdown()}).<br />
 */
public class LocalSnapshotManagerImpl implements LocalSnapshotManager {
    /**
     * The interval (in milliseconds) in which we generate a new local {@link net.helix.pendulum.service.snapshot.Snapshot}
     */
    private static final int LOCAL_SNAPSHOT_RESCAN_INTERVAL = 1000*60*1;

    /**
     * To prevent jumping back and forth in and out of sync, there is a buffer in between.
     * Only when the latest milestone and latest snapshot differ more than this number, we fall out of sync
     */
    //@VisibleForTesting
    protected static final int LOCAL_SNAPSHOT_SYNC_BUFFER = 5;

    /**
     * Logger for this class allowing us to dump debug and status messages.
     */
    private static final Logger log = LoggerFactory.getLogger(LocalSnapshotManagerImpl.class);

    /**
     * Data provider for the relevant {@link net.helix.pendulum.service.snapshot.Snapshot} instances.
     */
    private SnapshotProvider snapshotProvider;

    /**
     * Service that contains the logic for generating local {@link net.helix.pendulum.service.snapshot.Snapshot}s.
     */
    private SnapshotService snapshotService;

    /**
     * Manager for the pruning jobs that allows us to clean up old transactions.
     */
    private TransactionPruner transactionPruner;

    /**
     * Configuration with important snapshot related parameters.
     */
    private SnapshotConfig config;

    /**
     * If this node is currently seen as in sync
     */
    private boolean isInSync;

    /**
     * Holds a reference to the {@link ThreadIdentifier} for the monitor thread.
     *
     * Using a {@link ThreadIdentifier} for spawning the thread allows the {@link ThreadUtils} to spawn exactly one
     * thread for this instance even when we call the {@link #start(MilestoneTracker)} method multiple times.
     */
    private ThreadIdentifier monitorThreadIdentifier = new ThreadIdentifier("Local Snapshots Monitor");

    /**
     * This method initializes the instance and registers its dependencies.<br />
     * <br />
     * It simply stores the passed in values in their corresponding private properties.<br />
     * <br />
     * Note: Instead of handing over the dependencies in the constructor, we register them lazy. This allows us to have
     *       circular dependencies because the instantiation is separated from the dependency injection. To reduce the
     *       amount of code that is necessary to correctly instantiate this class, we return the instance itself which
     *       allows us to still instantiate, initialize and assign in one line - see Example:<br />
     *       <br />
     *       {@code localSnapshotManager = new LocalSnapshotManagerImpl().init(...);}
     *
     * @param snapshotProvider data provider for the snapshots that are relevant for the node
     * @param snapshotService service instance of the snapshot package that gives us access to packages' business logic
     * @param transactionPruner manager for the pruning jobs that allows us to clean up old transactions
     * @param config important snapshot related configuration parameters
     * @return the initialized instance itself to allow chaining
     */
    public LocalSnapshotManagerImpl init(SnapshotProvider snapshotProvider, SnapshotService snapshotService,
                                         TransactionPruner transactionPruner, SnapshotConfig config) {

        this.snapshotProvider = snapshotProvider;
        this.snapshotService = snapshotService;
        this.transactionPruner = transactionPruner;
        this.config = config;

        this.isInSync = false;

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(MilestoneTracker milestoneTracker) {
        ThreadUtils.spawnThread(() -> monitorThread(milestoneTracker), monitorThreadIdentifier);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
        ThreadUtils.stopThread(monitorThreadIdentifier);
    }

    /**
     * This method contains the logic for the monitoring Thread.
     *
     * It triggers the periodic creation of a {@link net.helix.pendulum.service.snapshot.Snapshot} by calling
     * {@link SnapshotService#takeLocalSnapshot(MilestoneTracker, TransactionPruner)}.
     *
     * @param milestoneTracker tracker for the milestones to determine when a new local snapshot is due
     */
    //@VisibleForTesting
    void monitorThread(MilestoneTracker milestoneTracker) {
        while (!Thread.currentThread().isInterrupted()) {
            log.trace("Is initial milestone scan complete? / {}", milestoneTracker.isInitialScanComplete());
            log.trace("Current round index = {}", milestoneTracker.getCurrentRoundIndex());
            log.trace("Latest snapshot index = {}", snapshotProvider.getLatestSnapshot().getIndex());
            log.trace("Sync check = {}", milestoneTracker.getCurrentRoundIndex() -  snapshotProvider.getLatestSnapshot().getIndex());
            ThreadUtils.sleep(LOCAL_SNAPSHOT_RESCAN_INTERVAL / 2); // wait 30 seconds
            if (milestoneTracker.isInitialScanComplete()){
                try {
                    snapshotService.takeLocalSnapshot(milestoneTracker, transactionPruner);
                }
                catch (SnapshotException e) {
                    log.error("error while taking local snapshot", e);
                }
            }
            ThreadUtils.sleep(LOCAL_SNAPSHOT_RESCAN_INTERVAL / 2); // wait 30 seconds
        }
    }

    /**
     * A snapshot is taken in an interval.
     * This interval changes based on the state of the node.
     * @param inSync if this node is in sync
     * @return the current interval in which we take local snapshots
     */
    //@VisibleForTesting
    protected int getSnapshotInterval(boolean inSync) {
        return inSync
                ? config.getLocalSnapshotsIntervalSynced()
                : config.getLocalSnapshotsIntervalUnsynced();
    }

    /**
     * A node is defined in sync when the latest snapshot milestone index and the
     * latest milestone index are equal. In order to prevent a bounce between in and
     * out of sync, a buffer is added when a node became in sync.
     * This will always return false if we are not done scanning milestone
     * candidates during initialization.
     * @param milestoneTracker tracker we use to determine milestones
     * @return <code>true</code> if we are in sync, otherwise <code>false</code>
     */
    //@VisibleForTesting
    boolean isInSync(MilestoneTracker milestoneTracker) {
        if (!milestoneTracker.isInitialScanComplete()) {
            return false;
        }

        int latestIndex = milestoneTracker.getCurrentRoundIndex();
        int latestSnapshot = snapshotProvider.getLatestSnapshot().getIndex();

        // If we are out of sync, only a full sync will get us in
        if (!isInSync && latestIndex == latestSnapshot) {
            isInSync = true;

        // When we are in sync, only dropping below the buffer gets us out of sync
        } else if (latestSnapshot < latestIndex - LOCAL_SNAPSHOT_SYNC_BUFFER) {
            isInSync = false;
        }

        return isInSync;
    }

}
