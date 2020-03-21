package net.helix.pendulum;

import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.crypto.Sponge;
import net.helix.pendulum.crypto.SpongeFactory;
import net.helix.pendulum.crypto.Winternitz;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.service.ValidationException;
import net.helix.pendulum.service.snapshot.Snapshot;
import net.helix.pendulum.storage.Tangle;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Validates bundles.
 * <p>
 * Bundles are lists of transactions that represent an atomic transfer, meaning that either all
 * transactions inside the bundle will be accepted by the network, or none. All transactions in a bundle have
 * the same bundle hash and are chained together via their trunks.
 *</p>
 */
public class BundleValidator {

    private final static Logger log = LoggerFactory.getLogger(BundleValidator.class);

    /**
     * Fetches a bundle of transactions identified by the {@code tailHash} and validates the transactions.
     * Bundle is a group of transactions with the same bundle hash chained by their trunks.
     * <p>
     * The fetched transactions have the same bundle hash as the transaction identified by {@code tailHash}
     * The validation does the following semantic checks:
     * <ol>
     *     <li>The absolute bundle value never exceeds the total, global supply of HLX</li>
     *     <li>The last trit when we convert from binary</li>
     *     <li>Total bundle value is 0 (inputs and outputs are balanced)</li>
     *     <li>Recalculate the bundle hash by absorbing and squeezing the transactions' essence</li>
     *     <li>Validate the signature on input transactions</li>
     * </ol>
     *
     * As well as the following syntactic checks:
     * <ol>
     *    <li>{@code tailHash} has an index of 0</li>
     *    <li>The transactions' reference order is consistent with the indexes</li>
     *    <li>The last index of each transaction in the bundle matches the last index of the tail transaction</li>
     *    <li>Check that last trit in a valid address hash is 0. We generate addresses using binary Kerl and
     *    we lose the last trit in the process</li>
     * </ol>
     *
     * @implNote if {@code tailHash} was already invalidated/validated by a previous call to this method
     * then we don't validate it
     * again.
     *</p>
     * @param tangle used to fetch the bundle's transactions from the persistence layer
     * @param initialSnapshot the initial snapshot that defines the genesis for our ledger state
     * @param tailHash the hash of the last transaction in a bundle.
     * @return A list of transactions of the bundle contained in another list. If the bundle is valid then the tail
     * transaction's {@link TransactionViewModel#getValidity()} will return 1, else
     * {@link TransactionViewModel#getValidity()} will return -1.
     * If the bundle is invalid then an empty list will be returned.
     * @throws Exception if a persistence error occured
     */
    public static List<List<TransactionViewModel>> validate(Tangle tangle, Snapshot initialSnapshot, Hash tailHash) throws Exception {

        TransactionViewModel tail = TransactionViewModel.fromHash(tangle, tailHash);
        if (tail.getCurrentIndex() != 0) {
            log.trace("{} is not a tail", tail);
            return Collections.emptyList();
        }

//        if (tail.getValidity() == -1) {
//            log.trace("{} is not valid", tail);
//            return Collections.emptyList();
//        }

        List<List<TransactionViewModel>> transactions = new LinkedList<>();
        final Map<Hash, TransactionViewModel> bundleTransactions = loadTransactionsFromTangle(tangle, tail);

        LinkedList<TransactionViewModel> sortedTxs;
        try {
            sortedTxs = validateOrder(bundleTransactions.values());
            validateValue(sortedTxs);
            validateBundleHash(sortedTxs);
            validateSignatures(sortedTxs);
        } catch (ValidationException ve) {
            tail.setValidity(tangle, initialSnapshot, -1);
            log.warn("Bundle validation exception {}", (Object)ve);
            return Collections.emptyList();
        }

        tail.setValidity(tangle, initialSnapshot, 1);
        transactions.add(sortedTxs);

        return transactions;
    }



    /**
     * Checks that the bundle's inputs and outputs are balanced.
     *
     * @param transactionViewModels list of transactions that are in a bundle
     * @return {@code true} if balanced, {@code false} if unbalanced or {@code transactionViewModels} is empty
     */
    public static boolean isInconsistent(List<TransactionViewModel> transactionViewModels) {
        long value = 0;
        for (final TransactionViewModel bundleTransactionViewModel : transactionViewModels) {
            if (bundleTransactionViewModel.value() != 0) {
                value += bundleTransactionViewModel.value();
                /*
                if(!milestone && bundleTransactionViewModel.getAddressHash().equals(Hash.NULL_HASH) && bundleTransactionViewModel.snapshotIndex() == 0) {
                    return true;
                }
                */
            }
        }
        return (value != 0 || transactionViewModels.size() == 0);
    }

    /**
     * Traverses down the given {@code tail} trunk until all transactions that belong to the same bundle
     * (identified by the bundle hash) are found and loaded.
     *
     * @param tangle connection to the persistence layer
     * @param tail should be the last transaction of the bundle
     * @return map of all transactions in the bundle, mapped by their transaction hash
     */
    private static Map<Hash, TransactionViewModel> loadTransactionsFromTangle(Tangle tangle, TransactionViewModel tail) {
        final Map<Hash, TransactionViewModel> bundleTransactions = new HashMap<>();
        final Hash bundleHash = tail.getBundleHash();
        try {
            TransactionViewModel tx = tail;
            long i = 0;
            long end = tx.lastIndex();
            do {
                bundleTransactions.put(tx.getHash(), tx);
                tx = tx.getTrunkTransaction(tangle);
            } while (i++ < end && tx.getCurrentIndex() != 0 && tx.getBundleHash().equals(bundleHash));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bundleTransactions;
    }

    /**
     * Sorts the provided list according to the index
     *
     * @param bundleTxs unordered list of transactions
     * @return List of <code>TransactionViewModel</code> in the bundle, from starting from <code>currentIndex == 0</code> to
     * <code>currentIndex == tail.lastIndex()</code>
     *
     * @throws ValidationException if there is a missing index or the size does not match the last index
     */
    static LinkedList<TransactionViewModel> validateOrder(Collection<TransactionViewModel> bundleTxs) {
        SortedSet<TransactionViewModel> set = new TreeSet<>(Comparator.comparing(TransactionViewModel::getCurrentIndex));
        set.addAll(bundleTxs);
        LinkedList<TransactionViewModel> sorted = new LinkedList<>(set);
        int index = 0;
        TransactionViewModel lastTx = sorted.getFirst();
        for (TransactionViewModel txvm : sorted) {
            if (txvm.getCurrentIndex() != index) {
                throw new ValidationException(String.format("%s does not match index %d", txvm.toString(), index));
            }
            if (index > 0 && !lastTx.getTrunkTransactionHash().equals(txvm.getHash())) {
                throw new ValidationException(String.format("Trunk of %s should be equal to %s",
                        lastTx.toString(), txvm.getHash()));
            }
            lastTx = txvm;
            index ++;
        }
        long expectedSize = sorted.getFirst().lastIndex() + 1;
        if (sorted.size() != expectedSize) {
            throw new ValidationException(String.format("The bundle is incomplete. Expected bundle size: %d", expectedSize));
        }
        return sorted;
    }

    /**
     *
     * @param bundleTxs Ordered list of the transaction bundles
     *
     * @throws ValidationException if the value transfers are out of bounds or the bundle value is inconsistent
     * (i.e. cummulative value transfers don't add up to zero)
     */
    static void validateValue(LinkedList<TransactionViewModel> bundleTxs) {
        long bundleValue = 0;
        for (TransactionViewModel txvm : bundleTxs) {
            bundleValue = Math.addExact(bundleValue, txvm.value());
            if (bundleValue < -TransactionViewModel.SUPPLY) {
                throw new ValidationException("Bundle value is below the negative max supply");
            }
            if (bundleValue > TransactionViewModel.SUPPLY) {
                throw new ValidationException("Bundle value is above the max supply");
            }
        }

        if (bundleValue != 0) {
            throw new ValidationException("Bundle transaction values are inconsistent");
        }
    }

    /**
     *
     *  @param bundleTxs Ordered list of the bundle transactions
     *
     *  @throws ValidationException If the bundle hash as stored in the tail does not match the actual bundle hash
     *  as stored in the essense part of the bundle transactions
     */
    static void validateBundleHash(LinkedList<TransactionViewModel> bundleTxs) {
        if (bundleTxs == null || bundleTxs.size() == 0) {
            throw new ValidationException("Bundle txs list is empty");
        }

        Sponge sha3Instance = SpongeFactory.create(SpongeFactory.Mode.S256);
        byte[] bundleHashBytes = new byte[TransactionViewModel.BUNDLE_SIZE];
        sha3Instance.reset();
        for (final TransactionViewModel txvm : bundleTxs) {
            sha3Instance.absorb(txvm.getBytes(), TransactionViewModel.ESSENCE_OFFSET, TransactionViewModel.ESSENCE_SIZE);
        }
        sha3Instance.squeeze(bundleHashBytes, 0, bundleHashBytes.length);
        TransactionViewModel tail = bundleTxs.getFirst();
        Hash bundleHash = tail.getBundleHash();
        if (bundleHash == null) {
            throw new ValidationException("Bundle hash of the tail tx is null: " + tail.toString());
        }

        if (!Arrays.equals(bundleHash.bytes(), bundleHashBytes)) {
            throw new ValidationException(String.format("Bundle hash %s does not match calculated hash %s",
                    Hex.toHexString(bundleHash.bytes()), Hex.toHexString(bundleHashBytes)));
        }
    }

    /**
     *  After a spendig txs, a few zero-value txs bearing the signature should follow.
     *  @param bundleTxs Ordered list of the bundle transactions
     *
     *  @throws ValidationException If the address hashes of the spending transactions
     *                              do not match the public address (Winternitz OTS)
     */
    static void validateSignatures(LinkedList<TransactionViewModel> bundleTxs) {
        TransactionViewModel tail = bundleTxs.getFirst();
        Hash bundleHash = tail.getBundleHash();
        if (bundleHash == null) {
            throw new ValidationException("Bundle hash of the tail tx is null: " + tail.toString());
        }
        byte[] bundleHashBytes = bundleHash.bytes();
        ArrayList<TransactionViewModel> bundleArray = new ArrayList<>(bundleTxs);

        int txIndex = 0;
        while (txIndex < bundleArray.size()) {

            TransactionViewModel spendingTx = bundleArray.get(txIndex);
            if (spendingTx.value() >= 0) {
                // only spending transactions require a signature
                txIndex++;
                continue;
            }

            List<TransactionViewModel> signatureTxs = extractSignatureTxs(txIndex, bundleArray);
            byte[] signatureFragments = extractSignatureBytes(signatureTxs);
            boolean valid = Winternitz.validateSignature(SpongeFactory.Mode.S256,
                    spendingTx.getAddressHash().bytes(),
                    signatureFragments,
                    bundleHashBytes);
            if (!valid) {
                throw new ValidationException(
                     String.format("Signature verification failed for address %s",
                                    spendingTx.getAddressHash()));
            }
            // fast forward past the signinging transactions in the bundle
            txIndex += signatureTxs.size();
        }
    }

    /**
     *
     * @return the zero-val signing transactions that follow a spending transaction at index <code>startIndex</code>
     *
     * @throws <code>IllegalArgumentException</code> is the transaction at startIndex is not a spending transaction
     *   (i.e. it has a non-negative value)
     */

    private static ArrayList<TransactionViewModel> extractSignatureTxs(int startIndex, ArrayList<TransactionViewModel> bundleTxs) {
        ArrayList<TransactionViewModel> result = new ArrayList<>();
        TransactionViewModel spendingTx = bundleTxs.get(startIndex);

        if (spendingTx.value() >= 0) {
            throw new IllegalArgumentException("The first signature must be a spending signature");
        }
        result.add(spendingTx);
        int index = startIndex + 1;
        while (index < bundleTxs.size()) {
            TransactionViewModel nextTx = bundleTxs.get(index);

            if  (nextTx.value() != 0) {
                break;
            }

            if (!spendingTx.getAddressHash().equals(nextTx.getAddressHash())) {
                break;
            }

            result.add(bundleTxs.get(index));
            index++;
        }

        return result;
    }

    private static byte[] extractSignatureBytes(List<TransactionViewModel> signatureTxs) {
        int numFragments = signatureTxs.size();
        byte[] signature = new byte[numFragments * TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE];
        int i = 0;
        for (TransactionViewModel tx : signatureTxs) {
            System.arraycopy(tx.getSignature(),
                    0,
                            signature,
                    i * TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE,
                            TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE);
            i++;
        }
        return signature;
    }
}
