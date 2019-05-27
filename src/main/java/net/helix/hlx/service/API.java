package net.helix.hlx.service;

import com.google.gson.JsonSyntaxException;
import net.helix.hlx.*;
import net.helix.hlx.HLX;
import net.helix.hlx.conf.APIConfig;
import net.helix.hlx.conf.HelixConfig;
import net.helix.hlx.controllers.*;
import net.helix.hlx.crypto.*;
import net.helix.hlx.model.Hash;
import net.helix.hlx.model.HashFactory;
import net.helix.hlx.model.persistables.Transaction;
import net.helix.hlx.network.Neighbor;
import net.helix.hlx.network.Node;
import net.helix.hlx.network.TransactionRequester;
import net.helix.hlx.service.dto.*;
import net.helix.hlx.service.ledger.LedgerService;
import net.helix.hlx.service.milestone.LatestMilestoneTracker;
import net.helix.hlx.service.restserver.RestConnector;
import net.helix.hlx.service.snapshot.SnapshotProvider;
import net.helix.hlx.service.spentaddresses.SpentAddressesService;
import net.helix.hlx.service.tipselection.TipSelector;
import net.helix.hlx.service.tipselection.impl.WalkValidatorImpl;
import net.helix.hlx.storage.Tangle;
import net.helix.hlx.utils.Serializer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.IntStream;

/**
 * <p>
 *   The API makes it possible to interact with the node by requesting information or actions to be taken.
 *   You can interact with it by passing a JSON object which at least contains a <tt>command</tt>.
 *   Upon successful execution of the command, the API returns your requested information in an {@link AbstractResponse}.
 * </p>
 * <p>
 *   If the request is invalid, an {@link ErrorResponse} is returned.
 *   This, for example, happens when the command does not exist or there is no command section at all.
 *   If there is an error in the given data during the execution of a command, an {@link ErrorResponse} is also sent.
 * </p>
 * <p>
 *   If an Exception is thrown during the execution of a command, an {@link ExceptionResponse} is returned.
 * </p>
 */
@SuppressWarnings("unchecked")
public class API {

    private static final Logger log = LoggerFactory.getLogger(API.class);

    //region [CONSTANTS] ///////////////////////////////////////////////////////////////////////////////

    public static final String REFERENCE_TRANSACTION_NOT_FOUND = "reference transaction not found";
    public static final String REFERENCE_TRANSACTION_TOO_OLD = "reference transaction is too old";

    public static final String INVALID_SUBTANGLE = "This operation cannot be executed: "
                                                 + "The subtangle has not been updated yet.";

    private static final String OVER_MAX_ERROR_MESSAGE = "Could not complete request";
    private static final String INVALID_PARAMS = "Invalid parameters";

    private final static char ZERO_LENGTH_ALLOWED = 'Y';
    private final static char ZERO_LENGTH_NOT_ALLOWED = 'N';

    private final static int HASH_SIZE = 32;
    private final static int BYTES_SIZE = 768;

    private final static long MAX_TIMESTAMP_VALUE = (long) (Math.pow(2, 8) - 1) / 2; // max positive 8 byte value

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static int counterGetTxToApprove = 0;
    private static long ellapsedTime_getTxToApprove = 0L;
    private static int counter_PoW = 0;
    private static long ellapsedTime_PoW = 0L;

    //region [CONSTRUCTOR_FIELDS] ///////////////////////////////////////////////////////////////////////////////

    private final HelixConfig configuration;
    private final HXI hxi;
    private final TransactionRequester transactionRequester;
    private final SpentAddressesService spentAddressesService;
    private final Tangle tangle;
    private final BundleValidator bundleValidator;
    private final SnapshotProvider snapshotProvider;
    private final LedgerService ledgerService;
    private final Node node;
    private final TipSelector tipsSelector;
    private final TipsViewModel tipsViewModel;
    private final TransactionValidator transactionValidator;
    private final LatestMilestoneTracker latestMilestoneTracker;
    private final Graphstream graph;

    private final int maxFindTxs;
    private final int maxRequestList;
    private final int maxGetBytes;

    private final String[] features;

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    private final Gson gson = new GsonBuilder().create();
    private GreedyMiner miner = new GreedyMiner();

    private final AtomicInteger counter = new AtomicInteger(0);
    private Pattern hexPattern = Pattern.compile("[0-9a-f]*");

    private final int milestoneStartIndex;

    final Map<ApiCommand, Function<Map<String, Object>, AbstractResponse>> commandRoute;
    private RestConnector connector;

    /**
     * Starts loading the Helix API, parameters do not have to be initialized.
     *
     * @param configuration configuration
     * @param hxi If a command is not in the standard API,
     *            we try to process it as a Nashorn JavaScript module through {@link HXI}
     * @param transactionRequester Service where transactions get requested
     * @param spentAddressesService Service to check if addresses are spent
     * @param tangle The transaction storage
     * @param bundleValidator Validates bundles
     * @param snapshotProvider Manager of our currently taken snapshots
     * @param ledgerService contains all the relevant business logic for modifying and calculating the ledger state.
     * @param node Handles and manages neighbors
     * @param tipsSelector Handles logic for selecting tips based on other transactions
     * @param tipsViewModel Contains the current tips of this node
     * @param transactionValidator Validates transactions
     * @param latestMilestoneTracker Service that tracks the latest milestone
     */
    public API(HelixConfig configuration, HXI hxi, TransactionRequester transactionRequester,
               SpentAddressesService spentAddressesService, Tangle tangle, BundleValidator bundleValidator,
               SnapshotProvider snapshotProvider, LedgerService ledgerService, Node node, TipSelector tipsSelector,
               TipsViewModel tipsViewModel, TransactionValidator transactionValidator,
               LatestMilestoneTracker latestMilestoneTracker, Graphstream graph) {
        this.configuration = configuration;
        this.hxi = hxi;

        this.transactionRequester = transactionRequester;
        this.spentAddressesService = spentAddressesService;
        this.tangle = tangle;
        this.bundleValidator = bundleValidator;
        this.snapshotProvider = snapshotProvider;
        this.ledgerService = ledgerService;
        this.node = node;
        this.tipsSelector = tipsSelector;
        this.tipsViewModel = tipsViewModel;
        this.transactionValidator = transactionValidator;
        this.latestMilestoneTracker = latestMilestoneTracker;
        this.graph = graph;

        maxFindTxs = configuration.getMaxFindTransactions();
        maxRequestList = configuration.getMaxRequestsList();
        maxGetBytes = configuration.getMaxBytes();
        milestoneStartIndex = configuration.getMilestoneStartIndex();

        features = Feature.calculateFeatureNames(configuration);

        commandRoute = new HashMap<>();
        commandRoute.put(ApiCommand.ADD_NEIGHBORS, addNeighbors());
        commandRoute.put(ApiCommand.ATTACH_TO_TANGLE, attachToTangle());
        commandRoute.put(ApiCommand.BROADCAST_TRANSACTIONS, broadcastTransactions());
        commandRoute.put(ApiCommand.FIND_TRANSACTIONS, findTransactions());
        commandRoute.put(ApiCommand.GET_BALANCES, getBalances());
        commandRoute.put(ApiCommand.GET_INCLUSION_STATES, getInclusionStates());
        commandRoute.put(ApiCommand.GET_NEIGHBORS, getNeighbors());
        commandRoute.put(ApiCommand.GET_NODE_INFO, getNodeInfo());
        commandRoute.put(ApiCommand.GET_NODE_API_CONFIG, getNodeAPIConfiguration());
        commandRoute.put(ApiCommand.GET_TIPS, getTips());
        commandRoute.put(ApiCommand.GET_TRANSACTIONS_TO_APPROVE, getTransactionsToApprove());
        commandRoute.put(ApiCommand.GET_TRYTES, getHBytes());
        commandRoute.put(ApiCommand.INTERRUPT_ATTACHING_TO_TANGLE, interruptAttachingToTangle());
        commandRoute.put(ApiCommand.REMOVE_NEIGHBORS, removeNeighbors());
        commandRoute.put(ApiCommand.STORE_TRANSACTIONS, storeTransactions());
        commandRoute.put(ApiCommand.GET_MISSING_TRANSACTIONS, getMissingTransactions());
        commandRoute.put(ApiCommand.CHECK_CONSISTENCY, checkConsistency());
        commandRoute.put(ApiCommand.WERE_ADDRESSES_SPENT_FROM, wereAddressesSpentFrom());
    }

    /**
     * Initializes the API for usage.
     * Will initialize and start the supplied {@link RestConnector}
     *
     * @param connector THe connector we use to handle API requests
     */
    public void init(RestConnector connector){
        this.connector = connector;
        connector.init(this::process);
        connector.start();
    }

    /**
     * Handles an API request body.
     * Its returned {@link AbstractResponse} is created using the following logic
     * <ul>
     *     <li>
     *         {@link ExceptionResponse} if the body cannot be parsed.
     *     </li>
     *     <li>
     *         {@link ErrorResponse} if the body does not contain a '<tt>command</tt>' section.
     *     </li>
     *     <li>
     *         {@link AccessLimitedResponse} if the command is not allowed on this node.
     *     </li>
     *     <li>
     *         {@link ErrorResponse} if the command contains invalid parameters.
     *     </li>
     *     <li>
     *         {@link ExceptionResponse} if we encountered an unexpected exception during command processing.
     *     </li>
     *     <li>
     *         {@link AbstractResponse} when the command is successfully processed.
     *         The response class depends on the command executed.
     *     </li>
     * </ul>
     *
     * @param requestString The JSON encoded data of the request.
     *                      This String is attempted to be converted into a {@code Map<String, Object>}.
     * @param netAddress The address from the sender of this API request.
     * @return The result of this request.
     */
    private AbstractResponse process(final String requestString, InetAddress netAddress) {

        try {
            // Request JSON data into map
            Map<String, Object> request;
            try {
                request = gson.fromJson(requestString, Map.class);
            }
            catch(JsonSyntaxException jsonSyntaxException) {
                return ErrorResponse.create("Invalid JSON syntax: " + jsonSyntaxException.getMessage());
            }

            if (request == null) {
            return ExceptionResponse.create("Invalid request payload: '" + requestString + "'");
            }

            // Did the requester ask for a command?
            final String command = (String) request.get("command");
            if (command == null) {
                return ErrorResponse.create("COMMAND parameter has not been specified in the request.");
            }

            // Is this command allowed to be run from this request address?
            // We check the remote limit API configuration.
            if (configuration.getRemoteLimitApi().contains(command) && !configuration.getRemoteTrustedApiHosts().contains(netAddress)) {
                return AccessLimitedResponse.create("COMMAND " + command + " is not available on this node");
            }

            log.debug("# {} -> Requesting command '{}'", counter.incrementAndGet(), command);

            ApiCommand apiCommand = ApiCommand.findByName(command);
            if (apiCommand != null) {
                return commandRoute.get(apiCommand).apply(request);
            } else {
                AbstractResponse response = hxi.processCommand(command, request);
                if (response == null) {
                    return ErrorResponse.create("Command [" + command + "] is unknown");
                } else {
                    return response;
                }
            }
        } catch (ValidationException e) {
            log.error("API Validation failed: " + e.getLocalizedMessage());
            return ExceptionResponse.create(e.getLocalizedMessage());
        } catch (IllegalStateException e) {
            log.error("API Exception: " + e.getLocalizedMessage());
            return ExceptionResponse.create(e.getLocalizedMessage());
        } catch (RuntimeException e) {
            log.error("Unexpected API Exception: " + e.getLocalizedMessage());
            return ExceptionResponse.create(e.getLocalizedMessage());
        }
    }

    /**
     * Check if a list of addresses was ever spent from, in the current epoch, or in previous epochs.
     * If an address has a pending transaction, it is also marked as spend.
     *
     * @param addresses List of addresses to check if they were ever spent from.
     * @return {@link net.helix.hlx.service.dto.WereAddressesSpentFrom}
     **/
    private AbstractResponse wereAddressesSpentFromStatement(List<String> addresses) throws Exception {
        final List<Hash> addressesHash = addresses.stream()
                .map(HashFactory.ADDRESS::create)
                .collect(Collectors.toList());

        final boolean[] states = new boolean[addressesHash.size()];
        int index = 0;

        for (Hash address : addressesHash) {
            states[index++] = spentAddressesService.wasAddressSpentFrom(address);
        }
        return WereAddressesSpentFrom.create(states);
    }

    /**
     * Walks back from the hash until a tail transaction has been found or transaction aprovee is not found.
     * A tail transaction is the first transaction in a bundle, thus with <code>index = 0</code>
     *
     * @param hash The transaction hash where we start the search from. If this is a tail, its hash is returned.
     * @return The transaction hash of the tail
     * @throws Exception When a model could not be loaded.
     */
    private Hash findTail(Hash hash) throws Exception {
        TransactionViewModel tx = TransactionViewModel.fromHash(tangle, hash);
        final Hash bundleHash = tx.getBundleHash();
        long index = tx.getCurrentIndex();
        boolean foundApprovee = false;

        // As long as the index is bigger than 0 and we are still traversing the same bundle
        // If the hash we asked about is already a tail, this loop never starts
        while (index-- > 0 && tx.getBundleHash().equals(bundleHash)) {
            Set<Hash> approvees = tx.getApprovers(tangle).getHashes();
            for (Hash approvee : approvees) {
                TransactionViewModel nextTx = TransactionViewModel.fromHash(tangle, approvee);
                if (nextTx.getBundleHash().equals(bundleHash)) {
                    tx = nextTx;
                    foundApprovee = true;
                    break;
                }
            }
            if (!foundApprovee) {
                break;
            }
        }
        if (tx.getCurrentIndex() == 0) {
            return tx.getHash();
        }
        return null;
    }

    /**
     *
     * Checks the consistency of the transactions.
     * Marks state as false on the following checks:
     * <ul>
     *     <li>Missing a reference transaction</li>
     *     <li>Invalid bundle</li>
     *     <li>Tails of tails are invalid</li>
     * </ul>
     *
     * If a transaction does not exist, or it is not a tail, an {@link ErrorResponse} is returned.
     *
     * @param transactionsList Transactions you want to check the consistency for
     * @return {@link CheckConsistency}
     **/
    private AbstractResponse checkConsistencyStatement(List<String> transactionsList) throws Exception {
        final List<Hash> transactions = transactionsList.stream().map(HashFactory.TRANSACTION::create).collect(Collectors.toList());
        boolean state = true;
        String info = "";

        // Check if the transactions themselves are valid
        for (Hash transaction : transactions) {
            TransactionViewModel txVM = TransactionViewModel.fromHash(tangle, transaction);
            if (txVM.getType() == TransactionViewModel.PREFILLED_SLOT) {
                return ErrorResponse.create("Invalid transaction, missing: " + transaction);
            }
            if (txVM.getCurrentIndex() != 0) {
                return ErrorResponse.create("Invalid transaction, not a tail: " + transaction);
            }


            if (!txVM.isSolid()) {
                state = false;
                info = "tails are not solid (missing a referenced tx): " + transaction;
                break;
            // TODO: When validate isn't static anymore, change to bundleValidator.validate().
            } else if (BundleValidator.validate(tangle, snapshotProvider.getInitialSnapshot(), txVM.getHash()).size() == 0) {
                state = false;
                info = "tails are not consistent (bundle is invalid): " + transaction;
                break;
            }
        }

        // Transactions are valid, lets check ledger consistency
        if (state) {
            snapshotProvider.getLatestSnapshot().lockRead();
            try {
                WalkValidatorImpl walkValidator = new WalkValidatorImpl(tangle, snapshotProvider, ledgerService, configuration);
                for (Hash transaction : transactions) {
                    if (!walkValidator.isValid(transaction)) {
                        state = false;
                        info = "tails are not consistent (would lead to inconsistent ledger state or below max depth)";
                        break;
                    }
                }
            } finally {
                snapshotProvider.getLatestSnapshot().unlockRead();
            }
        }

        return CheckConsistency.create(state, info);
    }

    /**
     * Compares the last received confirmed milestone with the last global snapshot milestone.
     * If these are equal, it means the tangle is empty and therefore invalid.
     *
     * @return <tt>false</tt> if we received at least a solid milestone, otherwise <tt>true</tt>
     */
    public boolean invalidSubtangleStatus() {
        return (snapshotProvider.getLatestSnapshot().getIndex() == snapshotProvider.getInitialSnapshot().getIndex());
    }
    /**
     * Returns the set of neighbors you are connected with, as well as their activity statistics (or counters).
     * The activity counters are reset after restarting IRI.
     *
     * @return {@link net.helix.hlx.service.dto.GetNeighborsResponse}
     **/
    private AbstractResponse getNeighborsStatement() {
        return GetNeighborsResponse.create(node.getNeighbors());
    }

    /**
     * Temporarily add a list of neighbors to your node.
     * The added neighbors will not be available after restart.
     * Add the neighbors to your config file
     * or supply them in the <tt>-n</tt> command line option if you want to add them permanently.
     *
     * The URI (Unique Resource Identification) for adding neighbors is:
     * <b>udp://IPADDRESS:PORT</b>
     *
     * @param uris list of neighbors to add
     * @return {@link net.helix.hlx.service.dto.AddedNeighborsResponse}
     **/
    private AbstractResponse addNeighborsStatement(final List<String> uris) {
        int numberOfAddedNeighbors = 0;
        try {
            for (final String uriString : uris) {
                log.info("Adding neighbor: " + uriString);
                final Neighbor neighbor = node.newNeighbor(new URI(uriString), true);
                if (!node.getNeighbors().contains(neighbor)) {
                    node.getNeighbors().add(neighbor);
                    numberOfAddedNeighbors++;
                }
            }
        } catch (URISyntaxException|RuntimeException e) {
            return ErrorResponse.create("Invalid uri scheme: " + e.getLocalizedMessage());
        }
        return AddedNeighborsResponse.create(numberOfAddedNeighbors);
    }

    /**
     * Temporarily removes a list of neighbors from your node.
     * The added neighbors will be added again after relaunching SBX.
     * Remove the neighbors from your config file or make sure you don't supply them in the -n command line option if you want to keep them removed after restart.
     *
     * The URI (Unique Resource Identification) for removing neighbors is:
     * <b>udp://IPADDRESS:PORT</b>
     *
     * Returns an {@link net.helix.hlx.service.dto.ErrorResponse} if the URI scheme is wrong
     *
     * @param uris The URIs of the neighbors we want to remove.
     * @return {@link net.helix.hlx.service.dto.RemoveNeighborsResponse}
     **/
    private AbstractResponse removeNeighborsStatement(List<String> uris) {
        int numberOfRemovedNeighbors = 0;
        try {
            for (final String uriString : uris) {
                log.info("Removing neighbor: " + uriString);
                if (node.removeNeighbor(new URI(uriString),true)) {
                    numberOfRemovedNeighbors++;
                }
            }
        } catch (URISyntaxException|RuntimeException e) {
            return ErrorResponse.create("Invalid uri scheme: " + e.getLocalizedMessage());
        }
        return RemoveNeighborsResponse.create(numberOfRemovedNeighbors);
    }

    /**
     * Returns the raw transaction data (bytes) of a specific transaction.
     * These bytes can then be easily converted into the actual transaction object.
     * See utility and {@link Transaction} functions in an Helix library for more details.
     *
     * @param hashes The transaction hashes you want to get bytes from.
     * @return {@link net.helix.hlx.service.dto.GetHBytesResponse}
     **/
    private synchronized AbstractResponse getHBytesStatement(List<String> hashes) throws Exception {
        final List<String> elements = new LinkedList<>();
        for (final String hash : hashes) {
            final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, HashFactory.TRANSACTION.create(hash));
            if (transactionViewModel != null) {
                elements.add(Hex.toHexString(transactionViewModel.getBytes()));
            }
        }
        if (elements.size() > maxGetBytes){
            return ErrorResponse.create(OVER_MAX_ERROR_MESSAGE);
        }
        return GetHBytesResponse.create(elements);
    }

    /**
     * Tip selection which returns <tt>trunkTransaction</tt> and <tt>branchTransaction</tt>.
     * The input value <tt>depth</tt> determines how many milestones to go back for finding the transactions to approve.
     * The higher your <tt>depth</tt> value, the more work you have to do as you are confirming more transactions.
     * If the <tt>depth</tt> is too large (usually above 15, it depends on the node's configuration) an error will be returned.
     * The <tt>reference</tt> is an optional hash of a transaction you want to approve.
     * If it can't be found at the specified <tt>depth</tt> then an error will be returned.
     *
     * @param depth Number of bundles to go back to determine the transactions for approval.
     * @param reference Hash of transaction to start random-walk from, used to make sure the tips returned reference a given transaction in their past.
     * @return {@link net.helix.hlx.service.dto.GetTransactionsToApproveResponse}
     * @throws Exception When tip selection has failed. Currently caught and returned as an {@link ErrorResponse}.
     **/
    private synchronized AbstractResponse getTransactionsToApproveStatement(int depth, Optional<Hash> reference) {
        if (depth < 0 || depth > configuration.getMaxDepth()) {
            return ErrorResponse.create("Invalid depth input");
        }

        try {
            List<Hash> tips = getTransactionToApproveTips(depth, reference);
            return GetTransactionsToApproveResponse.create(tips.get(0), tips.get(1));

        } catch (Exception e) {
            log.info("Tip selection failed: " + e.getLocalizedMessage());
            return ErrorResponse.create(e.getLocalizedMessage());
        }
    }

    /**
     * Gets tips which can be used by new transactions to approve.
     * If debug is enabled, statistics on tip selection will be gathered.
     *
     * @param depth The milestone depth for finding the transactions to approve.
     * @param reference An optional transaction hash to be referenced by tips.
     * @return The tips which can be approved.
     * @throws Exception if the subtangle is out of date or if we fail to retrieve transaction tips.
     * @see TipSelector
     */
    List<Hash> getTransactionToApproveTips(int depth, Optional<Hash> reference) throws Exception {
        if (invalidSubtangleStatus()) {
            throw new IllegalStateException(INVALID_SUBTANGLE);
        }

        List<Hash> tips = tipsSelector.getTransactionsToApprove(depth, reference);

        if (log.isDebugEnabled()) {
            gatherStatisticsOnTipSelection();
        }
        return tips;
    }

    /**
     * <p>
     *     Handles statistics on tip selection.
     *     Increases the tip selection by one use.
     * </p>
     * <p>
     *     If the {@link #getCounterGetTxToApprove()} is a power of 100, a log is send and counters are reset.
     * </p>
     */
    private void gatherStatisticsOnTipSelection() {
        API.incCounterGetTxToApprove();
        if ((getCounterGetTxToApprove() % 100) == 0) {
            String sb = "Last 100 getTxToApprove consumed "
                    + API.getEllapsedTimeGetTxToApprove() / 1000000000L
                    + " seconds processing time.";

            log.debug(sb);
            counterGetTxToApprove = 0;
            ellapsedTime_getTxToApprove = 0L;
        }
    }

    /**
     * Returns all tips currently known by this node.
     *
     * @return {@link net.helix.hlx.service.dto.GetTipsResponse}
     **/
    private synchronized AbstractResponse getTipsStatement() throws Exception {
        return GetTipsResponse.create(tipsViewModel.getTips()
                .stream()
                .map(Hash::hexString)
                .collect(Collectors.toList()));
    }

    /**
     * Stores transactions in the local storage.
     * The bytes to be used for this call should be valid, attached transaction bytes.
     * These bytes are returned by <tt>attachToTangle</tt>, or by doing proof of work somewhere else.
     *
     * @param txHex Transaction data to be stored.
     * @throws Exception When storing or updating a transaction fails
     **/

    public void storeTransactionsStatement(final List<String> txHex) throws Exception {
        final List<TransactionViewModel> elements = new LinkedList<>();
        byte[] txBytes;
        for (final String hex : txHex) {
            //validate all tx
            txBytes = Hex.decode(hex);
            final TransactionViewModel transactionViewModel = transactionValidator.validateBytes(txBytes,
                    transactionValidator.getMinWeightMagnitude());
            elements.add(transactionViewModel);
        }

        for (final TransactionViewModel transactionViewModel : elements) {
            //store transactions
            if(transactionViewModel.store(tangle, snapshotProvider.getInitialSnapshot())) { // v
                transactionViewModel.setArrivalTime(System.currentTimeMillis() / 1000L);
                transactionValidator.updateStatus(transactionViewModel);
                transactionViewModel.updateSender("local");
                transactionViewModel.update(tangle, snapshotProvider.getInitialSnapshot(), "sender");
            }

            if (graph != null) {
                graph.addNode(transactionViewModel.getHash().hexString(), transactionViewModel.getTrunkTransactionHash().hexString(), transactionViewModel.getBranchTransactionHash().hexString());
            }
        }
    }

    /**
     * Interrupts and completely aborts the <tt>attachToTangle</tt> process.
     *
     * @return {@link net.helix.hlx.service.dto.AbstractResponse.Emptyness}
     **/
    private AbstractResponse interruptAttachingToTangleStatement(){
        miner.cancel();
        return AbstractResponse.createEmptyResponse();
    }

    /**
     * Returns information about this node.
     *
     * @return {@link net.helix.hlx.service.dto.GetNodeInfoResponse}
     **/
    private AbstractResponse getNodeInfoStatement() throws Exception {
        String name = configuration.isTestnet() ? HLX.TESTNET_NAME : HLX.MAINNET_NAME;
        MilestoneViewModel milestone = MilestoneViewModel.first(tangle);
        return GetNodeInfoResponse.create(name, HLX.VERSION,
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().freeMemory(),
                System.getProperty("java.version"),
                Runtime.getRuntime().maxMemory(),
                Runtime.getRuntime().totalMemory(),
                latestMilestoneTracker.getLatestMilestoneHash(),
                latestMilestoneTracker.getLatestMilestoneIndex(),

                snapshotProvider.getLatestSnapshot().getHash(),
                snapshotProvider.getLatestSnapshot().getIndex(),

                milestone != null ? milestone.index() : -1,
                snapshotProvider.getLatestSnapshot().getInitialIndex(),

                node.howManyNeighbors(),
                node.queuedTransactionsSize(),
                System.currentTimeMillis(),
                tipsViewModel.size(),
                transactionRequester.numberOfTransactionsToRequest(),
                features,
                configuration.getCoordinator());
    }

    /**
     *  Returns information about this node configuration.
     *
     * @return {@link GetNodeAPIConfigurationResponse}
     */
    private AbstractResponse getNodeAPIConfigurationStatement() {
        return GetNodeAPIConfigurationResponse.create(configuration);
    }

    /**
     * <p>
     *     Get the inclusion states of a set of transactions.
     *     This is for determining if a transaction was accepted and confirmed by the network or not.
     *     You can search for multiple tips (and thus, milestones) to get past inclusion states of transactions.
     * </p>
     * <p>
     *     This API call returns a list of boolean values in the same order as the submitted transactions.<br/>
     *     Boolean values will be <tt>true</tt> for confirmed transactions, otherwise <tt>false</tt>.
     * </p>
     * Returns an {@link net.helix.hlx.service.dto.ErrorResponse} if a tip is missing or the subtangle is not solid
     *
     * @param transactions List of transactions you want to get the inclusion state for.
     * @param tips List of tips (including milestones) you want to search for the inclusion state.
     * @return {@link net.helix.hlx.service.dto.GetInclusionStatesResponse}
     * @throws Exception When a transaction cannot be loaded from hash
     **/
    private AbstractResponse getInclusionStatesStatement(final List<String> transactions, final List<String> tips) throws Exception {

        final List<Hash> trans = transactions.stream()
                .map(HashFactory.TRANSACTION::create)
                .collect(Collectors.toList());

        final List<Hash> tps = tips.stream().
                map(HashFactory.TRANSACTION::create)
                .collect(Collectors.toList());

        int numberOfNonMetTransactions = trans.size();
        final byte[] inclusionStates = new byte[numberOfNonMetTransactions];

        List<Integer> tipsIndex = new LinkedList<>();
        {
            for(Hash tip: tps) {
                TransactionViewModel tx = TransactionViewModel.fromHash(tangle, tip);
                if (tx.getType() != TransactionViewModel.PREFILLED_SLOT) {
                    tipsIndex.add(tx.snapshotIndex());
                }
            }
        }

        // Finds the lowest tips index, or 0
        int minTipsIndex = tipsIndex.stream().reduce((a,b) -> a < b ? a : b).orElse(0);

        // If the lowest tips index (minTipsIndex) is 0 (or lower),
        // we can't check transactions against snapshots because there were no tips,
        // or tips have not been confirmed by a snapshot yet
        if(minTipsIndex > 0) {
            // Finds the highest tips index, or 0
            int maxTipsIndex = tipsIndex.stream().reduce((a,b) -> a > b ? a : b).orElse(0);
            int count = 0;

            // Checks transactions with indexes of tips, and sets inclusionStates byte to 1 or -1 accordingly
            // Sets to -1 if the transaction is only known by hash,
            // or has no index, or index is above the max tip index (not included).

            // Sets to 1 if the transaction index is below the max index of tips (included).
            for(Hash hash: trans) {
                TransactionViewModel transaction = TransactionViewModel.fromHash(tangle, hash);
                if(transaction.getType() == TransactionViewModel.PREFILLED_SLOT || transaction.snapshotIndex() == 0) {
                    inclusionStates[count] = -1;
                } else if(transaction.snapshotIndex() > maxTipsIndex) {
                    inclusionStates[count] = -1;
                } else if(transaction.snapshotIndex() < maxTipsIndex) {
                    inclusionStates[count] = 1;
                }
                count++;
            }

        Set<Hash> analyzedTips = new HashSet<>();
        Map<Integer, Integer> sameIndexTransactionCount = new HashMap<>();
        Map<Integer, Queue<Hash>> sameIndexTips = new HashMap<>();

        // Sorts all tips per snapshot index. Stops if a tip is not in our database, or just as a hash.
        for (final Hash tip : tps) {
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, tip);
            if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT){
                return ErrorResponse.create("One of the tips is absent");
            }
            int snapshotIndex = transactionViewModel.snapshotIndex();
            sameIndexTips.putIfAbsent(snapshotIndex, new LinkedList<>());
            sameIndexTips.get(snapshotIndex).add(tip);
        }

        // Loop over all transactions without a state, and counts the amount per snapshot index
        for(int i = 0; i < inclusionStates.length; i++) {
            if(inclusionStates[i] == 0) {
                TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, trans.get(i));
                int snapshotIndex = transactionViewModel.snapshotIndex();
                sameIndexTransactionCount.putIfAbsent(snapshotIndex, 0);
                sameIndexTransactionCount.put(snapshotIndex, sameIndexTransactionCount.get(snapshotIndex) + 1);
            }
        }

        // Loop over all snapshot indexes of transactions that were not confirmed.
        // If we encounter an invalid tangle, stop this function completely.
        for(Integer index : sameIndexTransactionCount.keySet()) {
            // Get the tips from the snapshot indexes we are missing
            Queue<Hash> sameIndexTip = sameIndexTips.get(index);

            // We have tips on the same level as transactions, do a manual search.
            if (sameIndexTip != null && !exhaustiveSearchWithinIndex(
                    sameIndexTip, analyzedTips, trans,
                    inclusionStates, sameIndexTransactionCount.get(index), index)) {

                return ErrorResponse.create(INVALID_SUBTANGLE);
            }
        }
        final boolean[] inclusionStatesBoolean = new boolean[inclusionStates.length];
        for(int i = 0; i < inclusionStates.length; i++) {
            // If a state is 0 by now, we know nothing so assume not included
            inclusionStatesBoolean[i] = inclusionStates[i] == 1;
        }

        {
            return GetInclusionStatesResponse.create(inclusionStatesBoolean);
        }
    }
        final boolean[] inclusionStatesBoolean = new boolean[inclusionStates.length];
        for(int i = 0; i < inclusionStates.length; i++) {
            inclusionStatesBoolean[i] = inclusionStates[i] == 1;
        }
        {
            return GetInclusionStatesResponse.create(inclusionStatesBoolean);
        }
    }

    /**
     * Traverses down the tips until all transactions we wish to validate have been found or transaction data is missing.
     *
     * @param nonAnalyzedTransactions Tips we will analyze.
     * @param analyzedTips The hashes of tips we have analyzed.
     *                     Hashes specified here won't be analyzed again.
     * @param transactions All transactions we are validating.
     * @param inclusionStates The state of each transaction.
     *                        1 means confirmed, -1 means unconfirmed, 0 is unknown confirmation.
     *                        Should be of equal length as <tt>transactions</tt>.
     * @param count The amount of transactions on the same index level as <tt>nonAnalyzedTransactions</tt>.
     * @param index The snapshot index of the tips in <tt>nonAnalyzedTransactions</tt>.
     * @return <tt>true</tt> if all <tt>transactions</tt> are directly or indirectly references by
     *         <tt>nonAnalyzedTransactions</tt>.
     *         If at some point we are missing transaction data <tt>false</tt> is returned immediately.
     * @throws Exception If a {@link TransactionViewModel} cannot be loaded.
     */
    private boolean exhaustiveSearchWithinIndex(
            Queue<Hash> nonAnalyzedTransactions,
            Set<Hash> analyzedTips,
            List<Hash> transactions,
            byte[] inclusionStates, int count, int index) throws Exception {

        Hash pointer;
        MAIN_LOOP:
        // While we have nonAnalyzedTransactions in the Queue
        while ((pointer = nonAnalyzedTransactions.poll()) != null) {
            // Only analyze tips we haven't analyzed yet
            if (analyzedTips.add(pointer)) {

                // Check if the transactions have indeed this index. Otherwise ignore.
                // Starts off with the tips in nonAnalyzedTransactions, but transaction trunk & branch gets added.
                final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, pointer);
                if (transactionViewModel.snapshotIndex() == index) {
                    // Do we have the complete transaction?
                    if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT) {
                        // Incomplete transaction data, stop search.
                        return false;
                    } else {
                        // check all transactions we wish to verify confirmation for
                        for (int i = 0; i < inclusionStates.length; i++) {
                            if (inclusionStates[i] < 1 && pointer.equals(transactions.get(i))) {
                                // A tip, or its branch/trunk points to this transaction.
                                // That means this transaction is confirmed by this tip.
                                inclusionStates[i] = 1;

                                // Only stop search when we have found all transactions we were looking for
                                if (--count <= 0) {
                                    break MAIN_LOOP;
                                }
                            }
                        }

                        // Add trunk and branch to the queue for the transaction confirmation check
                        nonAnalyzedTransactions.offer(transactionViewModel.getTrunkTransactionHash());
                        nonAnalyzedTransactions.offer(transactionViewModel.getBranchTransactionHash());
                    }
                }
            }
        }
        return true;
    }

    /**
     * <p>
     *     Find the transactions which match the specified input and return.
     *     All input values are lists, for which a list of return values (transaction hashes), in the same order, is returned for all individual elements.
     *     The input fields can either be <tt>bundles</tt>, <tt>addresses</tt>, <tt>tags</tt> or <tt>approvees</tt>.
     * </p>
     *
     * Using multiple of these input fields returns the intersection of the values.
     * Returns an {@link net.helix.hlx.service.dto.ErrorResponse} if more than maxFindTxs was found.
     *
     * @param request The map with input fields
     *                Must contain at least one of 'bundles', 'addresses', 'tags' or 'approvees'.
     * @return {@link net.helix.hlx.service.dto.FindTransactionsResponse}.
     * @throws Exception If a model cannot be loaded, no valid input fields were supplied
     *                   or the total transactions to find exceeds {@link APIConfig#getMaxFindTransactions()}.
     **/
    private synchronized AbstractResponse findTransactionsStatement(final Map<String, Object> request) throws Exception {

        final Set<Hash> foundTransactions =  new HashSet<>();
        boolean containsKey = false;

        final Set<Hash> bundlesTransactions = new HashSet<>();
        if (request.containsKey("bundles")) {
            final Set<String> bundles = getParameterAsSet(request,"bundles",HASH_SIZE);
            for (final String bundle : bundles) {
                bundlesTransactions.addAll(
                        BundleViewModel.load(tangle, HashFactory.BUNDLE.create(bundle))
                                .getHashes());
            }
            foundTransactions.addAll(bundlesTransactions);
            containsKey = true;
        }

        final Set<Hash> addressesTransactions = new HashSet<>();
        if (request.containsKey("addresses")) {
            final Set<String> addresses = getParameterAsSet(request,"addresses",HASH_SIZE);
            for (final String address : addresses) {
                addressesTransactions.addAll(
                        AddressViewModel.load(tangle, HashFactory.ADDRESS.create(address))
                                .getHashes());
            }
            foundTransactions.addAll(addressesTransactions);
            containsKey = true;
        }

        final Set<Hash> tagsTransactions = new HashSet<>();
        if (request.containsKey("tags")) {
            final Set<String> tags = getParameterAsSet(request,"tags",0);
            for (String tag : tags) {
                tag = padTag(tag);
                tagsTransactions.addAll(
                        TagViewModel.load(tangle, HashFactory.TAG.create(tag))
                                .getHashes());
            }
            if (tagsTransactions.isEmpty()) {
                for (String tag : tags) {
                    tag = padTag(tag);
                    tagsTransactions.addAll(
                            TagViewModel.load(tangle, HashFactory.TAG.create(tag))
                                    .getHashes());
                }
            }
            foundTransactions.addAll(tagsTransactions);
            containsKey = true;
        }

        final Set<Hash> approveeTransactions = new HashSet<>();

        if (request.containsKey("approvees")) {
            final Set<String> approvees = getParameterAsSet(request,"approvees",HASH_SIZE);
            for (final String approvee : approvees) {
                approveeTransactions.addAll(
                        TransactionViewModel.fromHash(tangle, HashFactory.TRANSACTION.create(approvee))
                                .getApprovers(tangle)
                                .getHashes());
            }
            foundTransactions.addAll(approveeTransactions);
            containsKey = true;
        }

        if (!containsKey) {
            throw new ValidationException(INVALID_PARAMS);
        }

        //Using multiple of these input fields returns the intersection of the values.
        if (request.containsKey("bundles")) {
            foundTransactions.retainAll(bundlesTransactions);
        }
        if (request.containsKey("addresses")) {
            foundTransactions.retainAll(addressesTransactions);
        }
        if (request.containsKey("tags")) {
            foundTransactions.retainAll(tagsTransactions);
        }
        if (request.containsKey("approvees")) {
            foundTransactions.retainAll(approveeTransactions);
        }
        if (foundTransactions.size() > maxFindTxs){
            return ErrorResponse.create(OVER_MAX_ERROR_MESSAGE);
        }

        final List<String> elements = foundTransactions.stream()
                .map(Hash -> Hex.toHexString(Hash.bytes()))
                .collect(Collectors.toCollection(LinkedList::new));

        return FindTransactionsResponse.create(elements);
    }

    /**
     * Adds '0' until the String is of {@link #HASH_SIZE} length.
     *
     * @param tag The String to fill.
     * @return The updated String.
     * @throws ValidationException If the <tt>tag</tt> is a {@link Hash#NULL_HASH}.
     */
    private String padTag(String tag) throws ValidationException {
        while (tag.length() < HASH_SIZE) {
            tag += '0';
        }
        if (tag.equals(Hash.NULL_HASH.toString())) {
            throw new ValidationException("Invalid tag input");
        }
        return tag;
    }

    /**
     * Runs {@link #getParameterAsList(Map, String, int)} and transforms it into a {@link Set}.
     *
     * @param request All request parameters.
     * @param paramName The name of the parameter we want to turn into a list of Strings.
     * @param size the length each String must have.
     * @return the list of valid Byte Strings.
     * @throws ValidationException If the requested parameter does not exist or
     *                             the string is not exactly bytes of <tt>size</tt> length or
     *                             the amount of Strings in the list exceeds {@link APIConfig#getMaxRequestsList}
     */
    private HashSet<String> getParameterAsSet(Map<String, Object> request, String paramName, int size) throws ValidationException {

        HashSet<String> result = getParameterAsList(request,paramName,size).stream().collect(Collectors.toCollection(HashSet::new));
        if (result.contains(Hash.NULL_HASH.toString())) {
            throw new ValidationException("Invalid " + paramName + " input");
        }
        return result;
    }

    /**
     * Broadcast a list of transactions to all neighbors.
     * The bytes to be used for this call should be valid, attached transaction bytes.
     * These bytes are returned by <tt>attachToTangle</tt>, or by doing proof of work somewhere else.
     *
     * @param txHex the list of transaction bytes to broadcast
     **/
    public void broadcastTransactionsStatement(final List<String> txHex) {
        final List<TransactionViewModel> elements = new LinkedList<>();
        byte[] txBytes;
        for (final String hex : txHex) {
            //validate all tx
            txBytes = Hex.decode(hex);
            // TODO something possibly going wrong here.
            final TransactionViewModel transactionViewModel = transactionValidator.validateBytes(txBytes, transactionValidator.getMinWeightMagnitude());
            elements.add(transactionViewModel);
        }
        for (final TransactionViewModel transactionViewModel : elements) {
            //push first in line to broadcast
            transactionViewModel.weightMagnitude = Sha3.HASH_LENGTH;
            node.broadcast(transactionViewModel);
        }
    }

    /**
     * <p>
     *     Calculates the confirmed balance, as viewed by the specified <tt>tips</tt>.
     *     If you do not specify the referencing <tt>tips</tt>,
     *     the returned balance is based on the latest confirmed milestone.
     *     In addition to the balances, it also returns the referencing <tt>tips</tt> (or milestone),
     *     as well as the index with which the confirmed balance was determined.
     *     The balances are returned as a list in the same order as the addresses were provided as input.
     * </p>
     * Returns an {@link ErrorResponse} if tips are not found, inconsistent or the threshold is invalid.
     *
     * @param addresses The addresses where we will find the balance for.
     * @param tips The optional tips to find the balance through.
     * @param threshold The confirmation threshold between 0 and 100(inclusive).
     *                  Should be set to 100 for getting balance by counting only confirmed transactions.
     * @return {@link net.helix.hlx.service.dto.GetBalancesResponse}
     * @throws Exception When the database has encountered an error
     **/
    private AbstractResponse getBalancesStatement(List<String> addresses,
                                                  List<String> tips,
                                                  int threshold) throws Exception {

        if (threshold <= 0 || threshold > 100) {
            return ErrorResponse.create("Illegal 'threshold'");
        }

        final List<Hash> addressList = addresses.stream()
                .map(address -> (HashFactory.ADDRESS.create(address)))
                .collect(Collectors.toCollection(LinkedList::new));

        final List<Hash> hashes;
        final Map<Hash, Long> balances = new HashMap<>();
        snapshotProvider.getLatestSnapshot().lockRead();
        final int index = snapshotProvider.getLatestSnapshot().getIndex();

        if (tips == null || tips.size() == 0) {
            hashes = Collections.singletonList(snapshotProvider.getLatestSnapshot().getHash());
        } else {
            hashes = tips.stream()
                    .map(tip -> (HashFactory.TRANSACTION.create(tip)))
                    .collect(Collectors.toCollection(LinkedList::new));
        }

        try {
            // Get the balance for each address at the last snapshot
            for (final Hash address : addressList) {
                Long value = snapshotProvider.getLatestSnapshot().getBalance(address);
                if (value == null) {
                    value = 0L;
                }
                balances.put(address, value);
            }

            final Set<Hash> visitedHashes = new HashSet<>();
            final Map<Hash, Long> diff = new HashMap<>();

            // Calculate the difference created by the non-verified transactions which tips approve.
            // This difference is put in a map with address -> value changed
            for (Hash tip : hashes) {
                if (!TransactionViewModel.exists(tangle, tip)) {
                    return ErrorResponse.create("Tip not found: " + Hex.toHexString(tip.bytes()));
                }
                if (!ledgerService.isBalanceDiffConsistent(visitedHashes, diff, tip)) {
                    return ErrorResponse.create("Tips are not consistent");
                }
            }

            // Update the found balance according to 'diffs' balance changes
            diff.forEach((key, value) -> balances.computeIfPresent(key, (hash, aLong) -> value + aLong));
        } finally {
            snapshotProvider.getLatestSnapshot().unlockRead();
        }

        final List<String> elements = addressList.stream()
                .map(address -> balances.get(address).toString())
                .collect(Collectors.toCollection(LinkedList::new));

        return GetBalancesResponse.create(elements, hashes.stream()
                .map(h -> Hex.toHexString(h.bytes()))
                .collect(Collectors.toList()), index);
    }

    /**
     * Can be 0 or more, and is set to 0 every 100 requests.
     * Each increase indicates another 2 tips sent.
     *
     * @return The current amount of times this node has done proof of work.
     *         Doesn't distinguish between remote and local proof of work.
     */
    public static int getCounterPoW() {
        return counter_PoW;
    }

    /**
     * Increases the amount of times this node has done proof of work by one.
     */
    public static void incCounterPoW() {
        API.counter_PoW++;
    }

    /**
     * Can be 0 or more, and is set to 0 every 100 requests.
     *
     * @return The current amount of time spent on doing proof of work in milliseconds.
     *         Doesn't distinguish between remote and local proof of work.
     */
    public static long getEllapsedTimePoW() {
        return ellapsedTime_PoW;
    }

    /**
     * Increases the current amount of time spent on doing proof of work.
     *
     * @param ellapsedTime the time to add, in milliseconds.
     */
    public static void incEllapsedTimePoW(long ellapsedTime) {
        ellapsedTime_PoW += ellapsedTime;
    }

    /**
     * <p>
     *     Prepares the specified transactions (bytes) for attachment to the Tangle by doing Proof of Work.
     *     You need to supply <tt>branchTransaction</tt> as well as <tt>trunkTransaction</tt>.
     *     These are the tips which you're going to validate and reference with this transaction.
     *     These are obtainable by the <tt>getTransactionsToApprove</tt> API call.
     * </p>
     * <p>
     *     The returned value is a different set of byte values which you can input into
     *     <tt>broadcastTransactions</tt> and <tt>storeTransactions</tt>.
     *     The last 243 bytes of the return value consist of the following:
     *     <ul>
     *         <li><code>trunkTransaction</code></li>
     *         <li><code>branchTransaction</code></li>
     *         <li><code>nonce</code></li>
     *     </ul>
     *     These are valid bytes which are then accepted by the network.
     * </p>
     * @param trunkTransaction A reference to an external transaction (tip) used as trunk.
     *                         The transaction with index 0 will have this tip in its trunk.
     *                         All other transactions reference the previous transaction in the bundle (Their index-1).
     *
     * @param branchTransaction A reference to an external transaction (tip) used as branch.
     *                          Each Transaction in the bundle will have this tip as their branch, except the last.
     *                          The last one will have the branch in its trunk.
     * @param minWeightMagnitude The amount of work we should do to confirm this transaction.
     *                           Each 0-byte at the beginning of the transaction represents 1 magnitude.
     *                           Transactions with a different minWeightMagnitude are compatible.
     * @param txs the list of tx bytes to prepare for network attachment, by doing proof of work.
     * @return The list of transactions in bytes, ready to be broadcast to the network.
     **/

    public synchronized List<String> attachToTangleStatement(final Hash trunkTransaction, final Hash branchTransaction, int minWeightMagnitude, final List<String> txs) {
        final List<TransactionViewModel> transactionViewModels = new LinkedList<>();

        Hash prevTransaction = null;
        miner = new GreedyMiner();

        byte[] txBytes = new byte[BYTES_SIZE];

        // in case remote attachToTangle is enabled and current test magnitude is exceeded.
        minWeightMagnitude = (minWeightMagnitude > 2) ? 2 : minWeightMagnitude;

        for (final String tx : txs) {
            long startTime = System.nanoTime();
            long timestamp = System.currentTimeMillis();
            try {
                byte[] txHash = Hex.decode(tx);
                System.arraycopy(txHash, 0, txBytes, 0, txHash.length);
                //branch and trunk
                System.arraycopy((prevTransaction == null ? trunkTransaction : prevTransaction).bytes(), 0,
                        txBytes, TransactionViewModel.TRUNK_TRANSACTION_OFFSET,
                        TransactionViewModel.TRUNK_TRANSACTION_SIZE);
                System.arraycopy((prevTransaction == null ? branchTransaction : trunkTransaction).bytes(), 0,
                        txBytes, TransactionViewModel.BRANCH_TRANSACTION_OFFSET,
                        TransactionViewModel.BRANCH_TRANSACTION_SIZE);

                //attachment fields: tag and timestamps
                //tag - copy the obsolete tag to the attachment tag field only if tag isn't set.

                if(IntStream.range(TransactionViewModel.TAG_OFFSET, TransactionViewModel.TAG_OFFSET + TransactionViewModel.TAG_SIZE).allMatch(idx -> txBytes[idx] == ((byte) 0))) {
                    System.arraycopy(txBytes, TransactionViewModel.BUNDLE_NONCE_OFFSET, txBytes, TransactionViewModel.TAG_OFFSET, TransactionViewModel.TAG_SIZE);
                }
                System.arraycopy(Serializer.serialize(timestamp),0, txBytes,TransactionViewModel.ATTACHMENT_TIMESTAMP_OFFSET,
                        TransactionViewModel.ATTACHMENT_TIMESTAMP_SIZE);
                System.arraycopy(Serializer.serialize(0L),0,txBytes, TransactionViewModel.ATTACHMENT_TIMESTAMP_LOWER_BOUND_OFFSET,
                        TransactionViewModel.ATTACHMENT_TIMESTAMP_LOWER_BOUND_SIZE);
                System.arraycopy(Serializer.serialize(MAX_TIMESTAMP_VALUE),0,txBytes,TransactionViewModel.ATTACHMENT_TIMESTAMP_UPPER_BOUND_OFFSET,
                        TransactionViewModel.ATTACHMENT_TIMESTAMP_UPPER_BOUND_SIZE);

                 if (!configuration.isPoWDisabled()) {
                     if (!miner.mine(txBytes, minWeightMagnitude, 4)) {
                         transactionViewModels.clear();
                         break;
                     }
                 }

                //validate PoW - throws exception if invalid
                final TransactionViewModel transactionViewModel = transactionValidator.validateBytes(txBytes, transactionValidator.getMinWeightMagnitude());

                transactionViewModels.add(transactionViewModel);
                prevTransaction = transactionViewModel.getHash();
            } finally {
                API.incEllapsedTimePoW(System.nanoTime() - startTime);
                API.incCounterPoW();
                if ( ( API.getCounterPoW() % 100) == 0 ) {
                    String sb = "Last 100 PoW consumed " +
                            API.getEllapsedTimePoW() / 1000000000L +
                            " seconds processing time.";
                    log.info(sb);
                    counter_PoW = 0;
                    ellapsedTime_PoW = 0L;
                }
            }
        }

        final List<String> elements = new LinkedList<>();
        for (int i = transactionViewModels.size(); i-- > 0; ) {
            elements.add(Hex.toHexString(transactionViewModels.get(i).getBytes()));
        }
        return elements;
    }

    /**
     * Can be 0 or more, and is set to 0 every 100 requests.
     * Each increase indicates another 2 tips send.
     *
     * @return The current amount of times this node has returned transactions to approve
     */
    private static int getCounterGetTxToApprove() {
        return counterGetTxToApprove;
    }

    /**
     * Increases the amount of tips send for transactions to approve by one
     */
    private static void incCounterGetTxToApprove() {
        counterGetTxToApprove++;
    }

    /**
     * Can be 0 or more, and is set to 0 every 100 requests.
     *
     * @return The current amount of time spent on sending transactions to approve in milliseconds
     */
    private static long getEllapsedTimeGetTxToApprove() {
        return ellapsedTime_getTxToApprove;
    }

    /**
     * Increases the current amount of time spent on sending transactions to approve
     *
     * @param ellapsedTime the time to add, in milliseconds
     */
    private static void incEllapsedTimeGetTxToApprove(long ellapsedTime) {
        ellapsedTime_getTxToApprove += ellapsedTime;
    }

    /**
     * Transforms an object parameter into an int.
     *
     * @param request A map of all request parameters
     * @param paramName The parameter we want to get as an int.
     * @return The integer value of this parameter
     * @throws ValidationException If the requested parameter does not exist or cannot be transformed into an int.
     */
    private int getParameterAsInt(Map<String, Object> request, String paramName) throws ValidationException {
        validateParamExists(request, paramName);
        int result;
        try {
            result = ((Double) request.get(paramName)).intValue();
        } catch (ClassCastException e) {
            throw new ValidationException("Invalid " + paramName + " input");
        }
        return result;
    }

    /**
     * Transforms an object parameter into a String.
     *
     * @param request A map of all request parameters
     * @param paramName The parameter we want to get as a String.
     * @param size The expected length of this String
     * @return The String value of this parameter
     * @throws ValidationException If the requested parameter does not exist or
     *                             the string is not exactly bytes of <tt>size</tt> length
     */
    private String getParameterAsStringAndValidate(Map<String, Object> request, String paramName, int size) throws ValidationException {
        validateParamExists(request, paramName);
        String result = (String) request.get(paramName);
        validateHex(paramName, size, result);
        return result;
    }

    /**
     * Checks if a parameter exists in the map
     * @param request All request parameters
     * @param paramName The name of the parameter we are looking for
     * @throws ValidationException if <tt>request</tt> does not contain <tt>paramName</tt>
     */
    private void validateParamExists(Map<String, Object> request, String paramName) throws ValidationException {
        if (!request.containsKey(paramName)) {
            throw new ValidationException(INVALID_PARAMS);
        }
    }

    /**
     * Translates the parameter into a {@link List}.
     * We then validate if the amount of elements does not exceed the maximum allowed.
     * Afterwards we verify if each element is valid according to {@link #validateHex(String, int, String)}.
     *
     * @param request All request parameters
     * @param paramName The name of the parameter we want to turn into a list of Strings
     * @param size the length each String must have
     * @return the list of valid Byte Strings.
     * @throws ValidationException If the requested parameter does not exist or
     *                             the string is not exactly bytes of <tt>size</tt> length or
     *                             the amount of Strings in the list exceeds {@link APIConfig#getMaxRequestsList}
     */
    private List<String> getParameterAsList(Map<String, Object> request, String paramName, int size) throws ValidationException {
        validateParamExists(request, paramName);
        final List<String> paramList = (List<String>) request.get(paramName);
        if (paramList.size() > maxRequestList) {
            throw new ValidationException(OVER_MAX_ERROR_MESSAGE);
        }
        if (size > 0) {
            //validate
            for (final String param : paramList) {
                validateHex(paramName, size, param);
            }
        }
        return paramList;
    }

    /**
     * Checks if a string is non 0 length, and contains exactly <tt>size</tt> amount of bytes.
     * Our byte strings only contain a-f and 0-9.
     *
     * @param paramName The name of the parameter this String came from.
     * @param size The amount of bytes it should contain.
     * @param result The String we validate.
     * @throws ValidationException If the string is not exactly bytes of <tt>size</tt> length
     */
    private void validateHex(String paramName, int size, String result) throws ValidationException {
        if (!validHex(result,size,ZERO_LENGTH_NOT_ALLOWED)) {
            throw new ValidationException("Invalid " + paramName + " input");
        }
    }

    /**
     * Checks if a string is of a certain length, and contains exactly <tt>size</tt> amount of hex.
     * Input string may only contain a-f and 0-9.
     *
     * @param input The input we validate.
     * @param length The amount of hex it should contain.
     * @param zeroAllowed If set to '{@value #ZERO_LENGTH_ALLOWED}', an empty byte string is also valid.
     * @throws ValidationException If the string is not exactly hex of <tt>size</tt> length
     * @return <tt>true</tt> if the string is valid, otherwise <tt>false</tt>
     */
    private boolean validHex(String input, int length, char zeroAllowed) {
        if (input.length() == 0 && zeroAllowed == ZERO_LENGTH_ALLOWED) {
            return true;
        }
        if (input.length() != length*2) {
            return false;
        }
        Matcher matcher = hexPattern.matcher(input);
        return matcher.matches();
    }

    public void shutDown() {
        if (connector != null) {
            connector.stop();
        }
    }

    /**
     * <b>Only available on testnet.</b>
     * Creates, attaches, and broadcasts a transaction with this message
     *
     * @param address The address to add the message to
     * @param message The message to store
     **/
    private synchronized AbstractResponse storeMessageStatement(final String address, final String message) throws Exception {
        final List<Hash> txToApprove = getTransactionToApproveTips(3, Optional.empty());
        attachStoreAndBroadcast(address, message, txToApprove);
        return AbstractResponse.createEmptyResponse();
    }

    private void attachStoreAndBroadcast(final String address, final String message, final List<Hash> txToApprove) throws Exception {
        attachStoreAndBroadcast(address, message, txToApprove, 0, 1, false);
    }

    private void attachStoreAndBroadcast(final String address, final String message, final List<Hash> txToApprove, final long index, final int minWeightMagnitude, final boolean isMilestone) throws Exception {

        final int txMessageSize = TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE * 2; // size in hex
        final int txCount = (message.length() + txMessageSize - 1) / txMessageSize;

        final byte[] timestampBytes = Serializer.serialize(System.currentTimeMillis());
        final String timestampHex = Hex.toHexString(timestampBytes);

        byte[] currentIndexBytes = new byte[TransactionViewModel.CURRENT_INDEX_SIZE];

        final byte[] lastIndexBytes = new byte[TransactionViewModel.LAST_INDEX_SIZE];
        final String lastIndexHex = isMilestone ? Hex.toHexString(lastIndexBytes) : Integer.toHexString(txCount); // TODO: lastIndex has to be 0 for milestones and based on txCount for other tx.

        final byte[] newMilestoneIndex = Serializer.serialize(index);
        final String tagHex = Hex.toHexString(newMilestoneIndex);  // milestoneTracker index is parsed from tag

        List<String> transactions = new ArrayList<>();
        for (int i = 0; i < txCount; i++) {
            String tx;
            if (i != txCount - 1) {
                tx = message.substring(i * txMessageSize, (i + 1) * txMessageSize);
            } else {
                tx = message.substring(i * txMessageSize);
            }
            System.arraycopy(Serializer.serialize((long)i), 0, currentIndexBytes, 0, currentIndexBytes.length);

            tx = StringUtils.rightPad(tx, txMessageSize, '0');
            tx += address.substring(0, 64); // address
            tx += StringUtils.repeat('0', 16); // value
            tx += StringUtils.repeat('0', 64); // obsolete tag / bundle nonce  might change from 32 byte to 8 byte
            tx += timestampHex; // timestamp
            tx += StringUtils.leftPad(Hex.toHexString(currentIndexBytes), currentIndexBytes.length*2, '0'); // current index
            tx += StringUtils.leftPad(lastIndexHex, lastIndexBytes.length*2, '0'); // last index

            transactions.add(tx);

        }

        // let's calculate the bundle essence
        int startIdx = TransactionViewModel.ESSENCE_OFFSET * 2;
        Sponge sponge = SpongeFactory.create(SpongeFactory.Mode.S256);

        for (String tx : transactions) {
            byte[] essenceBytes = Hex.decode(tx.substring(startIdx, startIdx + TransactionViewModel.ESSENCE_SIZE * 2));
            sponge.absorb(essenceBytes, 0, essenceBytes.length);
        }

        byte[] essenceHash = new byte[32];
        sponge.squeeze(essenceHash, 0, essenceHash.length);
        final String bundleHash = Hex.toHexString(essenceHash);
        transactions = transactions.stream().map(tx -> StringUtils.rightPad(tx + bundleHash + StringUtils.repeat('0', 128) + tagHex, BYTES_SIZE, '0')).collect(Collectors.toList());
        List<String> powResult = attachToTangleStatement(txToApprove.get(0), txToApprove.get(1), minWeightMagnitude, transactions);
        storeTransactionsStatement(powResult);
        broadcastTransactionsStatement(powResult);
    }

    public void storeAndBroadcastMilestoneStatement(final String address, final String message, final int minWeightMagnitude, Boolean sign) throws Exception {

        // get tips
        int latestMilestoneIndex = latestMilestoneTracker.getLatestMilestoneIndex();
        long nextIndex = latestMilestoneIndex+1;
        List<Hash> txToApprove = new ArrayList<>();
        if(Hash.NULL_HASH.equals(latestMilestoneTracker.getLatestMilestoneHash())) {
            txToApprove.add(Hash.NULL_HASH);
            txToApprove.add(Hash.NULL_HASH);
        } else {
            txToApprove = getTransactionToApproveTips(3, Optional.empty());
        }

        // A milestone consists of two transactions.
        // The last transaction (currentIndex == lastIndex) contains the siblings for the merkle tree.
        byte[] txSibling = new byte[TransactionViewModel.SIZE];
        System.arraycopy(Serializer.serialize(1L), 0, txSibling, TransactionViewModel.CURRENT_INDEX_OFFSET, TransactionViewModel.CURRENT_INDEX_SIZE);
        System.arraycopy(Serializer.serialize(1L), 0, txSibling, TransactionViewModel.LAST_INDEX_OFFSET, TransactionViewModel.LAST_INDEX_SIZE);
        System.arraycopy(Serializer.serialize(System.currentTimeMillis() / 1000L), 0, txSibling, TransactionViewModel.TIMESTAMP_OFFSET, TransactionViewModel.TIMESTAMP_SIZE);

        // The other transactions contain a signature that signs the siblings and thereby ensures the integrity.
        byte[] txMilestone = new byte[TransactionViewModel.SIZE];
        System.arraycopy(Hex.decode(address), 0, txMilestone, TransactionViewModel.ADDRESS_OFFSET, TransactionViewModel.ADDRESS_SIZE);
        System.arraycopy(Serializer.serialize(1L), 0, txMilestone, TransactionViewModel.LAST_INDEX_OFFSET, TransactionViewModel.LAST_INDEX_SIZE);
        System.arraycopy(Serializer.serialize(System.currentTimeMillis() / 1000L), 0, txMilestone, TransactionViewModel.TIMESTAMP_OFFSET, TransactionViewModel.TIMESTAMP_SIZE);
        System.arraycopy(Serializer.serialize(nextIndex), 0, txMilestone, TransactionViewModel.TAG_OFFSET, TransactionViewModel.TAG_SIZE);

        // calculate bundle hash
        Sponge sponge = SpongeFactory.create(SpongeFactory.Mode.S256);

        byte[] milestoneEssence = Arrays.copyOfRange(txMilestone, TransactionViewModel.ESSENCE_OFFSET, TransactionViewModel.ESSENCE_OFFSET + TransactionViewModel.ESSENCE_SIZE);
        sponge.absorb(milestoneEssence, 0, milestoneEssence.length);
        byte[] siblingEssence = Arrays.copyOfRange(txSibling, TransactionViewModel.ESSENCE_OFFSET, TransactionViewModel.ESSENCE_OFFSET + TransactionViewModel.ESSENCE_SIZE);
        sponge.absorb(siblingEssence, 0, siblingEssence.length);

        byte[] bundleHash = new byte[32];
        sponge.squeeze(bundleHash, 0, bundleHash.length);
        System.arraycopy(bundleHash, 0, txSibling, TransactionViewModel.BUNDLE_OFFSET, TransactionViewModel.BUNDLE_SIZE);
        System.arraycopy(bundleHash, 0, txMilestone, TransactionViewModel.BUNDLE_OFFSET, TransactionViewModel.BUNDLE_SIZE);

        if (sign) {
            // Get merkle path and store in signatureMessageFragment of Sibling Transaction
            StringBuilder seedBuilder = new StringBuilder();
            byte[][][] merkleTree = Merkle.readKeyfile(new File("./src/main/resources/Coordinator.key"), seedBuilder);
            String seed = seedBuilder.toString(), coordinatorAddress = Hex.toHexString(merkleTree[merkleTree.length - 1][0]);
            // create merkle path from keyfile
            byte[] merklePath = Merkle.getMerklePath(merkleTree, (int) nextIndex);
            System.arraycopy(merklePath, 0, txSibling, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_OFFSET, merklePath.length);


            // sign bundle hash and store signature in Milestone Transaction
            byte[] normBundleHash = Winternitz.normalizedBundle(bundleHash);
            byte[] subseed = Winternitz.subseed(SpongeFactory.Mode.S256, Hex.decode(seed), (int) nextIndex);
            final byte[] key = Winternitz.key(SpongeFactory.Mode.S256, subseed, 1);
            byte[] bundleFragment = Arrays.copyOfRange(normBundleHash, 0, 16);
            byte[] keyFragment = Arrays.copyOfRange(key, 0, 512);
            byte[] signature = Winternitz.signatureFragment(SpongeFactory.Mode.S256, bundleFragment, keyFragment);
            System.arraycopy(signature, 0, txMilestone, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_OFFSET, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE);
        }

        // attach, broadcast and store
        List<String> transactions = new ArrayList<>();
        transactions.add(Hex.toHexString(txSibling));
        transactions.add(Hex.toHexString(txMilestone));
        List<String> powResult = attachToTangleStatement(txToApprove.get(0), txToApprove.get(1), minWeightMagnitude, transactions);
        storeTransactionsStatement(powResult);
        broadcastTransactionsStatement(powResult);
    }

    //
    // FUNCTIONAL COMMAND ROUTES
    //
    private Function<Map<String, Object>, AbstractResponse> addNeighbors() {
        return request -> {
            List<String> uris = getParameterAsList(request,"uris",0);
            log.debug("Invoking 'addNeighbors' with {}", uris);
            return addNeighborsStatement(uris);
        };
    }

    private Function<Map<String, Object>, AbstractResponse> attachToTangle() {
        return request -> {
            final Hash trunkTransaction  = HashFactory.TRANSACTION.create(getParameterAsStringAndValidate(request,"trunkTransaction", HASH_SIZE));
            final Hash branchTransaction = HashFactory.TRANSACTION.create(getParameterAsStringAndValidate(request,"branchTransaction", HASH_SIZE));
            final int minWeightMagnitude = getParameterAsInt(request,"minWeightMagnitude");

            final List<String> hbytes = getParameterAsList(request,"hbytes", BYTES_SIZE);

            List<String> elements = attachToTangleStatement(trunkTransaction, branchTransaction, minWeightMagnitude, hbytes);
            return AttachToTangleResponse.create(elements);
        };
    }

    private Function<Map<String, Object>, AbstractResponse>  broadcastTransactions() {
        return request -> {
            final List<String> hbytes = getParameterAsList(request,"hbytes", BYTES_SIZE);
            broadcastTransactionsStatement(hbytes);
            return AbstractResponse.createEmptyResponse();
        };
    }

    private Function<Map<String, Object>, AbstractResponse> findTransactions() {
        return request -> {
            try {
                return findTransactionsStatement(request);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }

    private Function<Map<String, Object>, AbstractResponse> getBalances() {
        return request -> {
            final List<String> addresses = getParameterAsList(request,"addresses", HASH_SIZE);
            final List<String> tips = request.containsKey("tips") ?
                    getParameterAsList(request,"tips", HASH_SIZE):
                    null;
            final int threshold = getParameterAsInt(request, "threshold");

            try {
                return getBalancesStatement(addresses, tips, threshold);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }

    private Function<Map<String, Object>, AbstractResponse> getInclusionStates() {
        return request -> {
            if (invalidSubtangleStatus()) {
                return ErrorResponse.create(INVALID_SUBTANGLE);
            }
            final List<String> transactions = getParameterAsList(request, "transactions", HASH_SIZE);
            final List<String> tips = getParameterAsList(request, "tips", HASH_SIZE);

            try {
                return getInclusionStatesStatement(transactions, tips);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }

    private Function<Map<String, Object>, AbstractResponse> getNeighbors() {
        return request -> getNeighborsStatement();
    }

    private Function<Map<String, Object>, AbstractResponse> getNodeInfo() {
        return request -> {
            try {
                return getNodeInfoStatement();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }

    private Function<Map<String, Object>, AbstractResponse> getNodeAPIConfiguration() {
        return request -> getNodeAPIConfigurationStatement();
    }

    private Function<Map<String, Object>, AbstractResponse> getTips() {
        return request -> {
            try {
                return getTipsStatement();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }

    private Function<Map<String, Object>, AbstractResponse> getTransactionsToApprove() {
        return request -> {
            Optional<Hash> reference = request.containsKey("reference") ?
                    Optional.of(HashFactory.TRANSACTION.create(getParameterAsStringAndValidate(request,"reference", HASH_SIZE)))
                    : Optional.empty();
            int depth = getParameterAsInt(request, "depth");

            return getTransactionsToApproveStatement(depth, reference);
        };
    }

    private Function<Map<String, Object>, AbstractResponse> getHBytes() {
        return request -> {
            final List<String> hashes = getParameterAsList(request,"hashes", HASH_SIZE);
            try {
                return getHBytesStatement(hashes);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }

    private Function<Map<String, Object>, AbstractResponse> interruptAttachingToTangle() {
        return request -> interruptAttachingToTangleStatement();
    }

    private Function<Map<String, Object>, AbstractResponse> removeNeighbors() {
        return request -> {
            List<String> uris = getParameterAsList(request,"uris",0);
            log.debug("Invoking 'removeNeighbors' with {}", uris);
            return removeNeighborsStatement(uris);
        };
    }

    private Function<Map<String, Object>, AbstractResponse> storeTransactions() {
        return request -> {
            try {
                final List<String> hbytes = getParameterAsList(request,"hbytes", BYTES_SIZE);
                storeTransactionsStatement(hbytes);
            } catch (Exception e) {
                //transaction not valid
                return ErrorResponse.create("Invalid bytes input");
            }
            return AbstractResponse.createEmptyResponse();
        };
    }

    private Function<Map<String, Object>, AbstractResponse> getMissingTransactions() {
        return request -> {
            synchronized (transactionRequester) {
                List<String> missingTx = Arrays.stream(transactionRequester.getRequestedTransactions())
                        .map(Hash::toString)
                        .collect(Collectors.toList());
                return GetTipsResponse.create(missingTx);
            }
        };
    }

    private Function<Map<String, Object>, AbstractResponse> checkConsistency() {
        return request -> {
            if (invalidSubtangleStatus()) {
                return ErrorResponse.create(INVALID_SUBTANGLE);
            }
            final List<String> transactions = getParameterAsList(request,"tails", HASH_SIZE);
            try {
                return checkConsistencyStatement(transactions);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }
    private Function<Map<String, Object>, AbstractResponse> wereAddressesSpentFrom() {
        return request -> {
            final List<String> addresses = getParameterAsList(request,"addresses", HASH_SIZE);
            try {
                return wereAddressesSpentFromStatement(addresses);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }
}
