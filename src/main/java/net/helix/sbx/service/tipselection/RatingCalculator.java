package net.helix.sbx.service.tipselection;

import net.helix.sbx.model.Hash;
import net.helix.sbx.model.HashId;
import net.helix.sbx.utils.collections.interfaces.UnIterableMap;

/**
 * Calculates the rating for a sub graph
 */
@FunctionalInterface
public interface RatingCalculator {

    /**
     * Rating calculator
     * <p>
     * Calculates the rating of all the transactions that reference
     * a given {@code entryPoint}.
     * </p>
     *
     * @param entryPoint  Transaction hash of a selected entry point.
     * @return  Map of ratings for each transaction that references entryPoint.
     * @throws Exception If DB fails to retrieve transactions
     */

    UnIterableMap<HashId, Integer> calculate(Hash entryPoint) throws Exception;
}
