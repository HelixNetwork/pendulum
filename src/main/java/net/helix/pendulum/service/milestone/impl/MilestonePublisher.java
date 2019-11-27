package net.helix.pendulum.service.milestone.impl;

import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.crypto.merkle.MerkleFactory;
import net.helix.pendulum.crypto.merkle.MerkleOptions;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.HashFactory;
import net.helix.pendulum.service.API;
import net.helix.pendulum.service.utils.RoundIndexUtil;
import net.helix.pendulum.service.validatormanager.CandidateTracker;
import net.helix.pendulum.utils.KeyfileUtil;
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
    private String keyfile;
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private PendulumConfig config;
    private API api;
    private CandidateTracker candidateTracker;

    private Hash address;
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

    private boolean active;

    public MilestonePublisher(PendulumConfig configuration, API api, CandidateTracker candidateTracker) {
        this.config = configuration;
        this.api = api;
        this.candidateTracker = candidateTracker;

        delay = config.getRoundDuration();
        mwm = config.getMwm();
        sign = config.isValidateTestnetMilestoneSig();
        pubkeyDepth = config.getMilestoneKeyDepth();
        keyfileIndex = 0;
        maxKeyIndex = (int) Math.pow(2, pubkeyDepth);
        currentKeyIndex = 0;
        startRound = 0;
        active = false;
        enabled = false;
        keyfile = configuration.getValidatorKeyfile();
        initSeed(configuration);
    }

    private void initSeed(PendulumConfig configuration) {
        if(new File(configuration.getValidatorSeedfile()).isFile()){
            //seed should be stored in a hidden file for which only the user and this application have read permissions
            seed = readSeedFile(configuration.getValidatorSeedfile());
        } else {
            try {
                readKeyfileMetadata();
            } catch (IOException e) {
                log.error("Error has occur during reading validatorPath key file! Fix it and restart the node, or use --validator-path argument", e);
            }
        }
    }

    private void writeKeyIndex() throws IOException {
        KeyfileUtil keyfileUtil = new KeyfileUtil();
        List<List<Hash>> merkleTree = keyfileUtil.readKeyfile(new File(keyfile));
        keyfileUtil.createKeyfile(merkleTree, Hex.decode(seed), pubkeyDepth, currentKeyIndex, keyfileIndex, keyfile);
    }

    private void readKeyfileMetadata() throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(new File(keyfile)))) {
            String[] fields = br.readLine().split(" ");
            pubkeyDepth = Integer.parseInt(fields[0]);
            keyfileIndex = Integer.parseInt(fields[2]);
            currentKeyIndex = Integer.parseInt(fields[3]);
            if(seed == null){
                seed =  fields[1];
            }
        }
        KeyfileUtil keyfileUtil = new KeyfileUtil();
        List<List<Hash>> merkleTree = keyfileUtil.readKeyfile(new File(keyfile));
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

    private void doKeyChange() throws Exception {
        // generate new keyfile
        int newKeyfileIndex = keyfileIndex + 1;
        log.debug("Generating Keyfile (idx: " + newKeyfileIndex + ")");

        KeyfileUtil keyfileUtil = new KeyfileUtil();
        List<List<Hash>> merkleTree = keyfileUtil.buildMerkleKeyTree(seed, pubkeyDepth, maxKeyIndex * newKeyfileIndex, maxKeyIndex, config.getValidatorSecurity());
        Hash newAddress = HashFactory.ADDRESS.create(merkleTree.get(merkleTree.size()-1).get(0).bytes());
        // send keyChange bundle to register new address
        api.publishKeyChange(address.toString(),  newAddress, mwm, sign, currentKeyIndex, maxKeyIndex);
        // store new keyfile, address, keyfileidx
        keyfileIndex = newKeyfileIndex;
        address = newAddress;
        currentKeyIndex = maxKeyIndex * keyfileIndex;
        keyfileUtil.createKeyfile(merkleTree, Hex.decode(seed), pubkeyDepth, 0, keyfileIndex, keyfile);
    }

    private void generateKeyfile(String seed) throws Exception {
        log.debug("Generating Keyfile (idx: " + keyfileIndex + ")");
        KeyfileUtil keyfileUtil = new KeyfileUtil();
        List<List<Hash>> merkleTree = keyfileUtil.buildMerkleKeyTree(seed, pubkeyDepth, maxKeyIndex * keyfileIndex, maxKeyIndex, config.getValidatorSecurity());
        keyfileUtil.createKeyfile(merkleTree, Hex.decode(seed), pubkeyDepth, 0, keyfileIndex, keyfile);
        address = HashFactory.ADDRESS.create(merkleTree.get(merkleTree.size()-1).get(0).bytes());
    }

    private void sendRegistration(Hash identity, boolean join) throws Exception {
        log.debug("Signing {} identity: {} ", (join ? "up" : "off"), identity);
        //api.publishRegistration(identity.toString(), mwm, sign, currentKeyIndex, maxKeyIndex, join); //todo remove when done with refactoring
        //api.publish(BundleTypes.registration, identity.toString(), mwm, sign, currentKeyIndex, maxKeyIndex, join, 0);
        api.publishRegistration(identity.toString(),  mwm, sign, currentKeyIndex, maxKeyIndex, join);
        currentKeyIndex += 1;
    }

    private int getRound(long time) {
        return RoundIndexUtil.getRound(time,  config.getGenesisTime(),config.getRoundDuration() ); }

    private long getStartTime(int round) {
        return RoundIndexUtil.getStartTime(config.getGenesisTime(), config.getRoundDuration(), round);
    }

    public void startScheduledExecutorService() {
        log.info("MilestonePublisher started.");
        readOrGenerateKeyfile();
        // get start time of next round
        int currentRound = getRound(RoundIndexUtil.getCurrentTime());
        long startTimeNextRound = getStartTime(currentRound + 1);
        log.debug("Next round commencing in {}s", (startTimeNextRound - RoundIndexUtil.getCurrentTime()) / 1000);
        scheduledExecutorService.scheduleWithFixedDelay(getRunnablePublishMilestone(), (startTimeNextRound - RoundIndexUtil.getCurrentTime()), delay,  TimeUnit.MILLISECONDS);
    }

    private void readOrGenerateKeyfile() {
        try {
            File f = new File(keyfile);
            // read keyIndex if key-file exists, otherwise build new key-file
            if (f.isFile()) {
                readKeyfileMetadata();
            } else {
                generateKeyfile(seed);
            }
            // send registration if validator isn't part of initial validators
            /*if (!config.getInitialValidators().contains(address)) {
                sendRegistration(address, true);
            }*/
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //todo here are some bugs:
    // - sometimes it passes the start round and is stucked in "Legitimized validator .."
    // - sometimes it is active and prints "Publishing next Milestone..." but then nothing happens (also doesn't build a new keyfile)
    // - when starting with two nodes and one node leaving this deactivates the publisher
    private void publishMilestone() throws Exception {
        if (!active) {
            if (startRound < getRound(RoundIndexUtil.getCurrentTime()) && !candidateTracker.getValidators().isEmpty() && candidateTracker.getValidators().contains(address)) {
                startRound = candidateTracker.getStartRound();
                log.debug("Legitimized validator {} for round #{}", address, startRound);
            }
            if (startRound == getRound(RoundIndexUtil.getCurrentTime())) {
                log.trace("Milestone rate: {}s", config.getRoundDuration() / 1000);
                active = true;
            }
        }
        if (active) {
            log.trace("Publishing next Milestone...");
            if (currentKeyIndex < maxKeyIndex * (keyfileIndex + 1) - 1) {
                //api.publishMilestone(address.toString(), mwm, sign, currentKeyIndex, maxKeyIndex);  <- todo remove when refactoring is done
                //api.publish(BundleTypes.milestone, address.toString(), mwm, sign, currentKeyIndex, maxKeyIndex, false, 0);
                log.trace("Address of milestone to publish = {}", address.toString());
                api.publishMilestone(address.toString(), mwm, sign, currentKeyIndex, maxKeyIndex);
                currentKeyIndex += 1;
            } else {
                log.debug("Keyfile has expired! The MilestonePublisher is paused until the new address is accepted by the network.");
                active = false;
                doKeyChange();
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
