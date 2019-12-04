package net.helix.pendulum.network;

import net.helix.pendulum.Pendulum;
import net.helix.pendulum.TransactionValidator;
import net.helix.pendulum.conf.NodeConfig;
import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.controllers.RoundViewModel;
import net.helix.pendulum.controllers.TipsViewModel;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.crypto.SpongeFactory;
import net.helix.pendulum.event.*;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.HashFactory;
import net.helix.pendulum.model.TransactionHash;
import net.helix.pendulum.network.impl.DatagramFactoryImpl;
import net.helix.pendulum.network.impl.RequestQueueImpl;
import net.helix.pendulum.network.impl.TipBroadcasterWorkerImpl;
import net.helix.pendulum.network.impl.TxPacketData;
import net.helix.pendulum.service.milestone.MilestoneTracker;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.storage.Tangle;
import net.helix.pendulum.utils.PendulumUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static net.helix.pendulum.model.Hash.NULL_HASH;


/**
 * Class Node is the core class for handling gossip protocol packets.
 * Both TCP and UDP receivers will pass incoming packets to this class's object.
 * It is also responsible for validating and storing the received transactions
 * into the Tangle Database. <br>
 *
 * The Gossip protocol is specific to nodes and is used for spamming and requesting
 * new transactions between peers. Every message sent on Gossip protocol consists of two
 * parts - the transaction in binary encoded format followed by a hash of another transaction to
 * be requested. The receiving entity will save the newly received transaction into
 * its own database and will respond with the received requested transaction - if
 * available in its own storgage.
 *
 */
public class Node implements PendulumEventListener, Pendulum.Initializable {

    private static final Logger log = LoggerFactory.getLogger(Node.class);

    private int BROADCAST_QUEUE_SIZE;
    private int RECV_QUEUE_SIZE;
    private int REPLY_QUEUE_SIZE;

    private static final int PAUSE_BETWEEN_BROADCASTS_MS = PendulumUtils.getSystemProp("node.broadcast.pause", 100);
    private static final int PAUSE_BETWEEN_NULL_REQUESTS_MS = PendulumUtils.getSystemProp("node.nullreq.pause", 1000);
    private static final int PAUSE_BETWEEN_DNS_CHECKS_MS = PendulumUtils.getSystemProp("node.dnscheck.pause", 60000);
    private static final int PAUSE_BETWEEN_RECEIVE_QUEUE_POLLS_MS = PendulumUtils.getSystemProp("node.receive.pause", 100);
    private static final int PAUSE_BETWEEN_REPLY_QUEUE_POLLS_MS = PendulumUtils.getSystemProp("node.reply.pause", 100);
    private static final int PAUSE_BETWEEN_TIP_BROADCASTS_MS = PendulumUtils.getSystemProp("node.tip.broadcast.pause", 300);
    private static final int PAUSE_BETWEEN_STATS_MS = PendulumUtils.getSystemProp("node.stats.pause", 5000);

    private static final int BROADCAST_BATCH_SIZE = PendulumUtils.getSystemProp("node.broadcast.batch.size", 100);
    private static final int REPLY_BATCH_SIZE = PendulumUtils.getSystemProp("node.reply.batch.size", 100);
    private static final int RECEIVE_BATCH_SIZE = PendulumUtils.getSystemProp("node.receive.batch.size", 30);
    private static final int TIP_BROADCAST_BATCH_SIZE = PendulumUtils.getSystemProp("node.tip.broadcast.batch.size", 10);



    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private final List<Neighbor> neighbors = new CopyOnWriteArrayList<>();

    private final ConcurrentSkipListSet<TransactionViewModel> broadcastQueue = weightQueue();
    private final ConcurrentSkipListSet<Pair<TransactionViewModel, Neighbor>> receiveQueue = weightQueueTxPair();
    private final ConcurrentSkipListSet<Pair<Hash, Neighbor>> replyQueue = weightQueueHashPair();
    private RequestQueue requestQueue;
    private TipBroadcasterWorker tipBroadcasterWorker;

    private DatagramFactory packetFactory = new DatagramFactoryImpl();

    private final int PROCESSOR_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors() );
    private ExecutorService udpReceiver;
    private ScheduledExecutorService scheduler;

    private NodeConfig configuration;
    private Tangle tangle;
    private SnapshotProvider snapshotProvider;
    private TipsViewModel tipsViewModel;
    private TransactionValidator transactionValidator;

    private static final SecureRandom rnd = new SecureRandom();

    private static long sendLimit = -1;
    private static AtomicLong sendPacketsCounter = new AtomicLong(0L);
    private static AtomicLong sendPacketsTimer = new AtomicLong(0L);

    public static final ConcurrentSkipListSet<String> rejectedAddresses = new ConcurrentSkipListSet<String>();
    private DatagramSocket udpSocket;



    /**
     * Internal map used to keep track of neighbor's IP vs DNS name
     */
    private final Map<String, String> neighborIpCache = new HashMap<>();

    /**
     * Constructs a Node class instance. The constructor is passed reference
     * of several other instances.
     *
     * @param tangle An instance of the Tangle storage interface
     * @param snapshotProvider data provider for the snapshots that are relevant for the node
     * @param transactionValidator makes sure transaction is not malformed.
     * @param tipsViewModel Contains a hash of solid and non solid tips
     * @param milestoneTracker Tracks milestones issued from the coordinator
     * @param configuration Contains all the config.
     *
     */
    @Deprecated
    public Node(final Tangle tangle, SnapshotProvider snapshotProvider, final TransactionValidator transactionValidator, final TipsViewModel tipsViewModel, final MilestoneTracker milestoneTracker, final NodeConfig configuration
    ) {
        this.configuration = configuration;
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider ;
        this.transactionValidator = transactionValidator;

        this.tipsViewModel = tipsViewModel;

        this.tipBroadcasterWorker = new TipBroadcasterWorkerImpl();
        this.requestQueue = new RequestQueueImpl();

        Pendulum.ServiceRegistry.get().register(RequestQueue.class, requestQueue);

        EventManager.get().subscribe(EventType.NEW_BYTES_RECEIVED, this);

    }

    public Node() {
        this.requestQueue = new RequestQueueImpl();
        Pendulum.ServiceRegistry.get().register(RequestQueue.class, requestQueue);

    }

    /**
     * Intialize the operations by spawning all the worker threads.
     *
     */
    @Override
    public Node init()  {
        this.configuration = Pendulum.ServiceRegistry.get().resolve(PendulumConfig.class);
        this.tangle = Pendulum.ServiceRegistry.get().resolve(Tangle.class);
        this.snapshotProvider = Pendulum.ServiceRegistry.get().resolve(SnapshotProvider.class);
        this.transactionValidator = Pendulum.ServiceRegistry.get().resolve(TransactionValidator.class);
        this.tipsViewModel = Pendulum.ServiceRegistry.get().resolve(TipsViewModel.class);
        this.tipBroadcasterWorker = new TipBroadcasterWorkerImpl();
        this.requestQueue.init();
        // default to 800 if not properly set
        int txPacketSize = configuration.getTransactionPacketSize() > 0
                ? configuration.getTransactionPacketSize() : TransactionViewModel.SIZE + Hash.SIZE_IN_BYTES;
        sendLimit = (long) ((configuration.getSendLimit() * 1000000) / txPacketSize);

        BROADCAST_QUEUE_SIZE = RECV_QUEUE_SIZE = REPLY_QUEUE_SIZE = configuration.getqSizeNode();

        parseNeighborsConfig();

        packetFactory.init();
        requestQueue.init();
        tipBroadcasterWorker.init();

        EventManager.get().subscribe(EventType.NEW_BYTES_RECEIVED, this);
        EventManager.get().subscribe(EventType.TX_STORED, this);
        EventManager.get().subscribe(EventType.TX_UPDATED, this);

        initialized.set(true);

        return this;
    }

    public void start() {
        if (!initialized.get()) {
            throw new IllegalStateException("Node is not initialized");
        }
        initScheduler();
        log.debug("Starting a Pendulum node...");
    }

    private void initScheduler() {
        BasicThreadFactory udpTheads = new BasicThreadFactory.Builder()
                .namingPattern("udp-rcv-%d")
                .daemon(true)
                .priority(Thread.MIN_PRIORITY)
                .build();
        udpReceiver = Executors.newFixedThreadPool(PROCESSOR_THREADS, udpTheads);

        BasicThreadFactory schedulerTheads = new BasicThreadFactory.Builder()
                .namingPattern("scheduler-%d")
                .daemon(true)
                .priority(Thread.NORM_PRIORITY)
                .build();

        scheduler = Executors.newScheduledThreadPool(PROCESSOR_THREADS * 2, schedulerTheads);

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                Thread.currentThread().setName("brdcst");
                for (int i=0; i < BROADCAST_BATCH_SIZE; i++) {
                    processBroadcastQueue();
                }
            } catch (Throwable t) {
                log.error("Broadcaster Exception:", t);
            }
        }, 0, PAUSE_BETWEEN_BROADCASTS_MS, TimeUnit.MILLISECONDS);

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                Thread.currentThread().setName("NULL-req");
                DatagramPacket nullPacket = packetFactory.create(TxPacketData.NULL_HASH_DATA);
                neighbors.forEach(n -> n.send(nullPacket));
            } catch (Throwable t) {
                log.error("NULL PACKET requester exception" , t);
            }
        }, 0, PAUSE_BETWEEN_NULL_REQUESTS_MS, TimeUnit.MILLISECONDS);

        if (!configuration.isDnsResolutionEnabled()) {
            log.info("Ignoring DNS Refresher Thread... DNS_RESOLUTION_ENABLED is false");
        } else {
            scheduler.scheduleWithFixedDelay(() -> {
                try {
                    Thread.currentThread().setName("dns-check");
                    checkAllDns();
                } catch (Throwable t) {
                    log.error("Error in DNS check", t);
                }
            }, 1000, PAUSE_BETWEEN_DNS_CHECKS_MS, TimeUnit.MILLISECONDS);
        }

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                Thread.currentThread().setName("rsv-q proc");
                for (int i=0; i < RECEIVE_BATCH_SIZE; i++) {
                    processReceivedTxQueue();
                }
            } catch (Throwable t) {
                log.error("Error processing the received transaction", t);
            }
        }, 0, PAUSE_BETWEEN_RECEIVE_QUEUE_POLLS_MS, TimeUnit.MILLISECONDS);

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                Thread.currentThread().setName("reply-q proc");
                for (int i=0; i < REPLY_BATCH_SIZE; i++) {
                    processReplyFromQueue();
                }
            } catch (Throwable t) {
                log.error("Error processing the reply to request queue", t);
            }
        }, 0, PAUSE_BETWEEN_REPLY_QUEUE_POLLS_MS, TimeUnit.MILLISECONDS);

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                Thread.currentThread().setName("tip-broadcst proc");
                for (int i = 0; i < TIP_BROADCAST_BATCH_SIZE; i++) {
                    doTipBroadcast();
                }
            } catch (Throwable t) {
                log.error("Error broadcasting a tip", t);
            }
        }, 0, PAUSE_BETWEEN_TIP_BROADCASTS_MS, TimeUnit.MILLISECONDS);

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                Thread.currentThread().setName("node stats");
                reportStats();
            } catch (Throwable t) {
                log.error("Error collecting stats", t);
            }
        }, 1000, PAUSE_BETWEEN_STATS_MS, TimeUnit.MILLISECONDS);
    }

    public RequestQueue getRequestQueue() {
        if (!initialized.get()) {
            throw new IllegalStateException("Node is not initialized");
        }

        return requestQueue;
    }

    /**
     * Keeps the passed UDP DatagramSocket reference from {@link UDPReceiver}.
     * This is currently only used in creating a new {@link UDPNeighbor}.
     *
     * @param {@link DatagramSocket} socket created by UDPReceiver
     */
    void setUDPSocket(final DatagramSocket socket) {
        this.udpSocket = socket;
    }

    /**
     * Returns the stored UDP DatagramSocket reference from {@link UDPReceiver}.
     *
     * @return {@link DatagramSocket} socket created by UDPReceiver
     */
    private DatagramSocket getUdpSocket() {
        return udpSocket;
    }

    void checkAllDns() {
        neighbors.forEach(this::checkDNS);
    }

    private void doTipBroadcast() {
        if (tipsViewModel.solidSize() < TipBroadcasterWorker.REQUESTER_THREAD_ACTIVATION_THRESHOLD) {
            return;
        }

        TransactionViewModel tip = tipBroadcasterWorker.tipToBroadcast();
        if (tip != null && !NULL_HASH.equals(tip.getHash())) {
            log.trace("Broadcasting tip {}", tip.getHash());
            toBroadcastQueue(tip);
        }
    }

    private void checkDNS(Neighbor n) {
        final String hostname = n.getAddress().getHostName();

        Optional<String> ipO = checkIp(hostname);
        if (!ipO.isPresent()) {
            return;
        }

        String ip = ipO.get();
        if (match(hostname, ip)) {
            return;
        }

        if (!configuration.isDnsRefresherEnabled()) {
            log.info("IP CHANGED for {}! Skipping... DNS_REFRESHER_ENABLED is false.", hostname);
            return;
        }

        log.info("IP CHANGED for {}! Updating...", hostname);
        tangle.publish("dnscu %s", hostname);
        String protocol = (n instanceof TCPNeighbor) ? "tcp://" : "udp://";
        String port = ":" + n.getAddress().getPort();

        uri(protocol + hostname + port).ifPresent(uri -> {
            removeNeighbor(uri, n.isFlagged());

            uri(protocol + ip + port).ifPresent(nuri -> {
                Neighbor neighbor = newNeighbor(nuri, n.isFlagged());
                addNeighbor(neighbor);
                neighborIpCache.put(hostname, ip);
            });
        });

    }

    boolean match(String hostname, String ip) {
        log.info("DNS Checker: Validating DNS Address '{}' with '{}'", hostname, ip);
        tangle.publish("dnscv %s %s", hostname, ip);
        final String neighborAddress = neighborIpCache.get(hostname);

        if (neighborAddress == null) {
            neighborIpCache.put(hostname, ip);
            return true;
        }

        if (neighborAddress.equals(ip)) {
            log.info("{} seems fine.", hostname);
            tangle.publish("dnscc %s", hostname);
            return true;
        }

        return false;
    }

    /**
     * Checks whether the passed DNS is an IP address in string form or a DNS
     * hostname.
     *
     * @return An IP address (decimal form) in string resolved from the given DNS
     *
     */
    Optional<String> checkIp(String dnsName) {

        if (StringUtils.isEmpty(dnsName)) {
            return Optional.empty();
        }

        InetAddress inetAddress;
        try {
            inetAddress = java.net.InetAddress.getByName(dnsName);
        } catch (UnknownHostException e) {
            return Optional.empty();
        }

        final String hostAddress = inetAddress.getHostAddress();

        if (StringUtils.equals(dnsName, hostAddress)) { // not a DNS...
            return Optional.empty();
        }

        return Optional.of(hostAddress);
    }

    @Override
    public void handle(EventType event, EventContext ctx) {
        if (!initialized.get()) {
            throw new IllegalStateException("Node is not initialized");
        }

        switch (event) {
            case NEW_BYTES_RECEIVED:
                if (udpReceiver == null) {
                    log.warn("UDP RECEIVER has not been started");
                    return;
                }

                byte[] bytes = ctx.get(Key.key("BYTES", byte[].class));
                SocketAddress address = ctx.get(Key.key("SENDER", SocketAddress.class));
                String uriScheme = ctx.get(Key.key("URI_SCHEME", String.class));
                udpReceiver.submit(() -> {
                        try {
                            Thread.currentThread().setName("udp rcv");
                            preProcessReceivedData(bytes.clone(), address, uriScheme);
                        } catch (Throwable t) {
                            log.error("Error in the receiver task", t);
                        }
                });
                break;

            case TX_STORED:
            case TX_UPDATED:
                try {
                    TransactionViewModel tx = TransactionViewModel.fromHash(tangle, EventUtils.getTxHash(ctx));
                    if (tx.getType() == TransactionViewModel.FILLED_SLOT) {
                        requestQueue.clearTransactionRequest(EventUtils.getTxHash(ctx));
                    }
                } catch (Exception e) {
                    log.error("",e);
                }

                break;

            default:

        }
    }

    /**
     * First Entry point for receiving any incoming transactions from TCP/UDP Receivers.
     * At this point, the transport protocol (UDP/TCP) is irrelevant.
     *
     * The packet is then added to receiveQueue for further processing.
     */

    private void preProcessReceivedData(byte[] receivedData, SocketAddress senderAddress, String uriScheme) {
        Neighbor neighbor = getNeighbor(senderAddress);
        if (neighbor == null) {
            log.trace("Received packets from an untethered neighbour {}", senderAddress.toString());
            if (configuration.isTestnet()) {
                addNewNeighbor(senderAddress, uriScheme);
            }
            return;
        }

        neighbor.incAllTransactions();
        TransactionViewModel receivedTx = preValidateTransaction(receivedData);

        if (receivedTx != null && !NULL_HASH.equals(receivedTx.getHash())) {
            Hash requestedHash = prepareReply(receivedData, neighbor, receivedTx.getHash());
            addTxToReceiveQueue(receivedTx, neighbor);

            log.trace("Received_txvm / requested_hash / sender / isMilestone = {} {} {} {}",
                    receivedTx.getHash().toString(),
                    requestedHash.toString(),
                    neighbor.getAddress().toString(),
                    receivedTx.isMilestone());
        }
    }

    private Neighbor getNeighbor(SocketAddress address) {
        for (final Neighbor neighbor : getNeighbors()) {
            if(neighbor.matches(address)) {
                return neighbor;
            }
        }
        return null;
    }

    /**
     * If the packet is new, we construct
     *  a {@link TransactionViewModel} object from it and perform some basic validation
     * on the received transaction via  {@link TransactionValidator#runValidation}
     * @param receivedData received data
     * @return transaction hash if the data passes pre-validation, null otherwise
     */
    private TransactionViewModel preValidateTransaction(byte[] receivedData) {

        double pDropTransaction = configuration.getpDropTransaction();

        if (rnd.nextDouble() < pDropTransaction) {
            log.trace("Randomly dropping transaction. Stand by... ");
            return null;
        }

        try {
            return doPreValidation(receivedData);
            //return cacheService.get(receivedData, () -> doPreValidation(receivedData));

            // TODO: this stuff  should be handled in preValidation
//        } catch (final TransactionValidator.StaleTimestampException e) {
//            log.debug(e.getMessage());
//            try {
//                requestQueue.clearTransactionRequest(receivedTransactionHash);
//            } catch (Exception e1) {
//                log.error(e1.getMessage());
//            }
//            neighbor.incStaleTransactions();

        } catch (final Exception e) {
            log.error(e.getMessage());
            log.error("Received an Invalid TransactionViewModel. Dropping it...");
            //neighbor.incInvalidTransactions();
            return null;
        }

    }

    private TransactionViewModel doPreValidation(byte[] receivedData) {
        TransactionViewModel receivedTransactionViewModel = new TransactionViewModel(receivedData,
                TransactionHash.calculate(receivedData, TransactionViewModel.SIZE, SpongeFactory.create(SpongeFactory.Mode.S256)));
        transactionValidator.runValidation(receivedTransactionViewModel, transactionValidator.getMinWeightMagnitude());

        return receivedTransactionViewModel;
    }

    private Hash prepareReply(byte[] receivedData, Neighbor neighbor, Hash receivedTransactionHash) {
        Hash requestedHash = HashFactory.TRANSACTION.create(receivedData,
                TransactionViewModel.SIZE,
                configuration.getRequestHashSize());

        if (requestedHash.equals(receivedTransactionHash)) {
            //requesting a random tip
            log.trace("Requesting random tip from {}", neighbor.getAddress().toString());
            requestedHash = NULL_HASH;
        }

        toReplyQueue(requestedHash, neighbor);
        return requestedHash;
    }

//    private void logStats(boolean cached) {
//        long hitCount;
//        long missCount;
//        if (cached) {
//            hitCount = recentSeenBytesHitCount.incrementAndGet();
//            missCount = recentSeenBytesMissCount.get();
//        } else {
//            hitCount = recentSeenBytesHitCount.get();
//            missCount = recentSeenBytesMissCount.incrementAndGet();
//        }
//        if (((hitCount + missCount) % 50000L == 0)) {
//            log.info("RecentSeenBytes cache hit/miss ratio: " + hitCount + "/" + missCount);
//            tangle.publish("hmr %d/%d", hitCount, missCount);
//            recentSeenBytesMissCount.set(0L);
//            recentSeenBytesHitCount.set(0L);
//        }
//    }

    private void addNewNeighbor(SocketAddress senderAddress, String uriScheme) {
        int maxPeersAllowed = configuration.getMaxPeers();
        String uriString = uriScheme + ":/" + senderAddress.toString();
        if (Neighbor.getNumPeers() < maxPeersAllowed) {
            log.info("Adding non-tethered neighbor: " + uriString);
            tangle.publish("antn %s", uriString);
            try {
                final URI uri = new URI(uriString);
                // 3rd parameter false (not tcp), 4th parameter true (configured tethering)
                final Neighbor newneighbor = newNeighbor(uri, false);
                if (!getNeighbors().contains(newneighbor)) {
                    getNeighbors().add(newneighbor);
                    Neighbor.incNumPeers();
                }
            } catch (URISyntaxException e) {
                log.error("Invalid URI string: " + uriString);
            }
        } else {
            if (rejectedAddresses.size() > 20) {
                // Avoid ever growing list in case of an attack.
                rejectedAddresses.clear();
            } else if (rejectedAddresses.add(uriString)) {
                tangle.publish("rntn %s %s", uriString, String.valueOf(maxPeersAllowed));
                log.info("Refused non-tethered neighbor: " + uriString +
                        " (max-peers = " + maxPeersAllowed + ")");
            }
        }
    }

    /**
     * Adds incoming transactions to the {@link Node#receiveQueue} to be processed later.
     */
    private void addTxToReceiveQueue(TransactionViewModel receivedTransactionViewModel, Neighbor neighbor) {
        if (NULL_HASH.equals(receivedTransactionViewModel.getHash())) {
            return;
        }
        receiveQueue.add(new ImmutablePair<>(receivedTransactionViewModel, neighbor));
        if (receiveQueue.size() > RECV_QUEUE_SIZE) {
            receiveQueue.pollLast();
        }

    }

    /**
     * Adds incoming transactions to the {@link Node#replyQueue} to be processed later
     */
    private void toReplyQueue(Hash requestedHash, Neighbor neighbor) {
        replyQueue.add(new ImmutablePair<>(requestedHash, neighbor));
        if (replyQueue.size() > REPLY_QUEUE_SIZE) {
            replyQueue.pollLast();
        }
    }

    /**
     * Picks up a transaction and neighbor pair from receive queue. Calls
     * {@link Node#processReceivedTx} on the pair.
     */
    private void processReceivedTxQueue() {
        final Pair<TransactionViewModel, Neighbor> receivedData = receiveQueue.pollFirst();
        if (receivedData != null) {
            processReceivedTx(receivedData.getLeft(), receivedData.getRight());
        }
    }

    /**
     * Picks up a transaction hash and neighbor pair from reply queue. Calls
     * {@link Node#replyToRequestedHash} on the pair.
     */
    private void processReplyFromQueue() {
        final Pair<Hash, Neighbor> receivedData = replyQueue.pollFirst();
        if (receivedData != null) {
            replyToRequestedHash(receivedData.getLeft(), receivedData.getRight());
        }
    }

    /**
     * This is second step of incoming transaction processing. The newly received
     * and validated transactions are stored in {@link Node#receiveQueue}. This function
     * picks up these transaction and stores them into the {@link Tangle} Database. The
     * transaction is then added to the broadcast queue, to be fruther spammed to the neighbors.
     */
    void processReceivedTx(TransactionViewModel receivedTransactionViewModel, Neighbor neighbor) {

        boolean stored = false;

        //store new transaction
        try {
            stored = receivedTransactionViewModel.store(tangle, snapshotProvider.getInitialSnapshot());
        } catch (Exception e) {
            log.error("Error accessing persistence store.", e);
            neighbor.incInvalidTransactions();
        }

        //if new, then broadcast to all neighbors
        if (stored) {
            receivedTransactionViewModel.setArrivalTime(System.currentTimeMillis()/1000L);
            try {
                // TODO: use interfaces
                transactionValidator.checkSolidity(receivedTransactionViewModel.getHash());
                receivedTransactionViewModel.updateSender(neighbor.getAddress().toString());
                receivedTransactionViewModel.update(tangle, snapshotProvider.getInitialSnapshot(), "arrivalTime|sender");
                tangle.publish("vis %s %s %s", receivedTransactionViewModel.getHash(), receivedTransactionViewModel.getTrunkTransactionHash(), receivedTransactionViewModel.getBranchTransactionHash());
            } catch (Exception e) {
                log.error("Error updating transactions.", e);
            }
            //log.trace("Stored_txhash = {}", receivedTransactionViewModel.getHash().toString());
            neighbor.incNewTransactions();
            toBroadcastQueue(receivedTransactionViewModel);

            //EventContext ctx = new EventContext();
            //ctx.put(Key.key("TX", TransactionViewModel.class), receivedTransactionViewModel);
            //EventManager.get().fire(EventType.TX_STORED, ctx);
        }
    }

    /**
     * Handle the hash part of the incoming UDP request.
     */
    private void replyToRequestedHash(Hash requestedHash, Neighbor neighbor) {

        //NULL_HASH indicates a tip request
        if (requestedHash.equals(NULL_HASH)) {
            handleRandomTipRequest(neighbor);
            return;
        }

        //Otherwise it's a full transaction request
        try {
            TransactionViewModel resolvedTx = TransactionViewModel.fromHash(tangle,
                    HashFactory.TRANSACTION.create(requestedHash.bytes(),
                            0, configuration.getRequestHashSize()));
                    //cacheService.get(requestedHash.bytes(), () ->
                    //TransactionViewModel.fromHash(tangle,
                    //    HashFactory.TRANSACTION.create(requestedHash.bytes(),
                    //        0, configuration.getRequestHashSize())));

            if (resolvedTx.getType() == TransactionViewModel.FILLED_SLOT) {
                sendPacketWithTxRequest(resolvedTx, neighbor);
                //cacheService.put(resolvedTx.getBytes(), resolvedTx);

            } else {
                log.trace("Not found the requested hash {}", requestedHash);
                requestQueue.enqueueTransaction(requestedHash, false);
            }

        } catch (Exception e) {
            log.error("Error while handling the request", e);
        }

    }

    private void handleRandomTipRequest(Neighbor neighbor) {
        //Random Tip Request
        try {
            //if (requestQueue.size() == 0) {
            //    log.trace("Empty request queue");
            //return;
            //}

            if (rnd.nextDouble() > configuration.getpReplyRandomTip()) {
                log.trace("Randomly dropped tip request");
                return;
            }

            neighbor.incRandomTransactionRequests();
            TransactionViewModel tip = TransactionViewModel.fromHash(tangle, getRandomTipPointer());
            sendPacketWithTxRequest(tip, neighbor);

        } catch (Exception e) {
            log.error("Error getting random tip.", e);
        }
    }

    private Hash getRandomTipPointer() throws Exception {
        if (rnd.nextDouble() < configuration.getpSendMilestone()) {
            log.trace("Random milestone");
            RoundViewModel latestRound = RoundViewModel.latest(tangle);
            return (latestRound != null) ? latestRound.getRandomMilestone(tangle) : NULL_HASH;
        }

        Hash tip = tipsViewModel.getRandomSolidTipHash();
        return tip == null ? NULL_HASH : tip;
    }

    /**
     * Sends a Datagram to the neighbour. Also appends a random hash request
     * to the outgoing packet. Note that this is only used for UDP handling. For TCP
     * the outgoing packets are sent by ReplicatorSinkProcessor
     *
     * @param {@link DatagramPacket} sendingPacket the UDP payload buffer
     * @param {@link TransactionViewModel} transactionViewModel which should be sent.
     * @praram {@link Neighbor} the neighbor where this should be sent.
     *
     */
    private void sendPacketWithTxRequest(TransactionViewModel transactionViewModel, Neighbor neighbor) throws Exception {
        log.trace("send tx, ngbr {} {}", transactionViewModel.getHash(), neighbor.getAddress().toString());
        //limit amount of sends per second
        long now = System.currentTimeMillis();
        if ((now - sendPacketsTimer.get()) > 1000L) {
            //reset counter every second
            sendPacketsCounter.set(0);
            sendPacketsTimer.set(now);
        }
        if (sendLimit >= 0 && sendPacketsCounter.get() > sendLimit) {
            //if exceeded limit - don't send
            log.debug("Send limit exceeded! Send_packet_counter = {}", sendPacketsCounter.get());
            return;
        }

        Hash hash = Optional.ofNullable(requestQueue.popTransaction()).orElse(transactionViewModel.getHash());
        DatagramPacket toSend = packetFactory.create(new TxPacketData(transactionViewModel, hash));

        neighbor.send(toSend);
        sendPacketsCounter.getAndIncrement();
    }

    private void processBroadcastQueue() {
        final TransactionViewModel transactionViewModel = broadcastQueue.pollFirst();
        if (transactionViewModel != null) {

            for (final Neighbor neighbor : neighbors) {
                try {
                    sendPacketWithTxRequest(transactionViewModel, neighbor);
                } catch (final Exception e) {
                    // ignore
                }
            }
            log.trace("Broadcasted_txhash = {}", transactionViewModel.getHash().toString());
        }
    }


    // TODO should be a separate stats publishing service catching a stats event
    private void reportStats() throws Exception {
        int rcv = receiveQueue.size();
        int brdcst = broadcastQueue.size();
        int rqst = requestQueue.size();
        int reply = replyQueue.size();
        int stored = TransactionViewModel.getNumberOfStoredTransactions(tangle);

        tangle.publish("rstat %d %d %d %d %d",
                rcv, brdcst, rqst, reply, stored);
        log.info("toProcess = {} , toBroadcast = {} , toRequest = {} , toReply = {} / totalTransactions = {}",
                rcv, brdcst, rqst, reply, stored);
    }

    private static ConcurrentSkipListSet<TransactionViewModel> weightQueue() {
        return new ConcurrentSkipListSet<>((transaction1, transaction2) -> {
            if (transaction1.weightMagnitude == transaction2.weightMagnitude) {
                for (int i = Hash.SIZE_IN_BYTES; i-- > 0; ) {
                    if (transaction1.getHash().bytes()[i] != transaction2.getHash().bytes()[i]) {
                        return transaction2.getHash().bytes()[i] - transaction1.getHash().bytes()[i];
                    }
                }
                return 0;
            }
            return transaction2.weightMagnitude - transaction1.weightMagnitude;
        });
    }

    //TODO generalize these weightQueues
    private static ConcurrentSkipListSet<Pair<Hash, Neighbor>> weightQueueHashPair() {
        return new ConcurrentSkipListSet<Pair<Hash, Neighbor>>((transaction1, transaction2) -> {
            Hash tx1 = transaction1.getLeft();
            Hash tx2 = transaction2.getLeft();

            for (int i = Hash.SIZE_IN_BYTES; i-- > 0; ) {
                if (tx1.bytes()[i] != tx2.bytes()[i]) {
                    return tx2.bytes()[i] - tx1.bytes()[i];
                }
            }
            return 0;

        });
    }

    private static ConcurrentSkipListSet<Pair<TransactionViewModel, Neighbor>> weightQueueTxPair() {
        return new ConcurrentSkipListSet<Pair<TransactionViewModel, Neighbor>>((transaction1, transaction2) -> {
            TransactionViewModel tx1 = transaction1.getLeft();
            TransactionViewModel tx2 = transaction2.getLeft();

            if (tx1.weightMagnitude == tx2.weightMagnitude) {
                for (int i = Hash.SIZE_IN_BYTES; i-- > 0; ) {
                    if (tx1.getHash().bytes()[i] != tx2.getHash().bytes()[i]) {
                        return tx2.getHash().bytes()[i] - tx1.getHash().bytes()[i];
                    }
                }
                return 0;
            }
            return tx2.weightMagnitude - tx1.weightMagnitude;
        });
    }


    public void toBroadcastQueue(final TransactionViewModel transactionViewModel) {
        broadcastQueue.add(transactionViewModel);
        if (broadcastQueue.size() > BROADCAST_QUEUE_SIZE) {
            log.trace("The broadcast queue exceeded its size {}", BROADCAST_QUEUE_SIZE);
            broadcastQueue.pollLast();
        }
    }

    public void shutdown() throws InterruptedException {
        shuttingDown.set(true);
        udpReceiver.shutdown();

        udpReceiver.awaitTermination(6, TimeUnit.SECONDS);
        scheduler.awaitTermination(6, TimeUnit.SECONDS);
    }

    private ByteBuffer getBytesDigest(byte[] receivedData) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(receivedData, 0, TransactionViewModel.SIZE);
        return ByteBuffer.wrap(digest.digest());
    }

    // helpers methods

    public boolean removeNeighbor(final URI uri, boolean isConfigured) {
        final Neighbor neighbor = newNeighbor(uri, isConfigured);
        if (uri.getScheme().equals("tcp")) {
            neighbors.stream().filter(n -> n instanceof TCPNeighbor)
                    .map(n -> ((TCPNeighbor) n))
                    .filter(n -> n.equals(neighbor))
                    .forEach(TCPNeighbor::clear);
        }
        return neighbors.remove(neighbor);
    }

    public boolean addNeighbor(Neighbor neighbor) {
        return !getNeighbors().contains(neighbor) && getNeighbors().add(neighbor);
    }

    public boolean isUriValid(final URI uri) {
        if (uri != null) {
            if ((uri.getScheme().equals("tcp") || uri.getScheme().equals("udp")) && (new InetSocketAddress(uri.getHost(), uri.getPort()).getAddress() != null)) {
                return true;
            }
            log.error("'{}' is not a valid uri schema or resolvable address.", uri);
            return false;
        }
        log.error("Cannot read uri schema, please check neighbor config!");
        return false;
    }

    public Neighbor newNeighbor(final URI uri, boolean isConfigured) {
        if (isUriValid(uri)) {
            if (uri.getScheme().equals("tcp")) {
                return new TCPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()), isConfigured);
            }
            if (uri.getScheme().equals("udp")) {
                return new UDPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()), udpSocket, isConfigured);
            }
        }
        throw new IllegalArgumentException(uri.toString());
    }

    public static Optional<URI> uri(final String uri) {
        try {
            return Optional.of(new URI(uri));
        } catch (URISyntaxException e) {
            log.error("Uri {} raised URI Syntax Exception", uri);
        }
        return Optional.empty();
    }

    private void parseNeighborsConfig() {
        configuration.getNeighbors().stream().distinct()
                .filter(s -> !s.isEmpty())
                .map(Node::uri).map(Optional::get)
                .filter(u -> isUriValid(u))
                .map(u -> newNeighbor(u, true))
                .peek(u -> {
                    log.info("-> Adding neighbor : {} ", u.getAddress());
                    tangle.publish("-> Adding Neighbor : %s", u.getAddress());
                }).forEach(neighbors::add);
    }

    // TODO should be read off the stats server
    public int broadcastQueueSize() {
        return broadcastQueue.size();
    }

    public int howManyNeighbors() {
        return getNeighbors().size();
    }

    public List<Neighbor> getNeighbors() {
        return neighbors;
    }


    /**
     * Creates a background worker that tries to work through the request queue by sending random tips along the requested
     * transactions.<br />
     * <br />
     * This massively increases the sync speed of new nodes that would otherwise be limited to requesting in the same rate
     * as new transactions are received.<br />
     */
    public interface TipBroadcasterWorker extends Pendulum.Initializable {
        int REQUESTER_THREAD_ACTIVATION_THRESHOLD = PendulumUtils.getSystemProp("tip.requester.activation.threshold", 5);
        /**
         * Works through the request queue by sending a request alongside a random tip to each of our neighbors.<br />
         *
        * @return <code>TransactionViewModel</code> when we have send the request to our neighbors, otherwise <code>null</code>
         */
        TransactionViewModel tipToBroadcast();

    }

    /**
     * This interface encapsulates the queue of transactions used
     * by the requester thread. The clients should use
     * <code>enqueueTransaction()</code> in order to place the required
     * transaction <code>Hash</code> into the queue.
     *
     * Access to the service is thread-safe.
     *
     * Date: 2019-11-05
     * Author: zhelezov
     */
    public interface RequestQueue extends Pendulum.Initializable {
        Hash[] getRequestedTransactions();

        int size();

        boolean clearTransactionRequest(Hash hash);

        void enqueueTransaction(Hash hash, boolean milestone);

        boolean isTransactionRequested(Hash transactionHash, boolean milestoneRequest);

        /**
         * Pops the transaction from the queue and place at the end
         * of the queue in a cyclyc manner, until the transaction hash is resolved
         *
         * @return Hash from the top of the queue which is needed to be requested
         */
        Hash popTransaction();
    }
}
