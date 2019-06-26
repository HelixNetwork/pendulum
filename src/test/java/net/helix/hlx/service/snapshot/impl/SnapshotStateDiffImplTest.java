package net.helix.hlx.service.snapshot.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

import net.helix.hlx.TransactionTestUtils;
import net.helix.hlx.model.Hash;


public class SnapshotStateDiffImplTest {

    private static final Hash A = TransactionTestUtils.getTransactionHash();
    private static final Hash B = TransactionTestUtils.getTransactionHash();
    private static final Hash C = TransactionTestUtils.getTransactionHash();
    
    @Test
    public void getBalanceChanges() {
        SnapshotStateDiffImpl stateDiff = new SnapshotStateDiffImpl(Collections.EMPTY_MAP);
        Map<Hash, Long> change = stateDiff.getBalanceChanges();
        change.put(A, 1L);
        
        assertNotEquals("Changes to the statediff balance changes shouldnt reflect on the original state", 
                stateDiff.getBalanceChanges().size(), change.size());
    }

    @Test
    public void isConsistent() {

        Map<Hash, Long> change1 = new HashMap<>();
        change1.put(A, 1L);
        change1.put(B, 5L);
        change1.put(C, -6L);
        SnapshotStateDiffImpl stateDiff = new SnapshotStateDiffImpl(change1);
        assertTrue("Sum of diffs should be 0", stateDiff.isConsistent());
        
        stateDiff = new SnapshotStateDiffImpl(Collections.EMPTY_MAP);
        assertTrue("Empty diff should be consisntent as sum is 0", stateDiff.isConsistent());
        
        Map<Hash, Long> change2 = new HashMap<>();
        change2.put(A, 1L);
        change2.put(B, 5L);
        stateDiff = new SnapshotStateDiffImpl(change2);
        
        assertFalse("Diff sum not 0 shouldnt be consistent", stateDiff.isConsistent());
    }
    
}
