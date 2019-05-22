package net.helix.hlx.service.milestone.impl;

import net.helix.hlx.BundleValidator;
import net.helix.hlx.conf.ConsensusConfig;
import net.helix.hlx.controllers.RoundViewModel;
import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.crypto.Sha3;
import net.helix.hlx.crypto.Winternitz;
import net.helix.hlx.crypto.Merkle;
import net.helix.hlx.crypto.SpongeFactory;
import net.helix.hlx.model.Hash;
import net.helix.hlx.model.HashFactory;
import net.helix.hlx.model.StateDiff;
import net.helix.hlx.service.milestone.MilestoneException;
import net.helix.hlx.service.milestone.MilestoneService;
import net.helix.hlx.service.milestone.MilestoneValidity;
import net.helix.hlx.service.snapshot.Snapshot;
import net.helix.hlx.service.snapshot.SnapshotProvider;
import net.helix.hlx.service.snapshot.SnapshotService;
import net.helix.hlx.storage.Tangle;
import net.helix.hlx.utils.Serializer;
import net.helix.hlx.utils.dag.DAGHelper;

import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

import static net.helix.hlx.service.milestone.MilestoneValidity.INCOMPLETE;
import static net.helix.hlx.service.milestone.MilestoneValidity.INVALID;
import static net.helix.hlx.service.milestone.MilestoneValidity.VALID;

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
    public MilestoneServiceImpl init(Tangle tangle, SnapshotProvider snapshotProvider, SnapshotService snapshotService, ConsensusConfig config) {

        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.snapshotService = snapshotService;
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
    public Optional<RoundViewModel> findLatestProcessedSolidMilestoneInDatabase() throws MilestoneException {
        try {
            // if we have no milestone in our database -> abort
            RoundViewModel latestMilestone = RoundViewModel.latest(tangle);
            if (latestMilestone == null) {
                return Optional.empty();
            }

            // trivial case #1: the node was fully synced
            if (wasMilestoneAppliedToLedger(latestMilestone)) {
                return Optional.of(latestMilestone);
            }

            // trivial case #2: the node was fully synced but the last milestone was not processed, yet
            RoundViewModel latestMilestonePredecessor = RoundViewModel.findClosestPrevRound(tangle,
                    latestMilestone.index(), snapshotProvider.getInitialSnapshot().getIndex());
            if (latestMilestonePredecessor != null && wasMilestoneAppliedToLedger(latestMilestonePredecessor)) {
                return Optional.of(latestMilestonePredecessor);
            }

            // non-trivial case: do a binary search in the database
            return binarySearchLatestProcessedSolidMilestoneInDatabase(latestMilestone);
        } catch (Exception e) {
            throw new MilestoneException(
                    "unexpected error while trying to find the latest processed solid milestone in the database", e);
        }
    }

    @Override
    public void updateMilestoneIndexOfMilestoneTransactions(Hash milestoneHash, int index) throws MilestoneException {
        if (index <= 0) {
            throw new MilestoneException("the new index needs to be bigger than 0 " +
                    "(use resetCorruptedMilestone to reset the milestone index)");
        }

        updateMilestoneIndexOfMilestoneTransactions(milestoneHash, index, index, new HashSet<>());
    }

    /**
     * {@inheritDoc}
     * <br />
     * We redirect the call to {@link #resetCorruptedMilestone(int, Set)} while initiating the set of {@code
     * processedTransactions} with an empty {@link HashSet} which will ensure that we reset all found
     * transactions.<br />
     */
    @Override
    public void resetCorruptedMilestone(int index) throws MilestoneException {
        resetCorruptedMilestone(index, new HashSet<>());
    }

    @Override
    public MilestoneValidity validateMilestone(TransactionViewModel transactionViewModel, int roundIndex,
                                               SpongeFactory.Mode mode, int securityLevel) throws MilestoneException {

        if (roundIndex < 0 || roundIndex >= 0x200000) {
            return INVALID;
        }

        try {
            if (RoundViewModel.get(tangle, roundIndex) != null) {
                // Already validated.
                return VALID;
            }

            final List<List<TransactionViewModel>> bundleTransactions = BundleValidator.validate(tangle,
                    snapshotProvider.getInitialSnapshot(), transactionViewModel.getHash());

            if (bundleTransactions.isEmpty()) {
                return INCOMPLETE;
            } else {
                for (final List<TransactionViewModel> bundleTransactionViewModels : bundleTransactions) {
                    final TransactionViewModel tail = bundleTransactionViewModels.get(0);
                    if (tail.getHash().equals(transactionViewModel.getHash())) {
                        //the signed transaction - which references the confirmed transactions and contains
                        // the Merkle tree siblings.
                        final TransactionViewModel siblingsTx = bundleTransactionViewModels.get(securityLevel);

                        if (isMilestoneBundleStructureValid(bundleTransactionViewModels, securityLevel)) {

                            //milestones sign the normalized hash of the sibling transaction. (why not bundle hash?)
                            byte[] bundleHash = new byte[Sha3.HASH_LENGTH];
                            Winternitz.normalizedBundle(siblingsTx.getBundleHash().bytes(), bundleHash);

                            //validate leaf signature
                            ByteBuffer bb = ByteBuffer.allocate(Sha3.HASH_LENGTH * securityLevel);

                            for (int i = 0; i < securityLevel; i++) {
                                byte[] bundleHashFragment = Arrays.copyOfRange(bundleHash, Winternitz.NORMALIZED_FRAGMENT_LENGTH * i, Winternitz.NORMALIZED_FRAGMENT_LENGTH * (i+1));
                                byte[] digest = Winternitz.digest(mode, bundleHashFragment, bundleTransactionViewModels.get(i).getSignature());
                                bb.put(digest);
                            }

                            byte[] digests = bb.array();
                            byte[] address = Winternitz.address(mode, digests);

                            //validate Merkle path
                            byte[] merkleRoot = Merkle.getMerkleRoot(mode, address,
                                    siblingsTx.getSignature(), 0, roundIndex, config.getNumberOfKeysInMilestone());

                            if ((config.isTestnet() && config.isDontValidateTestnetMilestoneSig()) ||
                                    (config.getValidatorAddresses().contains(HashFactory.ADDRESS.create(merkleRoot)))) {

                                RoundViewModel currentRoundViewModel = RoundViewModel.get(tangle, roundIndex);
                                currentRoundViewModel.addMilestone(transactionViewModel.getHash());
                                currentRoundViewModel.update(tangle);

                                // if we find a NEW milestone that should have been processed before our latest solid
                                // milestone -> reset the ledger state and check the milestones again
                                //
                                // NOTE: this can happen if a new subtangle becomes solid before a previous one while
                                //       syncing
                                //TODO: find a solution for this
                                /*if (roundIndex < snapshotProvider.getLatestSnapshot().getIndex() &&
                                        roundIndex > snapshotProvider.getInitialSnapshot().getIndex()) {

                                    resetCorruptedMilestone(roundIndex);
                                }*/

                                return VALID;
                            } else {
                                return INVALID;
                            }
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
    public int getRoundIndex(TransactionViewModel milestoneTransaction) {
        return (int) Serializer.getLong(milestoneTransaction.getBytes(), TransactionViewModel.TAG_OFFSET);
        //TODO: why is index stored in bundle nonce (obsolete tag) in new version?
        //return (int) Serializer.getLong(milestoneTransaction.getBytes(), BUNDLE_NONCE_OFFSET);
    }

    @Override
    public Set<Hash> getConfirmedTips(int roundNumber, int quorum) throws Exception {

        RoundViewModel round = RoundViewModel.get(tangle, roundNumber);
        Map<Hash, Integer> occurences = new HashMap<>();

        for (Hash milestoneHash : round.getHashes()) {
            final List<List<TransactionViewModel>> bundleTransactions = BundleValidator.validate(tangle,
                    snapshotProvider.getInitialSnapshot(), milestoneHash);

            for (final List<TransactionViewModel> bundleTransactionViewModels : bundleTransactions) {
                final TransactionViewModel tipsTx = bundleTransactionViewModels.get(bundleTransactionViewModels.size() - 1);

                for (int i = 0; i < 16; i++) {
                    Hash tip = HashFactory.TRANSACTION.create(tipsTx.getSignature(), i * Hash.SIZE_IN_BYTES, (i + 1) * Hash.SIZE_IN_BYTES);
                    if (occurences.containsKey(tip)) {
                        occurences.put(tip, occurences.get(tip) + 1);
                    } else {
                        occurences.put(tip, 1);
                    }
                }
            }
        }
        return occurences.entrySet().stream()
                .filter(entry -> entry.getValue() >= quorum)
                .map(entry -> entry.getKey())
                .collect(Collectors.toSet());
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
    public boolean isTransactionConfirmed(TransactionViewModel transaction, int milestoneIndex) {
        return transaction.snapshotIndex() != 0 && transaction.snapshotIndex() <= milestoneIndex;
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
    private Optional<RoundViewModel> binarySearchLatestProcessedSolidMilestoneInDatabase(
            RoundViewModel latestMilestone) throws Exception {

        Optional<RoundViewModel> lastAppliedCandidate = Optional.empty();

        int rangeEnd = latestMilestone.index();
        int rangeStart = snapshotProvider.getInitialSnapshot().getIndex() + 1;
        while (rangeEnd - rangeStart >= 0) {
            // if no candidate found in range -> return last candidate
            RoundViewModel currentCandidate = getMilestoneInMiddleOfRange(rangeStart, rangeEnd);
            if (currentCandidate == null) {
                return lastAppliedCandidate;
            }

            // if the milestone was applied -> continue to search for "later" ones that might have also been applied
            if (wasMilestoneAppliedToLedger(currentCandidate)) {
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
     * It is used by the binary search algorithm of {@link #findLatestProcessedSolidMilestoneInDatabase()}. It first
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
    private RoundViewModel getMilestoneInMiddleOfRange(int rangeStart, int rangeEnd) throws Exception {
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
     * @param milestone the milestone that shall be checked
     * @return {@code true} if the milestone has been processed by IRI before and {@code false} otherwise
     * @throws Exception if anything unexpected happens while checking the milestone
     */
    private boolean wasMilestoneAppliedToLedger(RoundViewModel milestone) throws Exception {
        //TODO: find a solution for this
        /*TransactionViewModel milestoneTransaction = TransactionViewModel.fromHash(tangle, milestone.getHash());
        return milestoneTransaction.getType() != TransactionViewModel.PREFILLED_SLOT &&
                milestoneTransaction.snapshotIndex() != 0;*/
        return true;
    }

    /**
     * This method implements the logic described by {@link #updateMilestoneIndexOfMilestoneTransactions(Hash, int)} but
     * accepts some additional parameters that allow it to be reused by different parts of this service.<br />
     *
     * @param milestoneHash the hash of the transaction
     * @param correctIndex the milestone index of the milestone that would be set if all transactions are marked
     *                     correctly
     * @param newIndex the milestone index that shall be set
     * @throws MilestoneException if anything unexpected happens while updating the milestone index
     * @param processedTransactions a set of transactions that have been processed already (for the recursive calls)
     */
    private void updateMilestoneIndexOfMilestoneTransactions(Hash milestoneHash, int correctIndex, int newIndex,
                                                             Set<Hash> processedTransactions) throws MilestoneException {

        processedTransactions.add(milestoneHash);

        Set<Integer> inconsistentMilestones = new HashSet<>();
        Set<TransactionViewModel> transactionsToUpdate = new HashSet<>();

        try {
            prepareMilestoneIndexUpdate(TransactionViewModel.fromHash(tangle, milestoneHash), correctIndex, newIndex,
                    inconsistentMilestones, transactionsToUpdate);

            DAGHelper.get(tangle).traverseApprovees(
                    milestoneHash,
                    currentTransaction -> {
                        // if the transaction was confirmed by a PREVIOUS milestone -> check if we have a back-referencing
                        // transaction and abort the traversal
                        if (isTransactionConfirmed(currentTransaction, correctIndex - 1)) {
                            patchSolidEntryPointsIfNecessary(snapshotProvider.getInitialSnapshot(), currentTransaction);

                            return false;
                        }

                        return true;
                    },
                    currentTransaction -> prepareMilestoneIndexUpdate(currentTransaction, correctIndex, newIndex,
                            inconsistentMilestones, transactionsToUpdate),
                    processedTransactions
            );
        } catch (Exception e) {
            throw new MilestoneException("error while updating the milestone index", e);
        }

        for(int inconsistentMilestoneIndex : inconsistentMilestones) {
            resetCorruptedMilestone(inconsistentMilestoneIndex, processedTransactions);
        }

        for (TransactionViewModel transactionToUpdate : transactionsToUpdate) {
            updateMilestoneIndexOfSingleTransaction(transactionToUpdate, newIndex);
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
    private void updateMilestoneIndexOfSingleTransaction(TransactionViewModel transaction, int index) throws
            MilestoneException {

        try {
            transaction.setSnapshot(tangle, snapshotProvider.getInitialSnapshot(), index);
        } catch (Exception e) {
            throw new MilestoneException("error while updating the snapshotIndex of " + transaction, e);
        }

        JsonObject addressTopicJson = new JsonObject();
        addressTopicJson.addProperty("hash", transaction.getHash().hexString());
        addressTopicJson.addProperty("signature", Hex.toHexString(transaction.getSignature()));
        addressTopicJson.addProperty("index", index);

        tangle.publish("%s %s", transaction.getAddressHash().hexString(), addressTopicJson.toString());
        tangle.publish("sn %d %s %s %s %s %s", index, transaction.getHash().hexString(), transaction.getAddressHash().hexString(),
                transaction.getTrunkTransactionHash().hexString(), transaction.getBranchTransactionHash().hexString(),
                transaction.getBundleHash().hexString());
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
    private void prepareMilestoneIndexUpdate(TransactionViewModel transaction, int correctMilestoneIndex,
                                             int newMilestoneIndex, Set<Integer> corruptMilestones, Set<TransactionViewModel> transactionsToUpdate) {

        if(transaction.snapshotIndex() > correctMilestoneIndex) {
            corruptMilestones.add(transaction.snapshotIndex());
        }
        if (transaction.snapshotIndex() != newMilestoneIndex) {
            transactionsToUpdate.add(transaction);
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
     * This method does the same as {@link #resetCorruptedMilestone(int)} but additionally receives a set of {@code
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
    private void resetCorruptedMilestone(int index, Set<Hash> processedTransactions) throws MilestoneException {
        if(index <= snapshotProvider.getInitialSnapshot().getIndex()) {
            return;
        }
        log.info("resetting corrupted milestone #" + index);
        try {
            RoundViewModel milestoneToRepair = RoundViewModel.get(tangle, index);
            if(milestoneToRepair != null) {
                if(milestoneToRepair.index() <= snapshotProvider.getLatestSnapshot().getIndex()) {
                    snapshotService.rollBackMilestones(snapshotProvider.getLatestSnapshot(), milestoneToRepair.index());
                }
                //TODO: find a solution for this
                /*
                updateMilestoneIndexOfMilestoneTransactions(milestoneToRepair.getHash(), milestoneToRepair.index(), 0,
                        processedTransactions);
                tangle.delete(StateDiff.class, milestoneToRepair.getHash());*/
            }
        } catch (Exception e) {
            throw new MilestoneException("failed to repair corrupted milestone with index #" + index, e);
        }
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////
}
