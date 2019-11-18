package net.helix.pendulum;

import net.helix.pendulum.conf.MainnetConfig;
import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.controllers.TipsViewModel;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.crypto.SpongeFactory;
import net.helix.pendulum.model.TransactionHash;
import net.helix.pendulum.network.Node;
import net.helix.pendulum.network.impl.RequestQueueImpl;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.service.snapshot.impl.SnapshotProviderImpl;
import net.helix.pendulum.storage.Tangle;
import net.helix.pendulum.storage.rocksdb.RocksDBPersistenceProvider;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static net.helix.pendulum.TransactionTestUtils.createTransactionWithHex;
import static net.helix.pendulum.TransactionTestUtils.getTransactionBytes;
import static net.helix.pendulum.TransactionTestUtils.getTransactionBytesWithTrunkAndBranch;
import static net.helix.pendulum.TransactionTestUtils.getTransactionHash;
import static net.helix.pendulum.TransactionTestUtils.createTransactionWithTrunkAndBranch;
import static org.junit.Assert.*;


public class TransactionValidatorTest {

    private static final int MAINNET_MWM = 1;
    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static Tangle tangle;
    private static SnapshotProvider snapshotProvider;
    private static TransactionValidator txValidator;

    @BeforeClass
    public static void setUp() throws Exception {
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

    @AfterClass
    public static void shutdown() throws Exception {
        tangle.shutdown();
        snapshotProvider.shutdown();
        dbFolder.delete();
        logFolder.delete();
    }

    @Test
    public void minDifficultyTest() throws InterruptedException {
        PendulumConfig oldConf =  Pendulum.ServiceRegistry.get().resolve(PendulumConfig.class);

        PendulumConfig testConf = new MainnetConfig() {
            @Override
            public boolean isTestnet() {
                return false;
            }

            @Override
            public int getMwm() {
                return 0;
            }
        };

        Pendulum.ServiceRegistry.get().register(PendulumConfig.class, testConf);
        // should initialize with the props as above
        txValidator.init();
        assertTrue(txValidator.getMinWeightMagnitude() == 1);

        Pendulum.ServiceRegistry.get().register(PendulumConfig.class, oldConf);
    }

    @Test
    public void validateBytesTest() {
        try {
            byte[] bytes = new byte[TransactionViewModel.SIZE];
            txValidator.validateBytes(bytes, MAINNET_MWM);
        } catch (Throwable t) {
            fail();
        }

    }

    @Test(expected = RuntimeException.class)
    public void validateBytesWithInvalidMetadataTest() {
        byte[] bytes = getTransactionBytes();
        txValidator.validateBytes(bytes, MAINNET_MWM);
    }

    @Test
    public void validateBytesWithNewSha3Test() {
        try{
            byte[] bytes = new byte[TransactionViewModel.SIZE];
                txValidator.validateBytes(bytes, txValidator.getMinWeightMagnitude(), SpongeFactory.create(SpongeFactory.Mode.S256));
        } catch (Throwable t) {
            fail();
        }
    }

    @Test
    public void verifyTxIsSolidTest() throws Exception {
        TransactionViewModel tx = getTxWithBranchAndTrunk();
        txValidator.checkSolidity(tx.getHash());

        txValidator.solidifyBackwards();
        txValidator.solidifyForward();

        assertTrue(txValidator.checkSolidity(tx.getHash()));
    }

    @Test
    public void verifyTxIsNotSolidTest() throws Exception {
        TransactionViewModel tx = getTxWithoutBranchAndTrunk();
        assertFalse(txValidator.checkSolidity(tx.getHash()));
    }

    @Test
    public void addSolidTransactionWithoutErrorsTest() {
        try {
            byte[] bytes = new byte[TransactionViewModel.SIZE];
            txValidator.addSolidTransaction(TransactionHash.calculate(SpongeFactory.Mode.S256, bytes));
        } catch (Throwable t) {
            fail();
        }
    }

    private TransactionViewModel getTxWithBranchAndTrunk() throws Exception {
        TransactionViewModel tx;
        TransactionViewModel trunkTx;
        TransactionViewModel branchTx;

        String hexTx = "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c2eb2d5297f4e70f3e40e3d7aa3f5c1d7405264aeb72232d06776605d8b61211000000000000000a0000000000000000000000000000000000000000000000000000000000000000000000005d092fc0000000000000000c000000000000000c5031b48d241283c312c68c777bc4563ddd7cbe1ae6a2c58079e1bf3cfef826790000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000016b6c8a2da00000000000000000000000000000007f00000000000000f40000000000000000000000000000007f00000000000091b0";
        byte[] bytes = createTransactionWithHex(hexTx).getBytes();

        trunkTx = new TransactionViewModel(bytes, TransactionHash.calculate(SpongeFactory.Mode.S256, bytes));
        branchTx = new TransactionViewModel(bytes, TransactionHash.calculate(SpongeFactory.Mode.S256, bytes));

        byte[] childTx = getTransactionBytes();
        System.arraycopy(trunkTx.getHash().bytes(), 0, childTx, TransactionViewModel.TRUNK_TRANSACTION_OFFSET, TransactionViewModel.TRUNK_TRANSACTION_SIZE);
        System.arraycopy(branchTx.getHash().bytes(), 0, childTx, TransactionViewModel.BRANCH_TRANSACTION_OFFSET, TransactionViewModel.BRANCH_TRANSACTION_SIZE);
        tx = new TransactionViewModel(childTx, TransactionHash.calculate(SpongeFactory.Mode.S256, childTx));

        trunkTx.store(tangle, snapshotProvider.getInitialSnapshot());
        branchTx.store(tangle, snapshotProvider.getInitialSnapshot());
        tx.store(tangle, snapshotProvider.getInitialSnapshot());

        return tx;
    }

    @Test
    public void transactionPropagationTest() throws Exception {
        TransactionViewModel leftChildLeaf = createTransactionWithHex("b0c1");
        leftChildLeaf.updateSolid(true);
        leftChildLeaf.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel rightChildLeaf = createTransactionWithHex("b0c2");
        rightChildLeaf.updateSolid(true);
        rightChildLeaf.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel parent = createTransactionWithTrunkAndBranch("b0",
                leftChildLeaf.getHash(), rightChildLeaf.getHash());
        parent.updateSolid(false);
        parent.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel parentSibling = createTransactionWithHex("b1");
        parentSibling.updateSolid(true);
        parentSibling.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel grandParent = createTransactionWithTrunkAndBranch("a0", parent.getHash(),
                parentSibling.getHash());
        grandParent.updateSolid(false);
        grandParent.store(tangle, snapshotProvider.getInitialSnapshot());

        txValidator.addSolidTransaction(leftChildLeaf.getHash());
        while (!txValidator.isNewSolidTxSetsEmpty()) {
            txValidator.solidifyBackwards();
            txValidator.solidifyForward();
        }

        parent = TransactionViewModel.fromHash(tangle, parent.getHash());
        assertTrue("Parent tx was expected to be solid", parent.isSolid());
        grandParent = TransactionViewModel.fromHash(tangle, grandParent.getHash());
        assertTrue("Grandparent  was expected to be solid", grandParent.isSolid());
    }

    @Test
    public void transactionPropagationFailureTest() throws Exception {
        TransactionViewModel leftChildLeaf = new TransactionViewModel(getTransactionBytes(), getTransactionHash());
        leftChildLeaf.updateSolid(true);
        leftChildLeaf.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel rightChildLeaf = new TransactionViewModel(getTransactionBytes(), getTransactionHash());
        rightChildLeaf.updateSolid(true);
        rightChildLeaf.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel parent = new TransactionViewModel(getTransactionBytesWithTrunkAndBranch(leftChildLeaf.getHash(),
                rightChildLeaf.getHash()), getTransactionHash());
        parent.updateSolid(false);
        parent.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel parentSibling = new TransactionViewModel(getTransactionBytes(), getTransactionHash());
        parentSibling.updateSolid(false);
        parentSibling.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel grandParent = new TransactionViewModel(getTransactionBytesWithTrunkAndBranch(parent.getHash(),
                parentSibling.getHash()), getTransactionHash());
        grandParent.updateSolid(false);
        grandParent.store(tangle, snapshotProvider.getInitialSnapshot());

        txValidator.addSolidTransaction(leftChildLeaf.getHash());
        while (!txValidator.isNewSolidTxSetsEmpty()) {
            txValidator.solidifyBackwards();
            txValidator.solidifyForward();
        }

        parent = TransactionViewModel.fromHash(tangle, parent.getHash());
        assertTrue("Parent tx was expected to be solid", parent.isSolid());
        grandParent = TransactionViewModel.fromHash(tangle, grandParent.getHash());
        assertFalse("GrandParent tx was expected to be not solid", grandParent.isSolid());
    }

    private TransactionViewModel getTxWithoutBranchAndTrunk() throws Exception {
        byte[] bytes = getTransactionBytes();
        TransactionViewModel tx = new TransactionViewModel(bytes, TransactionHash.calculate(SpongeFactory.Mode.S256, bytes));

        tx.store(tangle, snapshotProvider.getInitialSnapshot());

        return tx;
    }

}