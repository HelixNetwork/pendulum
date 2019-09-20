package net.helix.pendulum.model.persistables;

import net.helix.pendulum.model.Hash;

public class BundleNonce extends Tag {

    public BundleNonce(Hash hash) {
        super(hash);
    }

    // used by the persistence layer to instantiate the object
    public BundleNonce() {
    }

}
