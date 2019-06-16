package net.helix.hlx.network.impl;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import static org.mockito.Mockito.when;

import net.helix.hlx.controllers.TipsViewModel;
import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.model.Hash;
import net.helix.hlx.model.persistables.Transaction;
import net.helix.hlx.network.Node;
import net.helix.hlx.network.TransactionRequester;
import net.helix.hlx.service.snapshot.SnapshotProvider;
import net.helix.hlx.storage.Tangle;

import net.helix.hlx.TangleMockUtils;
import static net.helix.hlx.TransactionTestUtils.getTransaction;
import static net.helix.hlx.TransactionTestUtils.get0Transaction;
import static net.helix.hlx.TransactionTestUtils.buildTransaction;
import static net.helix.hlx.TransactionTestUtils.getTransactionHash;


public class TransactionRequesterWorkerImplTest {

    //Good
    private static final TransactionViewModel TVMRandomNull = new TransactionViewModel(
            getTransaction(), Hash.NULL_HASH);
    private static final TransactionViewModel TVMRandomNotNull = new TransactionViewModel(
            getTransaction(), getTransactionHash());
    private static final TransactionViewModel TVMAll0Null = new TransactionViewModel(
            get0Transaction(), Hash.NULL_HASH);
    private static final TransactionViewModel TVMAll0NotNull = new TransactionViewModel(
            get0Transaction(), getTransactionHash()); 
    
    //Bad
    private static final TransactionViewModel TVMNullNull = new TransactionViewModel((Transaction)null, Hash.NULL_HASH); 
    
    @Rule 
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private static SnapshotProvider snapshotProvider;
    
    private static TransactionRequester requester;
    private static TransactionRequesterWorkerImpl worker;

    @Mock
    private Tangle tangle;
    
    @Mock
    private Node node;
    
    @Mock
    private TipsViewModel tipsVM;

    @Before
    public void before() {
        requester = new TransactionRequester(tangle, snapshotProvider);
        
        worker = new TransactionRequesterWorkerImpl();
        worker.init(tangle, requester, tipsVM, node);
    }
    
    @After
    public void tearDown() {
        worker.shutdown();
    }
    
    @Test
    public void workerActive() throws Exception {
        assertFalse("Empty worker should not be active", worker.isActive());
        
        fillRequester();
        
        assertTrue("Worker should be active when it requester is over threshold", worker.isActive());
    }
    
    @Test
    public void processRequestQueueTest() throws Exception {
        //getTransactionToSendWithRequest starts reading from solid tips, so mock data from that call
        when(tipsVM.getRandomSolidTipHash()).thenReturn(
                TVMRandomNull.getHash(), 
                TVMRandomNotNull.getHash(), 
                TVMAll0Null.getHash(), 
                TVMAll0NotNull.getHash(),
                TVMNullNull.getHash(),
                null);
        
        assertFalse("Unfilled queue shouldnt process", worker.processRequestQueue());
        
        //Requester never goes down since nodes don't really request
        fillRequester();
        
        TangleMockUtils.mockTransaction(tangle, TVMRandomNull.getHash(), buildTransaction(TVMRandomNull.getBytes()));
        assertTrue("Null transaction hash should be processed", worker.processRequestQueue());
        
        TangleMockUtils.mockTransaction(tangle, TVMRandomNotNull.getHash(), buildTransaction(TVMRandomNotNull.getBytes()));
        assertTrue("Not null transaction hash should be processed", worker.processRequestQueue());
       
        TangleMockUtils.mockTransaction(tangle, TVMAll0Null.getHash(), buildTransaction(TVMAll0Null.getBytes()));
        assertTrue("Null transaction hash should be processed", worker.processRequestQueue());
        
        TangleMockUtils.mockTransaction(tangle, TVMAll0NotNull.getHash(), buildTransaction(TVMAll0NotNull.getBytes()));
        assertTrue("All 9s transaction should be processed", worker.processRequestQueue());
        
        // Null gets loaded as all 0, so type is 0 -> Filled
        TangleMockUtils.mockTransaction(tangle, TVMNullNull.getHash(), null);
        assertTrue("0 transaction should be processed", worker.processRequestQueue());
        
        // null -> NULL_HASH -> gets loaded as all 0 -> filled
        assertTrue("Null transaction should be processed", worker.processRequestQueue());
    }

    @Test
    public void validTipToAddTest() throws Exception {
       assertTrue("Null transaction hash should always be accepted", worker.isValidTransaction(TVMRandomNull));
       assertTrue("Not null transaction hash should always be accepted", worker.isValidTransaction(TVMRandomNotNull));
       assertTrue("Null transaction hash should always be accepted", worker.isValidTransaction(TVMAll0Null));
       assertTrue("All 9s transaction should be accepted", worker.isValidTransaction(TVMAll0NotNull));
       
       // Null gets loaded as all 0, so type is 0 -> Filled
       assertTrue("0 transaction should be accepted", worker.isValidTransaction(TVMNullNull));
       
       assertFalse("Null transaction should not be accepted", worker.isValidTransaction(null));
    }
    
    private void fillRequester() throws Exception {
        for (int i=0; i< TransactionRequesterWorkerImpl.REQUESTER_THREAD_ACTIVATION_THRESHOLD; i++) {
            addRequest();
        }
    }
    
    private void addRequest() throws Exception {
        Hash randomHash = getTransactionHash();
        TangleMockUtils.mockTransaction(tangle, randomHash);
        requester.requestTransaction(randomHash, false);
    } 
 
}
