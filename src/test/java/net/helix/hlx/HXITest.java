package net.helix.hlx;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import net.helix.hlx.service.CallableRequest;
import net.helix.hlx.service.dto.AbstractResponse;
import net.helix.hlx.service.dto.ErrorResponse;

/**
 * Unit tests for {@link HXI}
 */
public class HXITest {

    private static TemporaryFolder hxiDir = new TemporaryFolder();
    private static HXI hxi;

    /**
     * Create HXI temporary directory and start HXI.
     * @throws Exception if temporary folder can not be created.
     */
    @BeforeClass
    public static void setup() throws Exception {
        hxiDir.create();
        hxi = new HXI();
        hxi.init(hxiDir.getRoot().getAbsolutePath());

        Field hxiApiField = hxi.getClass().getDeclaredField("hxiAPI");
        hxiApiField.setAccessible(true);
        Map<String, Map<String, CallableRequest<AbstractResponse>>> hxiAPI =
                (Map<String, Map<String, CallableRequest<AbstractResponse>>>) hxiApiField.get(hxi);
        hxiAPI.put("HXI", new HashMap<>());
    }

    /**
     * Shutdown HXI and delete temporary folder.
     * @throws InterruptedException if directory watch thread was interrupted.
     */
    @AfterClass
    public static void shutdown() throws InterruptedException {
        hxi.shutdown();
        hxiDir.delete();
    }

    /**
     * If an command matches the command pattern, but is not valid, expect an unknown command error message.
     */
    @Test
    public void processCommandErrorTest() {
        AbstractResponse response = hxi.processCommand("testCommand.testSuffix", null);
        assertThat("Wrong type of response", response, CoreMatchers.instanceOf(ErrorResponse.class));
        assertTrue("Wrong error message returned in response", response.toString().contains("Command [testCommand.testSuffix] is unknown"));
    }

    /**
     * If null is given as a command, expect a parameter check error message.
     */
    @Test
    public void processCommandNullTest() {
        AbstractResponse response = hxi.processCommand(null, null);
        assertThat("Wrong type of response", response, CoreMatchers.instanceOf(ErrorResponse.class));
        assertTrue("Wrong error message returned in response", response.toString().contains("Command can not be null or empty"));
    }

    /**
     * If an empty string is given as a command, expect a parameter check error message.
     */
    @Test
    public void processCommandEmptyTest() {
        AbstractResponse response = hxi.processCommand("", null);
        assertThat("Wrong type of response", response, CoreMatchers.instanceOf(ErrorResponse.class));
        assertTrue("Wrong error message returned in response", response.toString().contains("Command can not be null or empty"));
    }

    /**
     * If the given command does not exist, expect an unknown command error message.
     */
    @Test
    public void processCommandUnknownTest() {
        AbstractResponse response = hxi.processCommand("unknown", null);
        assertThat("Wrong type of response", response, CoreMatchers.instanceOf(ErrorResponse.class));
        assertTrue("Wrong error message returned in response", response.toString().contains("Command [unknown] is unknown"));
    }

    /**
     * If an HXI module does not have the given command, expect an unknown command error message.
     */
    @Test
    public void processHXICommandUnknownTest() {
        AbstractResponse response = hxi.processCommand("HXI.unknown", null);
        assertThat("Wrong type of response", response, CoreMatchers.instanceOf(ErrorResponse.class));
        assertTrue("Wrong error message returned in response", response.toString().contains("Command [HXI.unknown] is unknown"));
    }

}
