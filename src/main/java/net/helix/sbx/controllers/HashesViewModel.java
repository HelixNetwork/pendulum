package net.helix.sbx.controllers;

import net.helix.sbx.model.Hash;
import net.helix.sbx.storage.Indexable;
import net.helix.sbx.storage.Tangle;

import java.util.*;

/**
 * Created by paul on 5/6/17.
 */

 /**
 * The HashesViewModel class is an interface for interacting with the Hashes model class.
 * It allows to store @see #store(Tangle) and delete @see #delete(Tangle) the Hashes of the model in the tangle
 * or let you jump to the next entry @see #next(Tangle).
 */
public interface HashesViewModel {
    boolean store(Tangle tangle) throws Exception;
    int size();
    boolean addHash(Hash theHash);
    Indexable getIndex();
    Set<Hash> getHashes();
    void delete(Tangle tangle) throws Exception;

    HashesViewModel next(Tangle tangle) throws Exception;
}
