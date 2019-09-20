package net.helix.pendulum.service.tipselection.impl;

import net.helix.pendulum.controllers.RoundViewModel;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.service.milestone.MilestoneTracker;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.service.tipselection.EntryPointSelector;
import net.helix.pendulum.storage.Tangle;


/**
 * Implementation of {@link EntryPointSelector} that given a depth {@code N}, returns a N-deep milestone.
 * Meaning <code>milestone(latestSolid - depth)</code>
 * Used as a starting point for the random walk.
 */
public class EntryPointSelectorImpl implements EntryPointSelector {

    private final Tangle tangle;
    private final SnapshotProvider snapshotProvider;
    private final MilestoneTracker milestoneTracker;

    /**
     * Constructor for Entry Point Selector
     * @param tangle Tangle object which acts as a database interface.
     * @param snapshotProvider accesses snapshots of the ledger state
     * @param milestoneTracker  used to get latest milestone.
     */
    public EntryPointSelectorImpl(Tangle tangle, SnapshotProvider snapshotProvider,
                                  MilestoneTracker milestoneTracker) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.milestoneTracker = milestoneTracker;
    }

    @Override
    public Hash getEntryPoint(int depth) throws Exception {
        int milestoneIndex = Math.max(snapshotProvider.getLatestSnapshot().getIndex() - depth - 1,
                snapshotProvider.getInitialSnapshot().getIndex());
        RoundViewModel roundViewModel = RoundViewModel.findClosestNextRound(tangle, milestoneIndex,
                milestoneTracker.getCurrentRoundIndex());
        //todo which transaction using as solid entry point when there are multiple milestones / confirmed tips?
        //todo sometimes produces error here because entry point is not consistent (not sure under what conditions)
        //temporary solution: select random
        if (roundViewModel != null && !roundViewModel.getHashes().isEmpty()) {
            return roundViewModel.getRandomMilestone(tangle);
        }

        return snapshotProvider.getLatestSnapshot().getHash();
    }
}