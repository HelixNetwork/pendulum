package net.helix.hlx;

import java.util.Random;
import net.helix.hlx.model.Hash;


public class TransactionTestUtils {

    private static final Random RND = new Random();

    public static Hash getTransactionHash() {
        byte[] bytes = new byte[Hash.SIZE_IN_BYTES];
        RND.nextBytes(bytes);
        return net.helix.hlx.model.HashFactory.TRANSACTION.create(bytes);
    }

}
