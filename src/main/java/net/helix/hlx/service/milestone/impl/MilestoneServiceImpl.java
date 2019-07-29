package net.helix.hlx.service.milestone.impl;

import com.google.gson.JsonObject;
import net.helix.hlx.BundleValidator;
import net.helix.hlx.conf.ConsensusConfig;
import net.helix.hlx.controllers.RoundViewModel;
import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.crypto.Merkle;
import net.helix.hlx.crypto.SpongeFactory;
import net.helix.hlx.model.Hash;
import net.helix.hlx.model.IntegerIndex;
import net.helix.hlx.model.StateDiff;
import net.helix.hlx.service.Graphstream;
import net.helix.hlx.service.milestone.MilestoneException;
import net.helix.hlx.service.milestone.MilestoneService;
import net.helix.hlx.service.milestone.MilestoneValidity;
import net.helix.hlx.service.snapshot.Snapshot;
import net.helix.hlx.service.snapshot.SnapshotProvider;
import net.helix.hlx.service.snapshot.SnapshotService;
import net.helix.hlx.storage.Tangle;
import net.helix.hlx.TransactionValidator;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static net.helix.hlx.service.milestone.MilestoneValidity.*;

/**
 * Creates a service instance that allows us to perform milestone specific operations.<br />
 * <br />
 * This class is stateless and does not hold any domain specific models.<br />
 */
public class MilestoneServiceImpl implements MilestoneService {
    /**
     * Holds the logger of this class.<br />
     */
    private final static Logger log = LoggerFactory.getLogger(MilestoneServiceImpl.class);

    /**
     * Holds the tangle object which acts as a database interface.<br />
     */
    private Tangle tangle;

    /**
     * Holds the snapshot provider which gives us access to the relevant snapshots.<br />
     */
    private SnapshotProvider snapshotProvider;

    /**
     * Holds a reference to the service instance of the snapshot package that allows us to rollback ledger states.<br />
     */
    private SnapshotService snapshotService;


    TransactionValidator transactionValidator;

    /**
     * Holds the config with important milestone specific settings.<br />
     */
    private ConsensusConfig config;

    /**
     * This method initializes the instance and registers its dependencies.<br />
     * <br />
     * It simply stores the passed in values in their corresponding private properties.<br />
     * <br />
     * Note: Instead of handing over the dependencies in the constructor, we register them lazy. This allows us to have
     *       circular dependencies because the instantiation is separated from the dependency injection. To reduce the
     *       amount of code that is necessary to correctly instantiate this class, we return the instance itself which
     *       allows us to still instantiate, initialize and assign in one line - see Example:<br />
     *       <br />
     *       {@code milestoneService = new MilestoneServiceImpl().init(...);}
     *
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider snapshot provider which gives us access to the relevant snapshots
     * @param config config with important milestone specific settings
     * @return the initialized instance itself to allow chaining
     */
    public MilestoneServiceImpl init(Tangle tangle, SnapshotProvider snapshotProvider, SnapshotService snapshotService, TransactionValidator transactionValidator, ConsensusConfig config) {

        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.snapshotService = snapshotService;
        this.transactionValidator = transactionValidator;
        this.config = config;

        return this;
    }

    //region {PUBLIC METHODS] //////////////////////////////////////////////////////////////////////////////////////////

    /**
     * {@inheritDoc}
     * <br />
     * We first check the trivial case where the node was fully synced. If no processed solid milestone could be found
     * within the last two milestones of the node, we perform a binary search from present to past, which reduces the
     * amount of database requests to a minimum (even with a huge amount of milestones in the database).<br />
     */
    @Override
    public Optional<RoundViewModel> findLatestProcessedSolidRoundInDatabase() throws MilestoneException {
        try {

            // if we have no milestone in our database -> abort
            RoundViewModel latestRound = RoundViewModel.latest(tangle);
            if (latestRound == null) {
                System.out.println("we have no milestone in our database");
                return Optional.empty();
            }
            System.out.println("Latest Round: " + latestRound.index());
            System.out.println("Was applied to ledger: " + wasRoundAppliedToLedger(latestRound));

            // trivial case #1: the node was fully synced
            if (wasRoundAppliedToLedger(latestRound)) {
                System.out.println("the node was fully synced");
                return Optional.of(latestRound);
            }

            // trivial case #2: the node was fully synced but the last milestone was not processed, yet
            RoundViewModel latestRoundPredecessor = RoundViewModel.findClosestPrevRound(tangle,
                    latestRound.index(), snapshotProvider.getInitialSnapshot().getIndex());
            if (latestRoundPredecessor != null && wasRoundAppliedToLedger(latestRoundPredecessor)) {
                return Optional.of(latestRoundPredecessor);
            }

            System.out.println("Closest Prev Round: " + latestRoundPredecessor.index());
            System.out.println("Was applied to ledger: " + wasRoundAppliedToLedger(latestRoundPredecessor));

            // non-trivial case: do a binary search in the database
            return binarySearchLatestProcessedSolidRoundInDatabase(latestRound);
        } catch (Exception e) {
            throw new MilestoneException(
                    "unexpected error while trying to find the latest processed solid milestone in the database", e);
        }
    }

    @Override
    public void updateRoundIndexOfMilestoneTransactions(int index, Graphstream graph) throws MilestoneException {
        if (index <= 0) {
            throw new MilestoneException("the new index needs to be bigger than 0 " +
                    "(use resetCorruptedMilestone to reset the milestone index)");
        }

        updateRoundIndexOfMilestoneTransactions(index, index, new HashSet<>(), graph);
    }

    /**
     * {@inheritDoc}
     * <br />
     * We redirect the call to {@link #resetCorruptedRound(int, Set)} while initiating the set of {@code
     * processedTransactions} with an empty {@link HashSet} which will ensure that we reset all found
     * transactions.<br />
     */
    @Override
    public void resetCorruptedRound(int index) throws MilestoneException {
        resetCorruptedRound(index, new HashSet<>());
    }

    @Override
    public MilestoneValidity validateMilestone(TransactionViewModel transactionViewModel, int roundIndex,
                                               SpongeFactory.Mode mode, int securityLevel, Set<Hash> validatorAddresses) throws MilestoneException {

        if (roundIndex < 0 || roundIndex >= 0x200000) {
            return INVALID;
        }

        try {
            RoundViewModel round = RoundViewModel.get(tangle, roundIndex);
            if (round != null && round.getHashes().contains(transactionViewModel.getHash())) {
                // Already validated.
                System.out.println("Already validated!");
                return VALID;
            }

            final List<List<TransactionViewModel>> bundleTransactions = BundleValidator.validate(tangle,
                    snapshotProvider.getInitialSnapshot(), transactionViewModel.getHash());

            if (bundleTransactions.isEmpty()) {
                return INCOMPLETE;
            } else {
                for (final List<TransactionViewModel> bundleTransactionViewModels : bundleTransactions) {
                    final TransactionViewModel tail = bundleTransactionViewModels.get(0);   // milestone transaction with signature
                    if (tail.getHash().equals(transactionViewModel.getHash())) {

                        //todo implement when sure how bundle structure has to look like
                        //if (isMilestoneBundleStructureValid(bundleTransactionViewModels, securityLevel)) {

                            Hash senderAddress = tail.getAddressHash();
                            boolean validSignature = Merkle.validateMerkleSignature(bundleTransactionViewModels, mode, senderAddress, securityLevel, config.getNumberOfKeysInMilestone());

                            if ((config.isTestnet() && config.isDontValidateTestnetMilestoneSig()) ||
                                    (validatorAddresses.contains(senderAddress)) && validSignature) {

                                transactionViewModel.isMilestone(tangle, snapshotProvider.getInitialSnapshot(), true);

                                //update tip status of approved tips
                                RoundViewModel.updateApprovees(tangle, transactionValidator, bundleTransactionViewModels, transactionViewModel.getHash());

                                // if we find a NEW milestone for a round that already has been processed
                                // and considered as solid (there is already a snapshot without this milestone)
                                // -> reset the ledger state and check the milestones again
                                //
                                // NOTE: this can happen if a new subtangle becomes solid before a previous one while
                                //       syncing
                                if (roundIndex < snapshotProvider.getLatestSnapshot().getIndex() &&
                                        roundIndex > snapshotProvider.getInitialSnapshot().getIndex()) {

                                    resetCorruptedRound(roundIndex);
                                }

                                return VALID;
                            } else {
                                return INVALID;
                            }
                    }
                }
            }
        } catch (Exception e) {
            throw new MilestoneException("error while checking milestone status of " + transactionViewModel, e);
        }

        return INVALID;
    }

    @Override
    public Set<Hash> getConfirmedTips(int roundNumber) throws Exception {

        RoundViewModel round = RoundViewModel.get(tangle, roundNumber);
        return round.getConfirmedTips(tangle);
    }

    /*@Override
    public Set<Hash> getConfirmedTransactions(int roundNumber, int quorum) throws Exception{
        Set<Hash> confirmedTips = getConfirmedTips(roundNumber, quorum);
        ...
    }*/

    /*@Override
    public boolean isTransactionConfirmed(Hash transactionHash, int roundNumber, int quorum) {
        Set<Hash> confimedTx = getConfirmedTransactions(roundNumber);
        ...
    }*/

    @Override
    public boolean isTransactionConfirmed(TransactionViewModel transaction, int roundIndex) {
        return transaction.snapshotIndex() != 0 && transaction.snapshotIndex() <= roundIndex;
    }

    @Override
    public boolean isTransactionConfirmed(TransactionViewModel transaction) {
        return isTransactionConfirmed(transaction, snapshotProvider.getLatestSnapshot().getIndex());
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region [PRIVATE UTILITY METHODS] /////////////////////////////////////////////////////////////////////////////////

    /**
     * Performs a binary search for the latest solid milestone which was already processed by the node and applied to
     * the ledger state at some point in the past (i.e. before IRI got restarted).<br />
     * <br />
     * It searches from present to past using a binary search algorithm which quickly narrows down the amount of
     * candidates even for big databases.<br />
     *
     * @param latestMilestone the latest milestone in the database (used to define the search range)
     * @return the latest solid milestone that was previously processed by hlx or an empty value if no previously
     *         processed solid milestone can be found
     * @throws Exception if anything unexpected happens while performing the search
     */
    private Optional<RoundViewModel> binarySearchLatestProcessedSolidRoundInDatabase(
            RoundViewModel latestMilestone) throws Exception {

        Optional<RoundViewModel> lastAppliedCandidate = Optional.empty();

        int rangeEnd = latestMilestone.index();
        int rangeStart = snapshotProvider.getInitialSnapshot().getIndex() + 1;
        while (rangeEnd - rangeStart >= 0) {
            // if no candidate found in range -> return last candidate
            RoundViewModel currentCandidate = getRoundInMiddleOfRange(rangeStart, rangeEnd);
            if (currentCandidate == null) {
                return lastAppliedCandidate;
            }

            // if the milestone was applied -> continue to search for "later" ones that might have also been applied
            if (wasRoundAppliedToLedger(currentCandidate)) {
                rangeStart = currentCandidate.index() + 1;

                lastAppliedCandidate = Optional.of(currentCandidate);
            }

            // if the milestone was not applied -> continue to search for "earlier" ones
            else {
                rangeEnd = currentCandidate.index() - 1;
            }
        }

        return lastAppliedCandidate;
    }

    /**
     * Determines the milestone in the middle of the range defined by {@code rangeStart} and {@code rangeEnd}.<br />
     * <br />
     * It is used by the binary search algorithm of {@link #findLatestProcessedSolidRoundInDatabase()}. It first
     * calculates the index that represents the middle of the range and then tries to find the milestone that is closest
     * to this index.<br/>
     * <br />
     * Note: We start looking for younger milestones first, because most of the times the latest processed solid
     *       milestone is close to the end.<br />
     *
     * @param rangeStart the milestone index representing the start of our search range
     * @param rangeEnd the milestone index representing the end of our search range
     * @return the milestone that is closest to the middle of the given range or {@code null} if no milestone can be
     *         found
     * @throws Exception if anything unexpected happens while trying to get the milestone
     */
    private RoundViewModel getRoundInMiddleOfRange(int rangeStart, int rangeEnd) throws Exception {
        int range = rangeEnd - rangeStart;
        int middleOfRange = rangeEnd - range / 2;

        RoundViewModel milestone = RoundViewModel.findClosestNextRound(tangle, middleOfRange - 1, rangeEnd);
        if (milestone == null) {
            milestone = RoundViewModel.findClosestPrevRound(tangle, middleOfRange, rangeStart);
        }

        return milestone;
    }

    /**
     * Checks if the milestone was applied to the ledger at some point in the past (before a restart of IRI).<br />
     * <br />
     * Since the {@code snapshotIndex} value is used as a flag to determine if the milestone was already applied to the
     * ledger, we can use it to determine if it was processed by IRI in the past. If this value is set we should also
     * have a corresponding {@link StateDiff} entry in the database.<br />
     *
     * @param round the milestone that shall be checked
     * @return {@code true} if the milestone has been processed by IRI before and {@code false} otherwise
     * @throws Exception if anything unexpected happens while checking the milestone
     */
    private boolean wasRoundAppliedToLedger(RoundViewModel round) throws Exception {
        //TODO: snapshot index of which milestone should be checked?
        if (round.size() > 0) {
            TransactionViewModel milestoneTransaction = TransactionViewModel.fromHash(tangle, (Hash) round.getHashes().toArray()[0]);
            System.out.println("round: " + round.index() + ", snapshot: " + milestoneTransaction.snapshotIndex());
            return milestoneTransaction.getType() != TransactionViewModel.PREFILLED_SLOT &&
                    milestoneTransaction.snapshotIndex() != 0;
        }
        else {
            return false;
        }
    }

    /**
     * This method implements the logic described by updateRoundIndexOfMilestoneTransactions() but
     * accepts some additional parameters that allow it to be reused by different parts of this service.<br />
     *
     * @param correctIndex the milestone index of the milestone that would be set if all transactions are marked
     *                     correctly
     * @param newIndex the milestone index that shall be set
     * @throws MilestoneException if anything unexpected happens while updating the milestone index
     * @param processedTransactions a set of transactions that have been processed already (for the recursive calls)
     */
    private void updateRoundIndexOfMilestoneTransactions(int correctIndex, int newIndex,
                                                             Set<Hash> processedTransactions, Graphstream graph) throws MilestoneException {

        //System.out.println("UPDATE ROUND INDEX");
        Set<Integer> inconsistentMilestones = new HashSet<>();

        try {
            // update milestones
            RoundViewModel round = RoundViewModel.get(tangle, newIndex);
            for (Hash milestoneHash : round.getHashes()){
                TransactionViewModel milestoneTx = TransactionViewModel.fromHash(tangle, milestoneHash);
                updateRoundIndexOfSingleTransaction(milestoneTx, newIndex);
                //System.out.println("milestone: " + milestoneHash.hexString() + ", Snapshot: " + milestoneTx.snapshotIndex());
            }
            // update confirmed transactions
            final Queue<Hash> transactionsToUpdate = new LinkedList<>(getConfirmedTips(newIndex));
            Hash transactionPointer;
            while ((transactionPointer = transactionsToUpdate.poll()) != null) {
                if (processedTransactions.add(transactionPointer)) {
                    final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle,
                            transactionPointer);
                    if (isTransactionConfirmed(transactionViewModel, correctIndex - 1)) {
                        patchSolidEntryPointsIfNecessary(snapshotProvider.getInitialSnapshot(), transactionViewModel);
                    } else {
                        prepareRoundIndexUpdate(transactionViewModel, correctIndex, newIndex,
                                inconsistentMilestones, transactionsToUpdate);
                        updateRoundIndexOfSingleTransaction(transactionViewModel, newIndex);
                        //System.out.println("tx: " + transactionViewModel.getHash().hexString() + ", Snapshot: " + transactionViewModel.snapshotIndex());
                        if (graph != null) {
                            graph.setConfirmed(transactionViewModel.getHash().toString(), 1);
                        }
                        if (!transactionsToUpdate.contains(transactionViewModel.getTrunkTransactionHash())) {
                            transactionsToUpdate.offer(transactionViewModel.getTrunkTransactionHash());
                        }
                        if (!transactionsToUpdate.contains(transactionViewModel.getBranchTransactionHash())) {
                            transactionsToUpdate.offer(transactionViewModel.getBranchTransactionHash());
                        }
                    }
                }
            }

        } catch (Exception e) {
            throw new MilestoneException("error while updating the milestone index", e);
        }

        for(int inconsistentMilestoneIndex : inconsistentMilestones) {
            resetCorruptedRound(inconsistentMilestoneIndex, processedTransactions);
        }

    }

    /**
     * This method resets the {@code milestoneIndex} of a single transaction.<br />
     * <br />
     * In addition to setting the corresponding value, we also publish a message to the ZeroMQ message provider, which
     * allows external recipients to get informed about this change.<br />
     *
     * @param transaction the transaction that shall have its {@code milestoneIndex} reset
     * @param index the milestone index that is set for the given transaction
     * @throws MilestoneException if anything unexpected happens while updating the transaction
     */
    private void updateRoundIndexOfSingleTransaction(TransactionViewModel transaction, int index) throws
            MilestoneException {

        try {
            transaction.setSnapshot(tangle, snapshotProvider.getInitialSnapshot(), index);
        } catch (Exception e) {
            throw new MilestoneException("error while updating the snapshotIndex of " + transaction, e);
        }

        JsonObject addressTopicJson = new JsonObject();
        addressTopicJson.addProperty("hash", transaction.getHash().toString());
        addressTopicJson.addProperty("signature", Hex.toHexString(transaction.getSignature()));
        addressTopicJson.addProperty("index", index);

        tangle.publish("%s %s", transaction.getAddressHash().toString(), addressTopicJson.toString());
        tangle.publish("sn %d %s %s %s %s %s", index, transaction.getHash().toString(), transaction.getAddressHash().toString(),
                transaction.getTrunkTransactionHash().toString(), transaction.getBranchTransactionHash().toString(),
                transaction.getBundleHash().toString());
    }

    /**
     * This method prepares the update of the milestone index by checking the current {@code snapshotIndex} of the given
     * transaction.<br />
     * <br />
     * If the {@code snapshotIndex} is higher than the "correct one", we know that we applied the milestones in the
     * wrong order and need to reset the corresponding milestone that wrongly approved this transaction. We therefore
     * add its index to the {@code corruptMilestones} set.<br />
     * <br />
     * If the milestone does not have the new value set already we add it to the set of {@code transactionsToUpdate} so
     * it can be updated by the caller accordingly.<br />
     *
     * @param transaction the transaction that shall get its milestoneIndex updated
     * @param correctMilestoneIndex the milestone index that this transaction should be associated to (the index of the
     *                              milestone that should "confirm" this transaction)
     * @param newMilestoneIndex the new milestone index that we want to assign (either 0 or the correctMilestoneIndex)
     * @param corruptMilestones a set that is used to collect all corrupt milestones indexes [output parameter]
     * @param transactionsToUpdate a set that is used to collect all transactions that need to be updated [output
     *                             parameter]
     */
    private void prepareRoundIndexUpdate(TransactionViewModel transaction, int correctMilestoneIndex,
                                             int newMilestoneIndex, Set<Integer> corruptMilestones, Queue<Hash> transactionsToUpdate) {

        if(transaction.snapshotIndex() > correctMilestoneIndex) {
            corruptMilestones.add(transaction.snapshotIndex());
        }
        if (transaction.snapshotIndex() != newMilestoneIndex) {
            transactionsToUpdate.offer(transaction.getHash());
        }
    }

    /**
     * This method patches the solid entry points if a back-referencing transaction is detected.<br />
     * <br />
     * While we iterate through the approvees of a milestone we stop as soon as we arrive at a transaction that has a
     * smaller {@code snapshotIndex} than the milestone. If this {@code snapshotIndex} is also smaller than the index of
     * the milestone of our local snapshot, we have detected a back-referencing transaction.<br />
     *
     * @param initialSnapshot the initial snapshot holding the solid entry points
     * @param transaction the transactions that was referenced by the processed milestone
     */
    private void patchSolidEntryPointsIfNecessary(Snapshot initialSnapshot, TransactionViewModel transaction) {
        if (transaction.snapshotIndex() <= initialSnapshot.getIndex() && !initialSnapshot.hasSolidEntryPoint(
                transaction.getHash())) {

            initialSnapshot.getSolidEntryPoints().put(transaction.getHash(), initialSnapshot.getIndex());
        }
    }

    /**
     * This method is a utility method that checks if the transactions belonging to the potential milestone bundle have
     * a valid structure (used during the validation of milestones).<br />
     * <br />
     * It first checks if the bundle has enough transactions to conform to the given {@code securityLevel} and then
     * verifies that the {@code branchTransactionsHash}es are pointing to the {@code trunkTransactionHash} of the head
     * transactions.<br />
     *
     * @param bundleTransactions all transactions belonging to the milestone
     * @param securityLevel the security level used for the signature
     * @return {@code true} if the basic structure is valid and {@code false} otherwise
     */
    private boolean isMilestoneBundleStructureValid(List<TransactionViewModel> bundleTransactions, int securityLevel) {
        if (bundleTransactions.size() <= securityLevel) {
            return false;
        }

        Hash headTransactionHash = bundleTransactions.get(securityLevel).getTrunkTransactionHash();
        return bundleTransactions.stream()
                .limit(securityLevel)
                .map(TransactionViewModel::getBranchTransactionHash)
                .allMatch(branchTransactionHash -> branchTransactionHash.equals(headTransactionHash));
    }

    /**
     * This method does the same as {@link #resetCorruptedRound(int)} but additionally receives a set of {@code
     * processedTransactions} that will allow us to not process the same transactions over and over again while
     * resetting additional milestones in recursive calls.<br />
     * <br />
     * It first checks if the desired {@code milestoneIndex} is reachable by this node and then triggers the reset
     * by:<br />
     * <br />
     * 1. resetting the ledger state if it addresses a milestone before the current latest solid milestone<br />
     * 2. resetting the {@code milestoneIndex} of all transactions that were confirmed by the current milestone<br />
     * 3. deleting the corresponding {@link StateDiff} entry from the database<br />
     *
     * @param index milestone index that shall be reverted
     * @param processedTransactions a set of transactions that have been processed already
     * @throws MilestoneException if anything goes wrong while resetting the corrupted milestone
     */
    private void resetCorruptedRound(int index, Set<Hash> processedTransactions) throws MilestoneException {
        if(index <= snapshotProvider.getInitialSnapshot().getIndex()) {
            return;
        }
        log.info("resetting corrupted milestone #" + index);
        try {
            RoundViewModel roundToRepair = RoundViewModel.get(tangle, index);
            if(roundToRepair != null) {
                if(roundToRepair.index() <= snapshotProvider.getLatestSnapshot().getIndex()) {
                    snapshotService.rollBackMilestones(snapshotProvider.getLatestSnapshot(), roundToRepair.index());
                }
                updateRoundIndexOfMilestoneTransactions(roundToRepair.index(), 0,
                            processedTransactions, new Graphstream());
                tangle.delete(StateDiff.class, new IntegerIndex(roundToRepair.index()));
            }
        } catch (Exception e) {
            throw new MilestoneException("failed to repair corrupted milestone with index #" + index, e);
        }
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////
}
