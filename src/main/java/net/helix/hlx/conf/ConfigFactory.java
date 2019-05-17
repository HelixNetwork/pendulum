package net.helix.hlx.conf;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
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
    public static HelixConfig createFromFile(File configFile, boolean testnet) throws IOException,
            IllegalArgumentException {
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
            objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
            helixConfig = objectMapper.convertValue(props, helixConfigClass);
        }
        return helixConfig;
    }
}
