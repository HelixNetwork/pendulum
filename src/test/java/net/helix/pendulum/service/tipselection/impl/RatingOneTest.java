package net.helix.pendulum.service.tipselection.impl;

import net.helix.pendulum.conf.MainnetConfig;
import net.helix.pendulum.controllers.TransactionViewModel;

import net.helix.pendulum.model.Hash;

import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.service.snapshot.impl.SnapshotProviderImpl;
import net.helix.pendulum.service.tipselection.RatingCalculator;
import net.helix.pendulum.storage.Tangle;
import net.helix.pendulum.storage.rocksdb.RocksDBPersistenceProvider;

import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static net.helix.pendulum.TransactionTestUtils.*;


public class RatingOneTest {

    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static final String TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT =
            "tx%d cumulative weight is not as expected";
    private static Tangle tangle;
    private static SnapshotProvider snapshotProvider;
    private static RatingCalculator rating;

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
                dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath(),1000,
                Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY));
        tangle.init();
        rating = new RatingOne(tangle);
    }

    @Test
    public void calculateTest() throws Exception {
        TransactionViewModel transaction = new TransactionViewModel(getTransactionBytes(), getTransactionHash());
        TransactionViewModel transaction1 = new TransactionViewModel(getTransactionBytesWithTrunkAndBranch(transaction.getHash(),
                transaction.getHash()), getTransactionHash());
        TransactionViewModel transaction2 = new TransactionViewModel(getTransactionBytesWithTrunkAndBranch(transaction1.getHash(),
                transaction1.getHash()), getTransactionHash());
        TransactionViewModel transaction3 = new TransactionViewModel(getTransactionBytesWithTrunkAndBranch(transaction2.getHash(),
                transaction1.getHash()), getTransactionHash());
        TransactionViewModel transaction4 = new TransactionViewModel(getTransactionBytesWithTrunkAndBranch(transaction2.getHash(),
                transaction3.getHash()), getTransactionHash());
        transaction.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction1.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction2.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction3.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction4.store(tangle, snapshotProvider.getInitialSnapshot());
        Map<Hash, Integer> rate = rating.calculate(transaction.getHash());

        Assert.assertEquals(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT,
                1, rate.get(transaction4.getHash()).intValue());
        Assert.assertEquals(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT,
                1, rate.get(transaction3.getHash()).intValue());
        Assert.assertEquals(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT,
                1, rate.get(transaction2.getHash()).intValue());
        Assert.assertEquals(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT,
                1, rate.get(transaction1.getHash()).intValue());
        Assert.assertEquals(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT,
                1, rate.get(transaction.getHash()).intValue());
    }

}
