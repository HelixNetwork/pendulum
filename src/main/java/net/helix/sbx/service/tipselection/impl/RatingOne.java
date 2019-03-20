package net.helix.sbx.service.tipselection.impl;

import net.helix.sbx.controllers.ApproveeViewModel;
import net.helix.sbx.model.Hash;
import net.helix.sbx.model.HashId;
import net.helix.sbx.service.tipselection.RatingCalculator;
import net.helix.sbx.storage.Tangle;
import net.helix.sbx.utils.collections.impl.TransformingMap;
import net.helix.sbx.utils.collections.interfaces.UnIterableMap;

import java.util.*;

/**
 * Implementation of <tt>RatingCalculator</tt> that gives a uniform rating of 1 to each transaction.
 * Used to create uniform random walks.
 */
public class RatingOne implements RatingCalculator {

    private final Tangle tangle;

    public RatingOne(Tangle tangle) {
        this.tangle = tangle;
    }

    @Override
    public UnIterableMap<HashId, Integer> calculate(Hash entryPoint) throws Exception {
        UnIterableMap<HashId, Integer> rating = new TransformingMap<>(null, null);

        Queue<Hash> queue = new LinkedList<>();
        queue.add(entryPoint);
        rating.put(entryPoint, 1);

        Hash hash;

        //traverse all transactions that reference entryPoint
        while ((hash = queue.poll()) != null) {
            Set<Hash> approvers = ApproveeViewModel.load(tangle, hash).getHashes();
            for (Hash tx : approvers) {
                if (!rating.containsKey(tx)) {
                    //add to rating w/ value "1"
                    rating.put(tx, 1);
                    queue.add(tx);
                }
            }
        }
        return rating;
    }
}