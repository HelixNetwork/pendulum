package net.helix.hlx;

import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.crypto.Sha3;
import net.helix.hlx.crypto.Sponge;
import net.helix.hlx.crypto.SpongeFactory;
import net.helix.hlx.crypto.Winternitz;
import net.helix.hlx.model.Hash;
import net.helix.hlx.service.snapshot.Snapshot;
import net.helix.hlx.storage.Tangle;
import org.bouncycastle.util.encoders.Hex;

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
        if (tail.getCurrentIndex() != 0 || tail.getValidity() == -1) {
            System.out.println("Empty List");
            return Collections.EMPTY_LIST;
        }

        List<List<TransactionViewModel>> transactions = new LinkedList<>();
        final Map<Hash, TransactionViewModel> bundleTransactions = loadTransactionsFromTangle(tangle, tail);
        // System.out.println("bundle size: " + bundleTransactions.size());

        //we don't really iterate, we just pick the tail tx. See the if on the next line
        for (TransactionViewModel transactionViewModel : bundleTransactions.values()) {

            if (transactionViewModel.getCurrentIndex() == 0 && transactionViewModel.getValidity() >= 0) {

                final List<TransactionViewModel> instanceTransactionViewModels = new LinkedList<>();

                final long lastIndex = transactionViewModel.lastIndex();
                long bundleValue = 0;
                int i = 0;
                final Sponge sha3Instance = SpongeFactory.create(SpongeFactory.Mode.S256);
                final Sponge addressInstance = SpongeFactory.create(SpongeFactory.Mode.S256);

                final byte[] addressBytes = new byte[TransactionViewModel.ADDRESS_SIZE];
                final byte[] bundleHashBytes = new byte[TransactionViewModel.BUNDLE_SIZE];
                final byte[] normalizedBundle = new byte[TransactionViewModel.BUNDLE_SIZE];
                byte[] digestBytes = new byte[Sha3.HASH_LENGTH];

                //here we iterate over the txs by checking the trunk of the current transaction
                MAIN_LOOP:
                while (true) {

                    instanceTransactionViewModels.add(transactionViewModel);

                    //semantic checks
                    if (
                            transactionViewModel.getCurrentIndex() != i
                                    || transactionViewModel.lastIndex() != lastIndex
                                    || ((bundleValue = Math.addExact(bundleValue, transactionViewModel.value())) < -TransactionViewModel.SUPPLY
                                    || bundleValue > TransactionViewModel.SUPPLY)
                    ) {
                        instanceTransactionViewModels.get(0).setValidity(tangle, initialSnapshot, -1);
                        System.out.println("Semantics Error!");
                        break;
                    }

                    // It's supposed to become -3812798742493 after 3812798742493 and to go "down" to -1 but
                    // we hope that no one will create such long bundles
                    if (i++ == lastIndex) {

                        if (bundleValue == 0) {

                            if (instanceTransactionViewModels.get(0).getValidity() == 0) {
                                sha3Instance.reset();
                                for (final TransactionViewModel transactionViewModel2 : instanceTransactionViewModels) {
                                    sha3Instance.absorb(transactionViewModel2.getBytes(), TransactionViewModel.ESSENCE_OFFSET, TransactionViewModel.ESSENCE_SIZE);
                                }
                                sha3Instance.squeeze(bundleHashBytes, 0, bundleHashBytes.length);
                                //verify bundle hash is correct
                                //System.out.println("Bundle Hash: "  + instanceTransactionViewModels.get(0).getBundleHash().hexString());
                                //System.out.println("recalculated Bundle Hash: " + Hex.toHexString(bundleHashBytes));
                                if (Arrays.equals(instanceTransactionViewModels.get(0).getBundleHash().bytes(), bundleHashBytes))  {
                                    //normalizing the bundle in preparation for signature verification
                                    Winternitz.normalizedBundle(bundleHashBytes, normalizedBundle);

                                    int offset = 0;
                                    for (int j = 0; j < instanceTransactionViewModels.size(); ) {

                                        transactionViewModel = instanceTransactionViewModels.get(j);
                                        //if it is a spent transaction that should be signed
                                        if (transactionViewModel.value() < 0) {
                                            // let's verify the signature by recalculating the public address
                                            addressInstance.reset();
                                            do {
                                                digestBytes = Winternitz.digest(SpongeFactory.Mode.S256, Arrays.copyOfRange(normalizedBundle, offset*Winternitz.NORMALIZED_FRAGMENT_LENGTH, (offset+1)*Winternitz.NORMALIZED_FRAGMENT_LENGTH),
                                                        Arrays.copyOfRange(instanceTransactionViewModels.get(j).getBytes(), 0, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE));
                                                addressInstance.absorb(digestBytes,0, Sha3.HASH_LENGTH);
                                                offset++;
                                            } //loop to traverse signature fragments divided between transactions
                                            while (++j < instanceTransactionViewModels.size()
                                                    && instanceTransactionViewModels.get(j).getAddressHash().equals(transactionViewModel.getAddressHash())
                                                    && instanceTransactionViewModels.get(j).value() == 0);

                                            addressInstance.squeeze(addressBytes, 0, addressBytes.length);
                                            //signature verification
                                            if (! Arrays.equals(transactionViewModel.getAddressHash().bytes(), addressBytes)) {
                                                instanceTransactionViewModels.get(0).setValidity(tangle, initialSnapshot, -1);
                                                System.out.println("Signature Error!");
                                                break MAIN_LOOP;
                                            }
                                        } else {
                                            j++;
                                        }
                                    }
                                    //should only be reached after the above for loop is done
                                    instanceTransactionViewModels.get(0).setValidity(tangle, initialSnapshot, 1);
                                    transactions.add(instanceTransactionViewModels);
                                }
                                //bundle hash verification failed
                                else {
                                    instanceTransactionViewModels.get(0).setValidity(tangle, initialSnapshot, -1);
                                    System.out.println("Bundle Hash Error!");
                                }
                            }
                            //bundle validity status is known
                            else {
                                transactions.add(instanceTransactionViewModels);
                            }
                        }
                        //total bundle value does not sum to 0
                        else {
                            instanceTransactionViewModels.get(0).setValidity(tangle, initialSnapshot, -1);
                            System.out.println("Bundle Sum Error!");
                        }
                        //break from main loop
                        break;

                    }
                    //traverse to the next tx in the bundle
                    else {
                        transactionViewModel = bundleTransactions.get(transactionViewModel.getTrunkTransactionHash());
                        if (transactionViewModel == null) {
                            //we found all the transactions and we can now return
                            break;
                        }
                    }
                }
            }
        }
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
            long i = 0, end = tx.lastIndex();
            do {
                bundleTransactions.put(tx.getHash(), tx);
                tx = tx.getTrunkTransaction(tangle);
            } while (i++ < end && tx.getCurrentIndex() != 0 && tx.getBundleHash().equals(bundleHash));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bundleTransactions;
    }
}
