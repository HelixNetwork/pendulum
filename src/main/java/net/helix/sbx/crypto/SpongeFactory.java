package net.helix.sbx.crypto;

/**
 * Created by paul on 7/27/17.
 */
public abstract class SpongeFactory {
    public enum Mode {
        K256,
        K512,
        S256
    }
    public static Sponge create(Mode mode){
        switch (mode) {
            case K256: return new K256();
            case K512: return new K512();
            case S256: return new Sha3();

            default: return null;
        }
    }
}
