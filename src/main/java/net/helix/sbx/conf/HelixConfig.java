package net.helix.sbx.conf;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import java.io.File;

/**
 *  A container for all possible configuration parameters of SBX.
 *  In charge of how we parse the configuration from given inputs.
 */
public interface HelixConfig extends APIConfig, NodeConfig, HXIConfig, DbConfig, ConsensusConfig, ZMQConfig, TipSelConfig, PoWConfig, SolidificationConfig, GraphConfig, LoggingConfig {
    File CONFIG_FILE = new File("sbx.ini");
    /**
     * Parses the args to populate the configuration object
     *
     * @param args command line args
     * @return {@link JCommander} instance that was used for parsing. It contains metadata about the parsing.
     * @throws ParameterException if the parsing failed
     */
    JCommander parseConfigFromArgs(String[] args) throws ParameterException;
    boolean isHelp();
}
