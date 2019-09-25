package net.helix.pendulum.utils.bundle;

import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.crypto.Merkle;
import net.helix.pendulum.crypto.Sponge;
import net.helix.pendulum.crypto.SpongeFactory;
import net.helix.pendulum.crypto.Winternitz;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.utils.Serializer;
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

    private List<byte[]> senderTransactions = new ArrayList<>();
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
        for (int i = senderTransactions.size() - 1; i >= 0; i--) {
            transactions.add(Hex.toHexString(senderTransactions.get(i)));
        }
        return transactions;
    }

    /**
     * Method for generating bundles
     * @param data signature message fragment
     * @param tag round index, start round or join/leave
     * @param sign whether to sign
     * @param keyIndex key index
     * @param maxKeyIndex maximum key index
     */
    public void create(byte[] data, long tag, Boolean sign, int keyIndex, int maxKeyIndex, String keyfile, int security) {

        // get number of transactions needed for tips
        int n = data.length % TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE == 0 ?
                data.length/TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE :
                (data.length/TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE) + 1;
        // pad data to mutiple of smf
        byte[] paddedData = new byte[n * TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE];
        System.arraycopy(data, 0, paddedData, 0, data.length);

        long timestamp = System.currentTimeMillis() / 1000L;
        int lastIndex = security + n;

        // contain a signature that signs the siblings and thereby ensures the integrity.
        this.senderTransactions.add(initTransaction(senderAddress, 0, lastIndex, timestamp, tag));
        for (int i = 1; i < security; i++) {
            byte[] tx = initTransaction(Hash.NULL_HASH.toString(), i, lastIndex, timestamp, tag);
            this.senderTransactions.add(tx);
        }

        // siblings for merkle tree.
        this.merkleTransaction = initTransaction(Hash.NULL_HASH.toString(), security, lastIndex, timestamp, (long) keyIndex % maxKeyIndex);

        // list of confirming tips
        for (int i = security + 1; i <= lastIndex; i++) {
            byte[] tx = initTransaction(this.receiverAddress, i, lastIndex, timestamp, 0L);
            byte[] dataFragment = Arrays.copyOfRange(paddedData, (i-(security+1)) * TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE, (i-security) * TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE);
            System.arraycopy(dataFragment, 0, tx, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_OFFSET, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE);
            this.dataTransactions.add(tx);
        }

        // calculate bundle hash
        List<byte[]> bundle = new ArrayList<>(this.senderTransactions);
        bundle.add(this.merkleTransaction);
        bundle.addAll(this.dataTransactions);
        byte[] bundleHash = addBundleHash(bundle, SpongeFactory.Mode.S256);

        // sign bundle
        if (sign) {
            try {
                signBundle(keyfile, this.merkleTransaction, this.senderTransactions, bundleHash, keyIndex, maxKeyIndex);
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
     * @param filepath              path to file
     * @param merkleTransaction     merkle transaction
     * @param senderTransactions   sender transactions
     * @param bundleHash            bundle hash
     * @param keyIndex              key index
     * @param maxKeyIndex           maximum key index
     * @throws IOException if file not found or not readable
     */
    private void signBundle(String filepath, byte[] merkleTransaction, List<byte[]> senderTransactions, byte[] bundleHash, int keyIndex, int maxKeyIndex) throws IOException {
        // Get merkle path and store in signatureMessageFragment of Sibling Transaction
        File keyfile = new File(filepath);
        List<List<Hash>> merkleTree = Merkle.readKeyfile(keyfile);
        String seed = Merkle.getSeed(keyfile);
        int security = senderTransactions.size();

        // create merkle path from keyfile
        List<Hash> merklePath = Merkle.getMerklePath(merkleTree, keyIndex % maxKeyIndex);
        byte[] path = Hex.decode(merklePath.stream().map(Hash::toString).collect(Collectors.joining()));
        System.arraycopy(path, 0, merkleTransaction, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_OFFSET, path.length);

        // sign bundle hash and store signature in Milestone Transaction
        byte[] normBundleHash = Winternitz.normalizedBundle(bundleHash);
        byte[] subseed = Winternitz.subseed(SpongeFactory.Mode.S256, Hex.decode(seed), keyIndex);
        final byte[] key = Winternitz.key(SpongeFactory.Mode.S256, subseed, security);

        for (int i = 0; i < security; i++) {
            byte[] bundleFragment = Arrays.copyOfRange(normBundleHash, i * 16, (i+1) * 16);
            byte[] keyFragment = Arrays.copyOfRange(key, i * 512, (i+1) * 512);
            byte[] signature = Winternitz.signatureFragment(SpongeFactory.Mode.S256, bundleFragment, keyFragment);
            System.arraycopy(signature, 0, senderTransactions.get(i), TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_OFFSET, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE);
        }
    }
}
