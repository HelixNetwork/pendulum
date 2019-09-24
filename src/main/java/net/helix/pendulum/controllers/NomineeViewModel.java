package net.helix.pendulum.controllers;

import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.IntegerIndex;
import net.helix.pendulum.model.persistables.Nominees;
import net.helix.pendulum.storage.Tangle;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class NomineeViewModel {

    private final Nominees nominees;
    private static final Map<Integer, NomineeViewModel> allNominees = new ConcurrentHashMap<>();

    private NomineeViewModel(final Nominees nominees) {
        this.nominees = nominees;
    }

    public NomineeViewModel(final int index, final Set<Hash> nomineeHashes) {
        this.nominees = new Nominees();
        this.nominees.index = new IntegerIndex(index);
        this.nominees.set = nomineeHashes;
    }

    public static NomineeViewModel get(Tangle tangle, int index) throws Exception {
        NomineeViewModel nomineeViewModel = allNominees.get(index);
        if(nomineeViewModel == null && load(tangle, index)) {
            nomineeViewModel = allNominees.get(index);
        }
        return nomineeViewModel;
    }


    public static boolean load(Tangle tangle, int index) throws Exception {
        Nominees nominees = (Nominees) tangle.load(Nominees.class, new IntegerIndex(index));
        if (nominees != null && nominees.index != null) {
            allNominees.put(index, new NomineeViewModel(nominees));
            return true;
        }
        return false;
    }


    public boolean store(Tangle tangle) throws Exception {
        return tangle.save(nominees, nominees.index);
    }

    public int size() {
        return nominees.set.size();
    }


    public Set<Hash> getHashes() {
        return nominees.set;
    }


    public Integer index() {
        return nominees.index.getValue();
    }


    public void delete(Tangle tangle) throws Exception {
        tangle.delete(Nominees.class, nominees.index);
    }

    public static NomineeViewModel findClosestPrevNominees(Tangle tangle, int index) throws Exception {
        // search for the previous milestone preceding our index
        NomineeViewModel previousNomineeViewModel = null;
        int currentIndex = index + 1;
        while(previousNomineeViewModel == null && --currentIndex >= 0) {
            previousNomineeViewModel = NomineeViewModel.get(tangle, currentIndex);
        }

        return previousNomineeViewModel;
    }

    @Override
    public String toString() {
        String hashes = getHashes().stream().map(Hash::toString).collect(Collectors.joining(","));
        return "round #" + index() + " (" + hashes + ")";
    }
}
