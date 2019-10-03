package net.helix.pendulum.service.tipselection;

import java.util.Map;

import net.helix.pendulum.model.Hash;


/**
 * Selects an {@code entryPoint} for tip selection.
 * <p>
 * this point is used as the starting point for {@link Walker#walk(Hash, Map, WalkValidator)}
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
