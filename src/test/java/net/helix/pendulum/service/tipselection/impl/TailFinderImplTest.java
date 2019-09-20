package net.helix.pendulum.service.tipselection.impl;

import java.util.Optional;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static net.helix.pendulum.TransactionTestUtils.getTransactionBytes;
import static net.helix.pendulum.TransactionTestUtils.getTransactionHash;
import static net.helix.pendulum.TransactionTestUtils.createBundleHead;
import static net.helix.pendulum.TransactionTestUtils.createTransactionWithTrunkBundleHash;
import static net.helix.pendulum.TransactionTestUtils.getTransactionBytesWithTrunkAndBranch;

import net.helix.pendulum.conf.MainnetConfig;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.service.snapshot.impl.SnapshotProviderImpl;
import net.helix.pendulum.storage.Tangle;
import net.helix.pendulum.storage.rocksdb.RocksDBPersistenceProvider;


public class TailFinderImplTest {

    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static Tangle tangle;
    private static SnapshotProvider snapshotProvider;
    private final TailFinderImpl tailFinder;

    public TailFinderImplTest() {
        tailFinder = new TailFinderImpl(tangle);
    }

    @AfterClass
    public static void shutdown() throws Exception {
        tangle.shutdown();
        snapshotProvider.shutdown();
        dbFolder.delete();
        logFolder.delete();
    }

    @BeforeClass
    public static void setUp() throws Exception {
        tangle = new Tangle();
        snapshotProvider = new SnapshotProviderImpl().init(new MainnetConfig());
        dbFolder.create();
        logFolder.create();
        tangle.addPersistenceProvider( new RocksDBPersistenceProvider(
                dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath(), 1000,
                Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY));
        tangle.init();
    }

    @Test
    public void findTailTest() throws Exception {
        TransactionViewModel txa = new TransactionViewModel(getTransactionBytes(), getTransactionHash());
        txa.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel tx2 = createBundleHead(2);
        tx2.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel tx1 = createTransactionWithTrunkBundleHash(tx2, txa.getHash());
        tx1.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel tx0 = createTransactionWithTrunkBundleHash(tx1, txa.getHash());
        tx0.store(tangle, snapshotProvider.getInitialSnapshot());

        //negative index - make sure we stop at 0
        TransactionViewModel txNeg = createTransactionWithTrunkBundleHash(tx0, txa.getHash());
        txNeg.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel txLateTail = createTransactionWithTrunkBundleHash(tx1, txa.getHash());
        txLateTail.store(tangle, snapshotProvider.getInitialSnapshot());

        Optional<Hash> tail = tailFinder.findTail(tx2.getHash());
        Assert.assertTrue("no tail was found", tail.isPresent());
        Assert.assertEquals("Expected tail not found", tx0.getHash(), tail.get());
    }

    @Test
    public void findMissingTailTest() throws Exception {
        TransactionViewModel txa = new TransactionViewModel(getTransactionBytes(), getTransactionHash());
        txa.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel tx2 = createBundleHead(2);
        tx2.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel tx1 = createTransactionWithTrunkBundleHash(tx2, txa.getHash());
        tx1.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel tx0 = new TransactionViewModel(getTransactionBytesWithTrunkAndBranch(tx1.getHash(), tx2.getHash()),
                getTransactionHash());
        tx0.store(tangle, snapshotProvider.getInitialSnapshot());

        Optional<Hash> tail = tailFinder.findTail(tx2.getHash());
        Assert.assertFalse("tail was found, but should me missing", tail.isPresent());
    }

}
