package com.vmware.corfudb.universe.util;

import org.corfudb.common.result.Result;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

public class PropertiesLoader {

    public Result<Properties, IllegalStateException> loadPropertiesFile(String file) {
        Properties props = new Properties();
        URL credentialsUrl = ClassLoader.getSystemResource(file);

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
