package net.helix.hlx.service.tipselection.impl;

import net.helix.hlx.controllers.RoundViewModel;
import net.helix.hlx.model.Hash;
import net.helix.hlx.service.milestone.LatestMilestoneTracker;
import net.helix.hlx.service.snapshot.SnapshotProvider;
import net.helix.hlx.service.tipselection.EntryPointSelector;
import net.helix.hlx.storage.Tangle;

import java.util.Random;


/**
 * Implementation of {@link EntryPointSelector} that given a depth {@code N}, returns a N-deep milestone.
 * Meaning <code>milestone(latestSolid - depth)</code>
 * Used as a starting point for the random walk.
 */
public class EntryPointSelectorImpl implements EntryPointSelector {

    private final Tangle tangle;
    private final SnapshotProvider snapshotProvider;
    private final LatestMilestoneTracker latestMilestoneTracker;

    /**
     * Constructor for Entry Point Selector
     * @param tangle Tangle object which acts as a database interface.
     * @param snapshotProvider accesses snapshots of the ledger state
     * @param latestMilestoneTracker  used to get latest milestone.
     */
    public EntryPointSelectorImpl(Tangle tangle, SnapshotProvider snapshotProvider,
                                  LatestMilestoneTracker latestMilestoneTracker) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.latestMilestoneTracker = latestMilestoneTracker;
    }

    @Override
    public Hash getEntryPoint(int depth) throws Exception {
        int milestoneIndex = Math.max(snapshotProvider.getLatestSnapshot().getIndex() - depth - 1,
                snapshotProvider.getInitialSnapshot().getIndex());
        RoundViewModel roundViewModel = RoundViewModel.findClosestNextRound(tangle, milestoneIndex,
                latestMilestoneTracker.getLatestRoundIndex());
        //todo which transaction using as solid entry point when there are multiple milestones / confirmed tips?
        //temporary solution: select random
        if (roundViewModel != null && roundViewModel.getHashes() != null) {
            int size = roundViewModel.getHashes().size();
            int item = new Random().nextInt(size);
            int i = 0;
            for(Hash obj : roundViewModel.getHashes()) {
                if (i == item)
                    return obj;
                i++;
            }
        }

        return snapshotProvider.getLatestSnapshot().getHash();
    }
}