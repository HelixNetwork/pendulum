package net.helix.pendulum.integration;

import net.helix.pendulum.AbstractPendulumTest;
import net.helix.pendulum.controllers.AddressViewModel;
import net.helix.pendulum.controllers.ApproveeViewModel;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.Hash;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.util.Arrays;
import java.util.List;

import static net.helix.pendulum.TransactionTestUtils.createTransactionWithHex;
import static net.helix.pendulum.TransactionTestUtils.createTransactionWithTrunkAndBranch;
import static org.junit.Assert.*;

/**
 * Date: 2019-11-19
 * Author: zhelezov
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SolidificationTest extends AbstractPendulumTest {
    TransactionViewModel trunk;
    TransactionViewModel branch;
    TransactionViewModel tip1;
    TransactionViewModel tip2;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        clearTangle();

        trunk = createTransactionWithHex("b0");
        branch = createTransactionWithHex("b1");
        tip1 = createTransactionWithTrunkAndBranch("a1", trunk.getHash(),
                branch.getHash());
        tip2 = createTransactionWithTrunkAndBranch("a2", trunk.getHash(),
                branch.getHash());

    }


    @Test
    public void solidifyTipsTest() throws Exception {
        setUp();

        tip1.store(tangle, snapshotProvider.getInitialSnapshot());
        tip2.store(tangle, snapshotProvider.getInitialSnapshot());
        trunk.store(tangle, snapshotProvider.getInitialSnapshot());
        branch.store(tangle, snapshotProvider.getInitialSnapshot());


        assertEquals("Should be two tips", 2, tipsViewModel.getTips().size());
        assertEquals("Should be no solid tips", 0, tipsViewModel.solidSize());

        txValidator.solidifyBackwards();

        tip1 = tangleCache.getTxVM(tip1.getHash());
        tip2 = tangleCache.getTxVM(tip2.getHash());
        trunk = tangleCache.getTxVM(trunk.getHash());
        branch = tangleCache.getTxVM(branch.getHash());

        assertTrue("all txs solid", tip1.isSolid());
        assertTrue("all txs solid", tip2.isSolid());
        assertTrue("all txs solid", trunk.isSolid());
        assertTrue("all txs solid", branch.isSolid());

        assertEquals("Should be two solid tips", 2, tipsViewModel.solidSize());
        assertEquals("Should be no non-solid tips", 0, tipsViewModel.nonSolidSize());
    }

    @Test
    public void verifyApprovees() throws Exception {
        setUp();

        tip1.store(tangle, snapshotProvider.getInitialSnapshot());
        trunk.store(tangle, snapshotProvider.getInitialSnapshot());
        branch.store(tangle, snapshotProvider.getInitialSnapshot());

        ApproveeViewModel avm = ApproveeViewModel.load(tangle, branch.getHash());

        assertTrue("Branch should be approved", avm.getHashes().contains(tip1.getHash()));

        avm = ApproveeViewModel.load(tangle, trunk.getHash());

        assertTrue("Trunk should be approved", avm.getHashes().contains(tip1.getHash()));

        tip2.store(tangle, snapshotProvider.getInitialSnapshot());

        avm = ApproveeViewModel.load(tangle, branch.getHash());
        assertEquals("Branch should have two approvees", avm.getHashes().size(), 2);
        avm = ApproveeViewModel.load(tangle, trunk.getHash());
        assertEquals("Trunk should have two approvees", avm.getHashes().size(), 2);

        assertEquals("tip1 is a tip", tip1.getApprovers(tangle).size(), 0);
        assertEquals("tip2 is a tip", tip2.getApprovers(tangle).size(), 0);

        publishMilestone(Arrays.asList(tip1.getHash(), tip2.getHash()), 10);
        processMilestone();

        tip1 = TransactionViewModel.fromHash(tangle, tip1.getHash());
        tip2 = TransactionViewModel.fromHash(tangle, tip2.getHash());

        assertEquals("Milestone should approve tip1", tip1.getApprovers(tangle).size(), 1);
        assertEquals("Milestone should approve tip2", tip2.getApprovers(tangle).size(), 1);

    }

    @Test
    public void testSolidifyMilestone() throws Exception {
        setUp();
        List<Hash> milestoneTxs = publishMilestone(Arrays.asList(tip1.getHash(), tip2.getHash()), 10);
        assertTrue("Should be detected by the address",
                AddressViewModel.load(tangle, validators.get(0)).getHashes().contains(milestoneTxs.get(0)));

        processMilestone();

        TransactionViewModel tailMilestone = TransactionViewModel.fromHash(tangle, milestoneTxs.get(0));
        assertTrue("Milestone should be stored", tailMilestone.getType() == TransactionViewModel.FILLED_SLOT);
        assertTrue("should be a milestone", tailMilestone.isMilestone());
        assertFalse("Not solid yet", tailMilestone.isSolid());

        txValidator.solidifyForward();
        //txValidator.checkSolidity(milestoneTxs.get(1));

        assertTrue("Tip1 should be requested", node.getRequestQueue().isTransactionRequested(tip1.getHash(), false));
        assertTrue("Tip2 should be requested", node.getRequestQueue().isTransactionRequested(tip2.getHash(), false));

        tip1.store(tangle, snapshotProvider.getInitialSnapshot());
        tip2.store(tangle, snapshotProvider.getInitialSnapshot());

        txValidator.solidifyForward();

        assertTrue("Trunk should be requested", node.getRequestQueue().isTransactionRequested(trunk.getHash(), false));
        assertTrue("Branch should be requested", node.getRequestQueue().isTransactionRequested(branch.getHash(), false));

        trunk.store(tangle, snapshotProvider.getInitialSnapshot());
        branch.store(tangle, snapshotProvider.getInitialSnapshot());

        txValidator.solidifyBackwards();

        for (Hash hash : milestoneTxs) {
            assertTrue("milestone should be solidified",
                    TransactionViewModel.fromHash(tangle, hash).isSolid());
        }

    }

    //@Test
    //public void b_publishMilestone() throws Exception {
     //   api.publishMilestone("eb0d925c1cfa4067db65e4b93fa17d451120cc5a719d637d44a39a983407d832",
     //           1, false, 0, 0);
    //    assertTrue("Milestone bundle should be broadcasted", node.broadcastQueueSize() > 1);
    //}
}
