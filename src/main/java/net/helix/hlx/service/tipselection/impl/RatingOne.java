package net.helix.hlx.service.tipselection.impl;

import net.helix.hlx.controllers.ApproveeViewModel;
import net.helix.hlx.model.Hash;
import net.helix.hlx.model.HashId;
import net.helix.hlx.service.tipselection.RatingCalculator;
import net.helix.hlx.storage.Tangle;
import net.helix.hlx.utils.collections.impl.TransformingMap;
import net.helix.hlx.utils.collections.interfaces.UnIterableMap;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

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