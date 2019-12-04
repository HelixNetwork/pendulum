package net.helix.pendulum.conf;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class TestnetConfig extends BasePendulumConfig {

    protected boolean validateTestnetMilestoneSig = Defaults.VALIDATE_MILESTONE_SIG;
    protected String snapshotFile = Defaults.SNAPSHOT_FILE;
    protected String snapshotSignatureFile = Defaults.SNAPSHOT_SIG;
    protected long snapshotTime = Defaults.SNAPSHOT_TIME;
    protected int mwm = Defaults.MWM;
    protected int milestoneStartIndex = Defaults.MILESTONE_START_INDEX;
    protected int numberOfKeysInMilestone = Defaults.KEYS_IN_MILESTONE;
    protected int transactionPacketSize = Defaults.PACKET_SIZE;
    protected int requestHashSize = Defaults.REQUEST_HASH_SIZE;
    protected long genesisTime= Defaults.GENESIS_TIME;

    public TestnetConfig() {
        super();
        dbPath = Defaults.DB_PATH;
        dbLogPath = Defaults.DB_LOG_PATH;
        spentAddressesDbPath = Defaults.SPENT_ADDRESSES_DB_PATH;
        spentAddressesDbLogPath= Defaults.SPENT_ADDRESSES_DB_LOG_PATH;
        localSnapshotsBasePath = Defaults.LOCAL_SNAPSHOTS_BASE_PATH;
    }

    @Override
    public boolean isTestnet() {
        return true;
    }

    @Override
    public boolean isValidateTestnetMilestoneSig() {
        return validateTestnetMilestoneSig;
    }

    @JsonProperty
    @Parameter(names = "--validate_testnet_milestone_sig", description = ValidatorConfig.Descriptions.VALIDATE_TESTNET_MILESTONE_SIG)
    protected void setValidateTestnetMilestoneSig(boolean validateTestnetMilestoneSig) {
        this.validateTestnetMilestoneSig = validateTestnetMilestoneSig;
    }

    @Override
    public String getSnapshotFile() {
        return snapshotFile;
    }

    @JsonProperty
    @Parameter(names = "--snapshot", description = SnapshotConfig.Descriptions.SNAPSHOT_FILE)
    protected void setSnapshotFile(String snapshotFile) {
        this.snapshotFile = snapshotFile;
    }

    @Override
    public String getSnapshotSignatureFile() {
        return snapshotSignatureFile;
    }

    @JsonProperty
    @Parameter(names = "--snapshot-sig", description = SnapshotConfig.Descriptions.SNAPSHOT_SIGNATURE_FILE)
    protected void setSnapshotSignatureFile(String snapshotSignatureFile) {
        this.snapshotSignatureFile = snapshotSignatureFile;
    }

    @Override
    public long getSnapshotTime() {
        return snapshotTime;
    }

    @JsonProperty
    @Parameter(names = "--snapshot-timestamp", description = SnapshotConfig.Descriptions.SNAPSHOT_TIME)
    protected void setSnapshotTime(long snapshotTime) {
        this.snapshotTime = snapshotTime;
    }

    @Override
    public int getMwm() {
        return mwm;
    }

    @JsonProperty
    @Parameter(names = "--mwm", description = ProtocolConfig.Descriptions.MWM)
    protected void setMwm(int mwm) {
        this.mwm = mwm;
    }

    @Override
    public int getMilestoneStartIndex() {
        return milestoneStartIndex;
    }

    @JsonProperty
    @Parameter(names = "--milestone-start", description = MilestoneConfig.Descriptions.MILESTONE_START_INDEX)
    protected void setMilestoneStartIndex(int milestoneStartIndex) {
        this.milestoneStartIndex = milestoneStartIndex;
    }

    @Override
    public int getNumberOfKeysInMilestone() {
        return numberOfKeysInMilestone;
    }

    @JsonProperty("NUMBER_OF_KEYS_IN_A_MILESTONE")
    @Parameter(names = "--milestone-keys", description = MilestoneConfig.Descriptions.NUMBER_OF_KEYS_IN_A_MILESTONE)
    protected void setNumberOfKeysInMilestone(int numberOfKeysInMilestone) {
        this.numberOfKeysInMilestone = numberOfKeysInMilestone;
    }

    @Override
    public int getTransactionPacketSize() {
        return transactionPacketSize;
    }

    @JsonProperty
    @Parameter(names = {"--packet-size"}, description = ProtocolConfig.Descriptions.TRANSACTION_PACKET_SIZE)
    protected void setTransactionPacketSize(int transactionPacketSize) {
        this.transactionPacketSize = transactionPacketSize;
    }

    @Override
    public int getRequestHashSize() {
        return requestHashSize;
    }

    @JsonProperty
    @Parameter(names = {"--request-hash-size"}, description = ProtocolConfig.Descriptions.REQUEST_HASH_SIZE)
    public void setRequestHashSize(int requestHashSize) {
        this.requestHashSize = requestHashSize;
    }

    @JsonProperty
    @Override
    public void setDbPath(String dbPath) {
        if (Objects.equals(MainnetConfig.Defaults.DB_PATH, dbPath)) {
            throw new ParameterException("Testnet Db folder cannot be configured to mainnet's db folder");
        }
        super.setDbPath(dbPath);
    }

    @JsonProperty
    @Override
    public void setDbLogPath(String dbLogPath) {
        if (Objects.equals(MainnetConfig.Defaults.DB_LOG_PATH, dbLogPath)) {
            throw new ParameterException("Testnet Db log folder cannot be configured to mainnet's db log folder");
        }
        super.setDbLogPath(dbLogPath);
    }

    @JsonProperty
    @Override
    public void setSpentAddressesDbPath(String spentAddressesDbPath) {
      if (Objects.equals(MainnetConfig.Defaults.SPENT_ADDRESSES_DB_PATH, spentAddressesDbPath)) {
          throw new ParameterException("Testnet spent-addresses db folder cannot be configured to mainnet's spent-addresses-db folder");
      }
      super.setSpentAddressesDbPath(spentAddressesDbPath);
    }

    @JsonProperty
    @Override
    public void setSpentAddressesDbLogPath(String spentAddressesDbLogPath) {
      if (Objects.equals(MainnetConfig.Defaults.SPENT_ADDRESSES_DB_LOG_PATH, spentAddressesDbLogPath)) {
          throw new ParameterException("Testnet spent-addresses db log folder cannot be configured to mainnet's spent-addresses-db log folder");
      }
        super.setSpentAddressesDbLogPath(spentAddressesDbLogPath);
    }

    @Override
    public long getGenesisTime() {
        return genesisTime;
    }

    @JsonProperty
    @Parameter(names = {"--genesis-testnet"}, description = RoundConfig.Descriptions.GENESIS_TIME)
    protected void setGenesisTime(int genesisTime) { this.genesisTime = genesisTime; }

    public interface Defaults {
        long GENESIS_TIME = 1571279107785L;
        boolean VALIDATE_MILESTONE_SIG = true;
        String LOCAL_SNAPSHOTS_BASE_PATH = "testnet-snapshot";
        String SNAPSHOT_FILE = "/snapshotTestnet.txt";
        int REQUEST_HASH_SIZE = 32;
        String SNAPSHOT_SIG = "/snapshotTestnet.sig";
        int SNAPSHOT_TIME = 1522306500;
        int MWM = 1;
        int MILESTONE_START_INDEX = 0;
        int KEYS_IN_MILESTONE = 10;
        int PACKET_SIZE = 800;
        String DB_PATH = "testnet-db";
        String DB_LOG_PATH = "testnet-db-log";
        String SPENT_ADDRESSES_DB_PATH = "testnet-spent-addresses-db";
        String SPENT_ADDRESSES_DB_LOG_PATH = "testnet-spent-addresses-db-log";
    }
}
