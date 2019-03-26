package net.helix.sbx.controllers;

import net.helix.sbx.model.persistables.Address;
import net.helix.sbx.model.Hash;
import net.helix.sbx.storage.Indexable;
import net.helix.sbx.storage.Persistable;
import net.helix.sbx.storage.Tangle;
import net.helix.sbx.utils.Pair;

import java.util.Set;

/**
 * Created by paul on 5/15/17.
 */

 /**
 * The AddressViewModel class is an implementation of the <code> HashesViewModel </code> interface.
 * It consists of an <code> Indexable </code> address hash and the Address model,
 * which contains the set of transaction hashes that are spend from this address.
 */
public class AddressViewModel implements HashesViewModel {
    private Address self;
    private Indexable hash;
    
    /**
    * Constructor with address hash
    * @param hash address hash
    */
    public AddressViewModel(Hash hash) {
        this.hash = hash;
    }

    /**
    * Constructor with address hash and related address model
    * @param hashes address model
    * @param hash address hash
    */
    private AddressViewModel(Address hashes, Indexable hash) {
        self = hashes == null || hashes.set == null ? new Address(): hashes;
        this.hash = hash;
    }

    /**
    * Get the AddressViewModel of a given address hash from the database.
    * @param tangle
    * @param hash address hash
    * @return <code> AddressViewModel </code>
    */
    public static AddressViewModel load(Tangle tangle, Indexable hash) throws Exception {
        return new AddressViewModel((Address) tangle.load(Address.class, hash), hash);
    }

    /**
    * Store the address hash + belonging transaction hashes in the database.
    * @param tangle
    * @return <code> boolean </code> success
    */
    public boolean store(Tangle tangle) throws Exception {
        return tangle.save(self, hash);
    }

    /**
    * Get the number of transactions belonging to the address.
    * @return <code> int </code> number
    */
    public int size() {
        return self.set.size();
    }

    /**
    * Add a transaction hash to the address.
    * @param theHash transaction hash
    * @return <code> boolean </code> success
    */
    public boolean addHash(Hash theHash) {
        return getHashes().add(theHash);
    }

    /**
    * Get the address hash / index.
    * @return <code> Indexable </code> address hash
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
      * Delete the address entry in the database.
      * @param tangle
      */
    @Override
    public void delete(Tangle tangle) throws Exception {
        tangle.delete(Address.class,hash);
    }

    /**
    * Get the first address entry from the database.
    * @param tangle
    * @return <code> AddressViewModel </code>
    */
    public static AddressViewModel first(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> bundlePair = tangle.getFirst(Address.class, Hash.class);
        if(bundlePair != null && bundlePair.hi != null) {
            return new AddressViewModel((Address) bundlePair.hi, (Hash) bundlePair.low);
        }
        return null;
    }

    /**
    * Get the next address entry from the database.
    * @param tangle
    * @return <code> AddressViewModel </code>
    */
    public AddressViewModel next(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> bundlePair = tangle.next(Address.class, hash);
        if(bundlePair != null && bundlePair.hi != null) {
            return new AddressViewModel((Address) bundlePair.hi, (Hash) bundlePair.low);
        }
        return null;
    }
}
