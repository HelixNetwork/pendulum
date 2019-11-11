package net.helix.pendulum.crypto.merkle;

import net.helix.pendulum.TransactionTestUtils;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.crypto.SpongeFactory;
import net.helix.pendulum.crypto.merkle.impl.MerkleTreeImpl;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.HashFactory;
import net.helix.pendulum.model.TransactionHash;
import net.helix.pendulum.utils.KeyfileUtil;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MerkleTreeTest {

    @Test
    public void createMerkleTreeAndValidateIt() {
        int keyDepth = 5;
        int firstIndex = 0;
        int security = 2;
        int keyIndex = 0;
        int maxKeyIndex = (int) Math.pow(2, keyDepth);
        String seed = "aabbccdd00000000000000000000000000000000000000000000000000000000";

        byte[] root = KeyfileUtil.getKeyTreeRoot(seed, keyDepth, firstIndex, maxKeyIndex, security);

        Hash senderAddress = HashFactory.ADDRESS.create(root);
        MerkleTree merkle = new MerkleTreeImpl();
        MerkleOptions options = new MerkleOptions(SpongeFactory.Mode.S256, senderAddress, security, keyDepth);

        List<List<MerkleNode>> merkleTree = KeyfileUtil.buildMerkleKeyTree(seed, keyDepth, maxKeyIndex * keyIndex, maxKeyIndex, security);

        Assert.assertTrue(senderAddress.equals(HashFactory.ADDRESS.create(merkleTree.get(merkleTree.size() - 1).get(0).bytes())));

        List<MerkleNode> merklePath = new MerkleTreeImpl().getMerklePath(merkleTree, keyIndex % maxKeyIndex);
        byte[] path = Hex.decode(merklePath.stream().map(m -> m.toString()).collect(Collectors.joining()));
        byte[] merkleTransactionSignature = new byte[TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE];

        System.arraycopy(path, 0, merkleTransactionSignature, 0, path.length);
        byte[] merkleRootTest = merkle.getMerkleRoot(options.getMode(), senderAddress.bytes(),
                merkleTransactionSignature, 0, keyIndex, options.getDepth());

        Assert.assertTrue(HashFactory.ADDRESS.create(merkleRootTest).equals(options.getAddress()));
    }

    private List<Hash> getHashes(int noOfLeaves) {
        List<Hash> transactions = new ArrayList<Hash>();
        for (int i = 0; i < noOfLeaves; i++) {
            transactions.add(TransactionHash.calculate(SpongeFactory.Mode.S256, TransactionTestUtils.getTransactionBytes()));
        }
        return transactions;
    }
}
