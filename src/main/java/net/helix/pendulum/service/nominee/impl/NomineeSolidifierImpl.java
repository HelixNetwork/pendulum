package net.helix.pendulum.service.nominee.impl;

import net.helix.pendulum.TransactionValidator;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.service.nominee.NomineeSolidifier;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.utils.log.interval.IntervalLogger;
import net.helix.pendulum.utils.thread.DedicatedScheduledExecutorService;
import net.helix.pendulum.utils.thread.SilentScheduledExecutorService;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


public class NomineeSolidifierImpl implements NomineeSolidifier {

    private static final int SOLIDIFICATION_QUEUE_SIZE = 2;

    private static final int SOLIDIFICATION_INTERVAL = 5000;

    private static final int SOLIDIFICATION_TRANSACTIONS_LIMIT = 50000;

    private static final IntervalLogger log = new IntervalLogger(NomineeSolidifier.class);

    private SnapshotProvider snapshotProvider;

    private TransactionValidator transactionValidator;

    private final SilentScheduledExecutorService executorService = new DedicatedScheduledExecutorService(
            "Nominee Solidifier", log.delegate());

    private final Map<Hash, Integer> newlyAddedCandidates = new ConcurrentHashMap<>();

    private final Map<Hash, Integer> unsolidCandidatesPool = new ConcurrentHashMap<>();

    private final Map<Hash, Integer> candidatesToSolidify = new HashMap<>();

    private Map.Entry<Hash, Integer> youngestCandidateInQueue = null;

    public NomineeSolidifierImpl init(SnapshotProvider snapshotProvider, TransactionValidator transactionValidator) {
        this.snapshotProvider = snapshotProvider;
        this.transactionValidator = transactionValidator;

        return this;
    }

    @Override
    public void add(Hash candidateHash, int roundIndex) {
        if (!unsolidCandidatesPool.containsKey(candidateHash) && !newlyAddedCandidates.containsKey(candidateHash) &&
                roundIndex > snapshotProvider.getInitialSnapshot().getIndex()) {

            newlyAddedCandidates.put(candidateHash, roundIndex);
        }
    }

    @Override
    public void start() {
        executorService.silentScheduleWithFixedDelay(this::candidateSolidificationThread, 0, SOLIDIFICATION_INTERVAL,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void shutdown() {
        executorService.shutdownNow();
    }

    private void addToSolidificationQueue(Map.Entry<Hash, Integer> candidateEntry) {
        if (candidatesToSolidify.containsKey(candidateEntry.getKey())) {
            return;
        }

        if (candidatesToSolidify.size() < SOLIDIFICATION_QUEUE_SIZE) {
            candidatesToSolidify.put(candidateEntry.getKey(), candidateEntry.getValue());

            if (youngestCandidateInQueue == null || candidateEntry.getValue() > youngestCandidateInQueue.getValue()) {
                youngestCandidateInQueue = candidateEntry;
            }
        } else if (candidateEntry.getValue() < youngestCandidateInQueue.getValue()) {
            candidatesToSolidify.remove(youngestCandidateInQueue.getKey());
            candidatesToSolidify.put(candidateEntry.getKey(), candidateEntry.getValue());

            determineYoungestCandidateInQueue();
        }
    }

    private void candidateSolidificationThread() {
        processNewlyAddedCandidates();
        processSolidificationQueue();
        refillSolidificationQueue();
    }

    private void processNewlyAddedCandidates() {
        for (Iterator<Map.Entry<Hash, Integer>> iterator = newlyAddedCandidates.entrySet().iterator();
             !Thread.currentThread().isInterrupted() && iterator.hasNext();) {

            Map.Entry<Hash, Integer> currentEntry = iterator.next();

            unsolidCandidatesPool.put(currentEntry.getKey(), currentEntry.getValue());

            if (youngestCandidateInQueue == null || currentEntry.getValue() < youngestCandidateInQueue.getValue()) {
                addToSolidificationQueue(currentEntry);
            }

            iterator.remove();
        }
    }

    private void processSolidificationQueue() {
        for (Iterator<Map.Entry<Hash, Integer>> iterator = candidatesToSolidify.entrySet().iterator();
             !Thread.currentThread().isInterrupted() && iterator.hasNext();) {

            Map.Entry<Hash, Integer> currentEntry = iterator.next();

            if (currentEntry.getValue() <= snapshotProvider.getInitialSnapshot().getIndex() || isSolid(currentEntry)) {
                unsolidCandidatesPool.remove(currentEntry.getKey());
                iterator.remove();

                if (youngestCandidateInQueue != null &&
                        currentEntry.getKey().equals(youngestCandidateInQueue.getKey())) {

                    youngestCandidateInQueue = null;
                }
            }
        }
    }

    private void refillSolidificationQueue() {
        if(youngestCandidateInQueue == null && !candidatesToSolidify.isEmpty()) {
            determineYoungestCandidateInQueue();
        }

        Map.Entry<Hash, Integer> nextSolidificationCandidate;
        while (!Thread.currentThread().isInterrupted() && candidatesToSolidify.size() < SOLIDIFICATION_QUEUE_SIZE &&
                (nextSolidificationCandidate = getNextSolidificationCandidate()) != null) {

            addToSolidificationQueue(nextSolidificationCandidate);
        }
    }

    private void determineYoungestCandidateInQueue() {
        youngestCandidateInQueue = null;
        for (Map.Entry<Hash, Integer> currentEntry : candidatesToSolidify.entrySet()) {
            if (youngestCandidateInQueue == null || currentEntry.getValue() > youngestCandidateInQueue.getValue()) {
                youngestCandidateInQueue = currentEntry;
            }
        }
    }

    private Map.Entry<Hash, Integer> getNextSolidificationCandidate() {
        Map.Entry<Hash, Integer> nextSolidificationCandidate = null;
        for (Map.Entry<Hash, Integer> candidateEntry : unsolidCandidatesPool.entrySet()) {
            if (!candidatesToSolidify.containsKey(candidateEntry.getKey()) && (nextSolidificationCandidate == null ||
                    candidateEntry.getValue() < nextSolidificationCandidate.getValue())) {

                nextSolidificationCandidate = candidateEntry;
            }
        }

        return nextSolidificationCandidate;
    }

    private boolean isSolid(Map.Entry<Hash, Integer> currentEntry) {
        if (unsolidCandidatesPool.size() > 1) {
            log.info("Solidifying candidate #" + currentEntry.getValue() +
                    " [" + candidatesToSolidify.size() + " / " + unsolidCandidatesPool.size() + "]");
        }

        try {
            return transactionValidator.checkSolidity(currentEntry.getKey(), true,
                    SOLIDIFICATION_TRANSACTIONS_LIMIT);
        } catch (Exception e) {
            log.error("Error while solidifying candidate #" + currentEntry.getValue(), e);

            return false;
        }
    }
}
