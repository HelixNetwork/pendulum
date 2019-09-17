package net.helix.hlx.model.persistables;

import net.helix.hlx.model.Hash;
import net.helix.hlx.model.HashFactory;
import net.helix.hlx.storage.Persistable;
import org.apache.commons.lang3.ArrayUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by paul on 3/8/17 for IRI.
 */

 /**
 * The Hashes model class is an implementation of the <code> Persistable </code> interface.
 * It contains a set <code> LinkedHashSet </code> and delimiter <code> byte </code>.
 * The <code> LinkedHashSet </code> is a hash table with linked entrys, which can be converted into a byte array.
 * The delimiter is used as indicator for next element of the LinkedHashSet.
 */
public class Hashes implements Persistable {
    public Set<Hash> set = new LinkedHashSet<>();
    static final byte delimiter = ",".getBytes()[0];

    /**
    * Get byte array of the set.
    * @return a <code> byte[] </code>
    */
    public byte[] bytes() {
        return set.parallelStream()
                .map(Hash::bytes)
                .reduce((a,b) -> ArrayUtils.addAll(ArrayUtils.add(a, delimiter), b))
                .orElse(new byte[0]);
    }

    /**
    * Create the set from a given byte array.
    * @param bytes is a <code> byte[] </code>
    */
    public void read(byte[] bytes) {
        if(bytes != null) {
            set = new LinkedHashSet<>(bytes.length / (1 + Hash.SIZE_IN_BYTES) + 1);
            for (int i = 0; i < bytes.length; i += 1 + Hash.SIZE_IN_BYTES) {
                set.add(HashFactory.TRANSACTION.create(bytes, i, Hash.SIZE_IN_BYTES));
            }
        }
    }

    /**
    * Get metadata: a zero byte stream
    * @return a <code> byte[] </code>
    */
    @Override
    public byte[] metadata() {
        return new byte[0];
    }

    /**
    * Set metadata as byte stream
    * @param bytes is a <code> byte[] </code>
    */
    @Override
    public void readMetadata(byte[] bytes) {
        // Does nothing
    }

    /**
    * Merge: true
    * @return a <code> boolean </code>
    */
    @Override
    public boolean merge() {
        return true;
    }
}
