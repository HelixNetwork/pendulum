package net.helix.pendulum.controllers;

import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.TransactionHash;
import net.helix.pendulum.model.persistables.Approvee;
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
 * The ApproveeViewModel class is an implementation of the <code> HashesViewModel </code> interface.
 * It consists of an <code> Indexable </code> transaction hash and the Approvee model,
 * which contains the set of transaction hashes that approves this hash.
 */
public class ApproveeViewModel implements HashesViewModel {
    private Approvee self;
    private Indexable hash;

    /**
    * Constructor with transaction hash
    * @param hash address hash
    */
    public ApproveeViewModel(Hash hash) {
        this.hash = hash;
    }

    /**
    * Constructor with transaction hash and related approvee model
    * @param hashes approvee model
    * @param hash transaction hash
    */
    private ApproveeViewModel(Approvee hashes, Indexable hash) {
        self = hashes == null || hashes.set == null ? new Approvee(): hashes;
        this.hash = hash;
    }

    /**
    * Get the ApproveeViewModel of a given transaction hash from the database.
    * @param tangle
    * @param hash transaction hash
    * @return <code> AddressViewModel </code>
    */
    public static ApproveeViewModel load(Tangle tangle, Indexable hash) throws Exception {
        return new ApproveeViewModel((Approvee) tangle.load(Approvee.class, hash), hash);
    }

    /**
    * Convert a mergable approvee set hash into the approvee model.
    * @param hash transaction hash
    * @param hashToMerge mergable set hash
    * @return <code> Map.Entry<Indexable, Persistable> </code> map entry of transaction hash and approvee model
    */
    public static Map.Entry<Indexable, Persistable> getEntry(Hash hash, Hash hashToMerge) throws Exception {
        Approvee hashes = new Approvee();
        hashes.set.add(hashToMerge);
        return new HashMap.SimpleEntry<>(hash, hashes);
    }

    /**
    * Store the transaction hash (key) and the set of the approoving transaction hashes (value) in the database.
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
    * Add an approving transaction hash to the set.
    * @param theHash transaction hash
    * @return <code> boolean </code> success
    */
    public boolean addHash(Hash theHash) {
        return getHashes().add(theHash);
    }

    /**
    * Get the transaction hash / index.
    * @return <code> Indexable </code> transaction hash
    */
    public Indexable getIndex() {
        return hash;
    }

    /**
    * Get the set of transaction hashes belonging to the address.
    * @return <code> Set<Hash> </code> transaction hashes
    */
    public Set<Hash> getHashes() {
        self = self == null ? new Approvee(): self;
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
        Pair<Indexable, Persistable> bundlePair = tangle.getFirst(Approvee.class, TransactionHash.class);
        if(bundlePair != null && bundlePair.hi != null) {
            return new ApproveeViewModel((Approvee) bundlePair.hi, bundlePair.low);
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
            return new ApproveeViewModel((Approvee) bundlePair.hi, bundlePair.low);
        }
        return null;
    }
}
