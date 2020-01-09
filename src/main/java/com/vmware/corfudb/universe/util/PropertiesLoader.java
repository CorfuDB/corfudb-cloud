package com.vmware.corfudb.universe.util;

import org.corfudb.common.result.Result;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Helper that load configuration parameters from disk
 */
public class PropertiesLoader {

    /**
     * Load configuration file from disk
     *
     * @param file properties file
     * @return result with config parameters
     */
    public Result<Properties, IllegalStateException> loadPropertiesFile(Path file) {
        Properties props = new Properties();
        URL credentialsUrl = ClassLoader.getSystemResource(file.toString());

        return Result.of(() -> {
            try (InputStream is = credentialsUrl.openStream()) {
                props.load(is);
            } catch (IOException e) {
                throw new IllegalStateException("Can't load props", e);
            }

            return props;
        });
    }
}
