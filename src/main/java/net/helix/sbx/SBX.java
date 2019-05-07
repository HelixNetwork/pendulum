package net.helix.sbx;

import net.helix.sbx.conf.BaseHelixConfig;
import net.helix.sbx.conf.Config;
import net.helix.sbx.conf.ConfigFactory;
import net.helix.sbx.conf.HelixConfig;
import net.helix.sbx.service.API;
import net.helix.sbx.service.milestone.MSS;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import net.helix.sbx.utils.HelixIOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;


/**
 *
 * Main sbx (SBX) starting class.
 * <p>
 *     SBX enables the Tangle to operate in a simplified environment. Developers and testers can run SBX to imitate behavior of an actual Helix node.
 *     The Node running the SBX software enables your device to communicate with neighbors
 *     in the peer-to-peer network that the Tangle operates on.
 * </p>
 * <p>
 *     SBX implements all the core functionality necessary for participating in the Helix network as a full node.
 *     This includes, but is not limited to:
 *     <ul>
 *         <li>Receiving and broadcasting transactions through TCP and UDP.</li>
 *         <li>Handling of HTTP requests from clients.</li>
 *         <li>Tracking and validating Milestones.</li>
 *         <li>Loading custom modules that extend the API.</li>
 *     </ul>
 * </p>
 *
 * @see <a href="https://docs.hlx.ai/sbx">Online documentation on sbx</a>
 */
public class SBX {

    public static final String MAINNET_NAME = "SBX";
    public static final String TESTNET_NAME = "SBX Testnet";
    public static final String VERSION = "0.5.1";

    /**
     * The entry point of the helix sandbox.
     * Starts by configuring the logging settings, then proceeds to {@link SBXLauncher#main(String[])}
     * The log level is set to INFO by default.
     *
     * @param args Configuration arguments. See {@link BaseHelixConfig} for a list of all options.
     * @throws Exception If we fail to start the SBX launcher.
     */

    public static void main(String[] args) throws Exception {
        // Logging is configured first before any references to Logger or LoggerFactory.
        // Any public method or field accessors needed in SBX should be put in SBX and then delegate to SBXLauncher. That
        // ensures that future code does not need to know about this setup.
        configureLogging();
        SBXLauncher.main(args);
    }

    private static void configureLogging() {
        String config = System.getProperty("logback.configurationFile");
        String level = System.getProperty("logging-level", "debug").toUpperCase();
        switch (level) {
            case "OFF":
            case "ERROR":
            case "WARN":
            case "INFO":
            case "DEBUG":
            case "TRACE":
                break;
            case "ALL":
                level = "TRACE";
                break;
            default:
                level = "INFO";
        }
        System.getProperties().put("logging-level", level);
        System.out.println("Logging - property 'logging-level' set to: [" + level + "]");
        if (config != null) {
            System.out.println("Logging - alternate logging configuration file specified at: '" + config + "'");
        }
        // HelixIOUtils.saveLogs(); TODO: move this to main
    }

    private static class SBXLauncher {
        private static final Logger log = LoggerFactory.getLogger(SBXLauncher.class);

        public static Helix helix;
        public static API api;
        public static HXI hxi;
        public static MSS mss;

        /**
         * Starts sbx. Setup is as follows:
         * <ul>
         *     <li>Load the configuration.</li>
         *     <li>Create {@link Helix}, {@link HXI} and {@link API}.</li>
         *     <li>Listen for node shutdown.</li>
         *     <li>Initialize {@link Helix}, {@link HXI} and {@link API} using their <tt>init()</tt> methods.</li>
         * </ul>
         *
         * If no exception is thrown, the node starts synchronizing with the network, and the API can be used.
         *
         * @param args Configuration arguments. See {@link BaseHelixConfig} for a list of all options.
         * @throws Exception If any of the <tt>init()</tt> methods failed to initialize.
         */
        public static void main(String [] args) throws Exception {
            HelixConfig config = createConfiguration(args);
            log.info("Welcome to {} {}", config.isTestnet() ? TESTNET_NAME : MAINNET_NAME, VERSION);

            helix = new Helix(config);
            hxi = new HXI(helix);
            api = new API(helix, hxi);
            mss = new MSS(config, api);
            shutdownHook();

            try {
                helix.init();
                api.init();
                //TODO redundant parameter but we will touch this when we refactor HXI
                hxi.init(config.getHxiDir());
                log.info("Helix Node initialised correctly.");
            } catch (Exception e) {
                log.error("Exception during Helix node initialisation: ", e);
                throw e;
            }
            if(config.getMsDelay() > 0) {
                mss.startScheduledExecutorService();
            }
            if (config.getSaveLog()) {
                HelixIOUtils.saveLogs();
            }
        }

        /**
         * Gracefully shuts down the node by calling <tt>shutdown()</tt> on {@link Helix}, {@link HXI} and {@link API}.
         * Exceptions during shutdown are caught and logged.
         */
        private static void shutdownHook() {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down Helix node, please hold tight...");
                try {
                    mss.shutdown();
                    hxi.shutdown();
                    api.shutDown();
                    helix.shutdown();
                } catch (Exception e) {
                    log.error("Exception occurred shutting down Helix node: ", e);
                }
            }, "Shutdown Hook"));
        }

        private static HelixConfig createConfiguration(String[] args) {
            HelixConfig helixConfig = null;
            String message = "Configuration is created using ";
            try {
                boolean testnet = ArrayUtils.contains(args, Config.TESTNET_FLAG);
                File configFile = chooseConfigFile(args);
                if (configFile != null) {
                    helixConfig = ConfigFactory.createFromFile(configFile, testnet);
                    message += configFile.getName() + " and command line args";
                }
                else {
                    helixConfig = ConfigFactory.createHelixConfig(testnet);
                    message += "command line args only";
                }
                JCommander jCommander = helixConfig.parseConfigFromArgs(args);
                if (helixConfig.isHelp()) {
                    jCommander.usage();
                    System.exit(0);
                }
            } catch (IOException | IllegalArgumentException e) {
                log.error("There was a problem reading configuration from file: {}", e.getMessage());
                log.debug("", e);
                System.exit(-1);
            } catch (ParameterException e) {
                log.error("There was a problem parsing commandline arguments: {}", e.getMessage());
                log.debug("", e);
                System.exit(-1);
            }

            log.info(message);
            log.info("parsed the following cmd args: {}", Arrays.toString(args));
            return helixConfig;
        }

        private static File chooseConfigFile(String[] args) {
            int index = Math.max(ArrayUtils.indexOf(args, "-c"), ArrayUtils.indexOf(args, "--config"));
            if (index != -1) {
                try {
                    String fileName = args[++index];
                    return new File(fileName);
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            "The file after `-c` or `--config` isn't specified or can't be parsed.", e);
                }
            }
            else if (HelixConfig.CONFIG_FILE.exists()) {
                return HelixConfig.CONFIG_FILE;
            }
            return null;
        }
    }
}
