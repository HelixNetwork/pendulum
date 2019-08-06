package net.helix.hlx.utils.bundletypes;

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

public class MilestoneBundle implements BundleTypes {

    private static final Logger log = LoggerFactory.getLogger(MilestoneBundle.class);

     byte[] txMilestone;
     byte[] txSibling;
     List<byte[]> txTips = new ArrayList<>();

     MilestoneBundle(final String address, Boolean sign, int keyIndex, int currentRoundIndex, int maxKeyIndex, long n) {

        // contain a signature that signs the siblings and thereby ensures the integrity.
        this.txMilestone = new byte[TransactionViewModel.SIZE];
        System.arraycopy(Hex.decode(address), 0, this.txMilestone, TransactionViewModel.ADDRESS_OFFSET, TransactionViewModel.ADDRESS_SIZE);
        System.arraycopy(Serializer.serialize(1L + n), 0, this.txMilestone, TransactionViewModel.LAST_INDEX_OFFSET, TransactionViewModel.LAST_INDEX_SIZE);
        System.arraycopy(Serializer.serialize(System.currentTimeMillis() / 1000L), 0, this.txMilestone, TransactionViewModel.TIMESTAMP_OFFSET, TransactionViewModel.TIMESTAMP_SIZE);
        System.arraycopy(Serializer.serialize((long) currentRoundIndex), 0, this.txMilestone, TransactionViewModel.TAG_OFFSET, TransactionViewModel.TAG_SIZE);

        // siblings for merkle tree.
        this.txSibling = new byte[TransactionViewModel.SIZE];
        System.arraycopy(Serializer.serialize(1L), 0, this.txSibling, TransactionViewModel.CURRENT_INDEX_OFFSET, TransactionViewModel.CURRENT_INDEX_SIZE);
        System.arraycopy(Serializer.serialize(1L + n), 0, this.txSibling, TransactionViewModel.LAST_INDEX_OFFSET, TransactionViewModel.LAST_INDEX_SIZE);
        System.arraycopy(Serializer.serialize(System.currentTimeMillis() / 1000L), 0, this.txSibling, TransactionViewModel.TIMESTAMP_OFFSET, TransactionViewModel.TIMESTAMP_SIZE);
        System.arraycopy(Serializer.serialize((long) keyIndex % maxKeyIndex), 0, this.txSibling, TransactionViewModel.TAG_OFFSET, TransactionViewModel.TAG_SIZE);

        // list of confirming tips
        for (long i = 2L; i <= (1L + n); i++) {
            byte[] tx = new byte[TransactionViewModel.SIZE];
            System.arraycopy(Serializer.serialize(i), 0, tx, TransactionViewModel.CURRENT_INDEX_OFFSET, TransactionViewModel.CURRENT_INDEX_SIZE);
            System.arraycopy(Serializer.serialize(1L + n), 0, tx, TransactionViewModel.LAST_INDEX_OFFSET, TransactionViewModel.LAST_INDEX_SIZE);
            System.arraycopy(Serializer.serialize(System.currentTimeMillis() / 1000L), 0, tx, TransactionViewModel.TIMESTAMP_OFFSET, TransactionViewModel.TIMESTAMP_SIZE);
            this.txTips.add(tx);
        }

        // calculate bundle hash
        Sponge sponge = SpongeFactory.create(SpongeFactory.Mode.S256);

        byte[] milestoneEssence = Arrays.copyOfRange(this.txMilestone, TransactionViewModel.ESSENCE_OFFSET, TransactionViewModel.ESSENCE_OFFSET + TransactionViewModel.ESSENCE_SIZE);
        sponge.absorb(milestoneEssence, 0, milestoneEssence.length);
        byte[] siblingEssence = Arrays.copyOfRange(this.txSibling, TransactionViewModel.ESSENCE_OFFSET, TransactionViewModel.ESSENCE_OFFSET + TransactionViewModel.ESSENCE_SIZE);
        sponge.absorb(siblingEssence, 0, siblingEssence.length);
        for (byte[] tx : this.txTips) {
            byte[] tipsEssence = Arrays.copyOfRange(tx, TransactionViewModel.ESSENCE_OFFSET, TransactionViewModel.ESSENCE_OFFSET + TransactionViewModel.ESSENCE_SIZE);
            sponge.absorb(tipsEssence, 0, tipsEssence.length);
        }

        byte[] bundleHash = new byte[32];
        sponge.squeeze(bundleHash, 0, bundleHash.length);
        System.arraycopy(bundleHash, 0, this.txMilestone, TransactionViewModel.BUNDLE_OFFSET, TransactionViewModel.BUNDLE_SIZE);
        System.arraycopy(bundleHash, 0, this.txSibling, TransactionViewModel.BUNDLE_OFFSET, TransactionViewModel.BUNDLE_SIZE);
        for (byte[] tx : this.txTips) {
            System.arraycopy(bundleHash, 0, tx, TransactionViewModel.BUNDLE_OFFSET, TransactionViewModel.BUNDLE_SIZE);
        }

        if (sign) {
            // Get merkle path and store in signatureMessageFragment of Sibling Transaction
            File keyfile = new File("./src/main/resources/Nominee.key");
            try {
                List<List<Hash>> merkleTree = Merkle.readKeyfile(keyfile);
                String seed = Merkle.getSeed(keyfile);

                // create merkle path from keyfile
                List<Hash> merklePath = Merkle.getMerklePath(merkleTree, keyIndex % maxKeyIndex);
                byte[] path = Hex.decode(merklePath.stream().map(Hash::toString).collect(Collectors.joining()));
                System.arraycopy(path, 0, txSibling, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_OFFSET, path.length);

                // sign bundletypes hash and store signature in MilestoneBundle Transaction
                byte[] normBundleHash = Winternitz.normalizedBundle(bundleHash);
                byte[] subseed = Winternitz.subseed(SpongeFactory.Mode.S256, Hex.decode(seed), keyIndex);
                final byte[] key = Winternitz.key(SpongeFactory.Mode.S256, subseed, 1);
                byte[] bundleFragment = Arrays.copyOfRange(normBundleHash, 0, 16);
                byte[] keyFragment = Arrays.copyOfRange(key, 0, 512);
                byte[] signature = Winternitz.signatureFragment(SpongeFactory.Mode.S256, bundleFragment, keyFragment);
                System.arraycopy(signature, 0, txMilestone, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_OFFSET, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE);
            } catch (IOException e) {
                log.error("Cannot read keyfile", e);
            }
        }
    }
    @Override
    public byte[] getTxMilestone() {
        return this.txMilestone;
    }
    @Override
    public byte[] getTxSibling() {
        return this.txSibling;
    }
    @Override
    public List<byte[]> getTxTips() {
        return this.txTips;
    }

    @Override
    public void publish() {
    }
}
