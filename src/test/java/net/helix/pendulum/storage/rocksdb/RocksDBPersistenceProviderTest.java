package net.helix.pendulum.storage.rocksdb;

import net.helix.pendulum.model.IntegerIndex;
import net.helix.pendulum.model.persistables.Transaction;
import net.helix.pendulum.storage.Indexable;
import net.helix.pendulum.storage.Persistable;
import net.helix.pendulum.storage.Tangle;
import net.helix.pendulum.utils.Pair;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class RocksDBPersistenceProviderTest {
    
    private static RocksDBPersistenceProvider rocksDBPersistenceProvider;
    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();


    @BeforeClass
    public static void setupDB() throws Exception {
        dbFolder.create();
        logFolder.create();
        rocksDBPersistenceProvider =  new RocksDBPersistenceProvider(
                dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath(),
                1000, Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY);
        rocksDBPersistenceProvider.init();
    }
    
    @AfterClass
    public static void shutdownDB() throws Exception {
        rocksDBPersistenceProvider.shutdown();
        dbFolder.delete();
        logFolder.delete();
        rocksDBPersistenceProvider = null;
    }
    
    @Before
    public void setUp() throws Exception {
        rocksDBPersistenceProvider.clear(Transaction.class);
    }

    @Test
    public void doSaveAndDeleteBatchTest() throws Exception {
        Persistable tx = new Transaction();
        byte[] bytes = new byte[Transaction.SIZE];
        Arrays.fill(bytes, (byte) 1);
        tx.read(bytes);
        tx.readMetadata(bytes);
        List<Pair<Indexable, Persistable>> models = IntStream.range(1, 1000)
                .mapToObj(i -> new Pair<>((Indexable) new IntegerIndex(i), tx))
                .collect(Collectors.toList());
        rocksDBPersistenceProvider.saveBatch(models);

        List<Pair<Indexable, ? extends Class<? extends Persistable>>> modelsToDelete = models.stream()
                .filter(entry -> ((IntegerIndex) entry.low).getValue() < 900)
                .map(entry -> new Pair<>(entry.low, entry.hi.getClass()))
                .collect(Collectors.toList());
        rocksDBPersistenceProvider.deleteBatch(modelsToDelete);

        for (Pair<Indexable, ? extends Class<? extends Persistable>> model : modelsToDelete) {
            Assert.assertNull("value at index " + ((IntegerIndex) model.low).getValue() + " should be deleted",
                    rocksDBPersistenceProvider.get(model.hi, model.low).bytes());
        }

        List<IntegerIndex> indexes = IntStream.range(900, 1000)
                .mapToObj(i -> new IntegerIndex(i))
                .collect(Collectors.toList());
        for (IntegerIndex index : indexes) {
            Assert.assertArrayEquals("saved bytes are not as expected in index " + index.getValue(), tx.bytes(),
                    rocksDBPersistenceProvider.get(Transaction.class, index).bytes());
        }
    }

}
