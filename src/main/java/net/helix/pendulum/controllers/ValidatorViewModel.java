package net.helix.pendulum.controllers;

import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.IntegerIndex;
import net.helix.pendulum.model.persistables.Validator;
import net.helix.pendulum.storage.Tangle;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ValidatorViewModel {

    private final Validator validators;
    private static final Map<Integer, ValidatorViewModel> allValidators = new ConcurrentHashMap<>();

    private ValidatorViewModel(final Validator validators) {
        this.validators = validators;
    }

    public ValidatorViewModel(final int index, final Set<Hash> validatorHashes) {
        this.validators = new Validator();
        this.validators.index = new IntegerIndex(index);
        this.validators.set = validatorHashes;
    }

    public static ValidatorViewModel get(Tangle tangle, int index) throws Exception {
        ValidatorViewModel validatorViewModel = allValidators.get(index);
        if(validatorViewModel == null && load(tangle, index)) {
            validatorViewModel = allValidators.get(index);
        }
        return validatorViewModel;
    }


    public static boolean load(Tangle tangle, int index) throws Exception {
        Validator validators = (Validator) tangle.load(Validator.class, new IntegerIndex(index));
        if (validators != null && validators.index != null) {
            allValidators.put(index, new ValidatorViewModel(validators));
            return true;
        }
        return false;
    }


    public boolean store(Tangle tangle) throws Exception {
        return tangle.save(validators, validators.index);
    }

    public int size() {
        return validators.set.size();
    }


    public Set<Hash> getHashes() {
        return validators.set;
    }


    public Integer index() {
        return validators.index.getValue();
    }


    public void delete(Tangle tangle) throws Exception {
        tangle.delete(Validator.class, validators.index);
    }

    public static ValidatorViewModel findClosestPrevValidators(Tangle tangle, int index) throws Exception {
        // search for the previous milestone preceding our index
        ValidatorViewModel previousValidatorViewModel = null;
        int currentIndex = index + 1;
        while(previousValidatorViewModel == null && --currentIndex >= 0) {
            previousValidatorViewModel = ValidatorViewModel.get(tangle, currentIndex);
        }

        return previousValidatorViewModel;
    }

    @Override
    public String toString() {
        String hashes = getHashes().stream().map(Hash::toString).collect(Collectors.joining(","));
        return "round #" + index() + " (" + hashes + ")";
    }
}
