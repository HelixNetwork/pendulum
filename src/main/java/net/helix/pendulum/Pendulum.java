package net.helix.pendulum;

import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.conf.TipSelConfig;
import net.helix.pendulum.controllers.TipsViewModel;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.network.Node;
import net.helix.pendulum.network.TransactionRequester;
import net.helix.pendulum.network.UDPReceiver;
import net.helix.pendulum.network.impl.TransactionRequesterWorkerImpl;
import net.helix.pendulum.network.replicator.Replicator;
import net.helix.pendulum.service.TipsSolidifier;
import net.helix.pendulum.service.ledger.impl.LedgerServiceImpl;
import net.helix.pendulum.service.milestone.impl.LatestSolidMilestoneTrackerImpl;
import net.helix.pendulum.service.milestone.impl.MilestoneServiceImpl;
import net.helix.pendulum.service.milestone.impl.MilestoneSolidifierImpl;
import net.helix.pendulum.service.milestone.impl.MilestoneTrackerImpl;
import net.helix.pendulum.service.milestone.impl.SeenMilestonesRetrieverImpl;
import net.helix.pendulum.service.snapshot.SnapshotException;
import net.helix.pendulum.service.snapshot.impl.LocalSnapshotManagerImpl;
import net.helix.pendulum.service.snapshot.impl.SnapshotProviderImpl;
import net.helix.pendulum.service.snapshot.impl.SnapshotServiceImpl;
import net.helix.pendulum.service.spentaddresses.SpentAddressesException;
import net.helix.pendulum.service.spentaddresses.impl.SpentAddressesProviderImpl;
import net.helix.pendulum.service.spentaddresses.impl.SpentAddressesServiceImpl;
import net.helix.pendulum.service.tipselection.EntryPointSelector;
import net.helix.pendulum.service.tipselection.RatingCalculator;
import net.helix.pendulum.service.tipselection.TailFinder;
import net.helix.pendulum.service.tipselection.TipSelector;
import net.helix.pendulum.service.tipselection.Walker;
import net.helix.pendulum.service.tipselection.impl.CumulativeWeightCalculator;
import net.helix.pendulum.service.tipselection.impl.EntryPointSelectorImpl;
import net.helix.pendulum.service.tipselection.impl.TailFinderImpl;
import net.helix.pendulum.service.tipselection.impl.TipSelectorImpl;
import net.helix.pendulum.service.tipselection.impl.WalkerAlpha;
import net.helix.pendulum.service.transactionpruning.TransactionPruningException;
import net.helix.pendulum.service.transactionpruning.async.AsyncTransactionPruner;
import net.helix.pendulum.service.validatormanager.impl.CandidateSolidifierImpl;
import net.helix.pendulum.service.validatormanager.impl.CandidateTrackerImpl;
import net.helix.pendulum.service.validatormanager.impl.ValidatorManagerServiceImpl;
import net.helix.pendulum.storage.Indexable;
import net.helix.pendulum.storage.Persistable;
import net.helix.pendulum.storage.PersistenceProvider;
import net.helix.pendulum.storage.Tangle;
import net.helix.pendulum.storage.rocksdb.RocksDBPersistenceProvider;
import net.helix.pendulum.utils.Pair;
import net.helix.pendulum.zmq.MessageQProviderImpl;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.List;

/**
 *
 * The main class of SBX. This will propagate transactions into and throughout the network.
 * This data is stored as a {@link Tangle}, a form of a Directed acyclic graph.
 * All incoming data will be stored in one or more implementations of {@link PersistenceProvider}.
 *
 * <p>
 *     During initialization, all the Providers can be set to rescan or revalidate their transactions.
 *     After initialization, an asynchronous process has started which will process inbound and outbound transactions.
 *     Each full node should be peered with 7-9 other full nodes (neighbors) to function optimally.
 * </p>
 * <p>
 *     If this node has no Neighbors defined, no data is transferred.
 *     However, if the node has Neighbors, but no Internet connection,
 *     synchronization will continue after Internet connection is established.
 *     Any transactions sent to this node in its local network will then be processed.
 *     This makes the node able to run partially offline if an already existing database exists on this node.
 * </p>
 * <p>
 *     Validation of a transaction is the process by which other devices choose the transaction.
 *     This is done via a {@link TipSelector} algorithm, after which the transaction performs
 *     the necessary proof-of-work in order to cast their vote of confirmation/approval upon those tips. <br/>
 *
 *     As many other transactions repeat this process on top of each other,
 *     validation of the transaction in question slowly builds up enough verifications.
 *     Eventually this will reach a minimum acceptable verification threshold.
 *     This threshold is determined by the recipient of the transaction.
 *     When this minimum threshold is reached, the transaction is "confirmed".
 * </p>
 *
 */
public class Pendulum {
    private static final Logger log = LoggerFactory.getLogger(Pendulum.class);

    public final SpentAddressesProviderImpl spentAddressesProvider;
    public final SpentAddressesServiceImpl spentAddressesService;
    public final SnapshotProviderImpl snapshotProvider;
    public final SnapshotServiceImpl snapshotService;
    public final LocalSnapshotManagerImpl localSnapshotManager;
    public final MilestoneServiceImpl milestoneService;
    public final ValidatorManagerServiceImpl validatorManagerService;
    public final MilestoneTrackerImpl latestMilestoneTracker;
    public final CandidateTrackerImpl candidateTracker;
    public final LatestSolidMilestoneTrackerImpl latestSolidMilestoneTracker;
    public final SeenMilestonesRetrieverImpl seenMilestonesRetriever;
    public final LedgerServiceImpl ledgerService = new LedgerServiceImpl();
    public final AsyncTransactionPruner transactionPruner;
    public final MilestoneSolidifierImpl milestoneSolidifier;
    public final CandidateSolidifierImpl candidateSolidifier;
    public final TransactionRequesterWorkerImpl transactionRequesterWorker;

    public final Tangle tangle;
    public final TransactionValidator transactionValidator;
    public final TipsSolidifier tipsSolidifier;
    public final TransactionRequester transactionRequester;
    public final Node node;
    public final UDPReceiver udpReceiver;
    public final Replicator replicator;
    public final PendulumConfig configuration;
    public final TipsViewModel tipsViewModel;
    public final TipSelector tipsSelector;
    public final BundleValidator bundleValidator;

    /**
     * Initializes the latest snapshot and then creates all services needed to run a node.
     *
     * @param configuration Information about how this node will be configured.
     * @throws TransactionPruningException If the TransactionPruner could not restore its state.
     * @throws SnapshotException If the Snapshot fails to initialize.
     *                           This can happen if the snapshot signature is invalid or the file cannot be read.
     */
    public Pendulum(PendulumConfig configuration) throws TransactionPruningException, SnapshotException, SpentAddressesException {
        this.configuration = configuration;

        // new refactored instances
        spentAddressesProvider = new SpentAddressesProviderImpl();
        spentAddressesService = new SpentAddressesServiceImpl();
        snapshotProvider = new SnapshotProviderImpl();
        snapshotService = new SnapshotServiceImpl();
        localSnapshotManager = configuration.getLocalSnapshotsEnabled()
                ? new LocalSnapshotManagerImpl()
                : null;
        milestoneService = new MilestoneServiceImpl();
        //validatorService = new ValidatorServiceImpl();
        validatorManagerService = new ValidatorManagerServiceImpl();
        latestMilestoneTracker = new MilestoneTrackerImpl();
        candidateTracker = new CandidateTrackerImpl();
        latestSolidMilestoneTracker = new LatestSolidMilestoneTrackerImpl();
        seenMilestonesRetriever = new SeenMilestonesRetrieverImpl();
        milestoneSolidifier = new MilestoneSolidifierImpl();
        //validatorSolidifier = new ValidatorSolidifierImpl();
        candidateSolidifier = new CandidateSolidifierImpl();
        transactionPruner = configuration.getLocalSnapshotsEnabled() && configuration.getLocalSnapshotsPruningEnabled()
                ? new AsyncTransactionPruner()
                : null;
        transactionRequesterWorker = new TransactionRequesterWorkerImpl();

        // legacy code
        bundleValidator = new BundleValidator();
        tangle = new Tangle();
        tipsViewModel = new TipsViewModel();
        transactionRequester = new TransactionRequester(tangle, snapshotProvider);
        transactionValidator = new TransactionValidator(tangle, snapshotProvider, tipsViewModel, transactionRequester, configuration);
        node = new Node(tangle, snapshotProvider, transactionValidator, transactionRequester, tipsViewModel,
                latestMilestoneTracker, configuration);
        replicator = new Replicator(node, configuration);
        udpReceiver = new UDPReceiver(node, configuration);
        tipsSolidifier = new TipsSolidifier(tangle, transactionValidator, tipsViewModel, configuration);
        tipsSelector = createTipSelector(configuration);

        injectDependencies();
    }

    /**
     * Adds all database providers, and starts initialization of our services.
     * According to the {@link PendulumConfig}, data is optionally cleared, reprocessed and reverified.<br/>
     * After this function, incoming and outbound transaction processing has started.
     *
     * @throws Exception If along the way a service fails to initialize.
     *                   Most common cause is a file read or database error.
     */
    public void init() throws Exception {
        initializeTangle();
        tangle.init();

        if (configuration.isRescanDb()){
            rescanDb();
        }

        if (configuration.isRevalidate()) {
            tangle.clearColumn(net.helix.pendulum.model.persistables.Round.class);
            tangle.clearColumn(net.helix.pendulum.model.StateDiff.class);
            tangle.clearMetadata(net.helix.pendulum.model.persistables.Transaction.class);
        }

        transactionValidator.init(configuration.isTestnet(), configuration.getMwm());
        tipsSolidifier.init();
        transactionRequester.init(configuration.getpRemoveRequest());
        udpReceiver.init();
        replicator.init();
        node.init();

        latestMilestoneTracker.start();
        latestSolidMilestoneTracker.start();
        candidateTracker.start();
        seenMilestonesRetriever.start();
        milestoneSolidifier.start();
        transactionRequesterWorker.start();

        if (localSnapshotManager != null) {
            localSnapshotManager.start(latestMilestoneTracker);
        }
        if (transactionPruner != null) {
            transactionPruner.start();
        }

        if (configuration.isZmqEnabled()) {
            tangle.addMessageQueueProvider(new MessageQProviderImpl(configuration));
        }
        if(Files.notExists(Paths.get(configuration.getResourcePath()))){
            new File(configuration.getResourcePath()).mkdir();
        }
    }

    //TODO: bundleValidator should be passed to: milestoneService, spentAddressService and ledgerService.
    private void injectDependencies() throws SnapshotException, TransactionPruningException, SpentAddressesException {
        //snapshot provider must be initialized first
        //because we check whether spent addresses data exists
        snapshotProvider.init(configuration);
        spentAddressesProvider.init(configuration);
        spentAddressesService.init(tangle, snapshotProvider, spentAddressesProvider);
        snapshotService.init(tangle, snapshotProvider, spentAddressesService, spentAddressesProvider, configuration);
        if (localSnapshotManager != null) {
            localSnapshotManager.init(snapshotProvider, snapshotService, transactionPruner, configuration);
        }
        milestoneService.init(tangle, snapshotProvider, snapshotService, transactionValidator, configuration);
        validatorManagerService.init(tangle, snapshotProvider, snapshotService, configuration);
        candidateTracker.init(tangle, snapshotProvider, validatorManagerService, candidateSolidifier, configuration);
        latestMilestoneTracker.init(tangle, snapshotProvider, milestoneService, milestoneSolidifier, candidateTracker, configuration);
        latestSolidMilestoneTracker.init(tangle, snapshotProvider, milestoneService, ledgerService,
                latestMilestoneTracker);
        seenMilestonesRetriever.init(tangle, snapshotProvider, transactionRequester);
        milestoneSolidifier.init(snapshotProvider, transactionValidator);
        //validatorSolidifier.init(snapshotProvider, transactionValidator);
        candidateSolidifier.init(snapshotProvider, transactionValidator);
        ledgerService.init(tangle, snapshotProvider, snapshotService, milestoneService, configuration);
        if (transactionPruner != null) {
            transactionPruner.init(tangle, snapshotProvider, spentAddressesService, tipsViewModel, configuration)
                    .restoreState();
        }
        transactionRequesterWorker.init(tangle, transactionRequester, tipsViewModel, node);
    }

    private void rescanDb() throws Exception {
        //delete all transaction indexes
        tangle.clearColumn(net.helix.pendulum.model.persistables.Address.class);
        tangle.clearColumn(net.helix.pendulum.model.persistables.Bundle.class);
        tangle.clearColumn(net.helix.pendulum.model.persistables.Approvee.class);
        tangle.clearColumn(net.helix.pendulum.model.persistables.BundleNonce.class);
        tangle.clearColumn(net.helix.pendulum.model.persistables.Tag.class);
        tangle.clearColumn(net.helix.pendulum.model.persistables.Round.class);
        tangle.clearColumn(net.helix.pendulum.model.StateDiff.class);
        tangle.clearMetadata(net.helix.pendulum.model.persistables.Transaction.class);

        //rescan all tx & refill the columns
        TransactionViewModel tx = TransactionViewModel.first(tangle);
        int counter = 0;
        while (tx != null) {
            if (++counter % 10000 == 0) {
                log.info("Rescanned {} Transactions", counter);
            }
            List<Pair<Indexable, Persistable>> saveBatch = tx.getSaveBatch();
            saveBatch.remove(5);
            tangle.saveBatch(saveBatch);
            tx = tx.next(tangle);
        }
    }

    /**
     * Gracefully shuts down by calling <tt>shutdown()</tt> on all used services.
     * Exceptions during shutdown are not caught.
     */
    public void shutdown() throws Exception {
        transactionRequesterWorker.shutdown();
        milestoneSolidifier.shutdown();
        seenMilestonesRetriever.shutdown();
        latestSolidMilestoneTracker.shutdown();
        latestMilestoneTracker.shutdown();

        if (transactionPruner != null) {
            transactionPruner.shutdown();
        }
        if (localSnapshotManager != null) {
            localSnapshotManager.shutdown();
        }

        tipsSolidifier.shutdown();
        node.shutdown();
        udpReceiver.shutdown();
        replicator.shutdown();
        transactionValidator.shutdown();
        tangle.shutdown();

        // free the resources of the snapshot provider last because all other instances need it
        snapshotProvider.shutdown();
    }

    private void initializeTangle() {
        switch (configuration.getMainDb()) {
            case "rocksdb": {
                tangle.addPersistenceProvider(new RocksDBPersistenceProvider(
                        configuration.getDbPath(),
                        configuration.getDbLogPath(),
                        configuration.getDbCacheSize(),
                        Tangle.COLUMN_FAMILIES,
                        Tangle.METADATA_COLUMN_FAMILY)
                );
                break;
            }
            default: {
                throw new NotImplementedException("No such database type.");
            }
        }
    }

    private TipSelector createTipSelector(TipSelConfig config) {
        EntryPointSelector entryPointSelector = new EntryPointSelectorImpl(tangle, snapshotProvider,
                latestMilestoneTracker);
        RatingCalculator ratingCalculator = new CumulativeWeightCalculator(tangle, snapshotProvider);
        TailFinder tailFinder = new TailFinderImpl(tangle);
        Walker walker = new WalkerAlpha(tailFinder, tangle, new SecureRandom(), config);
        return new TipSelectorImpl(tangle, snapshotProvider, ledgerService, entryPointSelector, ratingCalculator,
                walker, config);
    }
}