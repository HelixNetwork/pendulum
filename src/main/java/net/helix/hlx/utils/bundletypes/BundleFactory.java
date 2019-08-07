package net.helix.hlx.utils.bundletypes;

import net.helix.hlx.model.Hash;

import java.util.List;

public abstract class BundleFactory {



    public enum Type {
        milestone, // vote
        registration, // application / sign up / sign off / key transition
        nominee, // ?
        curator // to publish set of nominees. will be removed from public package
    }

    public static BundleTypes create(Type type, String senderAddress, String receiverAddress, byte[] data, long currentRoundIndex, Boolean sign, int keyIndex, int maxKeyIndex) {
        switch (type) {
            case milestone:
                return new MilestoneBundle(senderAddress, receiverAddress, data, currentRoundIndex, sign, keyIndex, maxKeyIndex);
            case registration:
                return new RegistrationBundle(senderAddress, receiverAddress, data, currentRoundIndex, sign, keyIndex, maxKeyIndex);
            case nominee:
                return new NomineeBundle(senderAddress, receiverAddress, data, currentRoundIndex, sign, keyIndex, maxKeyIndex);
            default:
                return null;
        }
    }
}