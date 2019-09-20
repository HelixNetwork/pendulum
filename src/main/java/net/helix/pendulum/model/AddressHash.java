package net.helix.pendulum.model;

public class AddressHash extends AbstractHash {

    public AddressHash() {
    }

    protected AddressHash(byte[] bytes, int offset, int sizeInBytes) {
        super(bytes, offset, sizeInBytes);
    }

    @Override
    protected int getByteSize() {
        return Hash.SIZE_IN_BYTES;
    }

}
