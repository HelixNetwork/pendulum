package net.helix.hlx.model.safe;

import java.util.Arrays;

public class HashSafeObject extends SafeObject {
    private Integer hashcode;

    public HashSafeObject(byte[] obj, String messageIfUnsafe) {
        super(obj, messageIfUnsafe);
        this.hashcode = Arrays.hashCode(getData());
        // TODO this.bytes = bytes; this.hashcode = Arrays.hashCode(bytes); ?
    }

    /**
     * Returns the hash code from the contained data
     * @return the hashcode
     */
    public Integer getHashcode() {
        return hashcode;
    }
}
