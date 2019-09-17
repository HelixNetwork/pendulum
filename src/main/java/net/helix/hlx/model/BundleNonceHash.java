package net.helix.hlx.model;

public class BundleNonceHash extends AbstractHash {
    
    public BundleNonceHash() {
    }
    
    protected BundleNonceHash(byte[] tagBytes, int offset, int tagSizeInBytes) {
        super(tagBytes, offset, tagSizeInBytes);
    }

    @Override
    protected int getByteSize() {
        return Hash.SIZE_IN_BYTES;
    }
}
