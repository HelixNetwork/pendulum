package net.helix.hlx;

import java.util.Random;

import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.crypto.SpongeFactory;
import net.helix.hlx.model.Hash;
import net.helix.hlx.model.TransactionHash;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;


public class TransactionTestUtils {

    private static final Random RND = new Random();

    /**
     * Generates a transaction with the provided hex string.
     * Transaction hash is calculated and added.
     *
     * @param hex The transaction hex to use
     * @return The transaction
     */
    public static TransactionViewModel createTransactionWithHex(String hex) {
        byte[] hbytes = Hex.decode(hex);
        return new TransactionViewModel(hbytes, TransactionHash.calculate(SpongeFactory.Mode.S256, hbytes));
    }

    /**
     * @param hex The hex to change.
     * @return The changed hex
     */
    public static String nextWord(String hex, int index) {
        return pad(Integer.toHexString(index+1));
    }

    private static String pad(String hex) {
        return StringUtils.rightPad(hex, TransactionViewModel.SIZE*2-hex.length()+1, '0');
    }

    public static Hash getTransactionHash() {
        byte[] bytes = new byte[Hash.SIZE_IN_BYTES];
        RND.nextBytes(bytes);
        return net.helix.hlx.model.HashFactory.TRANSACTION.create(bytes);
    }

}
