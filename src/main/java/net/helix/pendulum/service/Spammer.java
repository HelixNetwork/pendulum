package net.helix.pendulum.service;

import net.helix.pendulum.conf.SpamConfig;
import net.helix.pendulum.model.Hash;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Spammer {
    private static final Logger log = LoggerFactory.getLogger(Spammer.class);
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private API api;
    private SpamConfig config;

    private String address;
    private String message;
    private int delay;

    public Spammer(SpamConfig config, API api) {
        this.api = api;
        this.config = config;
        this.message = StringUtils.repeat('0', 1024*2);
        this.address = Hash.NULL_HASH.toString();
        this.delay = config.getSpamDelay();
    }

    public void startScheduledExecutorService() {
        log.info("Spammer scheduledExecutorService started.");
        log.info("Submitting Tx every: " + this.delay + "ms.");
        this.scheduledExecutorService.scheduleWithFixedDelay(this.getRunnableSendTx(), 20000, this.delay,  TimeUnit.MILLISECONDS);
    }

    private void sendTx() throws Exception {
        this.api.attachStoreAndBroadcast(this.address, this.message);
    }

    private Runnable getRunnableSendTx() {
        return () -> {
            try {
                sendTx();
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
    }

    public void shutdown() {
        log.info("Shutting down Spammer Thread");
        scheduledExecutorService.shutdown();
    }
}
