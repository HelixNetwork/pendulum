package net.helix.hlx.service.milestone;

import net.helix.hlx.conf.HelixConfig;
import net.helix.hlx.service.API;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.Executors;

public class MSS {

    private static final Logger log = LoggerFactory.getLogger(MSS.class);
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private HelixConfig config;
    private API api;

    private String address;
    private String message;
    private int delay;
    private int mwm;
    private Boolean sign;

    public MSS(HelixConfig configuration, API api) {
        this.config = configuration;
        this.api = api;
        this.delay = this.config.getMsDelay();
        int minDelay = this.config.getMinDelay();
        this.mwm = this.config.getMwm();
        this.message = StringUtils.repeat('0', 1024);
        this.address = this.config.getCoordinator();
        this.sign = !this.config.isDontValidateTestnetMilestoneSig();

        if(this.delay < minDelay) {
            this.delay = minDelay;
        }
    }

    public void startScheduledExecutorService() {
        log.info("MSS scheduledExecutorService started.");
        log.info("Submitting Milestones every: " + this.delay + "s.");
        this.scheduledExecutorService.scheduleWithFixedDelay(this.getRunnablePublishMilestone(), 5, this.delay,  TimeUnit.SECONDS);
    }

    private void publishMilestone() throws Exception {
        log.info("Publishing next Milestone...");
        this.api.storeAndBroadcastMilestoneStatement(this.address, this.message, this.mwm, this.sign);
    }

    private Runnable getRunnablePublishMilestone() {
        return () -> {
            try {
                publishMilestone();
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
    }

    public void shutdown() {
        log.info("Shutting down MSS Thread");
        scheduledExecutorService.shutdown();
    }
}
