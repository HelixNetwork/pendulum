package net.helix.pendulum.network;

import net.helix.pendulum.Pendulum;
import net.helix.pendulum.conf.MainnetConfig;
import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.network.impl.RequestQueueImpl;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.service.snapshot.impl.SnapshotProviderImpl;
import net.helix.pendulum.storage.Tangle;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.helix.pendulum.TransactionTestUtils.getTransactionHash;

public class TransactionRequesterTest {


    private static Tangle tangle;
    private static SnapshotProvider snapshotProvider;

    @Before
    public void setUp() throws Exception {
        PendulumConfig config = new MainnetConfig();

        snapshotProvider = new SnapshotProviderImpl().init(config);
        tangle = new Tangle();

        Pendulum.ServiceRegistry.get().register(SnapshotProvider.class, snapshotProvider);
        Pendulum.ServiceRegistry.get().register(Tangle.class, tangle);
        Pendulum.ServiceRegistry.get().register(PendulumConfig.class, config);


    }

    @After
    public void shutdown() throws Exception {
        snapshotProvider.shutdown();
    }

//    @Test
    public void init() throws Exception {
        // TODO implementation needed
    }

//    @Test
    public void rescanTransactionsToRequest() throws Exception {
        // TODO implementation needed
    }

//    @Test
    public void getRequestedTransactions() throws Exception {
        // TODO implementation needed
    }

//    @Test
    public void numberOfTransactionsToRequest() throws Exception {
        // TODO implementation needed
    }

//    @Test
    public void clearTransactionRequest() throws Exception {
        // TODO implementation needed
    }

//    @Test
    public void requestTransaction() throws Exception {
        // TODO implementation needed
    }

//    @Test
    public void transactionToRequest() throws Exception {
        // TODO implementation needed
    }

//    @Test
    public void checkSolidity() throws Exception {
        // TODO implementation needed
    }

//    @Test
    public void instance() throws Exception {
        // TODO implementation needed
    }

    @Test
    public void popEldestTransactionToRequest() throws Exception {
        RequestQueueImpl txReq = new RequestQueueImpl();
        txReq.init();

        // Add some Txs to the pool and see if the method pops the eldest one
        Hash eldest = getTransactionHash();
        txReq.enqueueTransaction(eldest, false);
        txReq.enqueueTransaction(getTransactionHash(), false);
        txReq.enqueueTransaction(getTransactionHash(), false);
        txReq.enqueueTransaction(getTransactionHash(), false);

        txReq.popEldestTransactionToRequest();
        // Check that the transaction is there no more
        Assert.assertFalse(txReq.isTransactionRequested(eldest, false));
    }

    @Test
    public void transactionRequestedFreshness() throws Exception {
        // Add some Txs to the pool and see if the method pops the eldest one
        List<Hash> eldest = new ArrayList<>(Arrays.asList(
                getTransactionHash(),
                getTransactionHash(),
                getTransactionHash()
        ));
        RequestQueueImpl txReq = new RequestQueueImpl();
        txReq.init();

        int capacity = RequestQueueImpl.MAX_TX_REQ_QUEUE_SIZE;
        //fill tips list
        for (int i = 0; i < 3; i++) {
            txReq.enqueueTransaction(eldest.get(i), false);
        }
        for (int i = 0; i < capacity; i++) {
            Hash hash = getTransactionHash();
            txReq.enqueueTransaction(hash, false);
        }

        //check that limit wasn't breached
        Assert.assertEquals("Queue capacity breached!!", capacity, txReq.size());
        // None of the eldest transactions should be in the pool
        for (int i = 0; i < 3; i++) {
            Assert.assertFalse("Old transaction has been requested", txReq.isTransactionRequested(eldest.get(i), false));
        }
    }

    @Test
    public void nonMilestoneCapacityLimited() throws Exception {
        RequestQueueImpl txReq = new RequestQueueImpl();
        txReq.init();

        int capacity = RequestQueueImpl.MAX_TX_REQ_QUEUE_SIZE;
        //fill tips list
        for (int i = 0; i < capacity * 2 ; i++) {
            Hash hash = getTransactionHash();
            txReq.enqueueTransaction(hash,false);
        }
        //check that limit wasn't breached
        Assert.assertEquals(capacity, txReq.size());
    }

    @Test
    public void milestoneCapacityNotLimited() throws Exception {
        RequestQueueImpl txReq = new RequestQueueImpl();
        txReq.init();

        int capacity = RequestQueueImpl.MAX_TX_REQ_QUEUE_SIZE;
        //fill tips list
        for (int i = 0; i < capacity * 2 ; i++) {
            Hash hash = getTransactionHash();
            txReq.enqueueTransaction(hash,true);
        }
        //check that limit was surpassed
        Assert.assertEquals(capacity * 2, txReq.size());
    }

    @Test
    public void mixedCapacityLimited() throws Exception {
        RequestQueueImpl txReq = new RequestQueueImpl();
        txReq.init();

        int capacity = RequestQueueImpl.MAX_TX_REQ_QUEUE_SIZE;
        //fill tips list
        for (int i = 0; i < capacity * 4 ; i++) {
            Hash hash = getTransactionHash();
            txReq.enqueueTransaction(hash, (i % 2 == 1));

        }
        //check that limit wasn't breached
        Assert.assertEquals(capacity + capacity * 2, txReq.size());
    }
 
}
