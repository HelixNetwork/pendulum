package net.helix.hlx.utils.bundletypes;

import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.crypto.SpongeFactory;
import net.helix.hlx.model.Hash;
import net.helix.hlx.utils.Serializer;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RegistrationBundle extends BundleTypes {

    private static final Logger log = LoggerFactory.getLogger(RegistrationBundle.class);
    byte[] curatorTransaction;


    public RegistrationBundle(String senderAddress, String receiverAddress, byte[] registrationData, long join, Boolean sign, int keyIndex, int maxKeyIndex) {

        // get number of transactions needed for tips
        int n = (registrationData.length/TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE) + 1;
        // pad data to mutiple of smf
        byte[] paddedData = new byte[n * TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE];
        System.arraycopy(registrationData, 0, paddedData, 0, registrationData.length);

        long timestamp = System.currentTimeMillis() / 1000L;
        int lastIndex = 1 + n;

        // contain a signature that signs the siblings and thereby ensures the integrity.
        senderTransaction = BundleService.initTransaction(senderAddress, 0, lastIndex, timestamp, join);

        // siblings for merkle tree.
        merkleTransaction = BundleService.initTransaction(Hash.NULL_HASH.toString(), 1, lastIndex, timestamp, (long) keyIndex % maxKeyIndex);

        // list of confirming tips
        for (int i = 2; i <= lastIndex; i++) {
            byte[] tx = BundleService.initTransaction(receiverAddress, i, lastIndex, timestamp, 0L);
            byte[] dataFragment = Arrays.copyOfRange(paddedData, (i-2) * TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE, (i-1) * TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE);
            System.arraycopy(dataFragment, 0, tx, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_OFFSET, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE);
            dataTransactions.add(tx);
        }

        // calculate bundle hash
        List<byte[]> bundle = new ArrayList<>();
        bundle.add(senderTransaction);
        bundle.add(merkleTransaction);
        bundle.addAll(dataTransactions);
        byte [] bundleHash = BundleService.addBundleHash(bundle, SpongeFactory.Mode.S256);

        // sign bundle
        if (sign) {
            try {
                BundleService.signBundle("./src/main/resources/Nominee.key", merkleTransaction, senderTransaction, bundleHash, keyIndex, maxKeyIndex);
            } catch (IOException e) {
                log.error("Cannot read keyfile", e);
            }
        }
    }

    public byte[] getCuratorTransaction() {
        return curatorTransaction;
    }

}
