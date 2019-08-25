package net.helix.hlx.model.persistables;

import net.helix.hlx.storage.Persistable;

public class SpentAddress implements Persistable {
    private boolean exists = false;

    @Override
    public byte[] bytes() {
        return new byte[0];
    }

    @Override
    public void read(byte[] bytes) {
        exists = bytes != null;
    }

    @Override
    public byte[] metadata() {
        return new byte[0];
    }

    @Override
    public void readMetadata(byte[] bytes) {
        // Does nothing
    }

    @Override
    public boolean merge() {
        return false;
    }

    public boolean exists() {
        return exists;
    }
}
