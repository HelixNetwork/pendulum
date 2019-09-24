package net.helix.pendulum.controllers;

import net.helix.pendulum.model.BundleHash;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.persistables.Bundle;
import net.helix.pendulum.storage.Indexable;
import net.helix.pendulum.storage.Persistable;
import net.helix.pendulum.storage.Tangle;
import net.helix.pendulum.utils.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by paul on 5/15/17.
 */

 /**
 * The BundleViewModel class is an implementation of the <code> HashesViewModel </code> interface.
 * It consists of an <code> Indexable </code> bundle hash and the Bundle model,
 * which contains the set of transaction hashes that are part of the bundle.
 */
public class BundleViewModel implements HashesViewModel {
    private Bundle self;
    private Indexable hash;

    /**
    * Constructor with bundle hash
    * @param hash bundle hash
    */
    public BundleViewModel(Hash hash) {
        this.hash = hash;
    }

    /**
    * Constructor with bundle hash and related bundle model
    * @param hashes bundle model
    * @param hash transaction hash
    */
    private BundleViewModel(Bundle hashes, Indexable hash) {
        self = hashes == null || hashes.set == null ? new Bundle(): hashes;
        this.hash = hash;
    }

    /**
    * Get the BundleViewModel of a given bundle hash from the database.
    * @param tangle
    * @param hash bundle hash
    * @return <code> BundleViewModel </code>
    */
    public static BundleViewModel load(Tangle tangle, Indexable hash) throws Exception {
        return new BundleViewModel((Bundle) tangle.load(Bundle.class, hash), hash);
    }

    /**
    * Convert a transaction set hash into the bundle model.
    * @param hash transaction hash
    * @param hashToMerge mergable set hash
    * @return <code> Map.Entry<Indexable, Persistable> </code> map entry of bundle hash and Bundle model
    */
    public static Map.Entry<Indexable, Persistable> getEntry(Hash hash, Hash hashToMerge) throws Exception {
        Bundle hashes = new Bundle();
        hashes.set.add(hashToMerge);
        return new HashMap.SimpleEntry<>(hash, hashes);
    }

    /*
    public static boolean merge(Hash hash, Hash hashToMerge) throws Exception {
        Bundle hashes = new Bundle();
        hashes.set = new HashSet<>(Collections.singleton(hashToMerge));
        return Tangle.instance().merge(hashes, hash);
    }
    */

    /**
    * Store the bundle hash (key) + belonging transaction hashes (value) in the database.
    * @param tangle
    * @return <code> boolean </code> success
    */
    public boolean store(Tangle tangle) throws Exception {
        return tangle.save(self, hash);
    }

    /**
    * Get the number of transactions belonging to the bundle.
    * @return <code> int </code> number
    */
    public int size() {
        return self.set.size();
    }

    /**
    * Add a transaction hash to the bundle.
    * @param theHash transaction hash
    * @return <code> boolean </code> success
    */
    public boolean addHash(Hash theHash) {
        return getHashes().add(theHash);
    }

    /**
    * Get the bundle hash / index.
    * @return <code> Indexable </code> bundle hash
    */
    public Indexable getIndex() {
        return hash;
    }

    /**
    * Get the set of transaction hashes belonging to the bundle.
    * @return <code> Set<Hash> </code> transaction hashes
    */
    public Set<Hash> getHashes() {
        return self.set;
    }

    /**
    * Delete the bundle from the database.
    * @param tangle
    */
    @Override
    public void delete(Tangle tangle) throws Exception {
        tangle.delete(Bundle.class,hash);
    }

    /**
    * Get the first bundle from the database.
    * @param tangle
    * @return <code> bundleViewModel </code>
    */
    public static BundleViewModel first(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> bundlePair = tangle.getFirst(Bundle.class, BundleHash.class);
        if(bundlePair != null && bundlePair.hi != null) {
            return new BundleViewModel((Bundle) bundlePair.hi, bundlePair.low);
        }
        return null;
    }

    /**
    * Get the next bundle from the database.
    * @param tangle
    * @return <code> bundleViewModel </code>
    */
    public BundleViewModel next(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> bundlePair = tangle.next(Bundle.class, hash);
        if(bundlePair != null && bundlePair.hi != null) {
            return new BundleViewModel((Bundle) bundlePair.hi, bundlePair.low);
        }
        return null;
    }
}
