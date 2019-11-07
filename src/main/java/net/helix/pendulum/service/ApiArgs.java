package net.helix.pendulum.service;

import net.helix.pendulum.BundleValidator;
import net.helix.pendulum.Pendulum;
import net.helix.pendulum.TransactionValidator;
import net.helix.pendulum.XI;
import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.controllers.TipsViewModel;
import net.helix.pendulum.network.Node;
import net.helix.pendulum.service.ledger.LedgerService;
import net.helix.pendulum.service.milestone.MilestoneTracker;
import net.helix.pendulum.service.validatormanager.CandidateTracker;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.service.spentaddresses.SpentAddressesService;
import net.helix.pendulum.service.tipselection.TipSelector;
import net.helix.pendulum.storage.Tangle;

public class ApiArgs {

    /**
     * configuration
     */
    private PendulumConfig configuration;

    /**
     * If a command is not in the standard API,
     * we try to process it as a Nashorn JavaScript module through {@link XI}
     */
    private XI XI;

    /**
     * Service where transactions get requested
     */
    private Node.RequestQueue transactionRequester;

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
    private MilestoneTracker latestMilestoneTracker;

    /**
     * Service that tracks the latest milestone
     */
    private CandidateTracker candidateTracker;

//    /**
//     * Service that tracks the latest milestone
//     */
//    private ValidatorTracker validatorTracker;

    public ApiArgs(PendulumConfig configuration) {
        this.configuration = configuration;
    }

    public ApiArgs(Pendulum pendulum, XI xi) {
        this.configuration = pendulum.configuration;
        this.XI = xi;
        //this.transactionRequester = pendulum.requestQueue;
        this.spentAddressesService = pendulum.spentAddressesService;
        this.tangle = pendulum.tangle;
        this.bundleValidator = pendulum.bundleValidator;
        this.snapshotProvider = pendulum.snapshotProvider;
        this.ledgerService = pendulum.ledgerService;
        this.node = pendulum.node;
        this.tipsSelector = pendulum.tipsSelector;
        this.tipsViewModel = pendulum.tipsViewModel;
        this.transactionValidator = pendulum.transactionValidator;
        this.latestMilestoneTracker = pendulum.latestMilestoneTracker;
        this.candidateTracker = pendulum.candidateTracker;
    }

    public PendulumConfig getConfiguration() {
        return configuration;
    }

    public void setConfiguration(PendulumConfig configuration) {
        this.configuration = configuration;
    }

    public XI getXI() {
        return XI;
    }

    public void setXI(XI XI) {
        this.XI = XI;
    }

    //public Node.RequestQueue getTransactionRequester() {
    //    return transactionRequester;
    //}

    //public void setTransactionRequester(Node.RequestQueue transactionRequester) {
    //    this.transactionRequester = transactionRequester;
    //}

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

    public MilestoneTracker getMilestoneTracker() {
        return latestMilestoneTracker;
    }

    public void setMilestoneTracker(MilestoneTracker latestMilestoneTracker) {
        this.latestMilestoneTracker = latestMilestoneTracker;
    }

    public CandidateTracker getCandidateTracker() {
        return candidateTracker;
    }

    public void setCandidateTracker(CandidateTracker candidateTracker) {
        this.candidateTracker = candidateTracker;
    }
}
