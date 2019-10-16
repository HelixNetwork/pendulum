package net.helix.pendulum.crypto.merkle;

import net.helix.pendulum.TransactionTestUtils;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.crypto.SpongeFactory;
import net.helix.pendulum.crypto.merkle.impl.TransactionMerkleTreeImpl;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.TransactionHash;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


public class TransactionMerkleTreeTest {

    private static final Logger log = LoggerFactory.getLogger(TransactionMerkleTreeTest.class);

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
        MerkleOptions options = MerkleOptions.getDefault();
        options.setMilestoneHash(milestoneHash);
        options.setAddress(Hash.NULL_HASH);
        List<MerkleNode> virtualTransactions = new TransactionMerkleTreeImpl().buildMerkle(transactions, options);
        Assert.assertTrue(virtualTransactions.size() > 0);
        Set<Long> merkleIndexes = new HashSet<Long>();
        virtualTransactions.forEach(t -> {
            try {
                TransactionViewModel virtualTransaction = (TransactionViewModel)t;
                Assert.assertTrue(virtualTransaction.isVirtual());
                merkleIndexes.add(virtualTransaction.getTagLongValue());

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Assert.assertEquals(expectedIndexes, merkleIndexes);
    }
}
