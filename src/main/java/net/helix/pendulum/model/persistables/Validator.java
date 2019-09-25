package net.helix.pendulum.model.persistables;

import net.helix.pendulum.model.IntegerIndex;
import net.helix.pendulum.utils.Serializer;
import org.apache.commons.lang3.ArrayUtils;

public class Validator extends Hashes {
    public IntegerIndex index;

    @Override
    public byte[] bytes() {
        return ArrayUtils.addAll(index.bytes(), super.bytes());
    }

    @Override
    public void read(byte[] bytes) {
        if (bytes != null) {
            index = new IntegerIndex(Serializer.getInteger(bytes));
            read(bytes, Integer.BYTES);
        }
    }
}
