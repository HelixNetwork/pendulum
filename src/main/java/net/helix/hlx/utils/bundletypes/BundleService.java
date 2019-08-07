package net.helix.hlx.utils.bundletypes;

import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.crypto.Merkle;
import net.helix.hlx.crypto.Sponge;
import net.helix.hlx.crypto.SpongeFactory;
import net.helix.hlx.crypto.Winternitz;
import net.helix.hlx.model.Hash;
import net.helix.hlx.utils.Serializer;
import org.bouncycastle.util.encoders.Hex;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BundleService {

    public static byte[] initTransaction(String address, int currentIndex, int lastIndex, long timestamp, long tag) {
        byte[] transaction = new byte[TransactionViewModel.SIZE];
        System.arraycopy(Hex.decode(address), 0, transaction, TransactionViewModel.ADDRESS_OFFSET, TransactionViewModel.ADDRESS_SIZE);
        System.arraycopy(Serializer.serialize((long) currentIndex), 0, transaction, TransactionViewModel.CURRENT_INDEX_OFFSET, TransactionViewModel.CURRENT_INDEX_SIZE);
        System.arraycopy(Serializer.serialize((long) lastIndex), 0, transaction, TransactionViewModel.LAST_INDEX_OFFSET, TransactionViewModel.LAST_INDEX_SIZE);
        System.arraycopy(Serializer.serialize(timestamp), 0, transaction, TransactionViewModel.TIMESTAMP_OFFSET, TransactionViewModel.TIMESTAMP_SIZE);
        System.arraycopy(Serializer.serialize(tag), 0, transaction, TransactionViewModel.TAG_OFFSET, TransactionViewModel.TAG_SIZE);
        return transaction;
    }

    public static byte[] addBundleHash(List<byte[]> bundle, SpongeFactory.Mode mode) {
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

    public static void signBundle(String filepath, byte[] merkleTransaction, byte[] mainTransaction, byte[] bundleHash, int keyIndex, int maxKeyIndex) throws IOException {
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
