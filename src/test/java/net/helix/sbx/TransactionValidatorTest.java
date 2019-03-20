package net.helix.sbx;

import net.helix.sbx.controllers.TipsViewModel;
import net.helix.sbx.network.TransactionRequester;
import net.helix.sbx.service.snapshot.SnapshotProvider;
import net.helix.sbx.storage.Tangle;
import net.helix.sbx.storage.rocksDB.RocksDBPersistenceProvider;
import net.helix.sbx.zmq.MessageQ;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import static org.junit.Assert.assertTrue;

/** Created by paul on 5/14/17. */

public class TransactionValidatorTest {

    private static final int MAINNET_MWM = 0;
    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static Tangle tangle;
    private static SnapshotProvider snapshotProvider;
    private static TransactionValidator txValidator;
    /*
    @BeforeClass
    public static void setUp() throws Exception {
        dbFolder.create();
        logFolder.create();
        tangle = new Tangle();
        tangle.addPersistenceProvider(
                new RocksDBPersistenceProvider(
                        dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath(),1000, Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY));
        tangle.init();
        TipsViewModel tipsViewModel = new TipsViewModel();
        MessageQ messageQ = Mockito.mock(MessageQ.class);
        TransactionRequester txRequester = new TransactionRequester(tangle, snapshotProvider, messageQ);
        txValidator = new TransactionValidator(tangle, snapshotProvider, tipsViewModel, txRequester);
        txValidator.setMwm(false, MAINNET_MWM);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        txValidator.shutdown();
        tangle.shutdown();
        dbFolder.delete();
        logFolder.delete();
    }

    @Test
    public void testMinMwm() throws InterruptedException {
        txValidator.shutdown();
        txValidator.init(false, 2);
        assertTrue(txValidator.getMinWeightMagnitude() == 2);
        txValidator.shutdown();
        txValidator.init(false, MAINNET_MWM);
    }
    */

}
