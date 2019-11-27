package net.helix.pendulum;

import net.helix.pendulum.conf.MainnetConfig;
import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.crypto.SpongeFactory;
import net.helix.pendulum.model.TransactionHash;
import org.junit.Test;

import static net.helix.pendulum.TransactionTestUtils.*;
import static org.junit.Assert.*;


public class TransactionValidatorTest extends AbstractPendulumTest {

    @Test
    public void minDifficultyTest() throws InterruptedException {
        PendulumConfig oldConf =  Pendulum.ServiceRegistry.get().resolve(PendulumConfig.class);

        PendulumConfig testConf = new MainnetConfig() {
            @Override
            public boolean isTestnet() {
                return false;
            }

            @Override
            public int getMwm() {
                return 0;
            }
        };

        Pendulum.ServiceRegistry.get().register(PendulumConfig.class, testConf);
        // should initialize with the props as above
        txValidator.init();
        assertTrue(txValidator.getMinWeightMagnitude() == 1);

        Pendulum.ServiceRegistry.get().register(PendulumConfig.class, oldConf);
    }

    @Test
    public void validateBytesTest() {
        try {
            byte[] bytes = new byte[TransactionViewModel.SIZE];
            txValidator.validateBytes(bytes, MAINNET_MWM);
        } catch (Throwable t) {
            fail();
        }

    }

    @Test(expected = RuntimeException.class)
    public void validateBytesWithInvalidMetadataTest() {
        byte[] bytes = getTransactionBytes();
        txValidator.validateBytes(bytes, MAINNET_MWM);
    }

    @Test
    public void validateBytesWithNewSha3Test() {
        try{
            byte[] bytes = new byte[TransactionViewModel.SIZE];
                txValidator.validateBytes(bytes, txValidator.getMinWeightMagnitude(), SpongeFactory.create(SpongeFactory.Mode.S256));
        } catch (Throwable t) {
            fail();
        }
    }

    @Test
    public void verifyTxIsSolidTest() throws Exception {
        TransactionViewModel tx = getTxWithBranchAndTrunk();
        txValidator.checkSolidity(tx.getHash());

        txValidator.solidifyBackwards();
        txValidator.solidifyForward();

        assertTrue(txValidator.checkSolidity(tx.getHash()));
    }


    @Test
    public void verifyTxIsNotSolidTest() throws Exception {
        TransactionViewModel tx = getTxWithoutBranchAndTrunk();
        assertFalse(txValidator.checkSolidity(tx.getHash()));
    }

    @Test
    public void addSolidTransactionWithoutErrorsTest() {
        try {
            byte[] bytes = new byte[TransactionViewModel.SIZE];
            txValidator.addSolidTransaction(TransactionHash.calculate(SpongeFactory.Mode.S256, bytes));
        } catch (Throwable t) {
            fail();
        }
    }

    @Test
    public void transactionPropagationTest() throws Exception {
        TransactionViewModel leftChildLeaf = createTransactionWithHex("b0c1");
        leftChildLeaf.updateSolid(true);
        leftChildLeaf.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel rightChildLeaf = createTransactionWithHex("b0c2");
        rightChildLeaf.updateSolid(true);
        rightChildLeaf.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel parent = createTransactionWithTrunkAndBranch("b0",
                leftChildLeaf.getHash(), rightChildLeaf.getHash());
        parent.updateSolid(false);
        parent.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel parentSibling = createTransactionWithHex("b1");
        parentSibling.updateSolid(true);
        parentSibling.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel grandParent = createTransactionWithTrunkAndBranch("a0", parent.getHash(),
                parentSibling.getHash());
        grandParent.updateSolid(false);
        grandParent.store(tangle, snapshotProvider.getInitialSnapshot());

        txValidator.addSolidTransaction(leftChildLeaf.getHash());
        while (!txValidator.isNewSolidTxSetsEmpty()) {
            txValidator.solidifyBackwards();
            txValidator.solidifyForward();
        }

        parent = TransactionViewModel.fromHash(tangle, parent.getHash());
        assertTrue("Parent tx was expected to be solid", parent.isSolid());
        grandParent = TransactionViewModel.fromHash(tangle, grandParent.getHash());
        assertTrue("Grandparent  was expected to be solid", grandParent.isSolid());
    }

    @Test
    public void transactionPropagationFailureTest() throws Exception {
        TransactionViewModel leftChildLeaf = new TransactionViewModel(getTransactionBytes(), getTransactionHash());
        leftChildLeaf.updateSolid(true);
        leftChildLeaf.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel rightChildLeaf = new TransactionViewModel(getTransactionBytes(), getTransactionHash());
        rightChildLeaf.updateSolid(true);
        rightChildLeaf.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel parent = new TransactionViewModel(getTransactionBytesWithTrunkAndBranch(leftChildLeaf.getHash(),
                rightChildLeaf.getHash()), getTransactionHash());
        parent.updateSolid(false);
        parent.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel parentSibling = new TransactionViewModel(getTransactionBytes(), getTransactionHash());
        parentSibling.updateSolid(false);
        parentSibling.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel grandParent = new TransactionViewModel(getTransactionBytesWithTrunkAndBranch(parent.getHash(),
                parentSibling.getHash()), getTransactionHash());
        grandParent.updateSolid(false);
        grandParent.store(tangle, snapshotProvider.getInitialSnapshot());

        txValidator.addSolidTransaction(leftChildLeaf.getHash());
        while (!txValidator.isNewSolidTxSetsEmpty()) {
            txValidator.solidifyBackwards();
            txValidator.solidifyForward();
        }

        parent = TransactionViewModel.fromHash(tangle, parent.getHash());
        assertTrue("Parent tx was expected to be solid", parent.isSolid());
        grandParent = TransactionViewModel.fromHash(tangle, grandParent.getHash());
        assertFalse("GrandParent tx was expected to be not solid", grandParent.isSolid());
    }

}