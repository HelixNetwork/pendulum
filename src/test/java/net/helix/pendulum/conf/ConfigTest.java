package net.helix.pendulum.conf;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import net.helix.pendulum.utils.PendulumUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.CoreMatchers;
import org.hamcrest.core.IsCollectionContaining;
import org.junit.*;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ConfigTest {

    private static File configFile;

    @BeforeClass
    public static void setUpBeforeClass() throws IOException {
        configFile = File.createTempFile("config", "ini");
    }

    @After
    public void tearDown() throws Exception {
        //clear the file
        try (Writer writer = new FileWriter(configFile)) {
            writer.write("");
        }
    }

    @AfterClass
    public static void tearDownAfterClass() throws IOException {
        FileUtils.forceDelete(configFile);
    }

    /*
    Test that iterates over common configs. It also attempts to check different types of types (double, boolean, string)
    */
    @Test
    public void argsParsingMainnetTest() {
        String[] args = {
                "-p", "8089",
                "-u", "4200",
                "-t", "5200",
                "-n", "udp://neighbor1 neighbor, tcp://neighbor2",
                "--api-host", "1.1.1.1",
                "--remote-limit-api", "call1 call2, call3",
                "--max-find-transactions", "500",
                "--max-requests-list", "1000",
                "--max-get-transaction-strings", "4000",
                "--max-body-length", "220",
                "--remote-auth", "2.2.2.2",
                "--p-remove-request", "0.23",
                "--send-limit", "1000",
                "--max-peers", "10",
                "--dns-refresher", "false",
                "--dns-resolution", "false",
                "--XI-dir", "/XI",
                "--db-path", "/db",
                "--db-log-path", "/dblog",
                "--zmq-enabled", "true",
                //we ignore this on mainnet
                "--mwm", "4",
                "--testnet-coordinator", "TTTTTTTTT",
                "--test-no-coo-validation",
                //this should be ignored everywhere
                "--fake-config"
        };
        PendulumConfig pendulumConfig = ConfigFactory.createHelixConfig(false);
        Assert.assertThat("wrong config class created", pendulumConfig, CoreMatchers.instanceOf(MainnetConfig.class));

        pendulumConfig.parseConfigFromArgs(args);
        Assert.assertEquals("port value", 8089, pendulumConfig.getPort());
        Assert.assertEquals("udp port", 4200, pendulumConfig.getUdpReceiverPort());
        Assert.assertEquals("tcp port", 5200, pendulumConfig.getTcpReceiverPort());
        Assert.assertEquals("neighbors", Arrays.asList("udp://neighbor1", "neighbor", "tcp://neighbor2"),
                pendulumConfig.getNeighbors());
        Assert.assertEquals("api host", "1.1.1.1", pendulumConfig.getApiHost());
        Assert.assertEquals("remote limit api", Arrays.asList("call1", "call2", "call3"),
                pendulumConfig.getRemoteLimitApi());
        Assert.assertEquals("max find transactions", 500, pendulumConfig.getMaxFindTransactions());
        Assert.assertEquals("max requests list", 1000, pendulumConfig.getMaxRequestsList());
        Assert.assertEquals("max get bytes", 4000, pendulumConfig.getMaxTransactionStrings());
        Assert.assertEquals("max body length", 220, pendulumConfig.getMaxBodyLength());
        Assert.assertEquals("remote-auth", "2.2.2.2", pendulumConfig.getRemoteAuth());
        Assert.assertEquals("p remove request", 0.23d, pendulumConfig.getpRemoveRequest(), 0d);
        Assert.assertEquals("send limit", 1000, pendulumConfig.getSendLimit());
        Assert.assertEquals("max peers", 10, pendulumConfig.getMaxPeers());
        Assert.assertEquals("dns refresher", false, pendulumConfig.isDnsRefresherEnabled());
        Assert.assertEquals("dns resolution", false, pendulumConfig.isDnsResolutionEnabled());
        Assert.assertEquals("XI-dir", "/XI", pendulumConfig.getXiDir());
        Assert.assertEquals("db path", "/db", pendulumConfig.getDbPath());
        Assert.assertEquals("zmq enabled", true, pendulumConfig.isZmqEnabled());
        Assert.assertNotEquals("mwm", 4, pendulumConfig.getMwm());
        Assert.assertNotEquals("coo", pendulumConfig.getCuratorAddress(), "TTTTTTTTT");
        Assert.assertEquals("--testnet-no-coo-validation", false, pendulumConfig.isDontValidateTestnetMilestoneSig());
    }

    @Test
    public void remoteFlagTest() {
        String[] args = {"--remote"};
        PendulumConfig pendulumConfig = ConfigFactory.createHelixConfig(false);
        pendulumConfig.parseConfigFromArgs(args);
        Assert.assertEquals("The api interface should be open to the public", "0.0.0.0", pendulumConfig.getApiHost());
    }

    @Test
    public void argsParsingTestnetTest() {
        String[] args = {
                "-p", "8089",
                "-u", "4200",
                "-t", "5200",
                "-n", "udp://neighbor1 neighbor, tcp://neighbor2",
                "--api-host", "1.1.1.1",
                "--remote-limit-api", "call1 call2, call3",
                "--max-find-transactions", "500",
                "--max-requests-list", "1000",
                "--max-get-transaction-strings", "4000",
                "--max-body-length", "220",
                "--remote-auth", "2.2.2.2",
                "--p-remove-request", "0.23",
                "--send-limit", "1000",
                "--max-peers", "10",
                "--dns-refresher", "false",
                "--dns-resolution", "false",
                "--XI-dir", "/XI",
                "--db-path", "/db",
                "--db-log-path", "/dblog",
                "--zmq-enabled", "true",
                //we ignore this on mainnet
                "--mwm", "4",
                "--testnet-coordinator", "TTTTTTTTT",
                "--testnet-no-coo-validation",
                //this should be ignored everywhere
                "--fake-config"
        };
        PendulumConfig pendulumConfig = ConfigFactory.createHelixConfig(true);
        Assert.assertThat("wrong config class created", pendulumConfig, CoreMatchers.instanceOf(TestnetConfig.class));

        pendulumConfig.parseConfigFromArgs(args);
        Assert.assertEquals("port value", 8089, pendulumConfig.getPort());
        Assert.assertEquals("udp port", 4200, pendulumConfig.getUdpReceiverPort());
        Assert.assertEquals("tcp port", 5200, pendulumConfig.getTcpReceiverPort());
        Assert.assertEquals("neighbors", Arrays.asList("udp://neighbor1", "neighbor", "tcp://neighbor2"),
                pendulumConfig.getNeighbors());
        Assert.assertEquals("api host", "1.1.1.1", pendulumConfig.getApiHost());
        Assert.assertEquals("remote limit api", Arrays.asList("call1", "call2", "call3"),
                pendulumConfig.getRemoteLimitApi());
        Assert.assertEquals("max find transactions", 500, pendulumConfig.getMaxFindTransactions());
        Assert.assertEquals("max requests list", 1000, pendulumConfig.getMaxRequestsList());
        Assert.assertEquals("max get tx strings", 4000, pendulumConfig.getMaxTransactionStrings());
        Assert.assertEquals("max body length", 220, pendulumConfig.getMaxBodyLength());
        Assert.assertEquals("remote-auth", "2.2.2.2", pendulumConfig.getRemoteAuth());
        Assert.assertEquals("p remove request", 0.23d, pendulumConfig.getpRemoveRequest(), 0d);
        Assert.assertEquals("send limit", 1000, pendulumConfig.getSendLimit());
        Assert.assertEquals("max peers", 10, pendulumConfig.getMaxPeers());
        Assert.assertEquals("dns refresher", false, pendulumConfig.isDnsRefresherEnabled());
        Assert.assertEquals("dns resolution", false, pendulumConfig.isDnsResolutionEnabled());
        Assert.assertEquals("XI-dir", "/XI", pendulumConfig.getXiDir());
        Assert.assertEquals("db path", "/db", pendulumConfig.getDbPath());
        Assert.assertEquals("zmq enabled", true, pendulumConfig.isZmqEnabled());
        Assert.assertEquals("mwm", 4, pendulumConfig.getMwm());
        //Assert.assertEquals("coo", "TTTTTTTTT", helixConfig.getCuratorAddress());
        Assert.assertEquals("--testnet-no-coo-validation", true,
                pendulumConfig.isDontValidateTestnetMilestoneSig());
    }

    @Test
    public void iniParsingMainnetTest() throws Exception {
        String iniContent = new StringBuilder()
                .append("[HLX]").append(System.lineSeparator())
                .append("PORT = 8088").append(System.lineSeparator())
                .append("NEIGHBORS = udp://neighbor1 neighbor, tcp://neighbor2").append(System.lineSeparator())
                .append("ZMQ_ENABLED = true").append(System.lineSeparator())
                .append("P_REMOVE_REQUEST = 0.4").append(System.lineSeparator())
                .append("MWM = 4").append(System.lineSeparator())
                .append("FAKE").append(System.lineSeparator())
                .append("FAKE2 = lies")
                .toString();

        try (Writer writer = new FileWriter(configFile)) {
            writer.write(iniContent);
        }

        PendulumConfig pendulumConfig = ConfigFactory.createFromFile(configFile, false);
        Assert.assertThat("Wrong config class created", pendulumConfig, CoreMatchers.instanceOf(MainnetConfig.class));
        Assert.assertEquals("PORT", 8088, pendulumConfig.getPort());
        Assert.assertEquals("NEIGHBORS", Arrays.asList("udp://neighbor1", "neighbor", "tcp://neighbor2"),
                pendulumConfig.getNeighbors());
        Assert.assertEquals("ZMQ_ENABLED", true, pendulumConfig.isZmqEnabled());
        Assert.assertEquals("P_REMOVE_REQUEST", 0.4d, pendulumConfig.getpRemoveRequest(), 0);
        Assert.assertNotEquals("MWM", 4, pendulumConfig.getMwm());
    }

    @Test
    public void iniParsingTestnetTest() throws Exception {
        String iniContent = new StringBuilder()
                .append("[HLX]").append(System.lineSeparator())
                .append("PORT = 8088").append(System.lineSeparator())
                .append("NEIGHBORS = udp://neighbor1 neighbor, tcp://neighbor2").append(System.lineSeparator())
                .append("ZMQ_ENABLED = true").append(System.lineSeparator())
                .append("DNS_RESOLUTION_ENABLED = true").append(System.lineSeparator())
                .append("P_REMOVE_REQUEST = 0.4").append(System.lineSeparator())
                .append("MWM = 4").append(System.lineSeparator())
                .append("NUMBER_OF_KEYS_IN_A_MILESTONE = 3").append(System.lineSeparator())
                .append("DONT_VALIDATE_TESTNET_MILESTONE_SIG = true").append(System.lineSeparator())
                .append("TIPSELECTION_ALPHA = 1.1").append(System.lineSeparator())
                //doesn't do anything
                .append("REMOTE")
                .append("FAKE").append(System.lineSeparator())
                .append("FAKE2 = lies")
                .toString();

        try (Writer writer = new FileWriter(configFile)) {
            writer.write(iniContent);
        }

        PendulumConfig pendulumConfig = ConfigFactory.createFromFile(configFile, true);
        Assert.assertThat("Wrong config class created", pendulumConfig, CoreMatchers.instanceOf(TestnetConfig.class));
        Assert.assertEquals("PORT", 8088, pendulumConfig.getPort());
        Assert.assertEquals("NEIGHBORS", Arrays.asList("udp://neighbor1", "neighbor", "tcp://neighbor2"),
                pendulumConfig.getNeighbors());
        Assert.assertEquals("ZMQ_ENABLED", true, pendulumConfig.isZmqEnabled());
        Assert.assertEquals("DNS_RESOLUTION_ENABLED", true, pendulumConfig.isDnsResolutionEnabled());
        //true by default
        Assert.assertEquals("DNS_REFRESHER_ENABLED", true, pendulumConfig.isDnsRefresherEnabled());
        //false by default
        Assert.assertEquals("RESCAN", false, pendulumConfig.isRescanDb());
        //false by default
        Assert.assertEquals("REVALIDATE", false, pendulumConfig.isRevalidate());
        Assert.assertEquals("P_REMOVE_REQUEST", 0.4d, pendulumConfig.getpRemoveRequest(), 0);
        Assert.assertEquals("MWM", 4, pendulumConfig.getMwm());
        Assert.assertEquals("NUMBER_OF_KEYS_IN_A_MILESTONE", 3, pendulumConfig.getNumberOfKeysInMilestone());
        Assert.assertEquals("TIPSELECTION_ALPHA", 1.1d, pendulumConfig.getAlpha(), 0);
        Assert.assertEquals("DONT_VALIDATE_TESTNET_MILESTONE_SIG",
                pendulumConfig.isDontValidateTestnetMilestoneSig(), true);
        //prove that REMOTE did nothing
        Assert.assertEquals("API_HOST", pendulumConfig.getApiHost(), "localhost");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidIni() throws IOException {
        String iniContent = new StringBuilder()
                .append("[HLX]").append(System.lineSeparator())
                .append("REVALIDATE")
                .toString();
        try (Writer writer = new FileWriter(configFile)) {
            writer.write(iniContent);
        }
        ConfigFactory.createFromFile(configFile, false);
    }

    //@Test
    public void backwardsIniCompatibilityTest() {
        Collection<String> configNames = PendulumUtils.getAllSetters(TestnetConfig.class)
                .stream()
                .map(this::deriveNameFromSetter)
                .collect(Collectors.toList());
        Stream.of(LegacyDefaultConf.values())
                .map(Enum::name)
                // make it explicit that we have removed some configs
                .filter(config -> !ArrayUtils.contains(new String[]{"CONFIG", "TESTNET", "DEBUG",
                        "MIN_RANDOM_WALKS", "MAX_RANDOM_WALKS"}, config))
                .forEach(config ->
                        Assert.assertThat(configNames, IsCollectionContaining.hasItem(config)));
    }

    @Test
    public void dontValidateMilestoneSigDefaultValueTest() {
        PendulumConfig pendulumConfig = ConfigFactory.createHelixConfig(true);
        Assert.assertFalse("By default testnet should be validating milestones",
                pendulumConfig.isDontValidateTestnetMilestoneSig());
    }

    private String deriveNameFromSetter(Method setter) {
        JsonIgnore jsonIgnore = setter.getAnnotation(JsonIgnore.class);
        if (jsonIgnore != null) {
            return null;
        }

        JsonProperty jsonProperty = setter.getAnnotation(JsonProperty.class);
        //Code works w/o annotation but we wish to enforce its usage
        Assert.assertNotNull("Setter " + setter.getName() + "must have JsonProperty annotation", jsonProperty);
        if (StringUtils.isEmpty(jsonProperty.value())) {
            String name = setter.getName().substring(3);
            name = PropertyNamingStrategy.SNAKE_CASE.nameForSetterMethod(null, null, name);
            return StringUtils.upperCase(name);
        }

        return jsonProperty.value();
    }

    public enum LegacyDefaultConf {
        CONFIG,
        PORT,
        API_HOST,
        UDP_RECEIVER_PORT,
        TCP_RECEIVER_PORT,
        TESTNET,
        DEBUG,
        REMOTE_LIMIT_API,
        REMOTE_AUTH,
        NEIGHBORS,
        XI_DIR,
        DB_PATH,
        DB_LOG_PATH,
        DB_CACHE_SIZE,
        P_REMOVE_REQUEST,
        P_DROP_TRANSACTION,
        P_SELECT_MILESTONE_CHILD,
        P_SEND_MILESTONE,
        P_REPLY_RANDOM_TIP,
        P_PROPAGATE_REQUEST,
        MAIN_DB,
        SEND_LIMIT,
        MAX_PEERS,
        DNS_RESOLUTION_ENABLED,
        DNS_REFRESHER_ENABLED,
        COORDINATOR,
        DONT_VALIDATE_TESTNET_MILESTONE_SIG,
        REVALIDATE,
        RESCAN_DB,
        MIN_RANDOM_WALKS,
        MAX_RANDOM_WALKS,
        MAX_FIND_TRANSACTIONS,
        MAX_REQUESTS_LIST,
        MAX_GET_TRANSACTION_STRINGS,
        MAX_BODY_LENGTH,
        MAX_DEPTH,
        MWM,
        ZMQ_ENABLED,
        ZMQ_PORT,
        ZMQ_IPC,
        ZMQ_THREADS,
        Q_SIZE_NODE,
        P_DROP_CACHE_ENTRY,
        CACHE_SIZE_BYTES,
        SNAPSHOT_FILE,
        SNAPSHOT_SIGNATURE_FILE,
        MILESTONE_START_INDEX,
        NUMBER_OF_KEYS_IN_A_MILESTONE,
        TRANSACTION_PACKET_SIZE,
        REQUEST_HASH_SIZE,
        SNAPSHOT_TIME,
        TIPSELECTION_ALPHA,
        BELOW_MAX_DEPTH_TRANSACTION_LIMIT
    }
}
