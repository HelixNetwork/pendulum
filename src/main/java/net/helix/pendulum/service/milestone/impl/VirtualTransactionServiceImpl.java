package net.helix.pendulum.service.milestone.impl;

import net.helix.pendulum.TransactionValidator;
import net.helix.pendulum.controllers.BundleNonceViewModel;
import net.helix.pendulum.controllers.RoundViewModel;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.crypto.SpongeFactory;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.service.milestone.VirtualTransactionService;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.storage.Tangle;
import net.helix.pendulum.utils.bundle.BundleUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static net.helix.pendulum.controllers.TransactionViewModel.PREFILLED_SLOT;
import static net.helix.pendulum.controllers.TransactionViewModel.fromHash;

/**
 * Creates a service instance that allows us to perform virtual transaction operations.<br />
 * <br />
 */
public class VirtualTransactionServiceImpl implements VirtualTransactionService {

    private static final Logger log = LoggerFactory.getLogger(VirtualTransactionServiceImpl.class);

    /**
     * Holds the tangle object which acts as a database interface.<br />
     */
    private Tangle tangle;

    /**
     * Holds the snapshot provider which gives us access to the relevant snapshots.<br />
     */
    private SnapshotProvider snapshotProvider;

    /**
     * Holds transaction validator. <br />
     */
    private TransactionValidator transactionValidator;


    /**
     * This method initializes the instance and registers its dependencies.<br />
     * <br />
     * It simply stores the passed in values in their corresponding private properties.<br />
     * <br />
     * Note: Instead of handing over the dependencies in the constructor, we register them lazy. This allows us to have
     * circular dependencies because the instantiation is separated from the dependency injection. To reduce the
     * amount of code that is necessary to correctly instantiate this class, we return the instance itself which
     * allows us to still instantiate, initialize and assign in one line - see Example:<br />
     * <br />
     * {@code milestoneService = new VirtualTransactionServiceImpl().init(...);}
     *
     * @param tangle           Tangle object which acts as a database interface
     * @param snapshotProvider snapshot provider which gives us access to the relevant snapshots
     * @return the initialized instance itself to allow chaining
     */
    public VirtualTransactionServiceImpl init(Tangle tangle, SnapshotProvider snapshotProvider, TransactionValidator transactionValidator) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.transactionValidator = transactionValidator;
        return this;
    }

    /**
     * Rebuilds a new virtual transaction which is based on current virtual transaction and its sibling (it this transaction exists)
     * Attached the new built transaction in local tangle
     *
     * @param transactionViewModel transaction view model of the child
     * @return true if the new transaction is created, otherwise false.
     */
    @Override
    public int rebuildVirtualTransactionsIfPossible(TransactionViewModel transactionViewModel) {
        if (!transactionViewModel.isVirtual()) {
            return 0;
        }
        int noOfRebuitTransactions = 0;
        TransactionViewModel nextTransaction = transactionViewModel;
        while (nextTransaction != null) {
            nextTransaction = rebuildViirtualParentTransactionIfPossible(nextTransaction);
            if (nextTransaction != null) {
                noOfRebuitTransactions++;
            }
        }
        return noOfRebuitTransactions;
    }

    private TransactionViewModel rebuildViirtualParentTransactionIfPossible(TransactionViewModel tvm) {
        long currentIndex = tvm.getTagLongValue();
        long siblingIndex = getSiblingIndex(currentIndex);
        long parentIndex = getParentIndex(currentIndex);

        Hash milestoneHash = tvm.getBundleNonceHash();
        Map<Long, Hash> transactions = findByTagAndMilestone(milestoneHash, Arrays.asList(siblingIndex, parentIndex));

        if (!transactions.containsKey(parentIndex) && transactions.containsKey(siblingIndex)) {
            try {
                TransactionViewModel siblingTransaction = fromHash(tangle, transactions.get(siblingIndex));
                TransactionViewModel newTransaction = reconstructParent(tvm, siblingTransaction);
                log.debug("Rebuild transaction virtual parent transaction {} for: {} ", newTransaction.getHash(), tvm.getHash());
                return newTransaction;
            } catch (Exception e) {
                log.error("Error during generation of virtual transaction parent!");
            }
        }
        return null;
    }

    /**
     * Reconstruct virtual parent based on 2 transactions which are consider the direct children of the new created transaction.
     * Order of the t1 and t2 doesn't matter, they will be sorted according with their merkle index.
     *
     * @param t1 first child transaction
     * @param t2 second child transactiopn
     * @return
     * @throws Exception
     */
    private TransactionViewModel reconstructParent(TransactionViewModel t1, TransactionViewModel t2) throws Exception {
        if (validVirtualTransaction(Arrays.asList(t1, t2))) {
            log.warn("Transactions: {} {} are not valid virtual transaction!", t1.getHash(), t2.getHash());
            return null;
        }
        Hash trunk = RoundViewModel.getRoundIndex(t1) > RoundViewModel.getRoundIndex(t2) ? t2.getHash() : t1.getHash();
        Hash branch = RoundViewModel.getRoundIndex(t1) < RoundViewModel.getRoundIndex(t2) ? t2.getHash() : t1.getHash();

        byte[] newTransaction = BundleUtils.createVirtualTransaction(branch, trunk, getParentIndex(RoundViewModel.getRoundIndex(t1)),
                t1.getSignature(), t1.getAddressHash());

        TransactionViewModel newTransactionViewModel = new TransactionViewModel(newTransaction, SpongeFactory.Mode.S256);
        newTransactionViewModel.storeTransactionLocal(tangle, snapshotProvider.getInitialSnapshot(), transactionValidator);
        return newTransactionViewModel;
    }

    /**
     * Get parent index of the current virtual transaction
     *
     * @param index merkle index of the current transaction
     * @return merkle index of the parent transaction
     */
    private long getParentIndex(long index) {
        return index % 2 == 0 ? index / 2 - 1 : (index - 1) / 2L;
    }

    /**
     * Get sibling index, based on current transaction index.
     *
     * @param index of the current transaction
     * @return the index of the other child (from merkle tree)
     */
    private long getSiblingIndex(long index) {
        return index % 2 == 0 ? index - 1 : index + 1;
    }


    /**
     * Virtual transactions validator. Call areVirtualTransaction and areSiblingVirtualTransactions validators.
     *
     * @param transactions, list of transactions that will be validated
     * @return true if all transaction from povided list are valid virtual transactions.
     */
    private boolean validVirtualTransaction(List<TransactionViewModel> transactions) {
        return areVirtualTransaction(transactions) &&
                areSiblingVirtualTransactions(transactions);
    }

    /**
     * Virtual transactions validator. Checks if all transactions are virtual.
     *
     * @param transactions, list of transactions that will be validated
     * @return true if all transaction from povided list are valid virtual transactions.
     */
    private boolean areVirtualTransaction(List<TransactionViewModel> transactions) {
        if (CollectionUtils.isEmpty(transactions)) {
            return false;
        }
        return transactions.stream().filter(t -> !t.isVirtual()).collect(Collectors.toList()).size() == 0;
    }

    /**
     * Virtual transactions validator. Checks if all transactions are siblings
     *
     * @param transactions, list of transactions that will be validated
     * @return true if all transaction from povided list are valid virtual transactions.
     */
    private boolean areSiblingVirtualTransactions(List<TransactionViewModel> transactions) {
        if (CollectionUtils.isEmpty(transactions)) {
            return false;
        }
        Hash firstTag = transactions.get(0).getTagValue();
        if (firstTag == null) {
            return false;
        }
        return transactions.stream().filter(t -> firstTag.equals(t.getTagValue())).collect(Collectors.toList()).size() == 0;
    }


    /**
     * Find all transaction which belongs to milestone and have tags from parameter.
     *
     * @param milestoneHash milestone hash
     * @param tags          List of longs which represents the index in merkle tree
     * @return map of tag (long value) and transaction hashes
     */
    public Map<Long, Hash> findByTagAndMilestone(Hash milestoneHash, List<Long> tags) {
        Map<Long, Hash> tagTransactionHash = new HashMap<Long, Hash>();
        try {
            Set<Hash> transactionHashes = BundleNonceViewModel.load(tangle, milestoneHash)
                    .getHashes();
            for (Hash t : transactionHashes) {
                TransactionViewModel tvm = fromHash(tangle, t);
                if (tvm.getType() == PREFILLED_SLOT) {
                    continue;
                }
                tags.stream().filter(tag -> !tagTransactionHash.keySet().contains(tag)).forEach(tag -> {
                    if (tag == tvm.getTagLongValue()) {
                        tagTransactionHash.put(tag, t);
                    }
                });
                if (tagTransactionHash.size() == tags.size()) {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return tagTransactionHash;
    }
}
