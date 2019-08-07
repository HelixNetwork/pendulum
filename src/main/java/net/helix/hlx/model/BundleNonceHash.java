package net.helix.hlx.model;

public class BundleNonceHash extends AbstractHash {
    
    public BundleNonceHash() {
    }
    
    protected BundleNonceHash(byte[] tagBytes, int offset, int tagSizeInBytes) {
        super(tagBytes, offset, tagSizeInBytes);
    }
}
