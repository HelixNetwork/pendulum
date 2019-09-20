package net.helix.pendulum.model;

public class BundleHash extends AbstractHash {
    
    public BundleHash() {
    }
    
    protected BundleHash(byte[] bytes, int offset, int sizeInBytes) {
        super(bytes, offset, sizeInBytes);
    }

    @Override
    protected int getByteSize() {
        return Hash.SIZE_IN_BYTES;
    }
}
