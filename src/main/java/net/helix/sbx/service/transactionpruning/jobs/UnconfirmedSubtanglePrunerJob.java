package net.helix.sbx.service.transactionpruning.jobs;

import net.helix.sbx.controllers.TransactionViewModel;
import net.helix.sbx.model.Hash;
import net.helix.sbx.model.HashFactory;
import net.helix.sbx.model.persistables.Transaction;
import net.helix.sbx.service.spentaddresses.SpentAddressesService;
import net.helix.sbx.service.transactionpruning.TransactionPrunerJobStatus;
import net.helix.sbx.service.transactionpruning.TransactionPruningException;
import net.helix.sbx.storage.Indexable;
import net.helix.sbx.storage.Persistable;
import net.helix.sbx.utils.Pair;
import net.helix.sbx.utils.dag.DAGHelper;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a job for the {@link net.helix.sbx.service.transactionpruning.TransactionPruner} that cleans up all
 * unconfirmed transactions approving a certain transaction.
 *
 * It is used to clean up orphaned subtangles when they become irrelevant for the ledger.
 */
public class UnconfirmedSubtanglePrunerJob extends AbstractTransactionPrunerJob {

    /**
     * Holds the hash of the transaction that shall have its unconfirmed approvers cleaned.
     */
    private Hash transactionHash;

    /**
     * This method parses the serialized representation of a {@link UnconfirmedSubtanglePrunerJob} and creates the
     * corresponding object.
     *
     * It simply creates a hash from the input and passes it on to the {@link #UnconfirmedSubtanglePrunerJob(Hash)}.
     *
     * @param input serialized String representation of a {@link MilestonePrunerJob}
     * @return a new {@link UnconfirmedSubtanglePrunerJob} with the provided hash
     */
    public static UnconfirmedSubtanglePrunerJob parse(String input) {
        return new UnconfirmedSubtanglePrunerJob(HashFactory.TRANSACTION.create(input));
    }

    /**
     * Creates a job that cleans up all transactions that are approving the transaction with the given hash, which have
     * not been confirmed, yet.
     *
     * @param transactionHash hash of the transaction that shall have its unconfirmed approvers pruned
     */
    public UnconfirmedSubtanglePrunerJob(Hash transactionHash) {
        this.transactionHash = transactionHash;
    }

    /**
     * {@inheritDoc}
     *
     * It iterates through all approvers and collects their hashes if they have not been approved. Then its deletes them
     * from the database (atomic) and also removes them from the runtime caches. After the job is done
     */
    @Override
    public void process() throws TransactionPruningException {
        if (getStatus() != TransactionPrunerJobStatus.DONE) {
            setStatus(TransactionPrunerJobStatus.RUNNING);

            Collection<TransactionViewModel> unconfirmedTxs = new HashSet<>();
            try {
                DAGHelper.get(getTangle()).traverseApprovers(
                        transactionHash,
                        approverTransaction -> approverTransaction.snapshotIndex() == 0,
                        unconfirmedTxs::add
                );

                //Only persist to db
                spentAddressesService.persistSpentAddresses(unconfirmedTxs);
                List<Pair<Indexable, ? extends Class<? extends Persistable>>> elementsToDelete = unconfirmedTxs.stream()
                        .map(tx -> new Pair<>((Indexable) tx.getHash(), Transaction.class))
                        .collect(Collectors.toList());

                // clean database entries
                getTangle().deleteBatch(elementsToDelete);

                // clean runtime caches
                elementsToDelete.forEach(element -> getTipsViewModel().removeTipHash((Hash) element.low));

                setStatus(TransactionPrunerJobStatus.DONE);
            } catch (Exception e) {
                setStatus(TransactionPrunerJobStatus.FAILED);
                throw new TransactionPruningException(
                        "failed to cleanup orphaned approvers of transaction " + transactionHash, e
                );
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * It simply dumps the string representation of the {@link #transactionHash} .
     */
    @Override
    public String serialize() {
        return transactionHash.toString();
    }
}