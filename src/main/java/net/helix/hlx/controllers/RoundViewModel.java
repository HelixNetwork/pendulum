package net.helix.hlx.controllers;

import net.helix.hlx.model.Hash;
import net.helix.hlx.model.HashFactory;
import net.helix.hlx.model.IntegerIndex;
import net.helix.hlx.model.persistables.Round;
import net.helix.hlx.storage.Indexable;
import net.helix.hlx.storage.Persistable;
import net.helix.hlx.storage.Tangle;
import net.helix.hlx.utils.Pair;
import org.bouncycastle.util.encoders.Hex;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Acts as a controller interface for a {@link Round} hash object. This controller is used by the
 * {@link net.helix.hlx.service.milestone.LatestMilestoneTracker} to manipulate a {@link Round} object.
 */
public class RoundViewModel {
    private final Round round;
    //todo might be nice to have direct access of confirmedTips and confirming Milestones
    //private final Set<Hash> confirmedTips = new HashSet<>();
    //private final Set<Hash> confirmingMilestones = new HashSet<>();
    private static final Map<Integer, RoundViewModel> rounds = new ConcurrentHashMap<>();

    private RoundViewModel(final Round round) {
        this.round = round;
    }

    /**
     * Removes the contents of the stored {@link Round} object set.
     */
    public static void clear() {
        rounds.clear();
    }

    /**
     * This method removes a {@link RoundViewModel} from the cache.
     *
     * It is used by the {@link net.helix.hlx.service.transactionpruning.TransactionPruner} to remove rounds that
     * were deleted in the database, so that the runtime environment correctly reflects the database state.
     *
     * @param index the index of the round
     */
    public static void clear(int index) {
        rounds.remove(index);
    }

    /**
     * Constructor for a {@link Round} set controller. This controller is generated from a finalized
     * {@link Round} hash identifier, indexing this object to the integer {@link Round} index.
     *
     * @param index The finalized numerical index the {@link Round} object will be referenced by in the set
     * @param milestoneHashes The finalized {@link Hash} identifier for the {@link Round} object
     */
    public RoundViewModel(final int index, final Set<Hash> milestoneHashes) {
        this.round = new Round();
        this.round.index = new IntegerIndex(index);
        this.round.set = milestoneHashes;
    }

    /**
     * Fetches an existing {@link RoundViewModel} if its index reference can be found in the controller. If the
     * {@link RoundViewModel} is null, but the indexed {@link Round} object exists in the database, a new
     * controller is created for the {@link Round} object.
     *
     * @param tangle The tangle reference for the database
     * @param index The integer index of the {@link Round} object that the controller should be returned for
     * @return The {@link RoundViewModel} for the indexed {@link Round} object
     * @throws Exception Thrown if the database fails to load the indexed {@link Round} object
     */
    public static RoundViewModel get(Tangle tangle, int index) throws Exception {
        RoundViewModel roundViewModel = rounds.get(index);
        if(roundViewModel == null && load(tangle, index)) {
            roundViewModel = rounds.get(index);
        }
        return roundViewModel;
    }

    public static TransactionViewModel getMilestone(Tangle tangle, int index, Hash address) throws Exception {
        RoundViewModel roundViewModel = get(tangle, index);
        for (Hash milestoneHash : roundViewModel.getHashes()){
            TransactionViewModel milestoneTx = TransactionViewModel.fromHash(tangle, milestoneHash);
            if (milestoneTx.getAddressHash() == address){
                return milestoneTx;
            }
        }
        return null;
    }

    /**
     * Fetches a {@link Round} object from the database using its integer index. If the {@link Round} and the
     * associated {@link Hash} identifier are not null, a new {@link RoundViewModel} is created for the
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
        // todo didn't really get this, because tangle.load never returns null
        if (round != null && round.index != null) {
            rounds.put(index, new RoundViewModel(round));
            return true;
        }
        return false;
    }

    /**
     * Fetches the first persistable {@link Round} object from the database and generates a new
     * {@link RoundViewModel} from it. If no {@link Round} objects exist in the database, it will return null.
     *
     * @param tangle the tangle reference for the database
     * @return The new {@link RoundViewModel}
     * @throws Exception Thrown if the database fails to return a first object
     */
    public static RoundViewModel first(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> milestonePair = tangle.getFirst(Round.class, IntegerIndex.class);
        if(milestonePair != null && milestonePair.hi != null) {
            Round round = (Round) milestonePair.hi;
            return new RoundViewModel(round);
        }
        return null;
    }

    /**
     * Fetches the most recent persistable {@link Round} object from the database and generates a new
     * {@link RoundViewModel} from it. If no {@link Round} objects exist in the database, it will return null.
     *
     * @param tangle the tangle reference for the database
     * @return The new {@link RoundViewModel}
     * @throws Exception Thrown if the database fails to return a first object
     */
    public static RoundViewModel latest(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> milestonePair = tangle.getLatest(Round.class, IntegerIndex.class);
        if(milestonePair != null && milestonePair.hi != null) {
            Round round = (Round) milestonePair.hi;
            return new RoundViewModel(round);
        }
        return null;
    }


    /**
     * Fetches the previously indexed persistable {@link Round} object from the database and generates a new
     * {@link RoundViewModel} from it. If no {@link Round} objects exist in the database, it will return null.
     *
     * @param tangle the tangle reference for the database
     * @return The new {@link RoundViewModel}
     * @throws Exception Thrown if the database fails to return a first object
     */
    public RoundViewModel previous(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> milestonePair = tangle.previous(Round.class, this.round.index);
        if(milestonePair != null && milestonePair.hi != null) {
            Round round = (Round) milestonePair.hi;
            return new RoundViewModel((Round) round);
        }
        return null;
    }

    /**
     * Fetches the next indexed persistable {@link Round} object from the database and generates a new
     * {@link RoundViewModel} from it. If no {@link Round} objects exist in the database, it will return null.
     *
     * @param tangle The tangle reference for the database
     * @return The new {@link RoundViewModel}
     * @throws Exception Thrown if the database fails to return a first object
     */
    public RoundViewModel next(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> milestonePair = tangle.next(Round.class, this.round.index);
        if(milestonePair != null && milestonePair.hi != null) {
            Round round = (Round) milestonePair.hi;
            return new RoundViewModel(round);
        }
        return null;
    }

    /**
     * Fetches a {@link RoundViewModel} for the closest {@link Round} object previously indexed in the
     * database. The method starts at the provided index and works backwards through the database to try and find a
     * {@link RoundViewModel} for the previous indexes until a non null controller is found.
     *
     * @param tangle The tangle reference for the database
     * @param index The beginning index the method will work backwards from
     * @param minIndex The minimum index that should be found in the database
     * @return The {@link RoundViewModel} of the closest found controller previously indexed in the database
     * @throws Exception Thrown if there is a failure to fetch a previous {@link RoundViewModel}
     */
    public static RoundViewModel findClosestPrevRound(Tangle tangle, int index, int minIndex) throws Exception {
        // search for the previous milestone preceding our index
        RoundViewModel previousRoundViewModel = null;
        int currentIndex = index;
        while(previousRoundViewModel == null && --currentIndex >= minIndex) {
            previousRoundViewModel = RoundViewModel.get(tangle, currentIndex);
        }

        return previousRoundViewModel;
    }

    /**
     * This method looks for the next round after a given index.
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
    public static RoundViewModel findClosestNextRound(Tangle tangle, int index, int maxIndex) throws Exception {
        // search for the next milestone following our index
        RoundViewModel nextRoundViewModel = null;
        int currentIndex = index;
        while((nextRoundViewModel == null || nextRoundViewModel.getHashes().isEmpty()) && ++currentIndex <= maxIndex ) {
            nextRoundViewModel = RoundViewModel.get(tangle, currentIndex);
        }

        return nextRoundViewModel;
    }

    public Set<Hash> getTipSet(Tangle tangle, Hash milestone) throws Exception {

        int security = 1;
        TransactionViewModel milestoneTx = TransactionViewModel.fromHash(tangle, milestone);
        BundleViewModel bundle = BundleViewModel.load(tangle, milestoneTx.getBundleHash());
        Set<Hash> tips = new HashSet<>();

        for (Hash bundleTxHash : bundle.getHashes()) {

            TransactionViewModel bundleTx = TransactionViewModel.fromHash(tangle, bundleTxHash);
            if ((bundleTx.getCurrentIndex() > security)) {

                for (int i = 0; i < 16; i++) {
                    Hash tip = HashFactory.TRANSACTION.create(bundleTx.getSignature(), i * Hash.SIZE_IN_BYTES, Hash.SIZE_IN_BYTES);
                    if (tip.equals(Hash.NULL_HASH)) {
                        break;
                    }
                    tips.add(tip);
                }
            }
        }
        return tips;
    }

    public Set<Hash> getConfirmedTips(Tangle tangle) throws Exception {

        Map<Hash, Integer> occurrences = new HashMap<>();
        int quorum = 2 * size() / 3;

        for (Hash milestoneHash : getHashes()) {
            Set<Hash> tips = getTipSet(tangle, milestoneHash);

            for (Hash tip : tips) {
                if (occurrences.containsKey(tip)) {
                    occurrences.put(tip, occurrences.get(tip) + 1);
                } else {
                    occurrences.put(tip, 1);
                }
            }
        }
        Set<Hash> tips = occurrences.entrySet().stream()
                .filter(entry -> entry.getValue() >= quorum)
                .map(entry -> entry.getKey())
                .collect(Collectors.toSet());
        return tips;
    }

    public Set<Hash> getConfirmingMilestones(Tangle tangle) throws Exception {
        Set<Hash> confirmedTips = getConfirmedTips(tangle);
        Set<Hash> confirmingMilestones = new HashSet<>();

        for (Hash milestoneHash : getHashes()) {
            Set<Hash> tips = getTipSet(tangle, milestoneHash);
            if (confirmedTips.equals(tips) || (confirmedTips.isEmpty() && tips.isEmpty())) {
                confirmingMilestones.add(milestoneHash);
            }
        }
        return  confirmingMilestones;
    }

    public Hash getRandomConfirmingMilestone(Tangle tangle) throws Exception {
        Set<Hash> confirmingMilestones = getHashes(); // todo getConfirmingMilestones(tangle);
        int item = new Random().nextInt(confirmingMilestones.size());
        int i = 0;
        for(Hash obj : confirmingMilestones) {
            if (i == item)
                return obj;
            i++;
        }
        return (Hash) confirmingMilestones.toArray()[0];
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

    public int size() {
        return round.set.size();
    }

    public boolean addMilestone(Hash milestoneHash) {
        return getHashes().add(milestoneHash);
    }

    public boolean update(Tangle tangle) throws Exception {
        return tangle.update(round, round.index, "round");
    }

    /**@return  The {@link Hash} identifier of the {@link Round} object*/
    public Set<Hash> getHashes() {
        return round.set;
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
        String hashes = getHashes().stream().map(Hash::hexString).collect(Collectors.joining(","));
        return "round #" + index() + " (" + hashes + ")";
    }
}
