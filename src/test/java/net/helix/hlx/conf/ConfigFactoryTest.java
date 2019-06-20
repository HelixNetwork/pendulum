package net.helix.hlx.conf;

import net.helix.hlx.model.Hash;
import net.helix.hlx.model.HashFactory;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Tests for the {@link ConfigFactory}
 */
public class ConfigFactoryTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    /**
     * Creates and validates a Testnet {@link HelixConfig}.
     */
    @Test
    public void createHelixConfigTestnet() {
        HelixConfig helixConfig = ConfigFactory.createHelixConfig(true);
        assertTrue("Expected helixConfig as instance of TestnetConfig.", helixConfig instanceof TestnetConfig);
        assertTrue("Expected helixConfig as Testnet.", helixConfig.isTestnet());
    }

    /**
     * Creates and validates a Mainnet {@link HelixConfig}.
     */
    @Test
    public void createHelixConfigMainnet() {
        HelixConfig helixConfig = ConfigFactory.createHelixConfig(false);
        assertTrue("Expected helixConfig as instance of MainnetConfig.", helixConfig instanceof MainnetConfig);
        assertFalse("Expected helixConfig as Mainnet.", helixConfig.isTestnet());
    }

    /**
     * Creates and validates a Testnet {@link HelixConfig} with <code>TESTNET=true</code> in config file and
     * <code>testnet: false</code> as method parameter for {@link ConfigFactory#createFromFile(File, boolean)}.
     * @throws IOException when config file not found.
     */
    @Test
    public void createFromFileTestnetWithTestnetTrueAndFalse() throws IOException {
        // lets assume in our configFile is TESTNET=true
        File configFile = createTestnetConfigFile("true");

        // but the parameter is set to testnet=false
        HelixConfig helixConfig = ConfigFactory.createFromFile(configFile, false);
        assertTrue("Expected helixConfig as instance of TestnetConfig.", helixConfig instanceof TestnetConfig);
        assertTrue("Expected helixConfig as Testnet.", helixConfig.isTestnet());
    }

    /**
     * Creates and validates a Testnet {@link HelixConfig} with <code>TESTNET=true</code> in config file and
     * <code>testnet: true</code> as method parameter for {@link ConfigFactory#createFromFile(File, boolean)}.
     * @throws IOException when config file not found.
     */
    @Test
    public void createFromFileTestnetWithTestnetTrueAndTrue() throws IOException {
        // lets assume in our configFile is TESTNET=true
        File configFile = createTestnetConfigFile("true");

        // but the parameter is set to testnet=true
        HelixConfig helixConfig = ConfigFactory.createFromFile(configFile, true);
        assertTrue("Expected helixConfig as instance of TestnetConfig.", helixConfig instanceof TestnetConfig);
        assertTrue("Expected helixConfig as Testnet.", helixConfig.isTestnet());
    }

    /**
     * Creates and validates a Testnet {@link HelixConfig} with <code>TESTNET=false</code> in config file and
     * <code>testnet: true</code> as method parameter for {@link ConfigFactory#createFromFile(File, boolean)}.
     * @throws IOException when config file not found.
     */
    @Test
    public void createFromFileTestnetWithTestnetFalseAndTrue() throws IOException {
        // lets assume in our configFile is TESTNET=false
        File configFile = createTestnetConfigFile("false");

        // but the parameter is set to testnet=true
        HelixConfig helixConfig = ConfigFactory.createFromFile(configFile, true);
        assertTrue("Expected helixConfig as instance of TestnetConfig.", helixConfig instanceof TestnetConfig);
        assertTrue("Expected helixConfig as Testnet.", helixConfig.isTestnet());
    }

    /**
     * Creates and validates a Mainnet {@link HelixConfig} with <code>TESTNET=false</code> in config file and
     * <code>testnet: false</code> as method parameter for {@link ConfigFactory#createFromFile(File, boolean)}.
     * @throws IOException when config file not found.
     */
    @Test
    public void createFromFileTestnetWithTestnetFalseAndFalse() throws IOException {
        // lets assume in our configFile is TESTNET=false
        File configFile = createTestnetConfigFile("false");

        // but the parameter is set to testnet=true
        HelixConfig helixConfig = ConfigFactory.createFromFile(configFile, false);
        assertTrue("Expected helixConfig as instance of MainnetConfig.", helixConfig instanceof MainnetConfig);
        assertFalse("Expected helixConfig as Mainnet.", helixConfig.isTestnet());
    }

    /**
     * Test if leading and trailing spaces are trimmed from string in properties file.
     * @throws IOException when config file not found.
     */
    @Test
    public void createFromFileTestnetWithTrailingSpaces() throws IOException {
        File configFile = createTestnetConfigFile("true");
        HelixConfig helixConfig = ConfigFactory.createFromFile(configFile, true);
        Hash expected = HashFactory.ADDRESS.create(
                "2bebfaee978c03e3263c3e5480b602fb040a120768c41d8bfae6c0c124b8e82a");
        assertEquals("Expected that leading and trailing spaces were trimmed.", expected, HashFactory.ADDRESS.create(helixConfig.getCoordinator())); // TODO: getCoordinator will return Hash after refactoring
    }

    /**
     * Test if trailing spaces are correctly trimmed from integer.
     * @throws IOException when config file not found.
     */
    @Test
    public void createFromFileTestnetWithInteger() throws IOException {
        File configFile = createTestnetConfigFile("true");
        HelixConfig helixConfig = ConfigFactory.createFromFile(configFile, true);
        assertEquals("Expected that trailing spaces are trimmed.", 2, helixConfig.getMilestoneStartIndex());
    }

    /**
     * Test if trailing spaces are correctly trimmed from boolean.
     * @throws IOException when config file not found.
     */
    @Test
    public void createFromFileTestnetWithBoolean() throws IOException {
        File configFile = createTestnetConfigFile("true");
        HelixConfig helixConfig = ConfigFactory.createFromFile(configFile, true);
        assertTrue("Expected that ZMQ is enabled.", helixConfig.isZmqEnabled());
    }

    /**
     * Try to create an {@link HelixConfig} from a not existing configFile.
     * @throws IOException when config file not found.
     */
    @Test(expected = FileNotFoundException.class)
    public void createFromFileTestnetWithFileNotFound() throws IOException {
        File configFile = new File("doesNotExist.ini");
        ConfigFactory.createFromFile(configFile, false);
    }

    private File createTestnetConfigFile(String testnet) throws IOException {
        Properties properties = new Properties();

        // properties include leading and trailing spaces to test against.
        properties.setProperty("TESTNET", testnet);
        properties.setProperty("ZMQ_ENABLED", " TRUE ");
        properties.setProperty("MWM", "4");
        properties.setProperty("SNAPSHOT_FILE", "conf/snapshot.txt");
        properties.setProperty("COORDINATOR", "  2bebfaee978c03e3263c3e5480b602fb040a120768c41d8bfae6c0c124b8e82a  ");
        properties.setProperty("MILESTONE_START_INDEX", "2 ");
        properties.setProperty("KEYS_IN_MILESTONE", "10");
        properties.setProperty("MAX_DEPTH", "1000");

        File configFile = folder.newFile("myCustomHelixConfig.ini");
        FileOutputStream fileOutputStream = new FileOutputStream(configFile);
        properties.store(fileOutputStream, "Test config file created by Unit test!");
        fileOutputStream.close();

        return configFile;
    }
}
