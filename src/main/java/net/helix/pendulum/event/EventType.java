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
    TX_DELETED,
    TX_CONFIRMED,
    TX_VALIDATION_STATUS_CHANGED,
    STALE_TX
}
