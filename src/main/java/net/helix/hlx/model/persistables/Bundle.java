package net.helix.hlx.model.persistables;

/**
 * Created by paul on 5/15/17.
 */

import net.helix.hlx.model.Hash;

/**
 * The Bundle model class is based on <code> Hashes </code>.
 * It's a set of transaction hashes belonging to the same bundle.
 */
public class Bundle extends Hashes {
    public Bundle(Hash hash) {
        set.add(hash);
    }

    public Bundle() {

    }
}
