package net.helix.pendulum.event;

/**
 * Date: 2019-11-04
 * Author: zhelezov
 */
public enum EventType {
    NEW_BYTES_RECEIVED,
    MERKLE_ROOT_CALCULATED,
    TX_STORED,
    TX_SOLIDIFIED,
    TX_UPDATED,
    STALE_TX
}
