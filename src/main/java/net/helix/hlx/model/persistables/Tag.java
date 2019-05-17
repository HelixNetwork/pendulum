package net.helix.hlx.model.persistables;

/**
 * Created by paul on 5/15/17.
 */

import net.helix.hlx.model.Hash;

/**
 * The Tag model class is based on <code> Hashes </code>.
 * It's a set of transaction hashes having the same tag.
 */
public class Tag extends Hashes {
    public Tag(Hash hash) {
        set.add(hash);
    }

    public Tag() {

    }
}
