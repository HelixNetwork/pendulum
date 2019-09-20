package net.helix.pendulum.service.curator.impl;

import net.helix.pendulum.conf.HelixConfig;
import net.helix.pendulum.service.API;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NomineePublisher {
    private static final Logger log = LoggerFactory.getLogger(NomineePublisher.class);
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private HelixConfig config;
    private API api;

    private int delay;
    private int mwm;
    private Boolean sign;
    private int currentKeyIndex;
    private int startRoundDelay;

    public NomineePublisher(HelixConfig configuration, API api) {
        this.config = configuration;
        this.api = api;
        delay = config.getUpdateNomineeDelay();
        mwm = config.getMwm();
        sign = !config.isDontValidateTestnetCuratorSig();
        currentKeyIndex = 0;
        startRoundDelay = config.getStartRoundDelay();
    }

    public void startScheduledExecutorService() {
        log.info("NomineePublisher scheduledExecutorService started.");
        log.debug("Set of nominees updated in: {} interval", delay / 1000 + "s");
        scheduledExecutorService.scheduleWithFixedDelay(getRunnableUpdateNominees(), delay, delay,  TimeUnit.MILLISECONDS);
    }



    private void UpdateNominees() throws Exception {
        log.debug("Publishing new Nominees...");
        //api.publishNominees(startRoundDelay, mwm, sign, currentKeyIndex, (int) Math.pow(2, config.getCuratorKeyDepth())); //todo remove after refactoring
        // (BundleTypes type, final String address, final int minWeightMagnitude, boolean sign, int keyIndex, int maxKeyIndex, boolean join, int startRoundDelay)
        //api.publish(BundleTypes.nominee, Hash.NULL_HASH.toString(), mwm, sign, currentKeyIndex, (int) Math.pow(2, config.getCuratorKeyDepth()), false, startRoundDelay);
        api.publishNominees(startRoundDelay, mwm, sign, currentKeyIndex, (int) Math.pow(2, config.getCuratorKeyDepth()));
        currentKeyIndex += 1;
    }

    private Runnable getRunnableUpdateNominees() {
        return () -> {
            try {
                UpdateNominees();
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
    }

    public void shutdown() {
        log.info("Shutting down NomineePublisher Thread");
        scheduledExecutorService.shutdown();
    }
}
