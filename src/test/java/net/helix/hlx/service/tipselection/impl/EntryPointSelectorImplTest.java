package net.helix.hlx.service.tipselection.impl;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import net.helix.hlx.TransactionTestUtils;
import net.helix.hlx.conf.MainnetConfig;
import net.helix.hlx.controllers.MilestoneViewModel;
import net.helix.hlx.model.Hash;
import net.helix.hlx.model.IntegerIndex;
import net.helix.hlx.model.persistables.Milestone;
import net.helix.hlx.service.milestone.LatestMilestoneTracker;
import net.helix.hlx.service.snapshot.SnapshotProvider;
import net.helix.hlx.service.snapshot.impl.SnapshotProviderImpl;
import net.helix.hlx.service.tipselection.EntryPointSelector;
import net.helix.hlx.storage.Tangle;


@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class EntryPointSelectorImplTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    
    @Mock
    private LatestMilestoneTracker latestMilestoneTracker;
    
    @Mock
    private Tangle tangle;
    
    private static SnapshotProvider snapshotProvider;

    @BeforeClass
    public static void setUp() throws Exception {
        snapshotProvider = new SnapshotProviderImpl().init(new MainnetConfig());
        MilestoneViewModel.clear();
    }

    @Test
    public void entryPointAWithoutTangleDataTest() throws Exception {
        mockMilestoneTrackerBehavior(0, Hash.NULL_HASH);

        EntryPointSelector entryPointSelector = new EntryPointSelectorImpl(tangle, snapshotProvider, latestMilestoneTracker);
        Hash entryPoint = entryPointSelector.getEntryPoint(10);

        Assert.assertEquals("The entry point should be the last tracked solid milestone", Hash.NULL_HASH, entryPoint);
    }

    @Test
    public void entryPointBWithTangleDataTest() throws Exception {
        Hash milestoneHash = TransactionTestUtils.getTransactionHash();
        mockTangleBehavior(milestoneHash);
        mockMilestoneTrackerBehavior(snapshotProvider.getInitialSnapshot().getIndex() + 1, Hash.NULL_HASH);

        EntryPointSelector entryPointSelector = new EntryPointSelectorImpl(tangle, snapshotProvider, latestMilestoneTracker);
        Hash entryPoint = entryPointSelector.getEntryPoint(10);

        Assert.assertEquals("The entry point should be the milestone in the Tangle", milestoneHash, entryPoint);
    }


    private void mockMilestoneTrackerBehavior(int latestSolidSubtangleMilestoneIndex, Hash latestSolidSubtangleMilestone) {
        snapshotProvider.getLatestSnapshot().setIndex(latestSolidSubtangleMilestoneIndex);
        snapshotProvider.getLatestSnapshot().setHash(latestSolidSubtangleMilestone);
        Mockito.when(latestMilestoneTracker.getLatestMilestoneIndex()).thenReturn(latestSolidSubtangleMilestoneIndex);
    }

    private void mockTangleBehavior(Hash milestoneModelHash) throws Exception {
        Milestone milestoneModel = new Milestone();
        milestoneModel.index = new IntegerIndex(snapshotProvider.getInitialSnapshot().getIndex() + 1);
        milestoneModel.hash = milestoneModelHash;
        Mockito.when(tangle.load(Milestone.class, milestoneModel.index)).thenReturn(milestoneModel);
    }

}
