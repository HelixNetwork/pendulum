package net.helix.pendulum.network.impl;

import net.helix.pendulum.Pendulum;
import net.helix.pendulum.controllers.TipsViewModel;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.event.EventContext;
import net.helix.pendulum.event.EventManager;
import net.helix.pendulum.event.EventType;
import net.helix.pendulum.event.Key;
import net.helix.pendulum.model.Hash;
/mport net.helix.pendulum.network.Node;
import net.helix.pendulum.storage.Tangle;
import net.helix.pendulum.utils.thread.DedicatedScheduledExecutorService;
import net.helix.pendulum.utils.thread.SilentScheduledExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

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
public class TipRequesterWorkerImpl implements Node.TipRequesterWorker {
    /**
     * The minimum amount of transactions in the request queue that are required for the worker to trigger.<br />
     */
    public static final int REQUESTER_THREAD_ACTIVATION_THRESHOLD = 50;

    /**
     * The time (in milliseconds) that the worker waits between its iterations.<br />
     */
    private static final int REQUESTER_THREAD_INTERVAL = 100;

    /**
     * The logger of this class (a rate limited logger than doesn't spam the CLI output).<br />
     */
    private static final Logger log = LoggerFactory.getLogger(TipRequesterWorkerImpl.class);

    /**
     * The Tangle object which acts as a database interface.<br />
     */
    private Tangle tangle;

    /**
     * The manager for the requested transactions that allows us to access the request queue.<br />
     */
    private Node.RequestQueue requestQueue;

    /**
     * Manager for the tips (required for selecting the random tips).
     */
    private TipsViewModel tipsViewModel;

    /**
     * The network manager of the node.<br />
     */

    /**
     * The manager of the background task.<br />
     */
    private final SilentScheduledExecutorService executorService = new DedicatedScheduledExecutorService(
            "Transaction Requester", log);

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
    public TipRequesterWorkerImpl init() {

        this.tangle = Pendulum.ServiceRegistry.get().resolve(Tangle.class);
        this.requestQueue = Pendulum.ServiceRegistry.get().resolve(Node.RequestQueue.class);
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
    public boolean processRequestQueue() {
        try {
            if (isActive()) {
                TransactionViewModel transaction = getTransactionToSendWithRequest();
                if (isValidTransaction(transaction)) {
                    EventContext ctx = new EventContext();
                    ctx.put(Key.key("TX", TransactionViewModel.class), transaction);
                    EventManager.get().fire(EventType.REQUEST_TIP_TX, ctx);

                    return true;
                }
            }
        } catch (Exception e) {
            log.error("unexpected error while processing the request queue", e);
        }
        return false;
    }

    @Override
    public void start() {
        executorService.silentScheduleWithFixedDelay(this::processRequestQueue, 0, REQUESTER_THREAD_INTERVAL,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void shutdown() {
        log.debug("Shutting down tip requester worker");
        executorService.shutdownNow();
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
    private TransactionViewModel getTransactionToSendWithRequest() throws Exception {
        Hash tip = tipsViewModel.getRandomSolidTipHash();
        if (tip == null) {
            tip = tipsViewModel.getRandomNonSolidTipHash();
        }

        return TransactionViewModel.fromHash(tangle, tip == null ? Hash.NULL_HASH : tip);
    }


    //@VisibleForTesting
    private boolean isActive() {
        return requestQueue.size() >= REQUESTER_THREAD_ACTIVATION_THRESHOLD;
    }

    //@VisibleForTesting
    private boolean isValidTransaction(TransactionViewModel transaction) {
        return transaction != null && (
                transaction.getType() != TransactionViewModel.PREFILLED_SLOT
                        || transaction.getHash().equals(Hash.NULL_HASH));
    }
}
