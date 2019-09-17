package net.helix.hlx.service.curator.impl;

import net.helix.hlx.BundleValidator;
import net.helix.hlx.conf.HelixConfig;
import net.helix.hlx.controllers.AddressViewModel;
import net.helix.hlx.controllers.BundleViewModel;
import net.helix.hlx.controllers.RoundViewModel;
import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.crypto.Merkle;
import net.helix.hlx.crypto.SpongeFactory;
import net.helix.hlx.model.Hash;
import net.helix.hlx.model.HashFactory;
import net.helix.hlx.service.curator.*;
import net.helix.hlx.service.milestone.MilestoneSolidifier;
import net.helix.hlx.service.snapshot.SnapshotProvider;
import net.helix.hlx.service.utils.RoundIndexUtil;
import net.helix.hlx.storage.Tangle;
import net.helix.hlx.utils.log.interval.IntervalLogger;
import net.helix.hlx.utils.thread.DedicatedScheduledExecutorService;
import net.helix.hlx.utils.thread.SilentScheduledExecutorService;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static net.helix.hlx.service.curator.CandidateValidity.INCOMPLETE;
import static net.helix.hlx.service.curator.CandidateValidity.INVALID;
import static net.helix.hlx.service.curator.CandidateValidity.VALID;

/**
 * This class implements the CandidateTracker interface and is responsible for searching for applicants,
 * processing and validating their requests and adding / removing candidates to the set of nominees.
 *
 * The basic flow is:
 * A search for candidate_bundle(s).<br />
 * B approve validity and solidity of the application_bundle.<br />
 * C validate candidate's registration data.<br />
 * D (update candidate's reputation).<br />
 * E sample candidate's from queue pseudo-randomly with reputation as a soft bias.<br />
 */

public class CandidateTrackerImpl implements CandidateTracker {

    /**
     * Holds the amount of milestone candidates that will be analyzed per iteration of the background worker.<br />
     */
    private static final int MAX_CANDIDATES_TO_ANALYZE = 5;

    /**
     * Holds the time (in milliseconds) between iterations of the background worker.<br />
     */
    private static final int RESCAN_INTERVAL = 1000;

    /**
     * Holds the logger of this class (a rate limited logger that doesn't spam the CLI output).<br />
     */
    private static final IntervalLogger log = new IntervalLogger(CandidateTrackerImpl.class);

    /**
     * Holds a reference to the manager of the background worker.<br />
     */
    private final SilentScheduledExecutorService executorService = new DedicatedScheduledExecutorService(
            "Candidate Tracker", log.delegate());

    /**
     * Holds the Tangle object which acts as a database interface.<br />
     */
    private Tangle tangle;
    /**
     * Holds the HelixConfig object which acts as a config.<br />
     */
    private HelixConfig config;

    /**
     * Service class containing the business logic of the curator package.
     */
    private CuratorService curatorService;

    private SnapshotProvider snapshotProvider;

    /**
     * Solidifies candidate transactions
     */
    private CandidateSolidifier candidateSolidifier;

    /**
     * A set that allows us to keep track of the candidates that have been seen and added to the {@link
     * #candidatesToAnalyze} already.<br />
     */
    private final Set<Hash> seenCandidates = new HashSet<>();

    /**
     * Maps candidateAddress hash to weight<br />
     */
    private Set<Hash> nominees = new HashSet<>();

    private int startRound;

    /**
     * A list of candidates that still have to be analyzed.<br />
     */
    private final Deque<Hash> candidatesToAnalyze = new ArrayDeque<>();

    /**
     * Initial nomination probability of a candidate <br />
     */
    private Double initialNomProb = 0.0;

    /**
     * A flag that allows us to detect if the background worker is in its first iteration (for different log
     * handling).<br />
     */
    private boolean firstRun = true;

    /**
     * Flag which indicates if this tracker has finished its initial scan of all old milestone candidates.<br />
     */
    private boolean initialized = false;


    /**
     * This method initializes the instance and registers its dependencies.<br />
     * <br />
     * Note: Instead of handing over the dependencies in the constructor, we register them lazy. This allows us to have
     *       circular dependencies because the instantiation is separated from the dependency injection. To reduce the
     *       amount of code that is necessary to correctly instantiate this class, we return the instance itself which
     *       allows us to still instantiate, initialize and assign in one line - see Example:<br />
     *       <br />
     *       {@code candidateTracker = new candidateTrackerImpl().init(...);}
     *
     * @param tangle Tangle object which acts as a database interface
     * @param config configuration object which allows us to determine the important config parameters of the node
     * @return the initialized instance itself to allow chaining
     */
    public CandidateTrackerImpl init(Tangle tangle, SnapshotProvider snapshotProvider, CuratorService curatorService, CandidateSolidifier candidateSolidifier, HelixConfig config) {

        this.tangle = tangle;
        this.config = config;
        this.snapshotProvider = snapshotProvider;
        this.curatorService = curatorService;
        this.candidateSolidifier = candidateSolidifier;

        nominees = config.getInitialNominees();
        startRound = RoundIndexUtil.getRound(RoundIndexUtil.getCurrentTime(),  config.getGenesisTime(), config.getRoundDuration(), 2);

        return this;
    }

    @Override
    public int getStartRound() {
        return startRound;
    }

    /**
     * This method contains the logic for scanning for new candidates that gets executed in a background
     * worker.<br />
     * <br />
     * It first collects all new  candidates that need to be analyzed, then analyzes them and finally checks if
     * the initialization is complete. In addition to this scanning logic it also issues regular log messages about the
     * progress of the scanning.<br />
     *
     * A search for candidate_bundle(s)  {@link #collectNewCandidates}
     * B approve validity and solidity of the candidate_bundle.{@link #processCandidate} <br />
     *
     */
    private void candidateTrackerThread() {
        try {
            logProgress();
            collectNewCandidates(); // A

            // additional log message on the first run to indicate how many application candidates we have in total
            if (firstRun) {
                firstRun = false;
                logProgress();
            }
            analyzeCandidates(); // B
            checkIfInitializationComplete();
        } catch (CuratorException e) {
            log.error("error while analyzing the applying candidates", e);
        }
    }

    /**
     * This method collects the new candidates that have not been "seen" before, by collecting them in the {@link
     * #candidatesToAnalyze} queue.<br />
     * <br />
     * We simply request all transaction that are originating from the coordinator address and treat them as potential
     * milestone candidates.<br />
     *
     * @throws CuratorException if anything unexpected happens while collecting the new milestone candidates
     */
    //@VisibleForTesting
    private void collectNewCandidates() throws CuratorException {
        try {
            for (Hash hash : AddressViewModel.load(tangle, this.config.getCuratorAddress()).getHashes()) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (seenCandidates.add(hash)) {
                    candidatesToAnalyze.addFirst(hash);
                }
            }
        } catch (Exception e) {
            throw new CuratorException("failed to collect the new candidates", e);
        }
    }

    /**
     * This method analyzes the candidates by working through the {@link #candidatesToAnalyze}
     * queue.<br />
     * <br />
     * We only process {@link #MAX_CANDIDATES_TO_ANALYZE} at a time, to give the caller the option to terminate early
     * and pick up new milestones as fast as possible without being stuck with analyzing the old ones for too
     * long.<br />
     *
     * @throws CuratorException if anything unexpected happens while analyzing the milestone candidates
     */
    private void analyzeCandidates() throws CuratorException {
        int toAnalyze = Math.min(candidatesToAnalyze.size(), MAX_CANDIDATES_TO_ANALYZE);
        for (int i = 0; i < toAnalyze; i++) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            Hash candidateTransactionHash = candidatesToAnalyze.pollFirst();
            if(!processCandidate(candidateTransactionHash)) {
                seenCandidates.remove(candidateTransactionHash);
            }
        }
    }

    @Override
    public boolean processCandidate(Hash transactionHash) throws CuratorException {
        try {
            return processCandidate(TransactionViewModel.fromHash(tangle, transactionHash));
        } catch (Exception e) {
            throw new CuratorException("unexpected error while analyzing the transaction " + transactionHash, e);
        }
    }

    /**
     * {@inheritDoc}
     * <br />
     * If we detect a candidate that is either {@code INCOMPLETE} or not solid, yet we hand it over to the
     * {@link MilestoneSolidifier} that takes care of requesting the missing parts of the milestone bundle.<br />
     */
    @Override
    public boolean processCandidate(TransactionViewModel transaction) throws CuratorException {
            try {
                if (this.config.getCuratorAddress().equals(transaction.getAddressHash()) && transaction.getCurrentIndex() == transaction.lastIndex()) {
                    log.info("Process Candidate Transaction " + transaction.getHash());
                    // get tail
                    BundleViewModel bundle = BundleViewModel.load(tangle, transaction.getBundleHash());
                    TransactionViewModel tail = null;
                    for (Hash bundleTx : bundle.getHashes()) {
                        TransactionViewModel tx = TransactionViewModel.fromHash(tangle, bundleTx);
                        if (tx.getCurrentIndex() == 0) {
                            tail = tx;
                        }
                    }
                    switch (curatorService.validateCandidate(tail, SpongeFactory.Mode.S256, config.getNomineeSecurity(), nominees)) {
                        case VALID:
                            // remove old address
                            removeFromNomineeQueue(tail.getAddressHash());
                            // add new address
                            Hash newAddress = HashFactory.ADDRESS.create(Arrays.copyOfRange(transaction.getSignature(), 0, Hash.SIZE_IN_BYTES));
                            addToNomineeQueue(newAddress);
                            // set start round
                            startRound = RoundIndexUtil.getRound(RoundIndexUtil.getCurrentTime(),  config.getGenesisTime(), config.getRoundDuration(), config.getStartRoundDelay());
                            log.info("Candidate Transaction " + transaction.getHash() + " is VALID, new Address: " + newAddress + ", start round: " + startRound);

                            if (!transaction.isSolid()) {
                                //int currentRoundIndex = (int) (System.currentTimeMillis() - config.getGenesisTime()) / config.getRoundDuration();
                                int currentRoundIndex = RoundIndexUtil.getRound(RoundIndexUtil.getCurrentTime(), config.getGenesisTime(), config.getRoundDuration());
                                candidateSolidifier.add(transaction.getHash(), currentRoundIndex);
                            }
                            break;

                        case INCOMPLETE:
                            log.info("Candidate Transaction " + transaction.getHash() + " is INCOMPLETE");
                            return false;

                        case INVALID:
                            // do not re-analyze anymore
                            log.info("Candidate Transaction " + transaction.getHash() + " is INVALID");
                            return true;

                        default:
                            // we can consider the candidate processed and move on w/o farther action
                    }
                }

                return true;
            } catch (Exception e) {
                throw new CuratorException("unexpected error while analyzing the transaction: " +   transaction.getHash(), e);
            }
    }

    /**
     * {@inheritDoc}
     * Adds a candidate to the {@code candidatesToNominate}
     * A corresponding initial probability based on reputation (work and time) is added, but isn't meaningful until the reputation update.
     * <p>
     * In addition to setting the internal properties, we also issue a log message and publish the change to the ZeroMQ
     * message processor so external receivers get informed about this change.
     * </p>
     *
     * @param candidateAddress
     *
     */

    private void addToNomineeQueue(Hash candidateAddress) {
        //Double w = (curatorService.getCandidateNormalizedWeight(candidateAddress, this.seenCandidates));
        this.nominees.add(candidateAddress);

        tangle.publish("nac %d", candidateAddress); //nac = newly added candidate
        log.delegate().info("New candidate {} added", candidateAddress);
    }

    private void removeFromNomineeQueue(Hash candidateAddress) {
        this.nominees.remove(candidateAddress);

        tangle.publish("nrc %d", candidateAddress); //nac = newly removed candidate
        log.delegate().info("Candidate {} removed", candidateAddress);
    }

    @Override
    public Set<Hash> getNominees(){
        return this.nominees;
    }

    /**
     * This method emits a log message about the scanning progress.<br />
     * <br />
     * It only emits a log message if we have more than one {@link #candidatesToAnalyze}, which means that the
     * very first call to this method in the "first run" on {@link #candidateTrackerThread()} will not produce any
     * output (which is the reason why we call this method a second time after we have collected all the
     * candidates in the "first run").<br />
     */
    private void logProgress() {
        if (candidatesToAnalyze.size() > 1) {
            log.info("Processing full node candidates (" + candidatesToAnalyze.size() + " remaining) ...");
        }
    }

    /**
     * This method checks if the initialization is complete.<br />
     * <br />
     * It simply checks if the {@link #initialized} flag is not set yet and there are no more {@link
     * #candidatesToAnalyze}. If the initialization was complete, we issue a log message and set the
     * corresponding flag to {@code true}.<br />
     */
    private void checkIfInitializationComplete() {
        if (!initialized && candidatesToAnalyze.size() == 0) {
            initialized = true;

            log.info("Processing candidates ... [DONE]").triggerOutput(true);
        }
    }

    /**
     * {@inheritDoc}
     * <br />
     * We repeatedly call {@link #candidateTrackerThread()} to search for new application bundles in the database.
     * This is a bit inefficient and should at some point maybe be replaced with a check on transaction arrival, but
     * this would required adjustments in the whole way IRI (and Helix-1.0) handles transactions and is therefore postponed for
     * now.<br />
     */
    @Override
    public void start() {
        executorService.silentScheduleWithFixedDelay(this::candidateTrackerThread, 0, RESCAN_INTERVAL,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void shutdown() {
        executorService.shutdownNow();
    }
}
