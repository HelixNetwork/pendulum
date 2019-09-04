package net.helix.hlx.model.persistables;

import net.helix.hlx.model.Hash;

public class BundleNonce extends Tag {

    public BundleNonce(Hash hash) {
        super(hash);
    }

    // used by the persistence layer to instantiate the object
    public BundleNonce() {
    }

}
