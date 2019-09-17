package net.helix.hlx.controllers;

import net.helix.hlx.model.Hash;
import net.helix.hlx.model.IntegerIndex;
import net.helix.hlx.model.StateDiff;
import net.helix.hlx.storage.Tangle;

import java.util.Map;

/**
 * Created by paul on 5/6/17.
 */

 /**
 * The StateDiffViewModel class interacts with the StateDiff model class.
 * It consists of a roundIndex and the statediff model.
 */
public class StateDiffViewModel {
    private StateDiff stateDiff;
    private IntegerIndex roundIndex;

    public static StateDiffViewModel load(Tangle tangle, final int roundIndex) throws Exception {
        return new StateDiffViewModel((StateDiff) tangle.load(StateDiff.class, new IntegerIndex(roundIndex)), roundIndex);
    }

    public StateDiffViewModel(final Map<Hash, Long> state, final int roundIndex) {
        this.roundIndex = new IntegerIndex(roundIndex);
        this.stateDiff = new StateDiff();
        this.stateDiff.state = state;
    }

    public static boolean maybeExists(Tangle tangle, Hash hash) throws Exception {
        return tangle.maybeHas(StateDiff.class, hash);
    }

    StateDiffViewModel(final StateDiff diff, final int roundIndex) {
        this.roundIndex = new IntegerIndex(roundIndex);
        this.stateDiff = diff == null || diff.state == null ? new StateDiff(): diff;
    }

    public boolean isEmpty() {
        return stateDiff == null || stateDiff.state == null || stateDiff.state.size() == 0;
    }

    public int getRoundIndex() {
        return this.roundIndex.getValue();
    }

    public Map<Hash, Long> getDiff() {
        return stateDiff.state;
    }

    public boolean store(Tangle tangle) throws Exception {
        return tangle.save(stateDiff, roundIndex);
    }

    public void delete(Tangle tangle) throws Exception {
        tangle.delete(StateDiff.class, roundIndex);
    }
}
