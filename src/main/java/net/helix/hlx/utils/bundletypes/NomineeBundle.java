package net.helix.hlx.utils.bundletypes;

import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.crypto.SpongeFactory;
import net.helix.hlx.model.Hash;
import net.helix.hlx.utils.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NomineeBundle extends BundleTypes {

    private static final Logger log = LoggerFactory.getLogger(NomineeBundle.class);

    NomineeBundle(String senderAddress, String receiverAddress, byte[] nominees, long startRound, Boolean sign, int keyIndex, int maxKeyIndex) {

        // get number of transactions needed for nominees
        int n = (nominees.length/TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE) + 1;
        // pad data to mutiple of smf
        byte[] paddedData = new byte[n * TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE];
        System.arraycopy(nominees, 0, paddedData, 0, nominees.length);

        long timestamp = System.currentTimeMillis() / 1000L;
        int lastIndex = 1 + n;

        // contains the sender address and the signature
        senderTransaction = BundleService.initTransaction(senderAddress, 0, lastIndex, timestamp, startRound);

        // contains for merkle tree to verify the signature
        merkleTransaction = BundleService.initTransaction(Hash.NULL_HASH.toString(), 1, lastIndex, timestamp, (long) keyIndex % maxKeyIndex);

        // contains the list of nominee addresses
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
}
