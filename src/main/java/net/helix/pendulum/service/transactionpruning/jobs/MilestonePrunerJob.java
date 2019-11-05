package net.helix.pendulum.service.transactionpruning.jobs;

import net.helix.pendulum.controllers.RoundViewModel;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.IntegerIndex;
import net.helix.pendulum.model.persistables.Round;
import net.helix.pendulum.model.persistables.Transaction;
import net.helix.pendulum.service.transactionpruning.TransactionPrunerJobStatus;
import net.helix.pendulum.service.transactionpruning.TransactionPruningException;
import net.helix.pendulum.storage.Indexable;
import net.helix.pendulum.storage.Persistable;
import net.helix.pendulum.utils.Pair;
import net.helix.pendulum.utils.dag.DAGHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a cleanup job for {@link net.helix.pendulum.service.transactionpruning.TransactionPruner}s that removes
 * milestones and all of their directly and indirectly referenced transactions (and the orphaned subtangles branching
 * off of the deleted transactions).
 *
 * It is used by the {@link net.helix.pendulum.service.snapshot.LocalSnapshotManager} to clean up milestones prior to a
 * snapshot. Even though it defines a range of milestones that shall be deleted, it gets processed one milestone at a
 * time, persisting the progress after each step.
 */
public class MilestonePrunerJob extends AbstractTransactionPrunerJob {

    /**
     * Holds the milestone index where this job starts cleaning up.
     */
    private int startingIndex;

    /**
     * Holds the milestone index where this job stops cleaning up.
     */
    private int targetIndex;

    /**
     * Holds the milestone index of the oldest milestone that was cleaned up already (the current progress).
     */
    private int currentIndex;

    /**
     * This method parses the string representation of a {@link MilestonePrunerJob} and creates the corresponding
     * object.
     *
     * It splits the String input in three parts (delimited by a ";") and passes them into the constructor of a new
     * {@link MilestonePrunerJob} by interpreting them as {@link #startingIndex}, {@link #currentIndex} and
     * {@link #targetIndex} of the job.
     *
     * @param input serialized String representation of a {@link MilestonePrunerJob}
     * @return a new {@link MilestonePrunerJob} with the provided details
     * @throws TransactionPruningException if the string can not be parsed
     */
    public static MilestonePrunerJob parse(String input) throws TransactionPruningException {
        String[] parts = input.split(";");
        if(parts.length == 3) {
            return new MilestonePrunerJob(Integer.valueOf(parts[0]), Integer.valueOf(parts[1]),
                    Integer.valueOf(parts[2]));
        }

        throw new TransactionPruningException("failed to parse TransactionPruner file - invalid input: " + input);
    }

    /**
     * Does same as {@link #MilestonePrunerJob(int, int, int)} but defaults to the {@link #currentIndex} being the same
     * as the {@link #startingIndex}. This is usually the case when we create a NEW job programmatically that does not
     * get restored from a state file, because we start cleaning up at the {@link #startingIndex}.
     *
     * @param startingIndex milestone index that defines where to start cleaning up
     * @param targetIndex milestone index which defines the end of this pruning job
     */
    public MilestonePrunerJob(int startingIndex, int targetIndex) {
        this(startingIndex, startingIndex, targetIndex);
    }

    /**
     * Creates a job that cleans all milestones prior (and including) the {@code startingIndex} and that is set to have
     * progressed already to the given {@code currentIndex}.
     *
     * Since cleanup jobs can be consolidated (to reduce the size of the
     * {@link net.helix.pendulum.service.transactionpruning.TransactionPruner} state file) and restored (after the node restarts),
     * we need to be able provide both parameters even tho the job usually always "starts" with its {@code currentIndex}
     * being equal to the {@code startingIndex}.
     *
     * If the {@link #currentIndex} is bigger than the {@link #targetIndex} we set the state to DONE (necessary when
     * restoring the job from the state file).
     *
     * @param startingIndex milestone index that defines where to start cleaning up
     * @param currentIndex milestone index that defines the next milestone that should be cleaned up by this job
     * @param targetIndex milestone index which defines the end of this pruning job
     */
    private MilestonePrunerJob(int startingIndex, int currentIndex, int targetIndex) {
        setStartingIndex(startingIndex);
        setCurrentIndex(currentIndex);
        setTargetIndex(targetIndex);

        if (currentIndex > targetIndex) {
            setStatus(TransactionPrunerJobStatus.DONE);
        }
    }

    /**
     * {@inheritDoc}
     *
     * It iterates from the {@link #currentIndex} to the provided {@link #targetIndex} and processes every milestone
     * one by one. After each step is finished we persist the progress to be able to continue with the current progress
     * upon the node restarts.
     */
    @Override
    public void process() throws TransactionPruningException {
        if (getStatus() != TransactionPrunerJobStatus.DONE) {
            setStatus(TransactionPrunerJobStatus.RUNNING);

            try {
                while (!Thread.currentThread().isInterrupted() && getStatus() != TransactionPrunerJobStatus.DONE) {
                    cleanupMilestoneTransactions();

                    setCurrentIndex(getCurrentIndex() + 1);

                    // synchronize this call because the MilestonePrunerJobQueue needs it to check if we can be extended
                    synchronized (this) {
                        if (getCurrentIndex() > getTargetIndex()) {
                            setStatus(TransactionPrunerJobStatus.DONE);
                        }
                    }

                    getTransactionPruner().saveState();
                }
            } catch (TransactionPruningException e) {
                setStatus(TransactionPrunerJobStatus.FAILED);

                throw e;
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * It simply concatenates the {@link #startingIndex} and the {@link #currentIndex} as they are necessary to fully
     * describe the job.
     */
    @Override
    public String serialize() {
        return getStartingIndex() + ";" + getCurrentIndex() + ";" + getTargetIndex();
    }

    /**
     * Getter of the {@link #startingIndex}.
     *
     * @return milestone index where this job starts cleaning up
     */
    public int getStartingIndex() {
        return startingIndex;
    }

    /**
     * Setter of the {@link #startingIndex}.
     *
     * @param startingIndex milestone index where this job starts cleaning up
     */
    public void setStartingIndex(int startingIndex) {
        this.startingIndex = startingIndex;
    }

    /**
     * Getter of the {@link #currentIndex}.
     *
     * @return milestone index of the oldest milestone that was cleaned up already (the current progress)
     */
    public int getCurrentIndex() {
        return currentIndex;
    }

    /**
     * Setter of the {@link #currentIndex}.
     *
     * @param currentIndex milestone index of the oldest milestone that was cleaned up already (the current progress)
     */
    public void setCurrentIndex(int currentIndex) {
        this.currentIndex = currentIndex;
    }

    /**
     * Getter of the {@link #targetIndex}.
     *
     * @return milestone index where this job stops cleaning up
     */
    public int getTargetIndex() {
        return targetIndex;
    }

    /**
     * Setter of the {@link #targetIndex}.
     *
     * @param targetIndex milestone index where this job stops cleaning up
     */
    public void setTargetIndex(int targetIndex) {
        this.targetIndex = targetIndex;
    }

    /**
     * This method takes care of cleaning up a single milestone and all of its transactions and performs the actual
     * database operations.
     *
     * It performs the deletions in an atomic way, which means that either the full processing succeeds or fails.
     *
     * We first retrieve the elements that shall be deleted and then analyze them before removing them from the
     * database. While processing them, we issue additional {@link UnconfirmedSubtanglePrunerJob}s that remove the
     * orphaned parts of the tangle that branch off the deleted transactions because they would otherwise loose their
     * connection to the rest of the tangle unless they are branching off a solid entry point (in which case we wait
     * with the deletion until the solid entry point expires).
     *
     * @throws TransactionPruningException if something goes wrong while cleaning up the milestone
     */
    private void cleanupMilestoneTransactions() throws TransactionPruningException {
        try {
            List<Pair<Indexable, ? extends Class<? extends Persistable>>> elementsToDelete = getElementsToDelete();

            for (Pair<Indexable, ? extends Class<? extends Persistable>> element : elementsToDelete) {
                if (Transaction.class.equals(element.hi)) {
                    getTipsViewModel().removeTipHash((Hash) element.low);

                    if (!getSnapshot().hasSolidEntryPoint((Hash) element.low)) {
                        getTransactionPruner().addJob(new UnconfirmedSubtanglePrunerJob((Hash) element.low));
                    }
                } else if(Round.class.equals(element.hi)) {
                    RoundViewModel.clear(((IntegerIndex) element.low).getValue());
                }
            }

            getTangle().deleteBatch(elementsToDelete);
        } catch(Exception e) {
            throw new TransactionPruningException("failed to cleanup milestone #" + getCurrentIndex(), e);
        }
    }

    /**
     * Collects all database items that belong to the current milestone and that shall be deleted.
     *
     * It does that by iterating through all the transactions that belong to the current milestone (that are directly or
     * indirectly referenced by the chosen milestone) and collecting them in a List of items to delete.
     *
     * @return list of elements that shall be deleted from the database
     * @throws TransactionPruningException if anything goes wrong while collecting the elements
     */
    private List<Pair<Indexable, ? extends Class<? extends Persistable>>> getElementsToDelete() throws
            TransactionPruningException {

        try {
            List<Pair<Indexable, ? extends Class<? extends Persistable>>> elementsToDelete = new ArrayList<>();

            RoundViewModel roundViewModel = RoundViewModel.get(getTangle(), getCurrentIndex());
            if (roundViewModel != null) {
                elementsToDelete.add(new Pair<>(new IntegerIndex(roundViewModel.index()), Round.class));

                for (Hash milestoneHash : roundViewModel.getHashes()) {
                    elementsToDelete.add(new Pair<>(milestoneHash, Transaction.class));

                    DAGHelper.get(getTangle()).traverseApprovees(milestoneHash,
                            approvedTransaction -> approvedTransaction.snapshotIndex() >= roundViewModel.index(),
                            approvedTransaction -> {
                                /*if (approvedTransaction.value() < 0 &&
                                        !spentAddressesService.wasAddressSpentFrom(approvedTransaction.getAddressHash())) {
                                    log.warn("Pruned spend transaction " + approvedTransaction.getHash() +
                                            " did not have its spent address recorded. Persisting it now");
                                    spentAddressesService
                                            .persistSpentAddresses(Collections.singletonList(approvedTransaction));
                                }*/ //todo patchfixes
                                elementsToDelete.add(new Pair<>(approvedTransaction.getHash(), Transaction.class));
                            });
                }
            }

            return elementsToDelete;
        } catch (Exception e) {
            throw new TransactionPruningException("failed to determine which elements to delete", e);
        }
    }
}