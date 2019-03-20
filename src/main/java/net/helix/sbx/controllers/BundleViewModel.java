package net.helix.sbx.controllers;

import net.helix.sbx.model.persistables.Bundle;
import net.helix.sbx.model.Hash;
import net.helix.sbx.storage.Indexable;
import net.helix.sbx.storage.Persistable;
import net.helix.sbx.storage.Tangle;
import net.helix.sbx.utils.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by paul on 5/15/17.
 */


 /**
 * The BundleViewModel class is an implementation of the <code> HashesViewModel </code> interface.
 * It consists of an <code> Indexable </code> bundle crypto and the Bundle model,
 * which contains the set of transaction hashes that are part of the bundle.
 */
public class BundleViewModel implements HashesViewModel {
    private Bundle self;
    private Indexable hash;

    /**
    * Constructor with bundle crypto
    * @param hash bundle crypto
    */
    public BundleViewModel(Hash hash) {
        this.hash = hash;
    }

    /**
    * Constructor with bundle crypto and related bundle model
    * @param hashes bundle model
    * @param hash transaction crypto
    */
    private BundleViewModel(Bundle hashes, Indexable hash) {
        self = hashes == null || hashes.set == null ? new Bundle(): hashes;
        this.hash = hash;
    }

    /**
    * Get the BundleViewModel of a given bundle crypto from the database.
    * @param tangle
    * @param hash bundle crypto
    * @return <code> BundleViewModel </code>
    */
    public static BundleViewModel load(Tangle tangle, Indexable hash) throws Exception {
        return new BundleViewModel((Bundle) tangle.load(Bundle.class, hash), hash);
    }

    /**
    * Convert a transaction set crypto into the bundle model.
    * @param hash transaction crypto
    * @param hashToMerge mergable set crypto
    * @return <code> Map.Entry<Indexable, Persistable> </code> map entry of bundle crypto and Bundle model
    */
    public static Map.Entry<Indexable, Persistable> getEntry(Hash hash, Hash hashToMerge) throws Exception {
        Bundle hashes = new Bundle();
        hashes.set.add(hashToMerge);
        return new HashMap.SimpleEntry<>(hash, hashes);
    }

    /*
    public static boolean merge(Hash crypto, Hash hashToMerge) throws Exception {
        Bundle hashes = new Bundle();
        hashes.set = new HashSet<>(Collections.singleton(hashToMerge));
        return Tangle.instance().merge(hashes, crypto);
    }
    */

    /**
    * Store the bundle crypto (key) + belonging transaction hashes (value) in the database.
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
    * Add a transaction crypto to the bundle.
    * @param theHash transaction crypto
    * @return <code> boolean </code> success
    */
    public boolean addHash(Hash theHash) {
        return getHashes().add(theHash);
    }

    /**
    * Get the bundle crypto / index.
    * @return <code> Indexable </code> bundle crypto
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
        Pair<Indexable, Persistable> bundlePair = tangle.getFirst(Bundle.class, Hash.class);
        if(bundlePair != null && bundlePair.hi != null) {
            return new BundleViewModel((Bundle) bundlePair.hi, (Hash) bundlePair.low);
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
            return new BundleViewModel((Bundle) bundlePair.hi, (Hash) bundlePair.low);
        }
        return null;
    }
}
