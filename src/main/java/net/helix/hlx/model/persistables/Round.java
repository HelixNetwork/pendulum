package net.helix.hlx.model.persistables;

import net.helix.hlx.model.Hash;
import net.helix.hlx.model.HashFactory;
import net.helix.hlx.model.IntegerIndex;
import net.helix.hlx.utils.Serializer;

import org.apache.commons.lang3.ArrayUtils;

import java.util.LinkedHashSet;

 /**
 * The Round model class consists of a set of milestone hashes and a corresponding roundIndex.
 */
public class Round extends Hashes {
     public IntegerIndex roundIndex;

     @Override
     public byte[] bytes() {
         byte[] hashes = set.parallelStream()
                 .map(Hash::bytes)
                 .reduce((a,b) -> ArrayUtils.addAll(ArrayUtils.add(a, delimiter), b))
                 .orElse(new byte[0]);
         return ArrayUtils.addAll(roundIndex.bytes(), hashes);
     }

     @Override
     public void read(byte[] bytes) {
         if(bytes != null) {
             roundIndex = new IntegerIndex(Serializer.getInteger(bytes));
             set = new LinkedHashSet<>((bytes.length - Integer.BYTES) / (1 + Hash.SIZE_IN_BYTES) + 1);
             for (int i = Integer.BYTES; i < bytes.length; i += 1 + Hash.SIZE_IN_BYTES) {
                 set.add(HashFactory.TRANSACTION.create(bytes, i, Hash.SIZE_IN_BYTES));
             }
         }
     }
}
