package net.helix.hlx.controllers;

import net.helix.hlx.conf.MainnetConfig;
import net.helix.hlx.crypto.SpongeFactory;
import net.helix.hlx.model.Hash;
import net.helix.hlx.model.HashFactory;
import net.helix.hlx.model.TransactionHash;
import net.helix.hlx.model.persistables.Transaction;
import net.helix.hlx.service.snapshot.SnapshotProvider;
import net.helix.hlx.service.snapshot.impl.SnapshotProviderImpl;
import net.helix.hlx.storage.Tangle;
import net.helix.hlx.storage.rocksDB.RocksDBPersistenceProvider;
import org.junit.Assert;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Created by paul on 3/5/17 for iri.
 */
public class TransactionViewModelTest {

    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    Logger log = LoggerFactory.getLogger(TransactionViewModelTest.class);
    private static Tangle tangle = new Tangle();
    private static SnapshotProvider snapshotProvider;

    private static final Random seed = new Random();

    public static void setUp() throws Exception {
        dbFolder.create();
        logFolder.create();
        RocksDBPersistenceProvider rocksDBPersistenceProvider;
        rocksDBPersistenceProvider =  new RocksDBPersistenceProvider(
                dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath(),1000,
                Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY);
        tangle.addPersistenceProvider(rocksDBPersistenceProvider);
        tangle.init();
        snapshotProvider = new SnapshotProviderImpl().init(new MainnetConfig());
    }

    public static void tearDown() throws Exception {
        tangle.shutdown();
        snapshotProvider.shutdown();
        dbFolder.delete();
        logFolder.delete();
    }

    public void getBundleTransactions() throws Exception {
    }

    public void getBranchTransaction() throws Exception {
    }

    public void getTrunkTransaction() throws Exception {
    }

    public void getApprovers() throws Exception {
        TransactionViewModel transactionViewModel, otherTxVM, trunkTx, branchTx;


        byte[] bytes = getRandomTransactionBytes();
        trunkTx = new TransactionViewModel(bytes, TransactionHash.calculate(SpongeFactory.Mode.S256, bytes));

        branchTx = new TransactionViewModel(bytes, TransactionHash.calculate(SpongeFactory.Mode.S256, bytes));

        byte[] childTx = getRandomTransactionBytes();
        System.arraycopy(trunkTx.getHash().bytes(), 0, childTx, TransactionViewModel.TRUNK_TRANSACTION_OFFSET, TransactionViewModel.TRUNK_TRANSACTION_SIZE);
        System.arraycopy(branchTx.getHash().bytes(), 0, childTx, TransactionViewModel.BRANCH_TRANSACTION_OFFSET, TransactionViewModel.BRANCH_TRANSACTION_SIZE);
        transactionViewModel = new TransactionViewModel(childTx, TransactionHash.calculate(SpongeFactory.Mode.S256, childTx));

        childTx = getRandomTransactionBytes();
        System.arraycopy(trunkTx.getHash().bytes(), 0, childTx, TransactionViewModel.TRUNK_TRANSACTION_OFFSET, TransactionViewModel.TRUNK_TRANSACTION_SIZE);
        System.arraycopy(branchTx.getHash().bytes(), 0, childTx, TransactionViewModel.BRANCH_TRANSACTION_OFFSET, TransactionViewModel.BRANCH_TRANSACTION_SIZE);
        otherTxVM = new TransactionViewModel(childTx, TransactionHash.calculate(SpongeFactory.Mode.S256, childTx));

        otherTxVM.store(tangle, snapshotProvider.getInitialSnapshot());
        transactionViewModel.store(tangle, snapshotProvider.getInitialSnapshot());
        trunkTx.store(tangle, snapshotProvider.getInitialSnapshot());
        branchTx.store(tangle, snapshotProvider.getInitialSnapshot());

        Set<Hash> approvers = trunkTx.getApprovers(tangle).getHashes();
        assertNotEquals(approvers.size(), 0);
    }

    public void fromHash() throws Exception {

    }

    public void fromHash1() throws Exception {

    }

    public void update() throws Exception {

    }

    // TODO @fsbbn
    public void getBytes() throws Exception {
        /*
        for(int i=0; i++ < 1000;) {
            int[] trits = getRandomTransactionTrits(seed);
            System.arraycopy(new int[TransactionViewModel.VALUE_SIZE], 0, trits, TransactionViewModel.VALUE_OFFSET, TransactionViewModel.VALUE_SIZE);
            Converter.copyTrits(seed.nextLong(), trits, TransactionViewModel.VALUE_OFFSET, TransactionViewModel.VALUE_USABLE_SIZE);
            TransactionViewModel transactionViewModel = new TransactionViewModel(trits);
            transactionViewModel.store();
            assertArrayEquals(transactionViewModel.getBytes(), TransactionViewModel.fromHash(transactionViewModel.getHash()).getBytes());
        }
        */
    }

    public void getHash() throws Exception {

    }

    public void getAddress() throws Exception {

    }

    public void getTag() throws Exception {

    }

    public void getBundleHash() throws Exception {

    }

    public void getTrunkTransactionHash() throws Exception {
    }

    public void getBranchTransactionHash() throws Exception {

    }

    public void getValue() throws Exception {

    }

    public void value() throws Exception {

    }

    public void setValidity() throws Exception {

    }

    public void getValidity() throws Exception {

    }

    public void getCurrentIndex() throws Exception {

    }

    public void getLastIndex() throws Exception {

    }

    public void mightExist() throws Exception {

    }

    public void update1() throws Exception {

    }

    public void setAnalyzed() throws Exception {

    }

    public void dump() throws Exception {

    }

    public void store() throws Exception {

    }

    public void updateTips() throws Exception {

    }

    public void updateReceivedTransactionCount() throws Exception {

    }

    public void updateApprovers() throws Exception {

    }

    public void hashesFromQuery() throws Exception {

    }

    public void approversFromHash() throws Exception {

    }

    public void fromTag() throws Exception {

    }

    public void fromBundle() throws Exception {

    }

    public void fromAddress() throws Exception {

    }

    public void getTransactionAnalyzedFlag() throws Exception {

    }

    public void getType() throws Exception {

    }

    public void setArrivalTime() throws Exception {

    }

    public void getArrivalTime() throws Exception {

    }

    public void updateHeightShouldWork() throws Exception {
        int count = 4;
        TransactionViewModel[] transactionViewModels = new TransactionViewModel[count];
        Hash hash = getRandomTransactionHash();
        transactionViewModels[0] = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(Hash.NULL_HASH,
                Hash.NULL_HASH), hash);
        transactionViewModels[0].store(tangle, snapshotProvider.getInitialSnapshot());
        for(int i = 0; ++i < count; ) {
            transactionViewModels[i] = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(hash,
                    Hash.NULL_HASH), hash = getRandomTransactionHash());
            transactionViewModels[i].store(tangle, snapshotProvider.getInitialSnapshot());
        }

        transactionViewModels[count-1].updateHeights(tangle, snapshotProvider.getInitialSnapshot());

        for(int i = count; i > 1; ) {
            assertEquals(i, TransactionViewModel.fromHash(tangle, transactionViewModels[--i].getHash()).getHeight());
        }
    }

    public void updateHeightPrefilledSlotShouldFail() throws Exception {
        int count = 4;
        TransactionViewModel[] transactionViewModels = new TransactionViewModel[count];
        Hash hash = getRandomTransactionHash();
        for(int i = 0; ++i < count; ) {
            transactionViewModels[i] = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(hash,
                    Hash.NULL_HASH), hash = getRandomTransactionHash());
            transactionViewModels[i].store(tangle, snapshotProvider.getInitialSnapshot());
        }

        transactionViewModels[count-1].updateHeights(tangle, snapshotProvider.getInitialSnapshot());

        for(int i = count; i > 1; ) {
            assertEquals(0, TransactionViewModel.fromHash(tangle, transactionViewModels[--i].getHash()).getHeight());
        }
    }

    public void findShouldBeSuccessful() throws Exception {
        byte[] bytes = getRandomTransactionBytes();
        TransactionViewModel transactionViewModel = new TransactionViewModel(bytes, TransactionHash.calculate(SpongeFactory.Mode.S256, bytes));
        transactionViewModel.store(tangle, snapshotProvider.getInitialSnapshot());
        Hash hash = transactionViewModel.getHash();
        TransactionViewModel tvm = TransactionViewModel.find(tangle, Arrays.copyOf(hash.bytes(), MainnetConfig.Defaults.REQ_HASH_SIZE));
        Assert.assertArrayEquals(tvm.getBytes(), transactionViewModel.getBytes());
    }

    public void findShouldReturnNull() throws Exception {
        byte[] bytes = getRandomTransactionBytes();
        TransactionViewModel transactionViewModel = new TransactionViewModel(bytes, TransactionHash.calculate(SpongeFactory.Mode.S256, bytes));
        bytes = getRandomTransactionBytes();
        TransactionViewModel transactionViewModelNoSave = new TransactionViewModel(bytes, TransactionHash.calculate(SpongeFactory.Mode.S256, bytes));
        transactionViewModel.store(tangle, snapshotProvider.getInitialSnapshot());
        Hash hash = transactionViewModelNoSave.getHash();
        Assert.assertFalse(Arrays.equals(TransactionViewModel.find(tangle, Arrays.copyOf(hash.bytes(), MainnetConfig.Defaults.REQ_HASH_SIZE)).getBytes(), transactionViewModel.getBytes()));
    }


    public void testManyTXInDB() throws Exception {
        int i, j;
        LinkedList<Hash> hashes = new LinkedList<>();
        Hash hash;
        hash = getRandomTransactionHash();
        hashes.add(hash);
        long start, diff, diffget;
        long subSumDiff=0,maxdiff=0, sumdiff = 0;
        int max = 990 * 1000;
        int interval1 = 50;
        int interval = interval1*10;
        log.info("Starting Test. #TX: {}", TransactionViewModel.getNumberOfStoredTransactions(tangle));
        new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(Hash.NULL_HASH, Hash.NULL_HASH), hash).store(tangle, snapshotProvider.getInitialSnapshot());
        TransactionViewModel transactionViewModel;
        boolean pop = false;
        for (i = 0; i++ < max;) {
            hash = getRandomTransactionHash();
            j = hashes.size();
            transactionViewModel = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(hashes.get(seed.nextInt(j)), hashes.get(seed.nextInt(j))), hash);
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

            if(i % interval1 == 0) {
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

    private Transaction getRandomTransaction(Random seed) {
        Transaction transaction = new Transaction();
        byte[] bytes = new byte[TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE];
        seed.nextBytes(bytes);
        transaction.bytes = bytes;
        return transaction;
    }
    public static byte[] getRandomTransactionWithTrunkAndBranch(Hash trunk, Hash branch) {
        byte[] bytes = getRandomTransactionBytes();
        System.arraycopy(trunk.bytes(), 0, bytes, TransactionViewModel.TRUNK_TRANSACTION_OFFSET,
                TransactionViewModel.TRUNK_TRANSACTION_SIZE);
        System.arraycopy(branch.bytes(), 0, bytes, TransactionViewModel.BRANCH_TRANSACTION_OFFSET,
                TransactionViewModel.BRANCH_TRANSACTION_SIZE);
        return bytes;
    }

    public static byte[] getRandomTransactionBytes() {
        byte[] bytes = new byte[TransactionViewModel.SIZE];
        // TODO generate array of random ints in 0x10 range.
        // seed.nextInt(0x10)+0x10
        seed.nextBytes(bytes);
        return bytes;
    }
    public static Hash getRandomTransactionHash() {
        byte[] bytes = new byte[Hash.SIZE_IN_BYTES];
        seed.nextBytes(bytes);
        return HashFactory.TRANSACTION.create(bytes);
    }
}
