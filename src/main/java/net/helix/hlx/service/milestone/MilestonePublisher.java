package net.helix.hlx.service.milestone;

import net.helix.hlx.conf.HelixConfig;
import net.helix.hlx.crypto.Merkle;
import net.helix.hlx.model.Hash;
import net.helix.hlx.model.HashFactory;
import net.helix.hlx.service.API;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MilestonePublisher {

    private static final Logger log = LoggerFactory.getLogger(MilestonePublisher.class);
    private static String keyfile = "./src/main/resources/Nominee.key";
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

        delay = config.getMsDelay();
        mwm = config.getMwm();
        message = StringUtils.repeat('0', 1024);
        address = HashFactory.ADDRESS.create("6a8413edc634e948e3446806afde11b17e0e188faf80a59a8b1147a0600cc5db");
        sign = !config.isDontValidateTestnetMilestoneSig();
        pubkeyDepth = config.getNumberOfKeysInMilestone();
        keyfileIndex = 0;
        maxKeyIndex = (int) Math.pow(2,pubkeyDepth);
        currentKeyIndex = 0;
        seed = "da6fdb6593d701c63acca421bf88d3fcd6699454ef4c6d6520767989aa5c2cce";     // todo how to store seed secure
        startRound = 0;
        active = false;
    }

    private void writeKeyIndex() throws IOException {
        List<List<Hash>> merkleTree = Merkle.readKeyfile(new File(keyfile));
        Merkle.createKeyfile(merkleTree, Hex.decode(seed), pubkeyDepth, currentKeyIndex, keyfileIndex, keyfile);
    }

    private void readKeyfileMetadata() throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(new File(keyfile)))) {
            String[] fields = br.readLine().split(" ");
            pubkeyDepth = Integer.parseInt(fields[0]);
            keyfileIndex = Integer.parseInt(fields[2]);
            currentKeyIndex = Integer.parseInt(fields[3]);
        }
        List<List<Hash>> merkleTree = Merkle.readKeyfile(new File(keyfile));
        address = HashFactory.ADDRESS.create(merkleTree.get(merkleTree.size()-1).get(0).bytes());
    }

    private void generateKeyfile(String seed) throws Exception {
        log.info("Generating Keyfile (idx: " + keyfileIndex + ")");
        List<List<Hash>> merkleTree = Merkle.buildMerkleKeyTree(seed, pubkeyDepth, maxKeyIndex * keyfileIndex, maxKeyIndex);
        Merkle.createKeyfile(merkleTree, Hex.decode(seed), pubkeyDepth, 0, keyfileIndex, keyfile);
        address = HashFactory.ADDRESS.create(merkleTree.get(merkleTree.size()-1).get(0).bytes());
    }

    private void sendApplication() throws Exception {
        log.info("Sending Application for Address " + address);
        api.sendApplication(address.toString(), mwm, sign, maxKeyIndex * keyfileIndex);
    }

    private int getRound(long time) {
        return (int) (time - config.getGenesisTime()) / config.getRoundDuration();
    }

    private long getStartTime(int round) {
        return config.getGenesisTime() + (round * config.getRoundDuration());
    }

    public void startScheduledExecutorService() {
        log.info("MSS scheduledExecutorService started.");
        try {
            File f = new File(keyfile);
            // read keyindex if keyfile exists, otherwise build new keyfile
            if (f.isFile()) {
                readKeyfileMetadata();
            } else {
                generateKeyfile(seed);
            }
            // activate publisher if address is nominee, otherwise send application
            if (nomineeTracker.getLatestNominees().contains(address)) {
                log.info("Submitting Milestones every: " + (config.getRoundDuration() / 1000) + "s.");
                active = true;
            } else {
                sendApplication();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // get start time of next round
        int currentRound = getRound(System.currentTimeMillis());
        long startTimeNextRound = getStartTime(currentRound + 1);
        log.info("Next Round starts in " + ((startTimeNextRound - System.currentTimeMillis()) / 1000) + "s.");
        scheduledExecutorService.scheduleWithFixedDelay(getRunnablePublishMilestone(), (startTimeNextRound - System.currentTimeMillis()), config.getRoundDuration(),  TimeUnit.MILLISECONDS);
    }

    private void publishMilestone() throws Exception {
        if (!active) {
            if (startRound < getRound(System.currentTimeMillis()) && nomineeTracker.getLatestNominees().contains(address)) {
                startRound = nomineeTracker.getStartRound();
                log.info("Address " + address + " is accepted from round #" + startRound);
            }
            if (startRound == getRound(System.currentTimeMillis())) {
                log.info("Submitting Milestones every: " + (config.getRoundDuration() / 1000) + "s.");
                active = true;
            }
        }
        if (active) {
            log.info("Publishing next Milestone...");
            if (currentKeyIndex < maxKeyIndex * (keyfileIndex + 1)) {
                api.storeAndBroadcastMilestoneStatement(address.toString(), message, mwm, sign, currentKeyIndex);
                currentKeyIndex += 1;
            } else {
                log.info("Keyfile has expired! The MilestonePublisher is paused until the new address is accepted by the network. This can take some time (<1m).");
                active = false;
                keyfileIndex += 1;
                generateKeyfile(seed);
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
        // store keyindex in keyfile
        try {
            writeKeyIndex();
        } catch (Exception e) {
            e.printStackTrace();
        }
        scheduledExecutorService.shutdown();
    }
}
