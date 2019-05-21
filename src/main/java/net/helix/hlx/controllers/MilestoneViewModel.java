package net.helix.hlx.controllers;

import net.helix.hlx.model.Hash;
import net.helix.hlx.model.IntegerIndex;
import net.helix.hlx.model.persistables.Round;
import net.helix.hlx.storage.Indexable;
import net.helix.hlx.storage.Persistable;
import net.helix.hlx.storage.Tangle;
import net.helix.hlx.utils.Pair;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Acts as a controller interface for a {@link Round} hash object. This controller is used by the
 * {@link net.helix.hlx.service.milestone.LatestMilestoneTracker} to manipulate a {@link Round} object.
 */
public class MilestoneViewModel {
    private final Round round;
    private static final Map<Integer, MilestoneViewModel> milestones = new ConcurrentHashMap<>();

    private MilestoneViewModel(final Round round) {
        this.round = round;
    }

    /**
     * Removes the contents of the stored {@link Round} object set.
     */
    public static void clear() {
        milestones.clear();
    }

    /**
     * This method removes a {@link MilestoneViewModel} from the cache.
     *
     * It is used by the {@link net.helix.hlx.service.transactionpruning.TransactionPruner} to remove milestones that
     * were deleted in the database, so that the runtime environment correctly reflects the database state.
     *
     * @param milestoneIndex the index of the milestone
     */
    public static void clear(int milestoneIndex) {
        milestones.remove(milestoneIndex);
    }

    /**
     * Constructor for a {@link Round} set controller. This controller is generated from a finalized
     * {@link Round} hash identifier, indexing this object to the integer {@link Round} index.
     *
     * @param index The finalized numerical index the {@link Round} object will be referenced by in the set
     * @param milestoneHash The finalized {@link Hash} identifier for the {@link Round} object
     */
    public MilestoneViewModel(final int index, final Hash milestoneHash) {
        this.round = new Round();
        this.round.index = new IntegerIndex(index);
        round.hash = milestoneHash;
    }

    /**
     * Fetches an existing {@link MilestoneViewModel} if its index reference can be found in the controller. If the
     * {@link MilestoneViewModel} is null, but the indexed {@link Round} object exists in the database, a new
     * controller is created for the {@link Round} object.
     *
     * @param tangle The tangle reference for the database
     * @param index The integer index of the {@link Round} object that the controller should be returned for
     * @return The {@link MilestoneViewModel} for the indexed {@link Round} object
     * @throws Exception Thrown if the database fails to load the indexed {@link Round} object
     */
    public static MilestoneViewModel get(Tangle tangle, int index) throws Exception {
        MilestoneViewModel milestoneViewModel = milestones.get(index);
        if(milestoneViewModel == null && load(tangle, index)) {
            milestoneViewModel = milestones.get(index);
        }
        return milestoneViewModel;
    }

    /**
     * Fetches a {@link Round} object from the database using its integer index. If the {@link Round} and the
     * associated {@link Hash} identifier are not null, a new {@link MilestoneViewModel} is created for the
     * {@link Round} object, and it is placed into the <tt>Milestones</tt> set, indexed by the provided integer
     * index.
     *
     * @param tangle The tangle reference for the database
     * @param index The integer index reference for the {@link Round} object
     * @return True if the {@link Round} object is stored in the <tt>Milestones</tt> set, False if not
     * @throws Exception Thrown if the database fails to load the {@link Round} object
     */
    public static boolean load(Tangle tangle, int index) throws Exception {
        Round round = (Round) tangle.load(Round.class, new IntegerIndex(index));
        if(round != null && round.hash != null) {
            milestones.put(index, new MilestoneViewModel(round));
            return true;
        }
        return false;
    }

    /**
     * Fetches the first persistable {@link Round} object from the database and generates a new
     * {@link MilestoneViewModel} from it. If no {@link Round} objects exist in the database, it will return null.
     *
     * @param tangle the tangle reference for the database
     * @return The new {@link MilestoneViewModel}
     * @throws Exception Thrown if the database fails to return a first object
     */
    public static MilestoneViewModel first(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> milestonePair = tangle.getFirst(Round.class, IntegerIndex.class);
        if(milestonePair != null && milestonePair.hi != null) {
            Round round = (Round) milestonePair.hi;
            return new MilestoneViewModel(round);
        }
        return null;
    }

    /**
     * Fetches the most recent persistable {@link Round} object from the database and generates a new
     * {@link MilestoneViewModel} from it. If no {@link Round} objects exist in the database, it will return null.
     *
     * @param tangle the tangle reference for the database
     * @return The new {@link MilestoneViewModel}
     * @throws Exception Thrown if the database fails to return a first object
     */
    public static MilestoneViewModel latest(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> milestonePair = tangle.getLatest(Round.class, IntegerIndex.class);
        if(milestonePair != null && milestonePair.hi != null) {
            Round round = (Round) milestonePair.hi;
            return new MilestoneViewModel(round);
        }
        return null;
    }


    /**
     * Fetches the previously indexed persistable {@link Round} object from the database and generates a new
     * {@link MilestoneViewModel} from it. If no {@link Round} objects exist in the database, it will return null.
     *
     * @param tangle the tangle reference for the database
     * @return The new {@link MilestoneViewModel}
     * @throws Exception Thrown if the database fails to return a first object
     */
    public MilestoneViewModel previous(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> milestonePair = tangle.previous(Round.class, this.round.index);
        if(milestonePair != null && milestonePair.hi != null) {
            Round round = (Round) milestonePair.hi;
            return new MilestoneViewModel((Round) round);
        }
        return null;
    }

    /**
     * Fetches the next indexed persistable {@link Round} object from the database and generates a new
     * {@link MilestoneViewModel} from it. If no {@link Round} objects exist in the database, it will return null.
     *
     * @param tangle The tangle reference for the database
     * @return The new {@link MilestoneViewModel}
     * @throws Exception Thrown if the database fails to return a first object
     */
    public MilestoneViewModel next(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> milestonePair = tangle.next(Round.class, this.round.index);
        if(milestonePair != null && milestonePair.hi != null) {
            Round round = (Round) milestonePair.hi;
            return new MilestoneViewModel((Round) round);
        }
        return null;
    }

    /**
     * Fetches a {@link MilestoneViewModel} for the closest {@link Round} object previously indexed in the
     * database. The method starts at the provided index and works backwards through the database to try and find a
     * {@link MilestoneViewModel} for the previous indexes until a non null controller is found.
     *
     * @param tangle The tangle reference for the database
     * @param index The beginning index the method will work backwards from
     * @param minIndex The minimum index that should be found in the database
     * @return The {@link MilestoneViewModel} of the closest found controller previously indexed in the database
     * @throws Exception Thrown if there is a failure to fetch a previous {@link MilestoneViewModel}
     */
    public static MilestoneViewModel findClosestPrevMilestone(Tangle tangle, int index, int minIndex) throws Exception {
        // search for the previous milestone preceding our index
        MilestoneViewModel previousMilestoneViewModel = null;
        int currentIndex = index;
        while(previousMilestoneViewModel == null && --currentIndex >= minIndex) {
            previousMilestoneViewModel = MilestoneViewModel.get(tangle, currentIndex);
        }

        return previousMilestoneViewModel;
    }

    /**
     * This method looks for the next milestone after a given index.
     *
     * In contrast to the {@link #next} method we do not rely on the insertion order in the database but actively search
     * for the milestone that was issued next by the coordinator (coo-order preserved).
     *
     * @param tangle Tangle object which acts as a database interface
     * @param index milestone index where the search shall start
     * @param maxIndex milestone index where the search shall stop
     * @return the milestone which follows directly after the given index or null if none was found
     * @throws Exception if anything goes wrong while loading entries from the database
     */
    public static MilestoneViewModel findClosestNextMilestone(Tangle tangle, int index, int maxIndex) throws Exception {
        // search for the next milestone following our index
        MilestoneViewModel nextMilestoneViewModel = null;
        int currentIndex = index;
        while(nextMilestoneViewModel == null && ++currentIndex <= maxIndex) {
            nextMilestoneViewModel = MilestoneViewModel.get(tangle, currentIndex);
        }

        return nextMilestoneViewModel;
    }

    /**
     * Save the {@link Round} object, indexed by its integer index, to the database.
     *
     * @param tangle The tangle reference for the database
     * @return True if the {@link Round} object is saved correctly, False if not
     * @throws Exception Thrown if there is an error while saving the {@link Round} object
     */
    public boolean store(Tangle tangle) throws Exception {
        return tangle.save(round, round.index);
    }

    /**@return  The {@link Hash} identifier of the {@link Round} object*/
    public Hash getHash() {
        return round.hash;
    }

    /**@return The integer index of the {@link Round} object*/
    public Integer index() {
        return round.index.getValue();
    }

    /**
     * Removes the {@link Round} object from the database.
     *
     * @param tangle The tangle reference for the database
     * @throws Exception Thrown if there is an error removing the {@link Round} object
     */
    public void delete(Tangle tangle) throws Exception {
        tangle.delete(Round.class, round.index);
    }

    /**
     * This method creates a human readable string representation of the milestone.
     *
     * It can be used to directly append the milestone in error and debug messages.
     *
     * @return human readable string representation of the milestone
     */
    @Override
    public String toString() {
        return "milestone #" + index() + " (" + getHash().toString() + ")";
    }
}
