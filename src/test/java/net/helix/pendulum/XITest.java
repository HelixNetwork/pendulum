package net.helix.pendulum;

import net.helix.pendulum.service.CallableRequest;
import net.helix.pendulum.service.dto.AbstractResponse;
import net.helix.pendulum.service.dto.ErrorResponse;
import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link XI}
 */
public class XITest {

    private static TemporaryFolder xiDir = new TemporaryFolder();
    private static XI XI;

    /**
     * Create XI temporary directory and start XI.
     * @throws Exception if temporary folder can not be created.
     */
    @BeforeClass
    public static void setUp() throws Exception {
        xiDir.create();
        XI = new XI();
        XI.init(xiDir.getRoot().getAbsolutePath());

        Field xiApiField = XI.getClass().getDeclaredField("xiAPI");
        xiApiField.setAccessible(true);
        Map<String, Map<String, CallableRequest<AbstractResponse>>> xiAPI =
                (Map<String, Map<String, CallableRequest<AbstractResponse>>>) xiApiField.get(XI);
        xiAPI.put("XI", new HashMap<>());
    }

    /**
     * Shutdown XI and delete temporary folder.
     * @throws InterruptedException if directory watch thread was interrupted.
     */
    @AfterClass
    public static void shutdown() throws InterruptedException {
        XI.shutdown();
        xiDir.delete();
    }

    /**
     * If an command matches the command pattern, but is not valid, expect an unknown command error message.
     */
    @Test
    public void processCommandErrorTest() {
        AbstractResponse response = XI.processCommand("testCommand.testSuffix", null);
        assertThat("Wrong type of response", response, CoreMatchers.instanceOf(ErrorResponse.class));
        assertTrue("Wrong error message returned in response", response.toString().contains("Command [testCommand.testSuffix] is unknown"));
    }

    /**
     * If null is given as a command, expect a parameter check error message.
     */
    @Test
    public void processCommandNullTest() {
        AbstractResponse response = XI.processCommand(null, null);
        assertThat("Wrong type of response", response, CoreMatchers.instanceOf(ErrorResponse.class));
        assertTrue("Wrong error message returned in response", response.toString().contains("Command can not be null or empty"));
    }

    /**
     * If an empty string is given as a command, expect a parameter check error message.
     */
    @Test
    public void processCommandEmptyTest() {
        AbstractResponse response = XI.processCommand("", null);
        assertThat("Wrong type of response", response, CoreMatchers.instanceOf(ErrorResponse.class));
        assertTrue("Wrong error message returned in response", response.toString().contains("Command can not be null or empty"));
    }

    /**
     * If the given command does not exist, expect an unknown command error message.
     */
    @Test
    public void processCommandUnknownTest() {
        AbstractResponse response = XI.processCommand("unknown", null);
        assertThat("Wrong type of response", response, CoreMatchers.instanceOf(ErrorResponse.class));
        assertTrue("Wrong error message returned in response", response.toString().contains("Command [unknown] is unknown"));
    }

    /**
     * If an XI module does not have the given command, expect an unknown command error message.
     */
    @Test
    public void processXICommandUnknownTest() {
        AbstractResponse response = XI.processCommand("XI.unknown", null);
        assertThat("Wrong type of response", response, CoreMatchers.instanceOf(ErrorResponse.class));
        assertTrue("Wrong error message returned in response", response.toString().contains("Command [XI.unknown] is unknown"));
    }

}
