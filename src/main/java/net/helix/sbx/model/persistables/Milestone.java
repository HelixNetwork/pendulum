package net.helix.sbx.model.persistables;

import net.helix.sbx.model.Hash;
import net.helix.sbx.model.HashFactory;
import net.helix.sbx.model.IntegerIndex;
import net.helix.sbx.storage.Persistable;
import net.helix.sbx.utils.Serializer;
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
    public void readMetadata(byte[] bytes) { }

    @Override
    public boolean merge() {
        return false;
    }
}
