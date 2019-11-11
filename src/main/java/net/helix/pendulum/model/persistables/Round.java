package net.helix.pendulum.model.persistables;

import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.IntegerIndex;
import net.helix.pendulum.utils.Serializer;
import org.apache.commons.lang3.ArrayUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The Round model class consists of a set of milestone hashes and a corresponding index.
 */
public class Round extends Hashes {
    public IntegerIndex index;

    public Set<Hash> inconsistentMilestones = new LinkedHashSet<>();

    @Override
    public byte[] bytes() {
        return ArrayUtils.addAll(index.bytes(), super.bytes());
    }

    @Override
    public void read(byte[] bytes) {
        if (bytes != null) {
            index = new IntegerIndex(Serializer.getInteger(bytes));
            read(bytes, Integer.BYTES, Transaction.class);
        }
    }
}
