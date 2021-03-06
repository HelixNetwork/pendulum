package net.helix.pendulum.service.snapshot.impl;

import net.helix.pendulum.model.Hash;
import net.helix.pendulum.service.snapshot.SnapshotMetaData;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Implements the basic contract of the {@link SnapshotMetaData} interface.
 */
public class SnapshotMetaDataImpl implements SnapshotMetaData {
    /**
     * Internal property for the value returned by {@link SnapshotMetaData#getInitialHash()}.
     */
    private Hash initialHash;

    /**
     * Internal property for the value returned by {@link SnapshotMetaData#getInitialIndex()}.
     */
    private int initialIndex;

    /**
     * Internal property for the value returned by {@link SnapshotMetaData#getInitialTimestamp()}.
     */
    private long initialTimestamp;

    /**
     * Internal property for the value returned by {@link SnapshotMetaData#getHash()}.
     */
    private Hash hash;

    /**
     * Internal property for the value returned by {@link SnapshotMetaData#getIndex()}.
     */
    private int index;

    /**
     * Internal property for the value returned by {@link SnapshotMetaData#getTimestamp()}.
     */
    private long timestamp;

    /**
     * Internal property for the value returned by {@link SnapshotMetaData#getSolidEntryPoints()}.
     */
    private Map<Hash, Integer> solidEntryPoints;

    /**
     * Internal property for the value returned by {@link SnapshotMetaData#getSeenRounds()}.
     */
    private Map<Integer, Hash> seenRounds;

    /**
     * Creates a meta data object with the given information.
     *
     * It simply stores the passed in parameters in the internal properties.
     *
     * @param hash hash of the transaction that the snapshot belongs to
     * @param index milestone index that the snapshot belongs to
     * @param timestamp timestamp of the transaction that the snapshot belongs to
     * @param solidEntryPoints map with the transaction hashes of the solid entry points associated to their milestone
     *                         index
     * @param seenRounds map of milestone transaction hashes associated to their milestone index
     */
    public SnapshotMetaDataImpl(Hash hash, int index, Long timestamp, Map<Hash, Integer> solidEntryPoints,
                                Map<Integer, Hash> seenRounds) {

        this.initialHash = hash;
        this.initialIndex = index;
        this.initialTimestamp = timestamp;

        setHash(hash);
        setIndex(index);
        setTimestamp(timestamp);
        setSolidEntryPoints(new HashMap<>(solidEntryPoints));
        setSeenRounds(new HashMap<>(seenRounds));
    }

    /**
     * Creates a deep clone of the passed in {@link SnapshotMetaData}.
     *
     * @param snapshotMetaData object that shall be cloned
     */
    public SnapshotMetaDataImpl(SnapshotMetaData snapshotMetaData) {
        this(snapshotMetaData.getInitialHash(), snapshotMetaData.getInitialIndex(),
                snapshotMetaData.getInitialTimestamp(), snapshotMetaData.getSolidEntryPoints(),
                snapshotMetaData.getSeenRounds());

        this.setIndex(snapshotMetaData.getIndex());
        this.setHash(snapshotMetaData.getHash());
        this.setTimestamp(snapshotMetaData.getTimestamp());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash getInitialHash() {
        return initialHash;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInitialHash(Hash initialHash) {
        this.initialHash = initialHash;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getInitialIndex() {
        return initialIndex;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInitialIndex(int initialIndex) {
        this.initialIndex = initialIndex;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getInitialTimestamp() {
        return initialTimestamp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInitialTimestamp(long initialTimestamp) {
        this.initialTimestamp = initialTimestamp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash getHash() {
        return hash;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setHash(Hash hash) {
        this.hash = hash;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getIndex() {
        return this.index;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIndex(int index) {
        this.index = index;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getTimestamp() {
        return this.timestamp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Hash, Integer> getSolidEntryPoints() {
        return solidEntryPoints;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSolidEntryPoints(Map<Hash, Integer> solidEntryPoints) {
        this.solidEntryPoints = solidEntryPoints;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasSolidEntryPoint(Hash solidEntrypoint) {
        return solidEntryPoints.containsKey(solidEntrypoint);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSolidEntryPointIndex(Hash solidEntrypoint) {
        return solidEntryPoints.get(solidEntrypoint);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Integer, Hash> getSeenRounds() {
        return seenRounds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSeenRounds(Map<Integer, Hash> seenRounds) {
        this.seenRounds = seenRounds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(SnapshotMetaData newMetaData) {
        initialIndex = newMetaData.getInitialIndex();
        initialHash = newMetaData.getInitialHash();
        initialTimestamp = newMetaData.getInitialTimestamp();

        setIndex(newMetaData.getIndex());
        setHash(newMetaData.getHash());
        setTimestamp(newMetaData.getTimestamp());
        setSolidEntryPoints(new HashMap<>(newMetaData.getSolidEntryPoints()));
        setSeenRounds(new HashMap<>(newMetaData.getSeenRounds()));
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), initialHash, initialIndex, initialTimestamp, hash, index, timestamp,
                solidEntryPoints, seenRounds);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj == null || !getClass().equals(obj.getClass())) {
            return false;
        }

        return Objects.equals(initialHash, ((SnapshotMetaDataImpl) obj).initialHash) &&
                Objects.equals(initialIndex, ((SnapshotMetaDataImpl) obj).initialIndex) &&
                Objects.equals(initialTimestamp, ((SnapshotMetaDataImpl) obj).initialTimestamp) &&
                Objects.equals(hash, ((SnapshotMetaDataImpl) obj).hash) &&
                Objects.equals(index, ((SnapshotMetaDataImpl) obj).index) &&
                Objects.equals(timestamp, ((SnapshotMetaDataImpl) obj).timestamp) &&
                Objects.equals(solidEntryPoints, ((SnapshotMetaDataImpl) obj).solidEntryPoints) &&
                Objects.equals(seenRounds, ((SnapshotMetaDataImpl) obj).seenRounds);

    }
}
