package net.helix.hlx.model.persistables;

import net.helix.hlx.model.Hash;
import net.helix.hlx.model.HashFactory;
import net.helix.hlx.model.IntegerIndex;
import net.helix.hlx.storage.Persistable;
import net.helix.hlx.utils.Serializer;
import org.apache.commons.lang3.ArrayUtils;

/**
 * Created by paul on 4/11/17.
 */

 /**
 * The MilestoneTracker model class is an implementation of the <code> Persistable </code> interface.
 * It consists of the milestoneTracker index, an <code> IntegerIndex </code>, and the transaction hash of the milestoneTracker transaction.
 */
public class Milestone implements Persistable {
    public IntegerIndex index;
    public Hash hash;

    public byte[] bytes() {
        return ArrayUtils.addAll(index.bytes(), hash.bytes());
    }

    public void read(byte[] bytes) {
        if(bytes != null) {
            index = new IntegerIndex(Serializer.getInteger(bytes));
            hash = HashFactory.TRANSACTION.create(bytes, Integer.BYTES, Hash.SIZE_IN_BYTES);
        }
    }

    @Override
    public byte[] metadata() {
        return new byte[0];
    }

    @Override
    public void readMetadata(byte[] bytes) {
        // Does nothing
    }

    @Override
    public boolean merge() {
        return false;
    }
}
