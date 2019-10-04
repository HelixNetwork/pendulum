package net.helix.pendulum.service.validatormanager.impl;

import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.service.API;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ValidatorPublisher {
    private static final Logger log = LoggerFactory.getLogger(ValidatorPublisher.class);
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private PendulumConfig config;
    private API api;

    private int delay;
    private int mwm;
    private Boolean sign;
    private int currentKeyIndex;
    private int startRoundDelay;

    public ValidatorPublisher(PendulumConfig configuration, API api) {
        this.config = configuration;
        this.api = api;
        delay = config.getUpdateValidatorDelay();
        mwm = config.getMwm();
        sign = !config.isDontValidateTestnetValidatorManagerSig();
        currentKeyIndex = 0;
        startRoundDelay = config.getStartRoundDelay();
    }

    public void startScheduledExecutorService() {
        log.info("ValidatorPublisher scheduledExecutorService started.");
        log.debug("Set of validators updated in: {} interval", delay / 1000 + "s");
        scheduledExecutorService.scheduleWithFixedDelay(getRunnableUpdateValidators(), delay, delay,  TimeUnit.MILLISECONDS);
    }



    private void UpdateValidators() throws Exception {
        log.debug("Publishing new Validator...");
        //api.publishValidator(startRoundDelay, mwm, sign, currentKeyIndex, (int) Math.pow(2, config.getValidatorManagerKeyDepth())); //todo remove after refactoring
        // (BundleTypes type, final String address, final int minWeightMagnitude, boolean sign, int keyIndex, int maxKeyIndex, boolean join, int startRoundDelay)
        //api.publish(BundleTypes.validator, Hash.NULL_HASH.toString(), mwm, sign, currentKeyIndex, (int) Math.pow(2, config.getValidatorManagerKeyDepth()), false, startRoundDelay);
        api.publishValidator(startRoundDelay, mwm, sign, currentKeyIndex, (int) Math.pow(2, config.getValidatorManagerKeyDepth()));
        currentKeyIndex += 1;
    }

    private Runnable getRunnableUpdateValidators() {
        return () -> {
            try {
                UpdateValidators();
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
    }

    public void shutdown() {
        log.info("Shutting down ValidatorPublisher Thread");
        scheduledExecutorService.shutdown();
    }
}
