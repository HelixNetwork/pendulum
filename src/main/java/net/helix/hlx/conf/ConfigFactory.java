package net.helix.hlx.conf;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.module.SimpleModule;
import net.helix.hlx.conf.deserializers.CustomBoolDeserializer;
import net.helix.hlx.conf.deserializers.CustomStringDeserializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigFactory {
    public static HelixConfig createHelixConfig(boolean isTestnet) {
        HelixConfig helixConfig;
        if (isTestnet) {
            helixConfig = new TestnetConfig();
        }
        else {
            helixConfig = new MainnetConfig();
        }
        return helixConfig;
    }
    /**
     * Creates the {@link HelixConfig} object for {@link TestnetConfig} or {@link MainnetConfig} from config file. Parse
     * the config file for <code>TESTNET=true</code>. If <code>TESTNET=true</code> is found we creates the
     * {@link TestnetConfig} object, else creates the {@link MainnetConfig}.
     *
     * @param configFile A property file with configuration options.
     * @param testnet When true a {@link TestnetConfig} is created.
     * @return the {@link HelixConfig} configuration.
     *
     * @throws IOException When config file could not be found.
     */
    public static HelixConfig createFromFile(File configFile, boolean testnet) throws IOException {
        HelixConfig helixConfig;

        try (FileInputStream confStream = new FileInputStream(configFile)) {
            Properties props = new Properties();
            props.load(confStream);
            boolean isTestnet = testnet || Boolean.parseBoolean(props.getProperty("TESTNET", "false"));
            Class<? extends HelixConfig> helixConfigClass = isTestnet ? TestnetConfig.class : MainnetConfig.class;
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
            objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);
            objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);

            SimpleModule booleanParser = new SimpleModule("BooleanParser");
            booleanParser.addDeserializer(Boolean.TYPE, new CustomBoolDeserializer());
            objectMapper.registerModule(booleanParser);

            SimpleModule stringParser = new SimpleModule("StringParser");
            stringParser.addDeserializer(String.class, new CustomStringDeserializer());
            objectMapper.registerModule(stringParser);

            helixConfig = objectMapper.convertValue(props, helixConfigClass);
        }
        return helixConfig;
    }
}
