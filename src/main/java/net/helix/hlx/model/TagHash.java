package net.helix.hlx.model;

import static net.helix.hlx.controllers.TransactionViewModel.TAG_SIZE;

public class TagHash extends AbstractHash {

    protected TagHash(byte[] tagBytes, int offset, int tagSizeInBytes) {
        super(tagBytes, offset, tagSizeInBytes);
    }

    @Override
    protected int getByteSize(){
        return TAG_SIZE;
    }
}
