package net.helix.pendulum;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import net.helix.pendulum.conf.BasePendulumConfig;
import net.helix.pendulum.conf.Config;
import net.helix.pendulum.conf.ConfigFactory;
import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.service.API;
import net.helix.pendulum.service.ApiArgs;
import net.helix.pendulum.service.milestone.impl.MilestonePublisher;
import net.helix.pendulum.service.restserver.resteasy.RestEasy;
import net.helix.pendulum.service.validatormanager.impl.ValidatorPublisher;
import net.helix.pendulum.utils.PendulumIOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;


/**
 *
 * Main starting class.
 * <p>
 *     Running the Pendulum software enables your device to communicate with neighbors
 *     in the peer-to-peer network that the Tangle operates on.
 * </p>
 * <p>
 *     The Main class implements all the core functionality necessary for participating in the Pendulum network as a full node.
 *     This includes, but is not limited to:
 *     <ul>
 *         <li>Receiving and broadcasting transactions through TCP and UDP.</li>
 *         <li>Handling of HTTP requests from clients.</li>
 *         <li>Tracking and validating Milestones.</li>
 *         <li>Loading custom modules that extend the API.</li>
 *     </ul>
 * </p>
 *
 * @see <a href="https://dev.hlx.ai">Online documentation on pendulum</a>
 */
public class Main {

    public static final String MAINNET_NAME = "Pendulum";
    public static final String TESTNET_NAME = "Pendulum Testnet";

    public static final String VERSION = "1.0.3";


    /**
     * The entry point of Pendulum.
     * Starts by configuring the logging settings, then proceeds to {@link MainLauncher#main(String[])}
     * The log level is set to INFO by default.
     *
     * @param args Configuration arguments. See {@link BasePendulumConfig} for a list of all options.
     * @throws Exception If we fail to start the MainLauncher.
     */

    public static void main(String[] args) throws Exception {
        // Logging is configured first before any references to Logger or LoggerFactory.
        // Any public method or field accessors needed in Main should be put in Main and then delegated to MainLauncher. That
        // ensures that future code does not need to know about this setup.
        configureLogging();
        MainLauncher.main(args);
    }

    private static void configureLogging() {
        PendulumIOUtils.saveLogs();
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
                break;
        }
        System.getProperties().put("logging-level", level);
        System.out.println("Logging - property 'logging-level' set to: [" + level + "]");
        if (config != null) {
            System.out.println("Logging - alternate logging configuration file specified at: '" + config + "'");
        }
    }

    private static class MainLauncher {
        private static final Logger log = LoggerFactory.getLogger(MainLauncher.class);

        public static Pendulum pendulum;
        public static API api;
        public static XI XI;
        public static MilestonePublisher milestonePublisher;
        public static ValidatorPublisher validatorPublisher;

        /**
         * Starts Pendulum. Setup is as follows:
         * <ul>
         *     <li>Load the configuration.</li>
         *     <li>Create {@link Pendulum}, {@link XI} and {@link API}.</li>
         *     <li>Listen for node shutdown.</li>
         *     <li>Initialize {@link Pendulum}, {@link XI} and {@link API} using their <tt>init()</tt> methods.</li>
         * </ul>
         *
         * If no exception is thrown, the node starts synchronizing with the network, and the API can be used.
         *
         * @param args Configuration arguments. See {@link BasePendulumConfig} for a list of all options.
         * @throws Exception If any of the <tt>init()</tt> methods failed to initialize.
         */
        public static void main(String [] args) throws Exception {
            PendulumConfig config = createConfiguration(args);
            log.info("Welcome to {} {}", config.isTestnet() ? TESTNET_NAME : MAINNET_NAME, VERSION);

            pendulum = new Pendulum(config);
            XI = new XI(pendulum);
            ApiArgs apiArgs = new ApiArgs(pendulum, XI);
            api = new API(apiArgs);
            shutdownHook();

            try {
                pendulum.init();
                api.init(new RestEasy(pendulum.configuration));
                //TODO redundant parameter but we will touch this when we refactor XI
                XI.init(config.getXiDir());
                log.info("Pendulum Node initialised correctly.");
            } catch (Exception e) {
                log.error("Exception during Pendulum node initialisation: ", e);
                throw e;
            }
            if (config.isValidatorEnabled()) {
                milestonePublisher = new MilestonePublisher(config, api, pendulum.candidateTracker);
                milestonePublisher.startScheduledExecutorService();
            }
            if (config.getValidatorManagerEnabled()) {
                validatorPublisher = new ValidatorPublisher(config, api);
                validatorPublisher.startScheduledExecutorService();
            }
            /* todo: disable spammer temporarily
            if (config.getSpamDelay() > 0) {
                spammer = new Spammer(config, api);
                spammer.startScheduledExecutorService();
            }*/
        }

        /**
         * Gracefully shuts down the node by calling <tt>shutdown()</tt> on {@link Pendulum}, {@link XI} and {@link API}.
         * Exceptions during shutdown are caught and logged.
         */
        private static void shutdownHook() {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down Pendulum node, please hold tight...");
                try {
                    if (pendulum.configuration.isValidatorEnabled()) {
                        milestonePublisher.shutdown();
                    }
                    /*if (pendulum.configuration.getValidatorManagerEnabled()) {
                        validatorPublisher.shutdown();
                    }*/
                    XI.shutdown();
                    api.shutDown();
                    pendulum.shutdown();
                } catch (Exception e) {
                    log.error("Exception occurred shutting down Pendulum node: ", e);
                }
            }, "Shutdown Hook"));
        }

        private static PendulumConfig createConfiguration(String[] args) {
            PendulumConfig pendulumConfig = null;
            String message = "Configuration is created using ";
            try {
                boolean testnet = ArrayUtils.contains(args, Config.TESTNET_FLAG);
                File configFile = chooseConfigFile(args);
                if (configFile != null) {
                    pendulumConfig = ConfigFactory.createFromFile(configFile, testnet);
                    message += configFile.getName() + " and command line args";
                }
                else {
                    pendulumConfig = ConfigFactory.createPendulumConfig(testnet);
                    message += "command line args only";
                }
                JCommander jCommander = pendulumConfig.parseConfigFromArgs(args);
                if (pendulumConfig.isHelp()) {
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
            return pendulumConfig;
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
            else if (PendulumConfig.CONFIG_FILE.exists()) {
                return PendulumConfig.CONFIG_FILE;
            }
            return null;
        }
    }
}
