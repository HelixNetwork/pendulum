package net.helix.pendulum;

import net.helix.pendulum.conf.MainnetConfig;
import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.controllers.TipsViewModel;
import net.helix.pendulum.network.Node;
import net.helix.pendulum.network.impl.RequestQueueImpl;
import net.helix.pendulum.service.API;
import net.helix.pendulum.service.ApiArgs;
import net.helix.pendulum.service.milestone.MilestoneTracker;
import net.helix.pendulum.service.milestone.impl.MilestoneTrackerImpl;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.service.snapshot.impl.SnapshotProviderImpl;
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

    @Before
    public void setUp() throws Exception {
        dbFolder.create();
        logFolder.create();
        tangle = new Tangle();
        MainnetConfig config = new MainnetConfig();
        snapshotProvider = new SnapshotProviderImpl().init(config);
        tangle.addPersistenceProvider(
                new RocksDBPersistenceProvider(
                        dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath(),
                        1000, Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY));

        TipsViewModel tipsViewModel = new TipsViewModel();
        RequestQueueImpl txRequester = new RequestQueueImpl();
        txValidator = new TransactionValidator();
        MilestoneTracker milestoneTracker = new MilestoneTrackerImpl();

        api = new API(new ApiArgs(config) {
            @Override
            public MilestoneTracker getMilestoneTracker() {
                return milestoneTracker;
            }
        });


        Pendulum.ServiceRegistry.get().register(SnapshotProvider.class, snapshotProvider);
        Pendulum.ServiceRegistry.get().register(Tangle.class, tangle);
        Pendulum.ServiceRegistry.get().register(PendulumConfig.class, config);
        Pendulum.ServiceRegistry.get().register(TipsViewModel.class, tipsViewModel);
        Pendulum.ServiceRegistry.get().register(Node.RequestQueue.class, txRequester);
        Pendulum.ServiceRegistry.get().register(TransactionValidator.class, txValidator);



        txRequester.init();
        txValidator.init();
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
    }
}
