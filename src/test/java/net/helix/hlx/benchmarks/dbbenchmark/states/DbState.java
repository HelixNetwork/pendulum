package net.helix.hlx.benchmarks.dbbenchmark.states;

import net.helix.hlx.TransactionTestUtils;
import net.helix.hlx.conf.BaseHelixConfig;
import net.helix.hlx.conf.MainnetConfig;
import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.model.persistables.Transaction;
import net.helix.hlx.service.snapshot.SnapshotProvider;
import net.helix.hlx.service.snapshot.impl.SnapshotProviderImpl;
import net.helix.hlx.storage.PersistenceProvider;
import net.helix.hlx.storage.Tangle;
import net.helix.hlx.storage.rocksDB.RocksDBPersistenceProvider;
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

    public void setup() throws Exception {
        System.out.println("-----------------------trial setup--------------------------------");
        boolean mkdirs = dbFolder.mkdirs();
        if (!mkdirs) {
            throw new IllegalStateException("db didn't start with a clean slate. Please delete "
                    + dbFolder.getAbsolutePath());
        }
        logFolder.mkdirs();
        PersistenceProvider dbProvider = new RocksDBPersistenceProvider(
                dbFolder.getAbsolutePath(), logFolder.getAbsolutePath(),  BaseHelixConfig.Defaults.DB_CACHE_SIZE, Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY);
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
