package net.helix.hlx.model.persistables;

import net.helix.hlx.model.Hash;

public class BundleNonce extends Hashes {
    public BundleNonce(Hash hash) { set.add(hash); }

    // used by the persistance layer to store the object
    public BundleNonce() {}
}
