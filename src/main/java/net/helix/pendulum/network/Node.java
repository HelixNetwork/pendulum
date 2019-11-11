package net.helix.pendulum.network;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.helix.pendulum.Pendulum;
import net.helix.pendulum.TransactionValidator;
import net.helix.pendulum.conf.NodeConfig;
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
import net.helix.pendulum.network.impl.TipRequesterWorkerImpl;
import net.helix.pendulum.network.impl.TxPacketData;
import net.helix.pendulum.service.milestone.MilestoneTracker;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.storage.Tangle;
import org.apache.commons.lang3.StringUtils;
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
public class Node implements PendulumEventListener {

    private static final Logger log = LoggerFactory.getLogger(Node.class);

    private int BROADCAST_QUEUE_SIZE;
    private int RECV_QUEUE_SIZE;
    private int REPLY_QUEUE_SIZE;
    private static final int PAUSE_BETWEEN_TRANSACTIONS = 1;

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private final List<Neighbor> neighbors = new CopyOnWriteArrayList<>();

    private final ConcurrentSkipListSet<TransactionViewModel> broadcastQueue = weightQueue();
    private final ConcurrentSkipListSet<Pair<TransactionViewModel, Neighbor>> receiveQueue = weightQueueTxPair();
    private final ConcurrentSkipListSet<Pair<Hash, Neighbor>> replyQueue = weightQueueHashPair();
    private final RequestQueue requestQueue;

    private DatagramFactory packetFactory = new DatagramFactoryImpl();
    //private final DatagramPacket sendingPacket;
    //private final DatagramPacket tipRequestingPacket;

    private final ExecutorService executor = Executors.newFixedThreadPool(5);
    private final TipRequesterWorker tipRequesterWorker;

    private final NodeConfig configuration;
    private final Tangle tangle;
    private final SnapshotProvider snapshotProvider;
    private final TipsViewModel tipsViewModel;
    private final TransactionValidator transactionValidator;
    private Cache<byte[], TransactionViewModel> cacheService;

    private static final SecureRandom rnd = new SecureRandom();


    //private FIFOCache<ByteBuffer, Hash> recentSeenBytes;

    //private static AtomicLong recentSeenBytesMissCount = new AtomicLong(0L);
    //private static AtomicLong recentSeenBytesHitCount = new AtomicLong(0L);

    private static long sendLimit = -1;
    private static AtomicLong sendPacketsCounter = new AtomicLong(0L);
    private static AtomicLong sendPacketsTimer = new AtomicLong(0L);

    public static final ConcurrentSkipListSet<String> rejectedAddresses = new ConcurrentSkipListSet<String>();
    private DatagramSocket udpSocket;

    private final int PROCESSOR_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors() * 4 );
    private final ExecutorService udpReceiver = Executors.newFixedThreadPool(PROCESSOR_THREADS);


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
    public Node(final Tangle tangle, SnapshotProvider snapshotProvider, final TransactionValidator transactionValidator, final TipsViewModel tipsViewModel, final MilestoneTracker milestoneTracker, final NodeConfig configuration
    ) {
        this.configuration = configuration;
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider ;
        this.transactionValidator = transactionValidator;

        this.tipsViewModel = tipsViewModel;
        //this.reqHashSize = configuration.getRequestHashSize();
        //int packetSize = configuration.getTransactionPacketSize();
        //this.sendingPacket = new DatagramPacket(new byte[packetSize], packetSize);
        //this.tipRequestingPacket = new DatagramPacket(new byte[packetSize], packetSize);

        this.tipRequesterWorker = new TipRequesterWorkerImpl();
        this.requestQueue = new RequestQueueImpl();

        Pendulum.ServiceRegistry.get().register(RequestQueue.class, requestQueue);

        EventManager.get().subscribe(EventType.NEW_BYTES_RECEIVED, this);
        EventManager.get().subscribe(EventType.REQUEST_TIP_TX, this);
    }

    /**
     * Intialize the operations by spawning all the worker threads.
     *
     */
    public void init()  {
        // default to 800 if not properly set
        int txPacketSize = configuration.getTransactionPacketSize() > 0
                ? configuration.getTransactionPacketSize() : 800;
        sendLimit = (long) ((configuration.getSendLimit() * 1000000) / txPacketSize);

        BROADCAST_QUEUE_SIZE = RECV_QUEUE_SIZE = REPLY_QUEUE_SIZE = configuration.getqSizeNode();
        //recentSeenBytes = new FIFOCache<>(configuration.getCacheSizeBytes(), configuration.getpDropCacheEntry());

        cacheService = CacheBuilder.newBuilder()
                .maximumSize(configuration.getCacheSizeBytes() / txPacketSize)
                .build();

        parseNeighborsConfig();

        packetFactory.init();
        requestQueue.init();
        tipRequesterWorker.init();

        initialized.set(true);

    }

    public void start() {
        if (!initialized.get()) {
            throw new IllegalStateException("Node is not initialized");
        }

        log.debug("Starting a Pendulum node...");
        tipRequesterWorker.start();
        executor.submit(spawnBroadcasterThread());
        executor.submit(spawnTipRequesterThread());
        executor.submit(spawnNeighborDNSRefresherThread());
        executor.submit(spawnProcessReceivedThread());
        executor.submit(spawnReplyToRequestThread());

        executor.shutdown();
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

    /**
     * One of the problem of dynamic DNS is neighbor could reconnect and get assigned
     * a new IP address. This thread periodically resovles the DNS to make sure
     * the IP is updated in the quickest possible manner. Doing it fast will increase
     * the detection of change - however will generate lot of unnecessary DNS outbound
     * traffic - so a balance is sought between speed and resource utilization.
     *
     */
    Runnable spawnNeighborDNSRefresherThread() {
        return () -> {
            if (configuration.isDnsResolutionEnabled()) {
                log.info("Spawning Neighbor DNS Refresher Thread");

                while (!shuttingDown.get()) {
                    int dnsCounter = 0;
                    log.info("Checking Neighbors' Ip...");

                    try {
                        neighbors.forEach(this::checkDNS);

                        while (dnsCounter++ < 60 * 30 && !shuttingDown.get()) {
                            Thread.sleep(1000);
                        }
                    } catch (final Exception e) {
                        log.error("Neighbor DNS Refresher Thread Exception:", e);
                    }
                }
                log.info("Shutting down Neighbor DNS Refresher Thread");
            } else {
                log.info("Ignoring DNS Refresher Thread... DNS_RESOLUTION_ENABLED is false");
            }
        };
    }

    private void checkDNS(Neighbor n) {
        final String hostname = n.getAddress().getHostName();
        checkIp(hostname).ifPresent(ip -> {
            log.info("DNS Checker: Validating DNS Address '{}' with '{}'", hostname, ip);
            tangle.publish("dnscv %s %s", hostname, ip);
            final String neighborAddress = neighborIpCache.get(hostname);

            if (neighborAddress == null) {
                neighborIpCache.put(hostname, ip);
            } else {
                if (neighborAddress.equals(ip)) {
                    log.info("{} seems fine.", hostname);
                    tangle.publish("dnscc %s", hostname);
                } else {
                    if (configuration.isDnsRefresherEnabled()) {
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
                    } else {
                        log.info("IP CHANGED for {}! Skipping... DNS_REFRESHER_ENABLED is false.", hostname);
                    }
                }
            }
        });
    }

    /**
     * Checks whether the passed DNS is an IP address in string form or a DNS
     * hostname.
     *
     * @return An IP address (decimal form) in string resolved from the given DNS
     *
     */
    private Optional<String> checkIp(final String dnsName) {

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
                byte[] bytes = ctx.get(Key.key("BYTES", byte[].class));
                SocketAddress address = ctx.get(Key.key("SENDER", SocketAddress.class));
                String uriScheme = ctx.get(Key.key("URI_SCHEME", String.class));
                udpReceiver.submit(() -> preProcessReceivedData(bytes.clone(), address, uriScheme));
                break;

            case REQUEST_TIP_TX:
                TransactionViewModel tx = ctx.get(Key.key("TX", TransactionViewModel.class));
                toBroadcastQueue(tx);

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

        if (receivedTx != null) {

            log.trace("Received_txvm / sender / isMilestone = {} {} {}",
                    receivedTx.getHash().toString(),
                    neighbor.getAddress().toString(),
                    receivedTx.isMilestone());

            prepareReply(receivedData, neighbor, receivedTx.getHash());
            addTxToReceiveQueue(receivedTx, neighbor);
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

            return cacheService.get(receivedData, () -> doPreValidation(receivedData));

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

    private void prepareReply(byte[] receivedData, Neighbor neighbor, Hash receivedTransactionHash) {
        Hash requestedHash = HashFactory.TRANSACTION.create(receivedData,
                TransactionViewModel.SIZE,
                configuration.getRequestHashSize());

        if (requestedHash.equals(receivedTransactionHash)) {
            //requesting a random tip
            log.trace("Requesting random tip from {}", neighbor.getAddress().toString());
            requestedHash = Hash.NULL_HASH;
        }

        toReplyQueue(requestedHash, neighbor);
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
     * {@link Node#replyToRequest} on the pair.
     */
    private void replyToRequestFromQueue() {
        final Pair<Hash, Neighbor> receivedData = replyQueue.pollFirst();
        if (receivedData != null) {
            replyToRequest(receivedData.getLeft(), receivedData.getRight());
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
                transactionValidator.updateStatus(receivedTransactionViewModel);
                receivedTransactionViewModel.updateSender(neighbor.getAddress().toString());
                receivedTransactionViewModel.update(tangle, snapshotProvider.getInitialSnapshot(), "arrivalTime|sender");
                tangle.publish("vis %s %s %s", receivedTransactionViewModel.getHash(), receivedTransactionViewModel.getTrunkTransactionHash(), receivedTransactionViewModel.getBranchTransactionHash());
            } catch (Exception e) {
                log.error("Error updating transactions.", e);
            }
            log.trace("Stored_txhash = {}", receivedTransactionViewModel.getHash().toString());
            neighbor.incNewTransactions();
            toBroadcastQueue(receivedTransactionViewModel);

            EventContext ctx = new EventContext();
            ctx.put(Key.key("TX", TransactionViewModel.class), receivedTransactionViewModel);
            EventManager.get().fire(EventType.TX_STORED, ctx);
        }
    }

    /**
     * This is second step of incoming transaction processing. The newly received
     * and validated transactions are stored in {@link Node#receiveQueue}. This function
     * picks up these transaction and stores them into the {@link Tangle} Database. The
     * transaction is then added to the broadcast queue, to be fruther spammed to the neighbors.
     */
    private void replyToRequest(Hash requestedHash, Neighbor neighbor) {

        //NULL_HASH indicates a tip request
        if (requestedHash.equals(Hash.NULL_HASH)) {
            handleRandomTipRequest(neighbor);
            return;
        }

        //Otherwise it's a full transaction request
        try {
            TransactionViewModel resolvedTx = cacheService.get(requestedHash.bytes(), () ->
                    TransactionViewModel.fromHash(tangle,
                        HashFactory.TRANSACTION.create(requestedHash.bytes(),
                            0, configuration.getRequestHashSize())));

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

            if (rnd.nextDouble() < configuration.getpReplyRandomTip()) {
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
            return (latestRound != null) ? latestRound.getRandomMilestone(tangle) : Hash.NULL_HASH;
        }

        Hash tip = tipsViewModel.getRandomSolidTipHash();
        return tip == null ? Hash.NULL_HASH : tip;
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
        log.trace("send tx {} to {}", transactionViewModel.getHash(), neighbor.getAddress().toString());
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


    /**
     * This thread picks up a new transaction from the broadcast queue and
     * spams it to all of the neigbors. Sadly, this also includes the neigbor who
     * originally sent us the transaction. This could be improved in future.
     *
     */
    private Runnable spawnBroadcasterThread() {
        return () -> {

            log.info("Spawning Broadcaster Thread");

            while (!shuttingDown.get()) {

                try {
                    processBroadcastQueue();
                    Thread.sleep(PAUSE_BETWEEN_TRANSACTIONS);
                } catch (final Exception e) {
                    log.error("Broadcaster Thread Exception:", e);
                }
            }
            log.info("Shutting down Broadcaster Thread");
        };
    }

    private void processBroadcastQueue() {
        final TransactionViewModel transactionViewModel = broadcastQueue.pollFirst();
        if (transactionViewModel != null) {

            for (final Neighbor neighbor : neighbors) {
                try {
                    sendPacketWithTxRequest(transactionViewModel, neighbor);
                    log.trace("Broadcasted_txhash = {}", transactionViewModel.getHash().toString());
                } catch (final Exception e) {
                    // ignore
                }
            }
        }
    }

    /**
     * We send a tip request packet (transaction corresponding to the latest milestone)
     * to all of our neighbors periodically.
     */
    private Runnable spawnTipRequesterThread() {
        return () -> {

            log.info("Spawning Tips Requester Thread");
            long lastTime = 0;
            while (!shuttingDown.get()) {

                try {

                    // 0-filled packet seems to be interpreted as a milestone request
                    DatagramPacket nullPacket = packetFactory.create(TxPacketData.NULL_HASH_DATA);
                    neighbors.forEach(n -> n.send(nullPacket));

                    long now = System.currentTimeMillis();
                    if ((now - lastTime) > 10000L) {
                        lastTime = now;
                        tangle.publish("rstat %d %d %d %d %d",
                                getReceiveQueueSize(), getBroadcastQueueSize(),
                                requestQueue.size(), getReplyQueueSize(),
                                TransactionViewModel.getNumberOfStoredTransactions(tangle));
                        log.info("toProcess = {} , toBroadcast = {} , toRequest = {} , toReply = {} / totalTransactions = {}",
                                getReceiveQueueSize(), getBroadcastQueueSize(),
                                requestQueue.size(), getReplyQueueSize(),
                                TransactionViewModel.getNumberOfStoredTransactions(tangle));
                    }

                    Thread.sleep(5000);
                } catch (final Exception e) {
                    log.error("Tips Requester Thread Exception:", e);
                }
            }
            log.info("Shutting down Requester Thread");
        };
    }

    private Runnable spawnProcessReceivedThread() {
        return () -> {

            log.info("Spawning Process Received Data Thread");

            while (!shuttingDown.get()) {

                try {
                    processReceivedTxQueue();
                    Thread.sleep(1);
                } catch (final Exception e) {
                    log.error("Process Received Data Thread Exception:", e);
                }
            }
            log.info("Shutting down Process Received Data Thread");
        };
    }

    private Runnable spawnReplyToRequestThread() {
        return () -> {

            log.info("Spawning Reply To Request Thread");

            while (!shuttingDown.get()) {

                try {
                    replyToRequestFromQueue();
                    Thread.sleep(1);
                } catch (final Exception e) {
                    log.error("Reply To Request Thread Exception:", e);
                }
            }
            log.info("Shutting down Reply To Request Thread");
        };
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
        tipRequesterWorker.shutdown();
        udpReceiver.awaitTermination(6, TimeUnit.SECONDS);
        executor.awaitTermination(6, TimeUnit.SECONDS);
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

    public int queuedTransactionsSize() {
        return broadcastQueue.size();
    }

    public int howManyNeighbors() {
        return getNeighbors().size();
    }

    public List<Neighbor> getNeighbors() {
        return neighbors;
    }

    public int getBroadcastQueueSize() {
        return broadcastQueue.size();
    }

    public int getReceiveQueueSize() {
        return receiveQueue.size();
    }

    public int getReplyQueueSize() {
        return replyQueue.size();
    }

    /**
     * Creates a background worker that tries to work through the request queue by sending random tips along the requested
     * transactions.<br />
     * <br />
     * This massively increases the sync speed of new nodes that would otherwise be limited to requesting in the same rate
     * as new transactions are received.<br />
     */
    public interface TipRequesterWorker extends Pendulum.Initializable {
        /**
         * Works through the request queue by sending a request alongside a random tip to each of our neighbors.<br />
         *
        * @return <code>true</code> when we have send the request to our neighbors, otherwise <code>false</code>
         */
        boolean processRequestQueue();

        /**
         * Starts the background worker that automatically calls {@link #processRequestQueue()} periodically to process the
         * requests in the queue.<br />
         */
        void start();

        /**
         * Stops the background worker that automatically works through the request queue.<br />
         */
        void shutdown();
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
