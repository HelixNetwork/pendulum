package net.helix.hlx;


import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.helix.hlx.conf.MainnetConfig;
import net.helix.hlx.controllers.TipsViewModel;
import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.crypto.SpongeFactory;
import net.helix.hlx.model.TransactionHash;
import net.helix.hlx.network.TransactionRequester;
import net.helix.hlx.service.snapshot.SnapshotProvider;
import net.helix.hlx.service.snapshot.impl.SnapshotProviderImpl;
import net.helix.hlx.storage.Tangle;
import net.helix.hlx.storage.rocksDB.RocksDBPersistenceProvider;
import net.helix.hlx.utils.Converter;
import static net.helix.hlx.TransactionTestUtils.*;



public class TransactionValidatorTest1 {

    private static final int MAINNET_MWM = 2;
    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static Tangle tangle;
    private static SnapshotProvider snapshotProvider;
    private static TransactionValidator txValidator;

    @BeforeClass
    public static void setup() throws Exception {
        dbFolder.create();
        logFolder.create();
        tangle = new Tangle();
        MainnetConfig config = new MainnetConfig();
        snapshotProvider = new SnapshotProviderImpl().init(config);
        tangle.addPersistenceProvider(
                new RocksDBPersistenceProvider(
                        dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath(),
                        1000, Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY));
        tangle.init();
        TipsViewModel tipsViewModel = new TipsViewModel();
        TransactionRequester txRequester = new TransactionRequester(tangle, snapshotProvider);
        txValidator = new TransactionValidator(tangle, snapshotProvider, tipsViewModel, txRequester, config);
        txValidator.setMwm(false, MAINNET_MWM);
    }

    @AfterClass
    public static void shutdown() throws Exception {
        tangle.shutdown();
        snapshotProvider.shutdown();
        dbFolder.delete();
        logFolder.delete();
    }

    @Test
    public void transactionPropagationTest() throws Exception {
        TransactionViewModel leftChildLeaf = TransactionTestUtils.createTransactionWithHex("c0");
        leftChildLeaf.updateSolid(true);
        leftChildLeaf.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel rightChildLeaf = TransactionTestUtils.createTransactionWithHex("c1");
        rightChildLeaf.updateSolid(true);
        rightChildLeaf.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel parent = TransactionTestUtils.createTransactionWithTrunkAndBranch("b0",
                leftChildLeaf.getHash(), rightChildLeaf.getHash());
        parent.updateSolid(false);
        parent.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel parentSibling = TransactionTestUtils.createTransactionWithHex("b1");
        parentSibling.updateSolid(true);
        parentSibling.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel grandParent = TransactionTestUtils.createTransactionWithTrunkAndBranch("a0", parent.getHash(),
                parentSibling.getHash());
        grandParent.updateSolid(false);
        grandParent.store(tangle, snapshotProvider.getInitialSnapshot());

        txValidator.addSolidTransaction(leftChildLeaf.getHash());
        while (!txValidator.isNewSolidTxSetsEmpty()) {
            txValidator.propagateSolidTransactions();
        }
        System.out.println("transactionPropagationTest");
        parent = TransactionViewModel.fromHash(tangle, parent.getHash());
        assertTrue("Parent tx was expected to be solid", parent.isSolid());
        System.out.println("Parent is solid: " + parent.isSolid());
        grandParent = TransactionViewModel.fromHash(tangle, grandParent.getHash());
        assertTrue("Grandparent  was expected to be solid", grandParent.isSolid());
        System.out.println("Grandparent is solid: " + grandParent.isSolid());
    }

    @Test
    public void transactionPropagationFailureTest() throws Exception {
        System.out.println("transactionPropagationFailureTest");
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
            txValidator.propagateSolidTransactions();
        }

        parent = TransactionViewModel.fromHash(tangle, parent.getHash());
        System.out.println("Parent is solid: " + parent.isSolid());
        assertTrue("Parent tx was expected to be solid", parent.isSolid());
        grandParent = TransactionViewModel.fromHash(tangle, grandParent.getHash());
        assertFalse("GrandParent tx was expected to be not solid", grandParent.isSolid());
        System.out.println("Grandparent is solid: " + grandParent.isSolid());
    }

}

