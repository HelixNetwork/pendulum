package net.helix.pendulum.service.tipselection.impl;

import net.helix.pendulum.TransactionTestUtils;
import net.helix.pendulum.conf.MainnetConfig;
import net.helix.pendulum.controllers.RoundViewModel;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.IntegerIndex;
import net.helix.pendulum.model.persistables.Round;
import net.helix.pendulum.service.milestone.MilestoneTracker;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.service.snapshot.impl.SnapshotProviderImpl;
import net.helix.pendulum.service.tipselection.EntryPointSelector;
import net.helix.pendulum.storage.Tangle;
import org.junit.*;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;


@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class EntryPointSelectorImplTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    
    @Mock
    private MilestoneTracker latestMilestoneTracker;
    
    @Mock
    private Tangle tangle;
    
    private static SnapshotProvider snapshotProvider;

    @BeforeClass
    public static void setUp() throws Exception {
        snapshotProvider = SnapshotProviderImpl.getInstance().init(new MainnetConfig());
        RoundViewModel.clear();
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
        Mockito.when(latestMilestoneTracker.getCurrentRoundIndex()).thenReturn(latestSolidSubtangleMilestoneIndex);
    }

    private void mockTangleBehavior(Hash milestoneModelHash) throws Exception {
        Round round = new Round();
        round.index = new IntegerIndex(snapshotProvider.getInitialSnapshot().getIndex() + 1);
        round.set.add(milestoneModelHash);
        Mockito.when(tangle.load(Round.class, round.index)).thenReturn(round);
    }

}
