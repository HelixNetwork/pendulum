package net.helix.pendulum.crypto.merkle;

import net.helix.pendulum.crypto.merkle.impl.MerkleTreeImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;

public enum MerkleFactory {
    MerkleTree(MerkleTreeImpl.class);

    private static final Logger log = LoggerFactory.getLogger(MerkleFactory.class);

    Class<? extends MerkleTree> clazz;

    MerkleFactory(Class<? extends net.helix.pendulum.crypto.merkle.MerkleTree> clazz) {
        this.clazz = clazz;
    }

    public static MerkleTree create(MerkleFactory type, MerkleOptions options) {
        try {
            Constructor<?> constructor = type.clazz.getConstructor(MerkleOptions.class);
            return (MerkleTree) constructor.newInstance(options);
        } catch (Exception e) {
            log.error("Could not instantiate merkle tree object! ", e);
        }
        return null;
    }
}
