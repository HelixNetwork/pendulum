package net.helix.pendulum.model;

import static net.helix.pendulum.controllers.TransactionViewModel.TAG_SIZE;

public class TagHash extends AbstractHash {

    public TagHash() {
    }
    
    protected TagHash(byte[] tagBytes, int offset, int tagSizeInBytes) {
        super(tagBytes, offset, tagSizeInBytes);
    }

    @Override
    protected int getByteSize(){
        return TAG_SIZE;
    }
}
