package net.helix.pendulum.integration;

import net.helix.pendulum.AbstractPendulumTest;
import net.helix.pendulum.Pendulum;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.event.EventManager;
import net.helix.pendulum.event.EventType;
import net.helix.pendulum.event.EventUtils;
import net.helix.pendulum.network.Node;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.openjdk.jmh.annotations.Setup;


import static com.jayway.restassured.RestAssured.when;
import static net.helix.pendulum.TransactionTestUtils.createTransactionWithHex;
import static net.helix.pendulum.TransactionTestUtils.createTransactionWithTrunkAndBranch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Date: 2019-11-19
 * Author: zhelezov
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SolidificationTest extends AbstractPendulumTest {
    TransactionViewModel trunk;
    TransactionViewModel branch;
    TransactionViewModel parent1;
    TransactionViewModel parent2;

    @Setup
    public void setUp() throws Exception {
        super.setUp();
        trunk = createTransactionWithHex("b0");
        branch = createTransactionWithHex("b1");
        parent1 = createTransactionWithTrunkAndBranch("a1", trunk.getHash(),
                branch.getHash());
        parent2 = createTransactionWithTrunkAndBranch("a2", trunk.getHash(),
                branch.getHash());

    }

    @Test
    public void a_solidifyTipsTest() throws Exception {
        clearTangle();

        parent1.store(tangle, snapshotProvider.getInitialSnapshot());
        parent2.store(tangle, snapshotProvider.getInitialSnapshot());
        trunk.store(tangle, snapshotProvider.getInitialSnapshot());
        branch.store(tangle, snapshotProvider.getInitialSnapshot());

        EventManager.get().fire(EventType.TX_STORED, EventUtils.fromTx(parent1));
        EventManager.get().fire(EventType.TX_STORED, EventUtils.fromTx(parent2));
        EventManager.get().fire(EventType.TX_STORED, EventUtils.fromTx(trunk));
        EventManager.get().fire(EventType.TX_STORED, EventUtils.fromTx(branch));

        assertEquals("Should be two tips", 2, tipsViewModel.getTips().size());
        assertEquals("Should be no solid tips", 0, tipsViewModel.solidSize());

        txValidator.solidifyBackwards();

        parent1 = tangleCache.getTxVM(parent1.getHash());
        parent2 = tangleCache.getTxVM(parent2.getHash());
        trunk = tangleCache.getTxVM(trunk.getHash());
        branch = tangleCache.getTxVM(branch.getHash());

        assertTrue("all txs solid", parent1.isSolid());
        assertTrue("all txs solid", parent2.isSolid());
        assertTrue("all txs solid", trunk.isSolid());
        assertTrue("all txs solid", branch.isSolid());

        assertEquals("Should be two solid tips", 2, tipsViewModel.solidSize());
        assertEquals("Should be no non-solid tips", 0, tipsViewModel.nonSolidSize());
    }

    @Test
    public void b_publishMilestone() throws Exception {
        api.publishMilestone("eb0d925c1cfa4067db65e4b93fa17d451120cc5a719d637d44a39a983407d832",
                1, false, 0, 0);
        assertTrue("Milestone bundle should be broadcasted", node.broadcastQueueSize() > 1);
    }
}
