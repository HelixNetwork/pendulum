package net.helix.pendulum.network;

/**
 * Typical request/response packets have two parts: one bearing the data payload,
 * and the "hash" part appended at the end.
 *
 * This interface incapsulates this structure and allows dynamic composition
 * using anonymous subclusses and abstract methods.
 *
 * Date: 2019-11-07
 * Author: zhelezov
 */
public interface PacketData {
    /**
     *
     * @return byte[] the data payload
     */
    byte[] getDataPart();
    int dataSize();

    /**
     *
     * @return byte[] the hash payload
     */
    byte[] getHashPart();

}
