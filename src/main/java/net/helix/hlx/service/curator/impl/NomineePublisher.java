package net.helix.hlx.service.curator.impl;

import net.helix.hlx.conf.HelixConfig;
import net.helix.hlx.service.API;
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
        this.delay = 30000;
        this.mwm = 2;
        this.sign = !this.config.isDontValidateTestnetMilestoneSig();
        this.currentKeyIndex = 0;
        this.startRoundDelay = 2;
    }

    public void startScheduledExecutorService() {
        log.info("NomineePublisher scheduledExecutorService started.");
        log.info("Update Nominees every: " + delay / 1000 + "s.");
        this.scheduledExecutorService.scheduleWithFixedDelay(this.getRunnableUpdateNominees(), this.delay, this.delay,  TimeUnit.MILLISECONDS);
    }

    private void UpdateNominees() throws Exception {
        log.info("Publishing new Nominees...");
        this.api.updateNominees(this.startRoundDelay, this.mwm, this.sign, this.currentKeyIndex);
        this.currentKeyIndex += 1;
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
