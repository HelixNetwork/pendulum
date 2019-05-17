package net.helix.hlx.model.persistables;

/**
 * Created by paul on 5/15/17.
 */

import net.helix.hlx.model.Hash;

/**
 * Address model class is based on <code> Hashes </code>.
 * It's a set of transaction hashes belonging to one address.
 */
public class Address extends Hashes {
    public Address(){}
    public Address(Hash hash) {
        set.add(hash);
    }
}
