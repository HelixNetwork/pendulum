package net.helix.pendulum.crypto;

import net.helix.pendulum.TransactionTestUtils;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.TransactionHash;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


public class MerkleTest {

    private static final Logger log = LoggerFactory.getLogger(MerkleTest.class);

    @Test
    public void testTreeTransactionGeneration() {
        // 6 index is missing because null leaves are not added into merkle tree
        checkMerkleVirtualIndexes(10, new HashSet<Long>(Arrays.asList(0L, 1L, 2L, 3L, 4L, 5L, 7L, 8L, 9L, 10L, 11L)));
    }

    private void checkMerkleVirtualIndexes(int noOfLeaves, Set<Long> expectedIndexes) {
        TransactionHash milestoneHash = TransactionHash.calculate(SpongeFactory.Mode.S256, TransactionTestUtils.getTransactionBytes());
        List<Hash> transactions = new ArrayList<Hash>();
        for (int i = 0; i < noOfLeaves; i++) {
            transactions.add(TransactionHash.calculate(SpongeFactory.Mode.S256, TransactionTestUtils.getTransactionBytes()));
        }

        List<byte[]> virtualTransactions = Merkle.buildMerkleTransactionTree(transactions, milestoneHash, Hash.NULL_HASH);
        Assert.assertTrue(virtualTransactions.size() > 0);
        Set<Long> merkleIndexes = new HashSet<Long>();
        virtualTransactions.forEach(t -> {
            try {
                TransactionViewModel virtualTransaction = new TransactionViewModel(t, SpongeFactory.Mode.S256);
                Assert.assertTrue(virtualTransaction.isVirtual());
                merkleIndexes.add(virtualTransaction.getTagLongValue());

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Assert.assertEquals(expectedIndexes, merkleIndexes);
    }
}
