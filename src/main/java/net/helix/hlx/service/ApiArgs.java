package net.helix.hlx.service;

import net.helix.hlx.BundleValidator;
import net.helix.hlx.Helix;
import net.helix.hlx.TransactionValidator;
import net.helix.hlx.XI;
import net.helix.hlx.conf.HelixConfig;
import net.helix.hlx.controllers.TipsViewModel;
import net.helix.hlx.network.Node;
import net.helix.hlx.network.TransactionRequester;
import net.helix.hlx.service.ledger.LedgerService;
import net.helix.hlx.service.milestone.LatestMilestoneTracker;
import net.helix.hlx.service.snapshot.SnapshotProvider;
import net.helix.hlx.service.spentaddresses.SpentAddressesService;
import net.helix.hlx.service.tipselection.TipSelector;
import net.helix.hlx.storage.Tangle;

public class ApiArgs {

    /**
     * configuration
     */
    private HelixConfig configuration;

    /**
     * If a command is not in the standard API,
     * we try to process it as a Nashorn JavaScript module through {@link XI}
     */
    private XI XI;

    /**
     * Service where transactions get requested
     */
    private TransactionRequester transactionRequester;

    /**
     * Service to check if addresses are spent
     */
    private SpentAddressesService spentAddressesService;

    /**
     *  The transaction storage
     */
    private Tangle tangle;

    /**
     * Validates bundles
     */
    private BundleValidator bundleValidator;

    /**
     * Manager of our currently taken snapshots
     */
    private SnapshotProvider snapshotProvider;

    /**
     * contains all the relevant business logic for modifying and calculating the ledger state.
     */
    private LedgerService ledgerService;

    /**
     * Handles and manages neighbors
     */
    private Node node;

    /**
     * Handles logic for selecting tips based on other transactions
     */
    private TipSelector tipsSelector;

    /**
     * Contains the current tips of this node
     */
    private TipsViewModel tipsViewModel;

    /**
     * Validates transactions
     */
    private TransactionValidator transactionValidator;

    /**
     * Service that tracks the latest milestone
     */
    private LatestMilestoneTracker latestMilestoneTracker;

    /**
     * Graph
     */
    private Graphstream graph;

    public ApiArgs(HelixConfig configuration) {
        this.configuration = configuration;
    }

    public ApiArgs(Helix helix, XI xi) {
        this.configuration = helix.configuration;
        this.XI = xi;
        this.transactionRequester = helix.transactionRequester;
        this.spentAddressesService = helix.spentAddressesService;
        this.tangle = helix.tangle;
        this.bundleValidator = helix.bundleValidator;
        this.snapshotProvider = helix.snapshotProvider;
        this.ledgerService = helix.ledgerService;
        this.node = helix.node;
        this.tipsSelector = helix.tipsSelector;
        this.tipsViewModel = helix.tipsViewModel;
        this.transactionValidator = helix.transactionValidator;
        this.latestMilestoneTracker = helix.latestMilestoneTracker;
        this.graph = helix.graph;
    }

    public HelixConfig getConfiguration() {
        return configuration;
    }

    public void setConfiguration(HelixConfig configuration) {
        this.configuration = configuration;
    }

    public net.helix.hlx.XI getXI() {
        return XI;
    }

    public void setXI(net.helix.hlx.XI XI) {
        this.XI = XI;
    }

    public TransactionRequester getTransactionRequester() {
        return transactionRequester;
    }

    public void setTransactionRequester(TransactionRequester transactionRequester) {
        this.transactionRequester = transactionRequester;
    }

    public SpentAddressesService getSpentAddressesService() {
        return spentAddressesService;
    }

    public void setSpentAddressesService(SpentAddressesService spentAddressesService) {
        this.spentAddressesService = spentAddressesService;
    }

    public Tangle getTangle() {
        return tangle;
    }

    public void setTangle(Tangle tangle) {
        this.tangle = tangle;
    }

    public BundleValidator getBundleValidator() {
        return bundleValidator;
    }

    public void setBundleValidator(BundleValidator bundleValidator) {
        this.bundleValidator = bundleValidator;
    }

    public SnapshotProvider getSnapshotProvider() {
        return snapshotProvider;
    }

    public void setSnapshotProvider(SnapshotProvider snapshotProvider) {
        this.snapshotProvider = snapshotProvider;
    }

    public LedgerService getLedgerService() {
        return ledgerService;
    }

    public void setLedgerService(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    public Node getNode() {
        return node;
    }

    public void setNode(Node node) {
        this.node = node;
    }

    public TipSelector getTipsSelector() {
        return tipsSelector;
    }

    public void setTipsSelector(TipSelector tipsSelector) {
        this.tipsSelector = tipsSelector;
    }

    public TipsViewModel getTipsViewModel() {
        return tipsViewModel;
    }

    public void setTipsViewModel(TipsViewModel tipsViewModel) {
        this.tipsViewModel = tipsViewModel;
    }

    public TransactionValidator getTransactionValidator() {
        return transactionValidator;
    }

    public void setTransactionValidator(TransactionValidator transactionValidator) {
        this.transactionValidator = transactionValidator;
    }

    public LatestMilestoneTracker getLatestMilestoneTracker() {
        return latestMilestoneTracker;
    }

    public void setLatestMilestoneTracker(LatestMilestoneTracker latestMilestoneTracker) {
        this.latestMilestoneTracker = latestMilestoneTracker;
    }

    public Graphstream getGraph() {
        return graph;
    }

    public void setGraph(Graphstream graph) {
        this.graph = graph;
    }
}
