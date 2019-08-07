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
    public boolean enabled;

    public boolean active;

    public MilestonePublisher(HelixConfig configuration, API api, NomineeTracker nomineeTracker) {
        this.config = configuration;
        this.api = api;
        this.nomineeTracker = nomineeTracker;

        delay = config.getRoundDuration();
        mwm = config.getMwm();
        message = StringUtils.repeat('0', 1024);
        sign = !config.isDontValidateTestnetMilestoneSig();
        pubkeyDepth = config.getNumberOfKeysInMilestone();
        keyfileIndex = 0;
        maxKeyIndex = (int) Math.pow(2, pubkeyDepth);
        currentKeyIndex = 0;
        startRound = 0;
        active = false;
        enabled = false;
        seed = readSeedFile(configuration.getNominee()); //seed should be stored in a hidden file for which only the user and this application have read permissions
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
        address = HashFactory.ADDRESS.create(merkleTree.get(merkleTree.size() - 1).get(0).bytes());
    }

    private String readSeedFile(String path)  {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(path))))) {
            this.enabled = true;
            return reader.readLine();
        } catch (IOException e) {
            log.debug("Failed to read the seed file at " + path, e);
            this.enabled = false;
            return null;
        }
    }

    private void generateKeyfile(String seed) throws Exception {
        log.debug("Generating Keyfile (idx: " + keyfileIndex + ")");
        List<List<Hash>> merkleTree = Merkle.buildMerkleKeyTree(seed, pubkeyDepth, maxKeyIndex * keyfileIndex, maxKeyIndex);
        Merkle.createKeyfile(merkleTree, Hex.decode(seed), pubkeyDepth, 0, keyfileIndex, keyfile);
        address = HashFactory.ADDRESS.create(merkleTree.get(merkleTree.size()-1).get(0).bytes());
    }

    private void sendApplication(Hash identity, boolean join) throws Exception {
        log.debug("Signing {} identity: {} ", (join ? "up" : "off"), identity);
        api.publishRegistration(identity.toString(), mwm, sign, currentKeyIndex, join);
        currentKeyIndex += 1;
    }

    private int getRound(long time) {
        return (int) (time - config.getGenesisTime()) / config.getRoundDuration();
    }

    private long getStartTime(int round) {
        return config.getGenesisTime() + (round * config.getRoundDuration());
    }

    public void startScheduledExecutorService() {
        log.info("MilestonePublisher started.");
        try {
            File f = new File(keyfile);
            // read keyIndex if key-file exists, otherwise build new key-file
            if (f.isFile()) {
                readKeyfileMetadata();
            } else {
                generateKeyfile(seed);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // get start time of next round
        int currentRound = getRound(System.currentTimeMillis());
        long startTimeNextRound = getStartTime(currentRound + 1);
        log.debug("Next round commencing in {}s", (startTimeNextRound - System.currentTimeMillis()) / 1000);
        scheduledExecutorService.scheduleWithFixedDelay(getRunnablePublishMilestone(), (startTimeNextRound - System.currentTimeMillis()), delay,  TimeUnit.MILLISECONDS);
    }

    private void publishMilestone() throws Exception {
        if (!active) {
            if (startRound < getRound(System.currentTimeMillis()) && !nomineeTracker.getLatestNominees().isEmpty() && nomineeTracker.getLatestNominees().contains(address)) {
                startRound = nomineeTracker.getStartRound();
                log.debug("Legitimized nominee {} for round #{}", address, startRound);
            }
            if (startRound == getRound(System.currentTimeMillis())) {
                log.debug("Submitting milestones every: " + (config.getRoundDuration() / 1000) + "s");
                active = true;
            }
        }
        if (active) {
            log.debug("Publishing next Milestone...");
            if (currentKeyIndex < maxKeyIndex * (keyfileIndex + 1) - 1) {
                api.publishMilestone(address.toString(), message, mwm, sign, currentKeyIndex);
                currentKeyIndex += 1;
            } else {
                log.debug("Keyfile has expired! The MilestonePublisher is paused until the new address is accepted by the network.");
                active = false;
                // remove old address
                sendApplication(address, false);
                // generate keyfile and add new address
                keyfileIndex += 1;
                currentKeyIndex = maxKeyIndex * keyfileIndex;
                generateKeyfile(seed);
                sendApplication(address, true);
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
