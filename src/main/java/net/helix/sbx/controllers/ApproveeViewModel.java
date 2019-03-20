package net.helix.sbx.controllers;

import net.helix.sbx.model.persistables.Approvee;
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
 * The ApproveeViewModel class is an implementation of the <code> HashesViewModel </code> interface.
 * It consists of an <code> Indexable </code> transaction crypto and the Approvee model,
 * which contains the set of transaction hashes that approves this crypto.
 */
public class ApproveeViewModel implements HashesViewModel {
    private Approvee self;
    private Indexable hash;

    /**
    * Constructor with transaction crypto
    * @param hash address crypto
    */
    public ApproveeViewModel(Hash hash) {
        this.hash = hash;
    }

    /**
    * Constructor with transaction crypto and related approvee model
    * @param hashes approvee model
    * @param hash transaction crypto
    */
    private ApproveeViewModel(Approvee hashes, Indexable hash) {
        self = hashes == null || hashes.set == null ? new Approvee(): hashes;
        this.hash = hash;
    }

    /**
    * Get the ApproveeViewModel of a given transaction crypto from the database.
    * @param tangle
    * @param hash transaction crypto
    * @return <code> AddressViewModel </code>
    */
    public static ApproveeViewModel load(Tangle tangle, Indexable hash) throws Exception {
        return new ApproveeViewModel((Approvee) tangle.load(Approvee.class, hash), hash);
    }

    /**
    * Convert a mergable approvee set crypto into the approvee model.
    * @param hash transaction crypto
    * @param hashToMerge mergable set crypto
    * @return <code> Map.Entry<Indexable, Persistable> </code> map entry of transaction crypto and approvee model
    */
    public static Map.Entry<Indexable, Persistable> getEntry(Hash hash, Hash hashToMerge) throws Exception {
        Approvee hashes = new Approvee();
        hashes.set.add(hashToMerge);
        return new HashMap.SimpleEntry<>(hash, hashes);
    }

    /**
    * Store the transaction crypto (key) and the set of the approoving transaction hashes (value) in the database.
    * @param tangle
    * @return <code> boolean </code> success
    */
    public boolean store(Tangle tangle) throws Exception {
        return tangle.save(self, hash);
    }

    /**
    * Get the number of approving transactions.
    * @return <code> int </code> number
    */
    public int size() {
        return self.set.size();
    }

    /**
    * Add an approving transaction crypto to the set.
    * @param theHash transaction crypto
    * @return <code> boolean </code> success
    */
    public boolean addHash(Hash theHash) {
        return getHashes().add(theHash);
    }

    /**
    * Get the transaction crypto / index.
    * @return <code> Indexable </code> transaction crypto
    */
    public Indexable getIndex() {
        return hash;
    }

    /**
    * Get the set of transaction hashes belonging to the address.
    * @return <code> Set<Hash> </code> transaction hashes
    */
    public Set<Hash> getHashes() {
        return self.set;
    }
    
    /**
    * Delete the approvee entry in the database.
    * @param tangle
    */
    @Override
    public void delete(Tangle tangle) throws Exception {
        tangle.delete(Approvee.class,hash);
    }

    /**
    * Get the first approvee entry from the database.
    * @param tangle
    * @return <code> ApproveeViewModel </code>
    */
    public static ApproveeViewModel first(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> bundlePair = tangle.getFirst(Approvee.class, Hash.class);
        if(bundlePair != null && bundlePair.hi != null) {
            return new ApproveeViewModel((Approvee) bundlePair.hi, (Hash) bundlePair.low);
        }
        return null;
    }

    /**
    * Get the next approvee entry from the database.
    * @param tangle
    * @return <code> ApproveeViewModel </code>
    */
    public ApproveeViewModel next(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> bundlePair = tangle.next(Approvee.class, hash);
        if(bundlePair != null && bundlePair.hi != null) {
            return new ApproveeViewModel((Approvee) bundlePair.hi, (Hash) bundlePair.low);
        }
        return null;
    }
}
