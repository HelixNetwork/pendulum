package net.helix.hlx.utils.bundle;

import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.crypto.Merkle;
import net.helix.hlx.crypto.Sponge;
import net.helix.hlx.crypto.SpongeFactory;
import net.helix.hlx.crypto.Winternitz;
import net.helix.hlx.model.Hash;
import net.helix.hlx.utils.Serializer;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BundleUtils {

    private final Logger log = LoggerFactory.getLogger(BundleUtils.class);

    private byte[] senderTransaction;
    private byte[] merkleTransaction;
    private List<byte[]> dataTransactions = new ArrayList<>();

    private String senderAddress;
    private String receiverAddress;

    /**
     * @param senderAddress   senderAddress
     * @param receiverAddress receiverAddress
     */
    public BundleUtils(Hash senderAddress, Hash receiverAddress) {
        this.senderAddress = senderAddress.toString();
        this.receiverAddress = receiverAddress.toString();
    }

    /**
     * @return transactions
     */
    public List<String> getTransactions() {
        List<String> transactions = new ArrayList<>();
        for (int i = dataTransactions.size() - 1; i >= 0; i--) {
            transactions.add(Hex.toHexString(dataTransactions.get(i)));
        }
        transactions.add(Hex.toHexString(merkleTransaction));
        transactions.add(Hex.toHexString(senderTransaction));
        return transactions;
    }

    /**
     * Method for generating bundles
     * @param tips tips
     * @param roundIndex round index
     * @param sign whether to sign
     * @param keyIndex key index
     * @param maxKeyIndex maximum key index
     */
    public void create(byte[] tips, long roundIndex, Boolean sign, int keyIndex, int maxKeyIndex) {

        // get number of transactions needed for tips
        int n = (tips.length/TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE) + 1;
        // pad data to mutiple of smf
        byte[] paddedData = new byte[n * TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE];
        System.arraycopy(tips, 0, paddedData, 0, tips.length);

        long timestamp = System.currentTimeMillis() / 1000L;
        int lastIndex = 1 + n;

        // contain a signature that signs the siblings and thereby ensures the integrity.
        this.senderTransaction = initTransaction(this.senderAddress, 0, lastIndex, timestamp, roundIndex);

        // siblings for merkle tree.
        this.merkleTransaction = initTransaction(Hash.NULL_HASH.toString(), 1, lastIndex, timestamp, (long) keyIndex % maxKeyIndex);

        // list of confirming tips
        for (int i = 2; i <= lastIndex; i++) {
            byte[] tx = initTransaction(this.receiverAddress, i, lastIndex, timestamp, 0L);
            byte[] dataFragment = Arrays.copyOfRange(paddedData, (i-2) * TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE, (i-1) * TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE);
            System.arraycopy(dataFragment, 0, tx, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_OFFSET, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE);
            this.dataTransactions.add(tx);
        }

        // calculate bundle hash
        List<byte[]> bundle = new ArrayList<>();
        bundle.add(this.senderTransaction);
        bundle.add(this.merkleTransaction);
        bundle.addAll(this.dataTransactions);
        byte[] bundleHash = addBundleHash(bundle, SpongeFactory.Mode.S256);

        // sign bundle
        if (sign) {
            try {
                signBundle("./src/main/resources/Nominee.key", this.merkleTransaction, this.senderTransaction, bundleHash, keyIndex, maxKeyIndex);
            } catch (IOException e) {
                log.error("Cannot read keyfile", e);
            }
        }
    }

    /**
     * @param address      address
     * @param currentIndex current index
     * @param lastIndex    last index
     * @param timestamp    timestamp
     * @param tag          tag
     * @return transaction
     */
    private byte[] initTransaction(String address, int currentIndex, int lastIndex, long timestamp, long tag) {
        byte[] transaction = new byte[TransactionViewModel.SIZE];
        System.arraycopy(Hex.decode(address), 0, transaction, TransactionViewModel.ADDRESS_OFFSET, TransactionViewModel.ADDRESS_SIZE);
        System.arraycopy(Serializer.serialize((long) currentIndex), 0, transaction, TransactionViewModel.CURRENT_INDEX_OFFSET, TransactionViewModel.CURRENT_INDEX_SIZE);
        System.arraycopy(Serializer.serialize((long) lastIndex), 0, transaction, TransactionViewModel.LAST_INDEX_OFFSET, TransactionViewModel.LAST_INDEX_SIZE);
        System.arraycopy(Serializer.serialize(timestamp), 0, transaction, TransactionViewModel.TIMESTAMP_OFFSET, TransactionViewModel.TIMESTAMP_SIZE);
        System.arraycopy(Serializer.serialize(tag), 0, transaction, TransactionViewModel.TAG_OFFSET, TransactionViewModel.TAG_SIZE);
        return transaction;
    }

    /**
     * @param bundle bundle
     * @param mode   hash mode
     * @return bundle hash
     */
    private byte[] addBundleHash(List<byte[]> bundle, SpongeFactory.Mode mode) {
        Sponge sponge = SpongeFactory.create(mode);

        for (byte[] transaction : bundle) {
            byte[] essence = Arrays.copyOfRange(transaction, TransactionViewModel.ESSENCE_OFFSET, TransactionViewModel.ESSENCE_OFFSET + TransactionViewModel.ESSENCE_SIZE);
            sponge.absorb(essence, 0, essence.length);
        }

        byte[] bundleHash = new byte[32];
        sponge.squeeze(bundleHash, 0, bundleHash.length);
        for (byte[] transaction : bundle) {
            System.arraycopy(bundleHash, 0, transaction, TransactionViewModel.BUNDLE_OFFSET, TransactionViewModel.BUNDLE_SIZE);
        }
        return bundleHash;
    }

    /**
     * @param filepath          path to file
     * @param merkleTransaction merkle transaction
     * @param mainTransaction   main transaction
     * @param bundleHash        bundle hash
     * @param keyIndex          key index
     * @param maxKeyIndex       maximum key index
     * @throws IOException if file not found or not readable
     */
    private void signBundle(String filepath, byte[] merkleTransaction, byte[] mainTransaction, byte[] bundleHash, int keyIndex, int maxKeyIndex) throws IOException {
        // Get merkle path and store in signatureMessageFragment of Sibling Transaction
        File keyfile = new File(filepath);
        List<List<Hash>> merkleTree = Merkle.readKeyfile(keyfile);
        String seed = Merkle.getSeed(keyfile);
        // create merkle path from keyfile
        List<Hash> merklePath = Merkle.getMerklePath(merkleTree, keyIndex % maxKeyIndex);
        byte[] path = Hex.decode(merklePath.stream().map(Hash::toString).collect(Collectors.joining()));
        System.arraycopy(path, 0, merkleTransaction, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_OFFSET, path.length);

        // sign bundle hash and store signature in Milestone Transaction
        byte[] normBundleHash = Winternitz.normalizedBundle(bundleHash);
        byte[] subseed = Winternitz.subseed(SpongeFactory.Mode.S256, Hex.decode(seed), keyIndex);
        final byte[] key = Winternitz.key(SpongeFactory.Mode.S256, subseed, 1);
        byte[] bundleFragment = Arrays.copyOfRange(normBundleHash, 0, 16);
        byte[] keyFragment = Arrays.copyOfRange(key, 0, 512);
        byte[] signature = Winternitz.signatureFragment(SpongeFactory.Mode.S256, bundleFragment, keyFragment);
        System.arraycopy(signature, 0, mainTransaction, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_OFFSET, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE);
    }
}
