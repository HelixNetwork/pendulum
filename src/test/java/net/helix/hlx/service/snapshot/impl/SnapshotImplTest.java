package net.helix.hlx.service.snapshot.impl;

import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import net.helix.hlx.TransactionTestUtils;
import net.helix.hlx.model.Hash;
import net.helix.hlx.service.snapshot.Snapshot;
import net.helix.hlx.service.snapshot.SnapshotMetaData;
import net.helix.hlx.service.snapshot.SnapshotState;


public class SnapshotImplTest {

    private static SnapshotState state;
    private static SnapshotMetaData metaData;

    @Before
    public void setup() throws Exception {
        state = new SnapshotStateImpl(new HashMap<>());
        metaData = new SnapshotMetaDataImpl(Hash.NULL_HASH, 1, 1L, new HashMap<>(), new HashMap<>());
    }

    @Test
    public void skippedMilestoneTest() {
        Snapshot snapshot = new SnapshotImpl(state, metaData);
        assertTrue("Not previously seen milestone should be accepted", snapshot.addSkippedMilestone(1));
        
        assertFalse("Previously seen milestone should not be accepted", snapshot.addSkippedMilestone(1));
        assertTrue("Skipped milestone should be removed correctly", snapshot.removeSkippedMilestone(1));
        assertFalse("Not skipped milestone should fail to get removed", snapshot.removeSkippedMilestone(1));
    }
    
    @Test
    public void updateTest() {
        Snapshot snapshot = new SnapshotImpl(state, metaData);
        snapshot.setIndex(0);
        snapshot.setHash(Hash.NULL_HASH);
        snapshot.setInitialTimestamp(1L);
        
        Snapshot newSnapshot = snapshot.clone();
        newSnapshot.setIndex(1);
        snapshot.setHash(TransactionTestUtils.getTransactionHash());
        snapshot.setInitialTimestamp(5L);
        
        assertNotEquals("Modified snapshot clone should not be equal to its original", snapshot, newSnapshot);
        snapshot.update(newSnapshot);
        assertEquals("Updating a snapshot with another snapshot should make them equal", snapshot, newSnapshot);
    }
    
    @Test
    public void cloneTest() {
        Snapshot oldSnapshot = new SnapshotImpl(state, metaData);
        Snapshot newSnapshot = oldSnapshot.clone();
        
        assertEquals("A clone of a snapshot is equal to its original", oldSnapshot, newSnapshot);
        
        oldSnapshot.addSkippedMilestone(1);
        
        // Clone shouldnt have the skipped milestone
        assertFalse("Adding a value to a clone should be reflected on the original", newSnapshot.removeSkippedMilestone(1));
        assertNotEquals("A clone should not be equal to its original after modification", oldSnapshot, newSnapshot);
    }
    
}
