package net.helix.pendulum.model.persistables;

/**
 * Created by paul on 5/15/17.
 */

import net.helix.pendulum.model.Hash;

/**
 * Approvee model class based on <code> Hashes </code>.
 * It's a set of transaction hashes approving the same transaction.
 */
public class Approvee extends Hashes {
    public Approvee(Hash hash) {
        set.add(hash);
    }

    public Approvee() {

    }
}
