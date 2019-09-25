package net.helix.pendulum.storage;

import net.helix.pendulum.conf.MainnetConfig;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.crypto.SpongeFactory;
import net.helix.pendulum.model.TransactionHash;
import net.helix.pendulum.model.persistables.Tag;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.service.snapshot.impl.SnapshotProviderImpl;
import net.helix.pendulum.storage.rocksdb.RocksDBPersistenceProvider;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Arrays;
import java.util.Random;
import java.util.Set;


public class TangleTest {
    
    private static final Random RND = new Random();

    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static SnapshotProvider snapshotProvider;
    private final Tangle tangle = new Tangle();

    
    @Before
    public void setUp() throws Exception {
        dbFolder.create();
        logFolder.create();
        RocksDBPersistenceProvider rocksDBPersistenceProvider =  new RocksDBPersistenceProvider(
                dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath(),
                1000, Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY);
        tangle.addPersistenceProvider(rocksDBPersistenceProvider);
        tangle.init();
        snapshotProvider = new SnapshotProviderImpl().init(new MainnetConfig());
    }

    @After
    public void shutdown() throws Exception {
        tangle.shutdown();
        snapshotProvider.shutdown();
        dbFolder.delete();
        logFolder.delete();
    }

//    @Test
    public void saveTest() throws Exception {
        // TODO implementation needed
    }

    @Test
    public void getKeysStartingWithValueTest() throws Exception {
        byte[] bytes = new byte[TransactionViewModel.SIZE];
        RND.nextBytes(bytes);
        TransactionViewModel transactionViewModel = new TransactionViewModel(bytes,
                TransactionHash.calculate(SpongeFactory.Mode.S256, bytes));
        transactionViewModel.store(tangle, snapshotProvider.getInitialSnapshot());
        Set<Indexable> tag = tangle.keysStartingWith(Tag.class,
                Arrays.copyOf(transactionViewModel.getTagValue().bytes(), TransactionViewModel.TAG_SIZE - 2));
        Assert.assertNotEquals(tag.size(), 0);
    }

}
