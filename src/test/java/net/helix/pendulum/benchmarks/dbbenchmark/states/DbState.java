package net.helix.pendulum.benchmarks.dbbenchmark.states;

import net.helix.pendulum.TransactionTestUtils;
import net.helix.pendulum.conf.BasePendulumConfig;
import net.helix.pendulum.conf.MainnetConfig;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.persistables.Transaction;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.service.snapshot.impl.SnapshotProviderImpl;
import net.helix.pendulum.storage.PersistenceProvider;
import net.helix.pendulum.storage.Tangle;
import net.helix.pendulum.storage.rocksdb.RocksDBPersistenceProvider;
import org.apache.commons.io.FileUtils;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@State(Scope.Benchmark)
public abstract class DbState {
    private final File dbFolder = new File("db-bench");
    private final File logFolder = new File("db-log-bench");

    private Tangle tangle;
    private SnapshotProvider snapshotProvider;
    private List<TransactionViewModel> transactions;

    @Param({"10", "100", "500", "1000", "3000"})
    private int numTxsToTest;

    public void setUp() throws Exception {
        System.out.println("-----------------------trial setup--------------------------------");
        boolean mkdirs = dbFolder.mkdirs();
        if (!mkdirs) {
            throw new IllegalStateException("db didn't start with a clean slate. Please delete "
                    + dbFolder.getAbsolutePath());
        }
        logFolder.mkdirs();
        PersistenceProvider dbProvider = new RocksDBPersistenceProvider(
                dbFolder.getAbsolutePath(), logFolder.getAbsolutePath(),  BasePendulumConfig.Defaults.DB_CACHE_SIZE, Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY);
        dbProvider.init();
        tangle = new Tangle();
        snapshotProvider = new SnapshotProviderImpl().init(new MainnetConfig());
        tangle.addPersistenceProvider(dbProvider);
        String hex = "";
        System.out.println("numTxsToTest = [" + numTxsToTest + "]");
        transactions = new ArrayList<>(numTxsToTest);
        for (int i = 0; i < numTxsToTest; i++) {
            hex = TransactionTestUtils.nextWord(hex, i);
            TransactionViewModel tvm = TransactionTestUtils.createTransactionWithHex(hex);
            transactions.add(tvm);
        }
        transactions = Collections.unmodifiableList(transactions);
    }

    public void shutdown() throws Exception {
        System.out.println("-----------------------trial shutdown--------------------------------");
        tangle.shutdown();
        snapshotProvider.shutdown();
        FileUtils.forceDelete(dbFolder);
        FileUtils.forceDelete(logFolder);
    }

    public void clearDb() throws Exception {
        System.out.println("-----------------------iteration shutdown--------------------------------");
        tangle.clearColumn(Transaction.class);
        tangle.clearMetadata(Transaction.class);
    }

    public Tangle getTangle() {
        return tangle;
    }

    public SnapshotProvider getSnapshotProvider() {
        return snapshotProvider;
    }

    public List<TransactionViewModel> getTransactions() {
        return transactions;
    }
}
