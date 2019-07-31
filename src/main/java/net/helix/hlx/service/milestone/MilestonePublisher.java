package net.helix.hlx.service.milestone;

import net.helix.hlx.conf.HelixConfig;
import net.helix.hlx.crypto.Merkle;
import net.helix.hlx.model.AddressHash;
import net.helix.hlx.model.Hash;
import net.helix.hlx.model.HashFactory;
import net.helix.hlx.service.API;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Random;

public class MilestonePublisher {

    private static final Logger log = LoggerFactory.getLogger(MilestonePublisher.class);
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private HelixConfig config;
    private API api;
    NomineeTracker nomineeTracker;

    private Hash address;
    private String message;
    private int delay;
    private int mwm;
    private Boolean sign;
    private int pubkeyDepth;
    private int keyfileIndex;
    private int maxKeyIndex;
    private int currentKeyIndex;
    private String seed;
    private int startRound;

    public boolean active;

    public MilestonePublisher(HelixConfig configuration, API api, NomineeTracker nomineeTracker) {
        this.config = configuration;
        this.api = api;
        this.nomineeTracker = nomineeTracker;
        this.delay = this.config.getMsDelay();
        this.mwm = this.config.getMwm();
        this.message = StringUtils.repeat('0', 1024);
        this.address = HashFactory.ADDRESS.create("6a8413edc634e948e3446806afde11b17e0e188faf80a59a8b1147a0600cc5db");
        this.sign = !this.config.isDontValidateTestnetMilestoneSig();
        this.pubkeyDepth = config.getNumberOfKeysInMilestone();
        this.keyfileIndex = 0;
        this.maxKeyIndex = (int) Math.pow(2,this.pubkeyDepth);
        this.currentKeyIndex = 1024;
        this.seed = "da6fdb6593d701c63acca421bf88d3fcd6699454ef4c6d6520767989aa5c2cce";     // todo how to store seed secure
        this.startRound = 0;

        /*try {
            generateKeyfile(this.seed);
        } catch (Exception e) {
            e.printStackTrace();
        }*/

        this.active = true;
    }

    public static String getSeed() throws IOException {
        StringBuilder seedBuilder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(new File("./src/main/resources/Nominee.key")))) {
            String[] fields = br.readLine().split(" ");
            seedBuilder.append(fields[1]);
        }
        return seedBuilder.toString();
    }

    private void generateKeyfile(String seed) throws Exception {
        log.info("Generating Keyfile (idx: " + this.keyfileIndex + ")");
        List<List<Hash>> merkleTree = Merkle.buildMerkleKeyTree(seed, pubkeyDepth, this.maxKeyIndex * this.keyfileIndex, this.maxKeyIndex);
        Merkle.createKeyfile(merkleTree, Hex.decode(seed), pubkeyDepth, "./src/main/resources/Nominee.key");
        this.address = HashFactory.ADDRESS.create(merkleTree.get(merkleTree.size()-1).get(0).bytes());
    }

    private void sendApplication() throws Exception {
        this.api.sendApplication(this.address.toString(), this.mwm, this.sign, this.maxKeyIndex * this.keyfileIndex);
    }

    private int getRound(long time) {
        return (int) (time - config.getGenesisTime()) / config.getRoundDuration();
    }

    private long getStartTime(int round) {
        return config.getGenesisTime() + (round * config.getRoundDuration());
    }

    public void startScheduledExecutorService() {
        log.info("MSS scheduledExecutorService started.");
        log.info("Submitting Milestones every: " + config.getRoundDuration() + "s.");
        int currentRound = getRound(System.currentTimeMillis());
        long startTimeNextRound = getStartTime(currentRound + 1);
        log.info("Next Round starts in " + ((startTimeNextRound - System.currentTimeMillis()) / 1000) + "s.");
        this.scheduledExecutorService.scheduleWithFixedDelay(this.getRunnablePublishMilestone(), (startTimeNextRound - System.currentTimeMillis()), config.getRoundDuration(),  TimeUnit.MILLISECONDS);
    }

    private void publishMilestone() throws Exception {
        if (!this.active) {
            if (this.startRound < getRound(System.currentTimeMillis()) && nomineeTracker.getLatestNominees().contains(this.address)) {
                this.startRound = nomineeTracker.getStartRound();
                log.info("Address " + this.address + " is accepted from round #" + this.startRound);
            }
            if (this.startRound == getRound(System.currentTimeMillis())) {
                this.active = true;
            }
        }
        if (this.active) {
            log.info("Publishing next Milestone...");
            if (currentKeyIndex < maxKeyIndex * (this.keyfileIndex + 1)) {
                this.api.storeAndBroadcastMilestoneStatement(this.address.toString(), this.message, this.mwm, this.sign, this.currentKeyIndex);
                this.currentKeyIndex += 1;
            } else {
                log.info("Keyfile has expired! The MilestonePublisher is paused until the new address is accepted by the network. This can take some time (<1m).");
                this.active = false;
                this.keyfileIndex += 1;
                generateKeyfile(this.seed);
                sendApplication();
            }
        }
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
