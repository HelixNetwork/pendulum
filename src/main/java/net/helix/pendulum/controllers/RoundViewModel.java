package net.helix.pendulum.controllers;

import net.helix.pendulum.TransactionValidator;
import net.helix.pendulum.conf.BasePendulumConfig;
import net.helix.pendulum.crypto.merkle.MerkleFactory;
import net.helix.pendulum.crypto.merkle.MerkleOptions;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.HashFactory;
import net.helix.pendulum.model.IntegerIndex;
import net.helix.pendulum.model.persistables.Round;
import net.helix.pendulum.service.milestone.MilestoneTracker;
import net.helix.pendulum.storage.Indexable;
import net.helix.pendulum.storage.Persistable;
import net.helix.pendulum.storage.Tangle;
import net.helix.pendulum.utils.Pair;
import net.helix.pendulum.utils.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static net.helix.pendulum.controllers.TransactionViewModel.fromHash;

/**
 * Acts as a controller interface for a {@link Round} hash object. This controller is used by the
 * {@link MilestoneTracker} to manipulate a {@link Round} object.
 */
public class RoundViewModel {
    private static final Logger log = LoggerFactory.getLogger(RoundViewModel.class);
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
     * It is used by the {@link net.helix.pendulum.service.transactionpruning.TransactionPruner} to remove rounds that
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
        for (Hash milestoneHash : roundViewModel.getHashes()) {
            TransactionViewModel milestoneTx = TransactionViewModel.fromHash(tangle, milestoneHash);
            if (milestoneTx.getAddressHash() == address) {
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
            return new RoundViewModel(round);
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

    public static int getRoundIndex(TransactionViewModel milestoneTransaction) {
        return (int) Serializer.getLong(milestoneTransaction.getBytes(), TransactionViewModel.TAG_OFFSET);
        //TODO: why is index stored in bundle nonce (obsolete tag) in new version?
        //return (int) Serializer.getLong(milestoneTransaction.getBytes(), BUNDLE_NONCE_OFFSET);
    }

    // todo this may be very inefficient
    public static Set<Hash> getMilestoneTrunk(Tangle tangle, TransactionViewModel transaction, TransactionViewModel milestoneTx) throws Exception{
        Set<Hash> trunk = new HashSet<>();
        int round = RoundViewModel.getRoundIndex(milestoneTx);
        // idx = n: milestone merkle root in trunk
        if (transaction.getCurrentIndex() == transaction.lastIndex()) {
            // add previous milestones to non analyzed transactions
            RoundViewModel prevMilestone = RoundViewModel.get(tangle, round-1);
            if (prevMilestone == null) {
                if (transaction.getBranchTransactionHash().equals(Hash.NULL_HASH)) {
                    trunk.add(Hash.NULL_HASH);
                }
            } else {
                Set<Hash> prevMilestones = prevMilestone.getHashes();
                List<List<Hash>> merkleTree = MerkleFactory.create(MerkleFactory.MerkleTree, MerkleOptions.getDefault()).buildMerkleTree(new ArrayList<>(prevMilestones));
                if (transaction.getTrunkTransactionHash().equals(merkleTree.get(merkleTree.size() - 1).get(0))) {
                    if (prevMilestones.isEmpty()) {
                        trunk.add(Hash.NULL_HASH);
                    } else {
                        trunk.addAll(prevMilestones);
                    }
                }
            }
        }
        else {
            // idx = 0 - (n-1): merkle root in branch, trunk is normal tx hash
            trunk.add(transaction.getTrunkTransactionHash());
        }
        return trunk;
    }

    public static Set<Hash> getMilestoneBranch(Tangle tangle, TransactionViewModel transaction, TransactionViewModel milestoneTx, int security) throws Exception{
        Set<Hash> branch = new HashSet<>();
        int round = RoundViewModel.getRoundIndex(milestoneTx);
        // idx = n: milestone merkle root in trunk and tips merkle root in branch
        if (transaction.getCurrentIndex() == transaction.lastIndex()) {
            // tips merkle root
            Set<Hash> confirmedTips = getTipSet(tangle, milestoneTx.getHash(), security);
            List<List<Hash>> merkleTree = MerkleFactory.create(MerkleFactory.MerkleTree, MerkleOptions.getDefault()).buildMerkleTree(new ArrayList<>(confirmedTips));
            if (transaction.getBranchTransactionHash().equals(merkleTree.get(merkleTree.size()-1).get(0))) {
                if (confirmedTips.isEmpty()){
                    branch.add(Hash.NULL_HASH);
                } else {
                    branch.addAll(confirmedTips);
                }
            }
        }
        else {
            // add previous milestones to non analyzed transactions
            RoundViewModel prevMilestone = RoundViewModel.get(tangle, round-1);
            if (prevMilestone == null) {
                if (transaction.getBranchTransactionHash().equals(Hash.NULL_HASH)) {
                    branch.add(Hash.NULL_HASH);
                }
            } else {
                Set<Hash> prevMilestones = prevMilestone.getHashes();
                List<List<Hash>> merkleTree = MerkleFactory.create(MerkleFactory.MerkleTree, MerkleOptions.getDefault()).buildMerkleTree(new ArrayList<>(prevMilestones));
                if (transaction.getBranchTransactionHash().equals(merkleTree.get(merkleTree.size() - 1).get(0))) {
                    if (prevMilestones.isEmpty()) {
                        branch.add(Hash.NULL_HASH);
                    } else {
                        branch.addAll(prevMilestones);
                    }
                }
            }
        }
        return branch;
    }

    public static Set<Hash> getTipSet(Tangle tangle, Hash milestone, int security) throws Exception {

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

    public Set<Hash> getConfirmedTips(Tangle tangle, int security) throws Exception {
        Map<Hash, Integer> occurrences = new HashMap<>();
        int quorum = 2 *  BasePendulumConfig.Defaults.NUMBER_OF_ACTIVE_VALIDATORS / 3;

        for (Hash milestoneHash : getHashes()) {
            Set<Hash> tips = getTipSet(tangle, milestoneHash, security);

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

    /**
     *
     *  This method can be used to check whether a transaction has been finalized.
     *  As getConfirmedTips is used, only transactions with a 2/3rd majority are considered.
     *
     * @param tangle tangle
     * @param security security level
     * @param transaction transaction
     * @return boolean, whether the transaction in question exists in the confirmedTransactions set.
     * @throws Exception Exception
     */
    public boolean isTransactionConfirmed(Tangle tangle, int security, Hash transaction) throws Exception {
        Set<Hash> confirmedTransactions = getReferencedTransactions(tangle, getConfirmedTips(tangle, security));
        return confirmedTransactions.contains(transaction);
    }

    /**
     *
     * This method is used to find parents of a given tip set. the tips should either have 0 roundIndex or a roundIndex corresponding to the this.index.
     * We mainly use it to count transaction.confirmations in MilestoneTracker.
     * In the future we could use DAGHelper to traverse tangle,
     * but as this method currently does not work with the finality update, we use this as a helper to count confirmations.
     *
     * @param tangle tangle
     * @param tips tips
     * @return Set of traversed transaction hashes that are parents of the provided tip set
     * @throws Exception Exception
     */
    public Set<Hash> getReferencedTransactions(Tangle tangle, Set<Hash> tips) throws Exception {

        Set<Hash> seenTransactions = new HashSet<Hash>();
        Set<Hash> transactions = new HashSet<>();
        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(tips);
        Hash hashPointer;
        while ((hashPointer = nonAnalyzedTransactions.poll()) != null) {
            final TransactionViewModel transaction = fromHash(tangle, hashPointer);
            // take only transactions into account that aren't confirmed yet or that belong to the round
            if (transaction.getRoundIndex() == 0 || transaction.getRoundIndex() == index()) {
                // we can add the tx to confirmed transactions, because it is a parent of confirmedTips
                transactions.add(hashPointer);
                // traverse parents and add new candidates to queue
                if(!seenTransactions.contains(transaction.getTrunkTransactionHash())){
                    seenTransactions.add(transaction.getTrunkTransactionHash());
                    nonAnalyzedTransactions.offer(transaction.getTrunkTransactionHash());
                }

                if(!seenTransactions.contains(transaction.getBranchTransactionHash())){
                    seenTransactions.add(transaction.getBranchTransactionHash());
                    nonAnalyzedTransactions.offer(transaction.getBranchTransactionHash());
                }

            // roundIndex already set, i.e. tx is already confirmed.
            } else {
                continue;
            }
        }
        return transactions;
    }

    public Hash getRandomMilestone(Tangle tangle) throws Exception {
        Set<Hash> confirmingMilestones = getHashes(); // todo getConfirmingMilestones(tangle);
        if (!confirmingMilestones.isEmpty()) {
            int item = new Random().nextInt(confirmingMilestones.size());
            int i = 0;
            for(Hash obj : confirmingMilestones) {
                if (i == item) {
                    return obj;
                }
                i++;
            }
        }
        return null;
    }

    public static void updateApprovees(Tangle tangle, TransactionValidator transactionValidator, List<TransactionViewModel> milestoneBundle, Hash milestone, int security) throws Exception{
        Set<Hash> confirmedTips = getTipSet(tangle, milestone, security);
        TransactionViewModel lastTx = milestoneBundle.get(milestoneBundle.size() - 1);
        // last transaction references tips
        for (Hash tip : confirmedTips){
            ApproveeViewModel approve = new ApproveeViewModel(tip);
            approve.addHash(lastTx.getHash());
            approve.store(tangle);
        }
        transactionValidator.updateStatus(TransactionViewModel.fromHash(tangle, lastTx.getHash()));
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

    public void update(Tangle tangle) throws Exception {
        tangle.update(round, round.index, "round");
    }

    /**@return  The {@link Hash} identifier of the {@link Round} object*/
    public Set<Hash> getHashes() {
        return round.set;
    }

    /**@return The integer index of the {@link Round} object*/
    public Integer index() {
        return round.index.getValue();
    }

    public Hash getMerkleRoot() {
        List<List<Hash>> merkleTree = MerkleFactory.create(MerkleFactory.MerkleTree, MerkleOptions.getDefault()).buildMerkleTree(new LinkedList<>(getHashes()));
        Hash root = merkleTree.get(merkleTree.size()-1).get(0);
        return root;
    }

    /**
     * Removes the {@link Round} object from the database.
     *
     * @param tangle The tangle reference for the database
     * @throws Exception Thrown if there is an error removing the {@link Round} object
     */
    public void delete(Tangle tangle) throws Exception {
        tangle.delete(Round.class, round.index);
        clear(round.index.getValue());
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
        String hashes = getHashes().stream().map(Hash::toString).collect(Collectors.joining(","));
        return "round #" + index() + " (" + hashes + ")";
    }
}
