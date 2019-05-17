package net.helix.hlx.service.tipselection;

import net.helix.hlx.model.Hash;
import net.helix.hlx.utils.collections.interfaces.UnIterableMap;

/**
 * Selects an {@code entryPoint} for tip selection.
 * <p>
 * this point is used as the starting point for {@link Walker#walk(Hash, UnIterableMap, WalkValidator)}
 * </p>
 */

public interface EntryPointSelector {

    /**
     *get an {@code entryPoint} for tip selection
     *
     *Uses depth to determine the entry point for the random walk.
     *
     * @param depth Depth, in milestones. a notion of how deep to search for a good starting point.
     * @return Entry point for walk method
     * @throws Exception If DB fails to retrieve transactions
     */
    Hash getEntryPoint(int depth)throws Exception;

}
