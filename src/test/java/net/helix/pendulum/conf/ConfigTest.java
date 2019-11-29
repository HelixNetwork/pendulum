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
    test that --remote returns true without specifically giving true on cmd line
    */
    @Test
    public void remoteFlagTest() {
        String[] args = {"--remote"};
        PendulumConfig pendulumConfig = ConfigFactory.createPendulumConfig(false);
        pendulumConfig.parseConfigFromArgs(args);
        Assert.assertEquals("The api interface should be open to the public",
                            "0.0.0.0", pendulumConfig.getApiHost());
    }

    /*
    The order of flags provided on cmd line makes a difference.
    Test that specifying --remote after any --api_host flag will set the value for api_host.
    */
    @Test
    public void testFlagPrecedentRule(){
        String[] args = {
                "--api_host", "localhost",
                "--remote"
        };
        PendulumConfig penConfig = ConfigFactory.createPendulumConfig(false);
        penConfig.parseConfigFromArgs(args);
        Assert.assertNotEquals(penConfig.getApiHost(), "localhost");
        Assert.assertEquals(penConfig.getApiHost(), "0.0.0.0");
    }
    @Test
    public void testIgnoredApiHosts(){
        String[] args = {
                "--ignored_api_endpoints",
                "addNeighbor, getNeighbors, removeNeighbors, attachToTangle, interruptAttachingToTangle"
        };
        PendulumConfig penConfig = ConfigFactory.createPendulumConfig(false);
        penConfig.parseConfigFromArgs(args);
        Assert.assertEquals(penConfig.getIgnoredApiEndpoints(),
                Arrays.asList(
                        "addNeighbor", "getNeighbors", "removeNeighbors", "attachToTangle", "interruptAttachingToTangle")
                );
    }
    /*
    Test all known command line arguments with non-default value to make sure they are being parsed and set correctly.
    */
    @Test
    public void testCommandLineArgs(){
        String[] args = {
                "--xi_dir", "not_here",
                "--remote", "true",
                //"--api_host", "localhost",
                "--api_port", "1234",
                //"--allowed_api_hosts", "0.0.0.0",
                "--pow_threads", "2",
                //"--remote_auth", "pendulum:swings",
                //"--ignored_api_endpoints", "addNeighbor, getNeighbors, removeNeighbors, attachToTangle, interruptAttachingToTangle",
                "--max_body_length", "123456",
                "--max_find_transactions", "123456",
                "--max_get_transaction_strings", "123456",
                "--max_requests_list", "123456",
                "--db", "mongodb", //not available, but we're just making sure the cmd line flags work as expected
                "--db_cache_size", "123456",
                "--db_log_path", "somewhere_fancy.log",
                "--db_path", "some_dir",
                "--local_snapshots_base_path", "./snapshot-go-here",
                "--local_snapshots_depth", "123456",
                "--local_snapshots_enabled", "false",
                "--local_snapshots_interval_synced", "123456",
                "--local_snapshots_interval_unsynced", "123456",
                "--local_snapshots_pruning_delay", "123456",
                "--local_snapshots_pruning_enabled", "true",
                "--neighbors", "udp://localhost:4101",
                "--tcp_receiver_port", "1234",
                "--udp_receiver_port", "1234",
                "--max_peers", "1234",
                "--p_drop_cache", "1.0",
                "--p_drop_transaction", "1.0",
                "--p_propagate_request", "1.0",
                "--p_remove_request", "1.0",
                "--p_reply_random", "1.0",
                "--p_select_milestone", "1.0",
                "--p_send_milestone", "1.0",
                "--queue_size", "1234",
                "--dns_refresher", "false",
                "--dns_resolution", "false",
                "--send_limit", "2",
                "--cache_size", "1234",
                "--update_validator_delay", "1234",
                "--validator", "true",
                "--validator_manager", "true",
                "--validator_path", "./somewhere.txt",
                "--start_validator", "10",
                "--round", "60000",
                "--round_pause", "60000",
                "--max_analyzed_transactions", "60000",
                "--max_depth", "10",
                "--rescan", "true",
                "--revalidate", "true",
                "--remote_auth", "pendulum:swings",
                "--alpha", "1.0",
                "--genesis", "2345024001000",
                "--zmq_enable_ipc", "true",
                "--zmq_enable_tcp", "true",
                "--zmq_enabled", "true",
                "--zmq_ipc", "ipc://pendulum",
                "--zmq_port", "1234",
                "--zmq_threads", "10",
                "--savelog_enabled", "true",
                "--savelog_path", "./somewhere/",
                "--savelog_xml", "/save.xml"
        };

        PendulumConfig penConfig = ConfigFactory.createPendulumConfig(false);
        penConfig.parseConfigFromArgs(args);
        Assert.assertEquals("xi_dir  ",   "not_here",  penConfig.getXiDir());
        Assert.assertEquals("api_port  ", 1234,   penConfig.getApiPort());
        Assert.assertEquals("pow_threads  ", 2,    penConfig.getPowThreads());
        Assert.assertEquals("max_body_length  ", 123456, penConfig.getMaxBodyLength());
        Assert.assertEquals("max_find_transactions  ", 123456,    penConfig.getMaxFindTransactions());
        Assert.assertEquals("max_get_transaction_strings  ", 123456,     penConfig.getMaxTransactionStrings());
        Assert.assertEquals("max_requests_list  ", 123456,    penConfig.getMaxRequestsList());
        Assert.assertEquals("db  ",   "mongodb" ,penConfig.getMainDb());
        Assert.assertEquals("db_cache_size  ",  123456,   penConfig.getDbCacheSize());
        Assert.assertEquals("db_log_path  ",  "somewhere_fancy.log" , penConfig.getDbLogPath());
        Assert.assertEquals("db_path  ", "some_dir",    penConfig.getDbPath());
        Assert.assertEquals("local_snapshots_base_path  ", "./snapshot-go-here",    penConfig.getLocalSnapshotsBasePath());
        Assert.assertEquals("local_snapshots_depth  ", 123456,    penConfig.getLocalSnapshotsDepth());
        Assert.assertEquals("local_snapshots_enabled  ", false,    penConfig.getLocalSnapshotsEnabled());
        Assert.assertEquals("local_snapshots_interval_synced  ", 123456,     penConfig.getLocalSnapshotsIntervalSynced());
        Assert.assertEquals("local_snapshots_interval_unsynced  ", 123456,    penConfig.getLocalSnapshotsIntervalUnsynced());
        Assert.assertEquals("local_snapshots_pruning_delay  ",  123456,   penConfig.getLocalSnapshotsPruningDelay());
        Assert.assertEquals("local_snapshots_pruning_enabled  ",  true,   penConfig.getLocalSnapshotsPruningEnabled());
        Assert.assertEquals("neighbors  ",  Arrays.asList("udp://localhost:4101"),    penConfig.getNeighbors());
        Assert.assertEquals("tcp_receiver_port  ",  1234,   penConfig.getTcpReceiverPort());
        Assert.assertEquals("udp_receiver_port  ",  1234,   penConfig.getUdpReceiverPort());
        Assert.assertEquals("max_peers  ",   1234,  penConfig.getMaxPeers());
        Assert.assertEquals("p_drop_cache  ",  1.0,   penConfig.getpDropCacheEntry(), 0d);
        Assert.assertEquals("p_drop_transaction  ",  1.0,   penConfig.getpDropTransaction(), 0d);
        Assert.assertEquals("p_propagate_request  ", 1.0,      penConfig.getpPropagateRequest(), 0d);
        Assert.assertEquals("p_remove_request  ",  1.0,     penConfig.getpRemoveRequest(), 0d);
        Assert.assertEquals("p_reply_random  ",  1.0,     penConfig.getpReplyRandomTip(), 0d);
        Assert.assertEquals("p_select_milestone  ", 1.0,      penConfig.getpSelectMilestoneChild(), 0d);
        Assert.assertEquals("p_send_milestone  ",1.0,       penConfig.getpSendMilestone(), 0d);
        Assert.assertEquals("queue_size  ", 1234,    penConfig.getqSizeNode());
        Assert.assertEquals("dns_refresher  ",  false,   penConfig.isDnsRefresherEnabled());
        Assert.assertEquals("dns_resolution  ", false,    penConfig.isDnsResolutionEnabled());
        Assert.assertEquals("send_limit  ",  2,   penConfig.getSendLimit());
        Assert.assertEquals("cache_size  ",  1234,   penConfig.getCacheSizeBytes());
        Assert.assertEquals("update_validator_delay  ",   1234,  penConfig.getUpdateValidatorDelay());
        Assert.assertEquals("validator  ",   true,  penConfig.isValidator());
        Assert.assertEquals("validator_manager  ", true,    penConfig.getValidatorManagerEnabled());
        Assert.assertEquals("validator_path  ", "./somewhere.txt",    penConfig.getValidatorPath());
        Assert.assertEquals("start_validator  ", 10,    penConfig.getStartRoundDelay());
        Assert.assertEquals("round  ", 60000,    penConfig.getRoundDuration());
        Assert.assertEquals("round_pause  ",  60000,   penConfig.getRoundPause());
        Assert.assertEquals("max_analyzed_transactions  ",  60000,   penConfig.getBelowMaxDepthTransactionLimit());
        Assert.assertEquals("max_depth  ",  10,   penConfig.getMaxDepth());
        Assert.assertEquals("rescan  ",   true,  penConfig.isRescanDb());
        Assert.assertEquals("revalidate  ",  true,   penConfig.isRevalidate());
        Assert.assertEquals("remote_auth  ",   "pendulum:swings",  penConfig.getRemoteAuth());
        Assert.assertEquals("alpha  ",  1.0,   penConfig.getAlpha(), 0d);
        Assert.assertEquals("genesis  ", 2345024001000L,    penConfig.getGenesisTime());
        Assert.assertEquals("zmq_enable_ipc  ",  true,   penConfig.isZmqEnableIpc());
        Assert.assertEquals("zmq_enable_tcp  ", true,    penConfig.isZmqEnableTcp());
        Assert.assertEquals("zmq_enabled  ",  true,   penConfig.isZmqEnabled());
        Assert.assertEquals("zmq_ipc  ", "ipc://pendulum", penConfig.getZmqIpc());
        Assert.assertEquals("zmq_port  ",  1234,   penConfig.getZmqPort());
        Assert.assertEquals("zmq_threads  ",  10,   penConfig.getZmqThreads());
        Assert.assertEquals("savelog_enabled  ",  true,   penConfig.isSaveLogEnabled());
        Assert.assertEquals("savelog_path  ",   "./somewhere/",   penConfig.getSaveLogBasePath());
        Assert.assertEquals("savelog_xml  ",  "/save.xml",   penConfig.getSaveLogXMLFile());
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
                "--api_host", "1.1.1.1",
                "--ignored_api_endpoints", "call1 call2, call3",
                "--max_find_transactions", "500",
                "--max_requests_list", "1000",
                "--max_get_transaction_strings", "4000",
                "--max_body_length", "220",
                "--remote_auth", "2.2.2.2",
                "--p_remove_request", "0.23",
                "--send_limit", "1000",
                "--max_peers", "10",
                "--dns_refresher", "false",
                "--dns_resolution", "false",
                "--xi_dir", "/XI",
                "--db_path", "/db",
                "--db_log_path", "/dblog",
                "--zmq_enabled", "true",
                "--mwm", "4", //we ignore this on mainnet
                "--testnet_coordinator", "TTTTTTTTT",
                "--test_no_coo_validation",
                "--fake_config" //this should be ignored everywhere
        };

        PendulumConfig pendulumConfig = ConfigFactory.createPendulumConfig(false);
        Assert.assertThat("wrong config class created",
                            pendulumConfig, CoreMatchers.instanceOf(MainnetConfig.class));
        pendulumConfig.parseConfigFromArgs(args);
        Assert.assertEquals("port value", 8089, pendulumConfig.getApiPort());
        Assert.assertEquals("udp port", 4200, pendulumConfig.getUdpReceiverPort());
        Assert.assertEquals("tcp port", 5200, pendulumConfig.getTcpReceiverPort());
        Assert.assertEquals("neighbors", Arrays.asList("udp://neighbor1", "neighbor", "tcp://neighbor2"),
                pendulumConfig.getNeighbors());
        Assert.assertEquals("api host", "1.1.1.1", pendulumConfig.getApiHost());
        Assert.assertEquals("ignored api endpoints", Arrays.asList("call1", "call2", "call3"),
                pendulumConfig.getIgnoredApiEndpoints());
        Assert.assertEquals("max find transactions", 500, pendulumConfig.getMaxFindTransactions());
        Assert.assertEquals("max requests list", 1000, pendulumConfig.getMaxRequestsList());
        Assert.assertEquals("max get bytes", 4000, pendulumConfig.getMaxTransactionStrings());
        Assert.assertEquals("max body length", 220, pendulumConfig.getMaxBodyLength());
        Assert.assertEquals("remote_auth", "2.2.2.2", pendulumConfig.getRemoteAuth());
        Assert.assertEquals("p remove request", 0.23d, pendulumConfig.getpRemoveRequest(), 0d);
        Assert.assertEquals("send limit", 1000, pendulumConfig.getSendLimit());
        Assert.assertEquals("max peers", 10, pendulumConfig.getMaxPeers());
        Assert.assertEquals("dns refresher", false, pendulumConfig.isDnsRefresherEnabled());
        Assert.assertEquals("dns resolution", false, pendulumConfig.isDnsResolutionEnabled());
        Assert.assertEquals("xi-dir", "/XI", pendulumConfig.getXiDir());
        Assert.assertEquals("db path", "/db", pendulumConfig.getDbPath());
        Assert.assertEquals("zmq enabled", true, pendulumConfig.isZmqEnabled());
        Assert.assertNotEquals("mwm", 4, pendulumConfig.getMwm());
        Assert.assertNotEquals("coo", pendulumConfig.getValidatorManagerAddress(), "TTTTTTTTT");
    }



    @Test
    public void argsParsingTestnetTest() {
        String[] args = {
                "-p", "8089",
                "-u", "4200",
                "-t", "5200",
                "-n", "udp://neighbor1 neighbor, tcp://neighbor2",
                "--api_host", "1.1.1.1",
                "--ignored_api_endpoints", "call1 call2, call3",
                "--max_find_transactions", "500",
                "--max_requests_list", "1000",
                "--max_get_transaction_strings", "4000",
                "--max_body_length", "220",
                "--remote_auth", "2.2.2.2",
                "--p_remove_request", "0.23",
                "--send_limit", "1000",
                "--max_peers", "10",
                "--dns_refresher", "false",
                "--dns_resolution", "false",
                "--xi_dir", "/XI",
                "--db_path", "/db",
                "--db_log_path", "/dblog",
                "--zmq_enabled", "true",
                //we ignore this on mainnet
                "--mwm", "4",
                "--testnet_coordinator", "TTTTTTTTT",
                "--testnet_no_milestone_sign_validation",
                //this should be ignored everywhere
                "--fake_config"
        };
        PendulumConfig pendulumConfig = ConfigFactory.createPendulumConfig(true);
        Assert.assertThat("wrong config class created", pendulumConfig, CoreMatchers.instanceOf(TestnetConfig.class));

        pendulumConfig.parseConfigFromArgs(args);
        Assert.assertEquals("port value", 8089, pendulumConfig.getApiPort());
        Assert.assertEquals("udp port", 4200, pendulumConfig.getUdpReceiverPort());
        Assert.assertEquals("tcp port", 5200, pendulumConfig.getTcpReceiverPort());
        Assert.assertEquals("neighbors", Arrays.asList("udp://neighbor1", "neighbor", "tcp://neighbor2"),
                pendulumConfig.getNeighbors());
        Assert.assertEquals("api host", "1.1.1.1", pendulumConfig.getApiHost());
        Assert.assertEquals("ignored api endpoints", Arrays.asList("call1", "call2", "call3"),
                pendulumConfig.getIgnoredApiEndpoints());
        Assert.assertEquals("max find transactions", 500, pendulumConfig.getMaxFindTransactions());
        Assert.assertEquals("max requests list", 1000, pendulumConfig.getMaxRequestsList());
        Assert.assertEquals("max get tx strings", 4000, pendulumConfig.getMaxTransactionStrings());
        Assert.assertEquals("max body length", 220, pendulumConfig.getMaxBodyLength());
        Assert.assertEquals("remote_auth", "2.2.2.2", pendulumConfig.getRemoteAuth());
        Assert.assertEquals("p remove request", 0.23d, pendulumConfig.getpRemoveRequest(), 0d);
        Assert.assertEquals("send limit", 1000, pendulumConfig.getSendLimit());
        Assert.assertEquals("max peers", 10, pendulumConfig.getMaxPeers());
        Assert.assertEquals("dns refresher", false, pendulumConfig.isDnsRefresherEnabled());
        Assert.assertEquals("dns resolution", false, pendulumConfig.isDnsResolutionEnabled());
        Assert.assertEquals("xi_dir", "/XI", pendulumConfig.getXiDir());
        Assert.assertEquals("db path", "/db", pendulumConfig.getDbPath());
        Assert.assertEquals("zmq enabled", true, pendulumConfig.isZmqEnabled());
        Assert.assertEquals("mwm", 4, pendulumConfig.getMwm());
        //Assert.assertEquals("coo", "TTTTTTTTT", pendulumConfig.getValidatorManagerAddress());
        Assert.assertEquals("validate testnet milestone signatures", true,
                pendulumConfig.isValidateTestnetMilestoneSig());
    }

    @Test
    public void iniParsingMainnetTest() throws Exception {
        String iniContent = new StringBuilder()
                .append("[PENDULUM]").append(System.lineSeparator())
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
        Assert.assertEquals("PORT", 8088, pendulumConfig.getApiPort());
        Assert.assertEquals("NEIGHBORS", Arrays.asList("udp://neighbor1", "neighbor", "tcp://neighbor2"),
                pendulumConfig.getNeighbors());
        Assert.assertEquals("ZMQ_ENABLED", true, pendulumConfig.isZmqEnabled());
        Assert.assertEquals("P_REMOVE_REQUEST", 0.4d, pendulumConfig.getpRemoveRequest(), 0);
        Assert.assertNotEquals("MWM", 4, pendulumConfig.getMwm());
    }

    @Test
    public void iniParsingTestnetTest() throws Exception {
        String iniContent = new StringBuilder()
                .append("[PENDULUM]").append(System.lineSeparator())
                .append("PORT = 8088").append(System.lineSeparator())
                .append("NEIGHBORS = udp://neighbor1 neighbor, tcp://neighbor2").append(System.lineSeparator())
                .append("ZMQ_ENABLED = true").append(System.lineSeparator())
                .append("DNS_RESOLUTION_ENABLED = true").append(System.lineSeparator())
                .append("P_REMOVE_REQUEST = 0.4").append(System.lineSeparator())
                .append("MWM = 4").append(System.lineSeparator())
                .append("NUMBER_OF_KEYS_IN_A_MILESTONE = 3").append(System.lineSeparator())
                .append("DONT_VALIDATE_TESTNET_MILESTONE_SIG = true").append(System.lineSeparator())
                .append("ALPHA = 1.1").append(System.lineSeparator())
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
        Assert.assertEquals("PORT", 8088, pendulumConfig.getApiPort());
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
        Assert.assertEquals("ALPHA", 1.1d, pendulumConfig.getAlpha(), 0);
        Assert.assertEquals("VALIDATE_TESTNET_MILESTONE_SIG",
                pendulumConfig.isValidateTestnetMilestoneSig(), true);
        //prove that REMOTE did nothing
        Assert.assertEquals("API_HOST", pendulumConfig.getApiHost(), "localhost");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidIni() throws IOException {
        String iniContent = new StringBuilder()
                .append("[PENDULUM]").append(System.lineSeparator())
                .append("REVALIDATE")
                .toString();
        try (Writer writer = new FileWriter(configFile)) {
            writer.write(iniContent);
        }
        ConfigFactory.createFromFile(configFile, false);
    }

    @Test
    public void backwardsIniCompatibilityTest() {
        Collection<String> configNames = PendulumUtils.getAllSetters(TestnetConfig.class)
                .stream()
                .map(this::deriveNameFromSetter)
                .collect(Collectors.toList());

        Stream.of(LegacyDefaultConf.values())
                .map(Enum::name)
                // make it explicit that we have removed some configs
                // in some cases, we have renamed the config param (to e.g. fix double negative variable names)
                .filter(config -> !ArrayUtils.contains(new String[]{"CONFIG", "TESTNET", "DEBUG",
                        "MIN_RANDOM_WALKS", "MAX_RANDOM_WALKS", "DONT_VALIDATE_TESTNET_MILESTONE_SIG",
                "COORDINATOR", "REMOTE_LIMIT_API", "MWM", "TIPSELECTION_ALPHA"}, config))
                .forEach(config ->
                        Assert.assertThat(configNames, IsCollectionContaining.hasItem(config))
                );
    }

    @Test
    public void validateMilestoneSigDefaultValueTest() {
        PendulumConfig pendulumConfig = ConfigFactory.createPendulumConfig(true);
        Assert.assertTrue("By default testnet should be validating milestones",
                pendulumConfig.isValidateTestnetMilestoneSig());
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
