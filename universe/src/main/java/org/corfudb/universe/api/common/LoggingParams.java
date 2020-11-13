package org.corfudb.universe.api.common;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.io.FilenameUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Specifies policies to collect logs from docker/vm/processes servers
 */
@Builder
public class LoggingParams {

    @NonNull
    private final String testName;

    @Default
    @Getter
    private final boolean enabled = false;

    public Path getRelativeServerLogDir() {
        return Paths.get(FilenameUtils.getName(testName));
    }
}
