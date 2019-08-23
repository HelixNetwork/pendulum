package net.helix.hlx.controllers;

import net.helix.hlx.conf.MainnetConfig;
import net.helix.hlx.crypto.SpongeFactory;
import net.helix.hlx.model.Hash;
import net.helix.hlx.model.TransactionHash;
import net.helix.hlx.service.snapshot.SnapshotProvider;
import net.helix.hlx.service.snapshot.impl.SnapshotProviderImpl;
import net.helix.hlx.storage.Tangle;
import net.helix.hlx.storage.rocksdb.RocksDBPersistenceProvider;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;

import static net.helix.hlx.TransactionTestUtils.*;


public class TransactionViewModelTest {

    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private Logger log = LoggerFactory.getLogger(TransactionViewModelTest.class);
    private static final Tangle tangle = new Tangle();
    private static SnapshotProvider snapshotProvider;

    private static final Random seed = new Random();

    @Before
    public void setup() throws Exception {
        dbFolder.create();
        logFolder.create();
        RocksDBPersistenceProvider rocksDBPersistenceProvider;
        rocksDBPersistenceProvider =  new RocksDBPersistenceProvider(
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

    //@Test
    public void getBundleTransactions() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void getBranchTransaction() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void getTrunkTransaction() throws Exception {
        // TODO implementation needed
    }

    @Test
    public void getApproversTest() throws Exception {
        TransactionViewModel transactionViewModel, otherTxVM, trunkTx, branchTx;

        byte[] bytes = getTransactionBytes();
        trunkTx = new TransactionViewModel(bytes, TransactionHash.calculate(SpongeFactory.Mode.S256, bytes));
        branchTx = new TransactionViewModel(bytes, TransactionHash.calculate(SpongeFactory.Mode.S256, bytes));

        byte[] childTx = getTransactionBytes();
        System.arraycopy(trunkTx.getHash().bytes(), 0, childTx, TransactionViewModel.TRUNK_TRANSACTION_OFFSET, TransactionViewModel.TRUNK_TRANSACTION_SIZE);
        System.arraycopy(branchTx.getHash().bytes(), 0, childTx, TransactionViewModel.BRANCH_TRANSACTION_OFFSET, TransactionViewModel.BRANCH_TRANSACTION_SIZE);
        transactionViewModel = new TransactionViewModel(childTx, TransactionHash.calculate(SpongeFactory.Mode.S256, childTx));

        childTx = getTransactionBytes();
        System.arraycopy(trunkTx.getHash().bytes(), 0, childTx, TransactionViewModel.TRUNK_TRANSACTION_OFFSET, TransactionViewModel.TRUNK_TRANSACTION_SIZE);
        System.arraycopy(branchTx.getHash().bytes(), 0, childTx, TransactionViewModel.BRANCH_TRANSACTION_OFFSET, TransactionViewModel.BRANCH_TRANSACTION_SIZE);
        otherTxVM = new TransactionViewModel(childTx, TransactionHash.calculate(SpongeFactory.Mode.S256, childTx));

        otherTxVM.store(tangle, snapshotProvider.getInitialSnapshot());
        transactionViewModel.store(tangle, snapshotProvider.getInitialSnapshot());
        trunkTx.store(tangle, snapshotProvider.getInitialSnapshot());
        branchTx.store(tangle, snapshotProvider.getInitialSnapshot());

        Set<Hash> approvers = trunkTx.getApprovers(tangle).getHashes();
        Assert.assertNotEquals(approvers.size(), 0);
    }

    //@Test
    public void fromHash() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void fromHash1() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void update() throws Exception {
        // TODO implementation needed
    }

    @Test
    public void getBytesTest() throws Exception {
        for(int i=0; i++ < 1000;) {
            byte[] bytes = getTransactionBytes();
            java.nio.ByteBuffer.wrap(bytes, TransactionViewModel.VALUE_OFFSET, TransactionViewModel.VALUE_SIZE).slice().putLong(seed.nextLong());
            Hash hash = getTransactionHash();
            TransactionViewModel transactionViewModel = new TransactionViewModel(bytes, hash);
            transactionViewModel.store(tangle, snapshotProvider.getInitialSnapshot());
            Assert.assertArrayEquals(transactionViewModel.getBytes(), TransactionViewModel.fromHash(tangle, transactionViewModel.getHash()).getBytes());
        }
    }

    //@Test
    public void getHash() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void getAddress() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void getTag() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void getBundleHash() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void getTrunkTransactionHash() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void getBranchTransactionHash() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void getValue() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void value() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void setValidity() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void getValidity() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void getCurrentIndex() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void getLastIndex() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void mightExist() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void update1() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void setAnalyzed() throws Exception {
        // TODO implementation needed
    }


    //@Test
    public void dump() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void store() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void updateTips() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void updateReceivedTransactionCount() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void updateApprovers() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void hashesFromQuery() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void approversFromHash() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void fromTag() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void fromBundle() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void fromAddress() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void getTransactionAnalyzedFlag() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void getType() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void setArrivalTime() throws Exception {
        // TODO implementation needed
    }

    //@Test
    public void getArrivalTime() throws Exception {
        // TODO implementation needed
    }

    @Test
    public void updateHeightShouldWorkTest() throws Exception {
        int count = 4;
        TransactionViewModel[] transactionViewModels = new TransactionViewModel[count];
        Hash hash = getTransactionHash();
        transactionViewModels[0] = new TransactionViewModel(getTransactionBytesWithTrunkAndBranch(Hash.NULL_HASH, Hash.NULL_HASH), hash);
        transactionViewModels[0].store(tangle, snapshotProvider.getInitialSnapshot());
        for(int i = 0; ++i < count; ) {
            transactionViewModels[i] = new TransactionViewModel(getTransactionBytesWithTrunkAndBranch(hash, Hash.NULL_HASH), hash = getTransactionHash());
            transactionViewModels[i].store(tangle, snapshotProvider.getInitialSnapshot());
        }

        transactionViewModels[count-1].updateHeights(tangle, snapshotProvider.getInitialSnapshot());

        for(int i = count; i > 1; ) {
            Assert.assertEquals(i, TransactionViewModel.fromHash(tangle, transactionViewModels[--i].getHash()).getHeight());
        }
    }

    @Test
    public void updateHeightPrefilledSlotShouldFailTest() throws Exception {
        int count = 4;
        TransactionViewModel[] transactionViewModels = new TransactionViewModel[count];
        Hash hash = getTransactionHash();
        for(int i = 0; ++i < count; ) {
            transactionViewModels[i] = new TransactionViewModel(getTransactionBytesWithTrunkAndBranch(hash, Hash.NULL_HASH), hash = getTransactionHash());
            transactionViewModels[i].store(tangle, snapshotProvider.getInitialSnapshot());
        }

        transactionViewModels[count-1].updateHeights(tangle, snapshotProvider.getInitialSnapshot());

        for(int i = count; i > 1; ) {
            Assert.assertEquals(0, TransactionViewModel.fromHash(tangle, transactionViewModels[--i].getHash()).getHeight());
        }
    }

    @Test
    public void findShouldBeSuccessfulTest() throws Exception {
        byte[] bytes = getTransactionBytes();
        TransactionViewModel transactionViewModel = new TransactionViewModel(bytes, TransactionHash.calculate(SpongeFactory.Mode.S256, bytes));
        transactionViewModel.store(tangle, snapshotProvider.getInitialSnapshot());
        Hash hash = transactionViewModel.getHash();
        Assert.assertArrayEquals(TransactionViewModel.find(tangle,
                Arrays.copyOf(hash.bytes(), MainnetConfig.Defaults.REQ_HASH_SIZE)).getBytes(), transactionViewModel.getBytes());
    }

    @Test
    public void findShouldReturnNullTest() throws Exception {
        byte[] bytes = getTransactionBytes();
        TransactionViewModel transactionViewModel = new TransactionViewModel(bytes, TransactionHash.calculate(SpongeFactory.Mode.S256, bytes));
        bytes = getTransactionBytes();
        TransactionViewModel transactionViewModelNoSave = new TransactionViewModel(bytes, TransactionHash.calculate(SpongeFactory.Mode.S256, bytes));
        transactionViewModel.store(tangle, snapshotProvider.getInitialSnapshot());
        Hash hash = transactionViewModelNoSave.getHash();
        Assert.assertFalse(Arrays.equals(TransactionViewModel.find(tangle,
                Arrays.copyOf(hash.bytes(), new MainnetConfig().getRequestHashSize())).getBytes(), transactionViewModel.getBytes()));
    }

    //@Test
    public void testManyTXInDBTest() throws Exception {
        int i, j;
        LinkedList<Hash> hashes = new LinkedList<>();
        Hash hash;
        hash = getTransactionHash();
        hashes.add(hash);
        long start, diff, diffget;
        long subSumDiff=0,maxdiff=0, sumdiff = 0;
        int max = 990 * 1000;
        int interval1 = 50;
        int interval = interval1*10;
        log.info("Starting Test. #TX: {}", TransactionViewModel.getNumberOfStoredTransactions(tangle));
        new TransactionViewModel(getTransactionBytesWithTrunkAndBranch(Hash.NULL_HASH, Hash.NULL_HASH), hash).store(tangle, snapshotProvider.getInitialSnapshot());
        TransactionViewModel transactionViewModel;
        boolean pop = false;
        for (i = 0; i++ < max;) {
            hash = getTransactionHash();
            j = hashes.size();
            transactionViewModel = new TransactionViewModel(getTransactionBytesWithTrunkAndBranch(hashes.get(seed.nextInt(j)), hashes.get(seed.nextInt(j))), hash);
            start = System.nanoTime();
            transactionViewModel.store(tangle, snapshotProvider.getInitialSnapshot());
            diff = System.nanoTime() - start;
            subSumDiff += diff;
            if (diff>maxdiff) {
                maxdiff = diff;
            }
            hash = hashes.get(seed.nextInt(j));
            start = System.nanoTime();
            TransactionViewModel.fromHash(tangle, hash);
            diffget = System.nanoTime() - start;
            hashes.add(hash);
            if(pop || i > 1000) {
                hashes.removeFirst();
            }

            //log.info("{}", new String(new char[(int) ((diff/ 10000))]).replace('\0', '|'));
            if(i % interval1 == 0) {
                //log.info("{}", new String(new char[(int) (diff / 50000)]).replace('\0', '-'));
                //log.info("{}", new String(new char[(int) ((subSumDiff / interval1 / 100000))]).replace('\0', '|'));
                sumdiff += subSumDiff;
                subSumDiff = 0;
            }
            if(i % interval == 0) {
                log.info("Save time for {}: {} us.\tGet Time: {} us.\tMax time: {} us. Average: {}", i,
                        (diff / 1000) , diffget/1000, (maxdiff/ 1000), sumdiff/interval/1000);
                sumdiff = 0;
                maxdiff = 0;
            }
        }
        log.info("Done. #TX: {}", TransactionViewModel.getNumberOfStoredTransactions(tangle));
    }

    @Test
    public void firstShouldFindTxTest() throws Exception {
        byte[] bytes = getTransactionBytes();
        TransactionViewModel transactionViewModel = new TransactionViewModel(bytes, TransactionHash.calculate(SpongeFactory.Mode.S256, bytes));
        transactionViewModel.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel result = TransactionViewModel.first(tangle);
        Assert.assertEquals(transactionViewModel.getHash(), result.getHash());
    }

}
