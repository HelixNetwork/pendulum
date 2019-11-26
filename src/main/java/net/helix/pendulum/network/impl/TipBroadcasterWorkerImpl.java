package net.helix.pendulum.network.impl;

import net.helix.pendulum.Pendulum;
import net.helix.pendulum.controllers.TipsViewModel;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.network.Node;
import net.helix.pendulum.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates a background worker that tries to work through the request queue by sending random tips along the requested
 * transactions.<br />
 * <br />
 * This massively increases the sync speed of new nodes that would otherwise be limited to requesting in the same rate
 * as new transactions are received.<br />
 * <br />
 * Note: To reduce the overhead for the node we only trigger this worker if the request queue gets bigger than the
 * {@link #REQUESTER_THREAD_ACTIVATION_THRESHOLD}. Otherwise we rely on the processing of the queue due to normal
 * outgoing traffic like transactions that get relayed by our node.<br />
 */
public class TipBroadcasterWorkerImpl implements Node.TipBroadcasterWorker {

    /**
     * The logger of this class (a rate limited logger than doesn't spam the CLI output).<br />
     */
    private static final Logger log = LoggerFactory.getLogger(TipBroadcasterWorkerImpl.class);

    /**
     * The Tangle object which acts as a database interface.<br />
     */
    private Tangle tangle;

    /**
     * Manager for the tips (required for selecting the random tips).
     */
    private TipsViewModel tipsViewModel;


    /**
     * Initializes the instance and registers its dependencies.<br />
     * <br />
     * It simply stores the passed in values in their corresponding private properties.<br />
     * <br />
     * Note: Instead of handing over the dependencies in the constructor, we register them lazy. This allows us to have
     * circular dependencies because the instantiation is separated from the dependency injection. To reduce the
     * amount of code that is necessary to correctly instantiate this class, we return the instance itself which
     * allows us to still instantiate, initialize and assign in one line - see Example:<br />
     * <br />
     * {@code transactionRequesterWorker = new TransactionRequesterWorkerImpl().init(...);}
     *
     * @return the initialized instance itself to allow chaining
     */
    public TipBroadcasterWorkerImpl init() {

        this.tangle = Pendulum.ServiceRegistry.get().resolve(Tangle.class);
        this.tipsViewModel = Pendulum.ServiceRegistry.get().resolve(TipsViewModel.class);

        return this;
    }

    /**
     * {@inheritDoc}
     * <br />
     * To reduce the overhead for the node we only trigger this worker if the request queue gets bigger than the {@link
     * #REQUESTER_THREAD_ACTIVATION_THRESHOLD}. Otherwise we rely on the processing of the queue due to normal outgoing
     * traffic like transactions that get relayed by our node.<br />
     */
    @Override
    public TransactionViewModel tipToBroadcast() {
        try {
            TransactionViewModel transaction = getTipToBroadcast();
            if (isValidTransaction(transaction)) {
                return transaction;
            }
        } catch (Exception e) {
            log.error("unexpected error while processing the request queue", e);
        }
        return null;
    }


    /**
     * Retrieves a random solid tip that can be sent together with our request.<br />
     * <br />
     * It simply retrieves the hash of the tip from the {@link #tipsViewModel} and tries to load it from the
     * database.<br />
     *
     * @return a random tip
     * @throws Exception if anything unexpected happens while trying to retrieve the random tip.
     */
    //@VisibleForTesting
    private TransactionViewModel getTipToBroadcast() throws Exception {
        Hash tip = tipsViewModel.getRandomSolidTipHash();
        if (tip == null) {
            tip = tipsViewModel.getRandomNonSolidTipHash();
        }

        return TransactionViewModel.fromHash(tangle, tip == null ? Hash.NULL_HASH : tip);
    }


    //@VisibleForTesting
    private boolean isValidTransaction(TransactionViewModel transaction) {
        return transaction != null && (
                transaction.getType() != TransactionViewModel.PREFILLED_SLOT
                        || transaction.getHash().equals(Hash.NULL_HASH));
    }
}
