package net.helix.hlx.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.helix.hlx.HXI;
import net.helix.hlx.Helix;
import net.helix.hlx.conf.ConfigFactory;
import net.helix.hlx.conf.HXIConfig;
import net.helix.hlx.conf.HelixConfig;
import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.crypto.SpongeFactory;
import net.helix.hlx.model.TransactionHash;
import net.helix.hlx.service.restserver.resteasy.RestEasy;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.ResponseSpecBuilder;
import com.jayway.restassured.config.HttpClientConfig;
import com.jayway.restassured.path.json.JsonPath;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.specification.RequestSpecification;
import com.jayway.restassured.specification.ResponseSpecification;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.fail;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;

/**
 * Windows developer notes:
 * For running this tests on windows you need the RocksDB dependencies. You need to install the
 * Visual C++ Redistributable for Visual Studio 2015 x64 from
 * https://www.microsoft.com/en-us/download/confirmation.aspx?id=48145
 * Make sure your Java JDK is a 64x version and your JAVA_HOME is set correctly.
 */

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class APIIntegrationTest {

    private static final Boolean spawnNode = true; //can be changed to false to use already deployed node
    private static final String portStr = "14266";
    private static final String hostName = "http://localhost";

    // No result should ever take a minute
    private static final int SOCKET_TIMEOUT = 60_000;

    // Expect to connect to any service worldwide in under 100 ms
    // and to any online machine local in 1 ms. The 50 ms default value is a suggested compromise.
    private static final int CONNECTION_TIMEOUT = 50;
    private static ResponseSpecification specSuccessResponse;
    private static ResponseSpecification specErrorResponse;
    // Constants used in tests

    private static final String[] URIS = {"udp://8.8.8.8:14266", "udp://8.8.8.5:14266"};
    private static final String[] ADDRESSES = {"d0e7e549a4ffe5b4f8343973f0237db9ede3597baced22715c22dcd8c76ae738"};
    private static final String[] HASHES = {"0000f13be306d278fae139dc4a54deb40389a8d1c3677a872a9a198f57aad343"};
    private static final String[] HBYTES = {"0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c2eb2d5297f4e70f3e40e3d7aa3f5c1d7405264aeb72232d06776605d8b6121100000000000000000000000000000000000000000000000000000000000000000000000000000000000000005d092fc0000000000000000000000000000000005031b48d241283c312c68c777bc4563ddd7cbe1ae6a2c58079e1bf3cfef826790000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000068656c6c6f68656c0000016b6c93ca0e0000000000000000000000000000007f000000000000006f0000000000000000000000000000007f00000000000091b0"};
    private static final String NULL_HASH = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final String[] NULL_HASH_LIST = {NULL_HASH};


    private static Helix helix;
    private static API api;
    private static HXI hxi;
    private static HelixConfig configuration;
    private static final Logger log = LoggerFactory.getLogger(APIIntegrationTest.class);

    
    @BeforeClass
    public static void setup() throws Exception {
        if (spawnNode) {
            //configure node parameters
            log.info("IRI integration tests - initializing node.");
            TemporaryFolder dbFolder = new TemporaryFolder();
            dbFolder.create();
            TemporaryFolder logFolder = new TemporaryFolder();
            logFolder.create();

            configuration = ConfigFactory.createHelixConfig(true);
            String[] args = {"-p", portStr, "--testnet", "true", "--db-path", dbFolder.getRoot().getAbsolutePath(), "--db-log-path",
                logFolder.getRoot().getAbsolutePath(), "--mwm", "1"};
            configuration.parseConfigFromArgs(args);

            //create node
            helix = new Helix(configuration);
            hxi = new HXI(helix);
            api = new API(configuration, hxi, helix.transactionRequester,
                    helix.spentAddressesService, helix.tangle, helix.bundleValidator,
                    helix.snapshotProvider, helix.ledgerService, helix.node, helix.tipsSelector,
                    helix.tipsViewModel, helix.transactionValidator,
                    helix.latestMilestoneTracker, helix.graph);

            //init
            try {
                helix.init();
                helix.snapshotProvider.getInitialSnapshot().setTimestamp(0);
                api.init(new RestEasy(configuration));
                hxi.init(HXIConfig.HXI_DIR);
            } catch (final Exception e) {
                log.error("Exception during Helix node initialisation: ", e);
                fail("Exception during Helix node initialisation");
            }
            log.info("Helix Node initialised correctly.");
        }
    }

    @AfterClass
    public static void shutdown() {
        if (spawnNode) {
            try {
                hxi.shutdown();
                api.shutDown();
                helix.shutdown();
            } catch (final Exception e) {
                log.error("Exception occurred shutting down Helix node: ", e);
                fail("Exception occurred shutting down Helix node");
            }
        }
    }

    static {
        RestAssured.port = Integer.parseInt(portStr);
        RestAssured.baseURI = hostName;

        // Define response specification for http status code 200
        specSuccessResponse = new ResponseSpecBuilder().
                expectStatusCode(200).
                expectBody(containsString("duration")).
                build();

        // Define response specification for http status code 500
        specErrorResponse = new ResponseSpecBuilder().
                expectStatusCode(400).
                expectBody(containsString("duration")).
                build();
    }

    /**
     * Tests can choose to use this method instead of the no-args given() static method
     * if they want to manually specify custom timeouts.
     *
     * @param socket_timeout     The Remote host response time.
     * @param connection_timeout Remote host connection time & HttpConnectionManager connection return time.
     * @return The RequestSpecification to use for the test.
     */
    private static RequestSpecification given(int socket_timeout, int connection_timeout) {
        return RestAssured.given().config(RestAssured.config()
            .httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.conn-manager.timeout", (long) connection_timeout)
                .setParam("http.connection.timeout", connection_timeout)
                .setParam("http.socket.timeout", socket_timeout)))
                .contentType("application/json").header("X-HELIX-API-Version", 1);
    }

    private static RequestSpecification given() {
        return given(SOCKET_TIMEOUT, CONNECTION_TIMEOUT);
    }

    private static Gson gson() {
        return new GsonBuilder().create();
    }

    @Test
    public void _order01_sendNonJsonBody() {
        given().
            body("thisIsInvalidJson").
            when().
            post("/").
            then().
            spec(specErrorResponse).
            body(containsString("Invalid JSON syntax"));
    }

    @Test
    public void _order02_getNodeInfoTest() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "getNodeInfo");

        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(specSuccessResponse).
            body(containsString("appName")).
            body(containsString("appVersion")).
            body(containsString("duration")).
            body(containsString("jreAvailableProcessors")).
            body(containsString("jreFreeMemory")).
            body(containsString("jreMaxMemory")).
            body(containsString("jreTotalMemory")).
            body(containsString("jreVersion")).
            body(containsString("latestMilestone")).
            body(containsString("latestMilestoneIndex")).
            body(containsString("jreAvailableProcessors")).
            body(containsString("latestSolidSubtangleMilestone")).
            body(containsString("latestSolidSubtangleMilestoneIndex")).
            body(containsString("milestoneStartIndex")).
            body(containsString("lastSnapshottedMilestoneIndex")).
            body(containsString("neighbors")).
            body(containsString("packetsQueueSize")).
            body(containsString("time")).
            body(containsString("tips")).
            body(containsString("transactionsToRequest"));
    }

    @Test
    public void _order03_getHelixConfigTest() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "getNodeAPIConfiguration");

        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(specSuccessResponse).
            body(containsString("maxFindTransactions")).
            body(containsString("maxRequestsList")).
            body(containsString("maxBytes")).
            body(containsString("maxBodyLength")).
            body(containsString("testNet")).
            body(containsString("milestoneStartIndex"));
    }

    @Test
    public void _order04_addNeighborsTest() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "addNeighbors");
        request.put("uris", URIS);

        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(specSuccessResponse).
            body(containsString("addedNeighbors"));
    }

    @Test
    public void _order05_getNeighborsTest() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "getNeighbors");

        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(specSuccessResponse).
            body(containsString("neighbors")).
            body(containsString("address")).
            body(containsString("numberOfAllTransactions")).
            body(containsString("numberOfInvalidTransactions")).
            body(containsString("numberOfNewTransactions"));
    }

    @Test
    public void removeNeighborsTest() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "removeNeighbors");
        request.put("uris", URIS);
        
        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(specSuccessResponse).
            body(containsString("removedNeighbors"));
    }

    @Test
    public void getTipsTest() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "getTips");

        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(specSuccessResponse).
            body(containsString("hashes"));
    }

    @Test
    public void findTransactionsTest() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "findTransactions");
        request.put("addresses", ADDRESSES);
        
        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(specSuccessResponse).
            body(containsString("hashes"));
    }

    @Test
    public void getHBytesTest() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "getHBytes");
        request.put("hashes", HASHES);

        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(specSuccessResponse).
            body(containsString("hbytes"));
    }

    //@Test
    //empty database returns {"error":"This operations cannot be executed: The subtangle has not been updated yet.","duration":0}
    public void getInclusionStatesTest() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "getInclusionStates");
        request.put("transactions", NULL_HASH_LIST);
        request.put("tips", NULL_HASH_LIST);

        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(specSuccessResponse).
            body(containsString("states"));
    }

    //@Test
    //FIXME: pending https://github.com/iotaledger/iri/issues/618
    public void getBalancesTest() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "getBalances");
        request.put("addresses", ADDRESSES);
        request.put("threshold", 100);

        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(specSuccessResponse).
            body(containsString("milestone"));
    }

    //@Test
    //empty database returns {"error":"This operations cannot be executed: The subtangle has not been updated yet.","duration":0}
    //{"error":"Invalid depth input","duration":0}
    public void getTransactionsToApproveTest() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "getTransactionsToApprove");
        request.put("depth", 27);

        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(specSuccessResponse).
            body(containsString("trunkTransaction")).
            body(containsString("branchTransaction"));
    }

    @Test
    public void broadcastTransactionsTest() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "broadcastTransactions");
        request.put("hbytes", HBYTES);

        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(specSuccessResponse).
            log().all().and();
    }

    @Test
    public void storeTransactionsTest() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "storeTransactions");
        request.put("hbytes", HBYTES);

        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(specSuccessResponse).
            log().all().and();
    }

    @Test
    public void attachToTangleTest() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "attachToTangle");
        request.put("hbytes", HBYTES);
        request.put("trunkTransaction", NULL_HASH);
        request.put("branchTransaction", NULL_HASH);
        request.put("minWeightMagnitude", configuration.getMwm());

        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(specSuccessResponse).
            body(containsString("hbytes"));
    }

    private List<Object> sendTransfer(String[] hbytesArray) {
        return sendTransfer(hbytesArray, NULL_HASH, NULL_HASH);
    }

    private List<Object> sendTransfer(String[] hbytesArray, String branch, String trunk) {
        //do PoW
        final Map<String, Object> request = new HashMap<>();
        request.put("command", "attachToTangle");
        request.put("hbytes", hbytesArray);
        request.put("trunkTransaction", branch);
        request.put("branchTransaction", trunk);
        request.put("minWeightMagnitude", configuration.getMwm());

        Response response = given().
                body(gson().toJson(request)).
                when().
                post("/");
        response.getBody();
        JsonPath responseJson = response.jsonPath();
        List<Object> hbytes = responseJson.getList("hbytes");

        //Store
        request.clear();
        request.put("command", "storeTransactions");
        request.put("hbytes", hbytes);
        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            log().all().and().spec(specSuccessResponse);

        return hbytes;
    }

    private List<Object> findTransactions(String key, String[] values) {
        final Map<String, Object> request = new HashMap<>();
        request.clear();
        request.put("command", "findTransactions");
        request.put(key, values);
        Response response = given().
                body(gson().toJson(request)).
                when().
                post("/");
        response.getBody();
        JsonPath responseJson = response.jsonPath();

        return responseJson.getList("hashes");
    }

    @Test
    public void shouldSendTransactionAndFetchByAddressTest() {
        List<Object> hbytes = sendTransfer(HBYTES);
        String temp = (String) hbytes.get(0);
        String hash = getHash(temp);

        String[] addresses = {temp.substring(TransactionViewModel.ADDRESS_OFFSET * 2,
                (TransactionViewModel.ADDRESS_OFFSET + TransactionViewModel.ADDRESS_SIZE) * 2)};
        List<Object> hashes = findTransactions("addresses", addresses);
        Assert.assertThat(hashes, hasItem(hash));
    }

    //@Test
    //HAS TO BE FIXED: transactions can't be found by tag
    public void shouldSendTransactionAndFetchByTagTest() {
        List<Object> hbytes = sendTransfer(HBYTES);
        String temp = (String) hbytes.get(0);
        String hash = getHash(temp);

        //Tag
        String[] tags = {temp.substring(TransactionViewModel.TAG_OFFSET * 2,
                (TransactionViewModel.TAG_OFFSET + TransactionViewModel.TAG_SIZE) * 2)};
        List<Object> hashes = findTransactions("tags", tags);
        Assert.assertThat(hashes, hasItem(hash));

        //ObsoleteTag
        String[] obsoleteTags = {temp.substring(TransactionViewModel.BUNDLE_NONCE_OFFSET * 2,
                (TransactionViewModel.BUNDLE_NONCE_OFFSET + TransactionViewModel.BUNDLE_NONCE_SIZE) * 2)};
        List<Object> hashes1 = findTransactions("tags", obsoleteTags);
        Assert.assertThat(hashes1, hasItem(hash));
    }

    private String getHash(String hex) {
        return TransactionHash.calculate(hex, 0, TransactionViewModel.SIZE, SpongeFactory.create(SpongeFactory.Mode.S256)).hexString();
    }
    
}
