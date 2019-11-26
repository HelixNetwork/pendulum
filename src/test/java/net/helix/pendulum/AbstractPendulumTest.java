package net.helix.pendulum;

import net.helix.pendulum.conf.MainnetConfig;
import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.controllers.TipsViewModel;
import net.helix.pendulum.event.EventManager;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.HashFactory;
import net.helix.pendulum.model.StateDiff;
import net.helix.pendulum.model.persistables.*;
import net.helix.pendulum.network.Node;
import net.helix.pendulum.network.Node.RequestQueue;
import net.helix.pendulum.network.impl.RequestQueueImpl;
import net.helix.pendulum.service.API;
import net.helix.pendulum.service.ApiArgs;
import net.helix.pendulum.service.cache.TangleCache;
import net.helix.pendulum.service.cache.impl.TangleCacheImpl;
import net.helix.pendulum.service.milestone.MilestoneService;
import net.helix.pendulum.service.milestone.MilestoneSolidifier;
import net.helix.pendulum.service.milestone.MilestoneTracker;
import net.helix.pendulum.service.milestone.impl.MilestoneServiceImpl;
import net.helix.pendulum.service.milestone.impl.MilestoneSolidifierImpl;
import net.helix.pendulum.service.milestone.impl.MilestoneTrackerImpl;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.service.snapshot.SnapshotService;
import net.helix.pendulum.service.snapshot.impl.SnapshotProviderImpl;
import net.helix.pendulum.service.snapshot.impl.SnapshotServiceImpl;
import net.helix.pendulum.service.validatormanager.CandidateSolidifier;
import net.helix.pendulum.service.validatormanager.CandidateTracker;
import net.helix.pendulum.service.validatormanager.ValidatorManagerService;
import net.helix.pendulum.service.validatormanager.impl.CandidateSolidifierImpl;
import net.helix.pendulum.service.validatormanager.impl.CandidateTrackerImpl;
import net.helix.pendulum.service.validatormanager.impl.ValidatorManagerServiceImpl;
import net.helix.pendulum.storage.Tangle;
import net.helix.pendulum.storage.rocksdb.RocksDBPersistenceProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.rules.TemporaryFolder;

/**
 * Date: 2019-11-18
 * Author: zhelezov
 */
public abstract class AbstractPendulumTest {
    protected final int MAINNET_MWM = 1;
    protected final TemporaryFolder dbFolder = new TemporaryFolder();
    protected final TemporaryFolder logFolder = new TemporaryFolder();
    protected Tangle tangle;
    protected SnapshotProvider snapshotProvider;
    protected TransactionValidator txValidator;
    protected API api;
    protected MainnetConfig config;
    protected TipsViewModel tipsViewModel;
    protected RequestQueue requestQueue;
    protected MilestoneTracker milestoneTracker;
    protected MilestoneService milestoneService;
    protected SnapshotService snapshotService;
    protected CandidateTracker candidateTracker;
    protected MilestoneSolidifier milestoneSolidifier;
    protected ValidatorManagerService validatorManagerService;
    protected CandidateSolidifier candidateSolidifier;
    protected TangleCache tangleCache;
    protected Node node;

    protected Hash v_address1 = HashFactory.ADDRESS.create("eb0d925c1cfa4067db65e4b93fa17d451120cc5a719d637d44a39a983407d832");

    @Before
    public void setUp() throws Exception {
        EventManager.get().clear();
        Pendulum.ServiceRegistry.get().clear();

        dbFolder.create();
        logFolder.create();
        tangle = new Tangle();

        config = new MainnetConfig() {
            @Override
            public boolean isTestnet() {
                return true;
            }
        };
        snapshotProvider = new SnapshotProviderImpl().init(config);
        tangle.addPersistenceProvider(
                new RocksDBPersistenceProvider(
                        dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath(),
                        1000, Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY));

        api = new API(new ApiArgs(config) {
            @Override
            public MilestoneTracker getMilestoneTracker() {
                return milestoneTracker;
            }
        });

        txValidator = new TransactionValidator();
        tipsViewModel = new TipsViewModel();
        requestQueue = new RequestQueueImpl();
        milestoneTracker = new MilestoneTrackerImpl();
        milestoneService = new MilestoneServiceImpl();
        snapshotService = new SnapshotServiceImpl();
        candidateTracker = new CandidateTrackerImpl();
        milestoneSolidifier = new MilestoneSolidifierImpl();
        validatorManagerService = new ValidatorManagerServiceImpl();
        candidateSolidifier = new CandidateSolidifierImpl();
        tangleCache = new TangleCacheImpl();
        node = new Node();

        Pendulum.ServiceRegistry.get().register(PendulumConfig.class, config);
        Pendulum.ServiceRegistry.get().register(SnapshotProvider.class, snapshotProvider);
        Pendulum.ServiceRegistry.get().register(Tangle.class, tangle);
        Pendulum.ServiceRegistry.get().register(PendulumConfig.class, config);
        Pendulum.ServiceRegistry.get().register(TipsViewModel.class, tipsViewModel);
        Pendulum.ServiceRegistry.get().register(RequestQueue.class, requestQueue);
        Pendulum.ServiceRegistry.get().register(TransactionValidator.class, txValidator);
        Pendulum.ServiceRegistry.get().register(MilestoneService.class, milestoneService);
        Pendulum.ServiceRegistry.get().register(SnapshotService.class, snapshotService);
        Pendulum.ServiceRegistry.get().register(MilestoneSolidifier.class, milestoneSolidifier);
        Pendulum.ServiceRegistry.get().register(CandidateTracker.class, candidateTracker);
        Pendulum.ServiceRegistry.get().register(ValidatorManagerService.class, validatorManagerService);
        Pendulum.ServiceRegistry.get().register(CandidateSolidifier.class, candidateSolidifier);
        Pendulum.ServiceRegistry.get().register(TangleCache.class, tangleCache);
        Pendulum.ServiceRegistry.get().register(Node.class, node);

        milestoneService.init();
        milestoneTracker.init();
        milestoneSolidifier.init();
        candidateTracker.init();
        requestQueue.init();
        txValidator.init();
        tipsViewModel.init();
        validatorManagerService.init();
        candidateSolidifier.init();
        tangleCache.init();
        node.init();

        txValidator.setMwm(false, MAINNET_MWM);

        tangle.init();
    }

    @After
    public void tearDown() throws Exception {
        tangle.shutdown();
        snapshotProvider.shutdown();
        api.shutDown();
        dbFolder.delete();
        logFolder.delete();
        EventManager.get().clear();
        Pendulum.ServiceRegistry.get().clear();
    }

    protected void clearTangle() throws Exception {
        tangle.clearColumn(Transaction.class);
        tangle.clearColumn(Round.class);
        tangle.clearColumn(StateDiff.class);
        tangle.clearColumn(Address.class);
        tangle.clearColumn(Approvee.class);
        tangle.clearColumn(Bundle.class);
        tangle.clearColumn(BundleNonce.class);
        tangle.clearColumn(Tag.class);
        tangle.clearColumn(Validator.class);

    }
}
