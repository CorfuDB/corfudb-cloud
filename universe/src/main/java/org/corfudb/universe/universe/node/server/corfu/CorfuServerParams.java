package org.corfudb.universe.universe.node.server.corfu;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.corfudb.universe.api.common.IpAddress;
import org.corfudb.universe.api.universe.node.CommonNodeParams;
import org.corfudb.universe.api.universe.node.NodeParams;
import org.corfudb.universe.universe.node.server.corfu.ApplicationServer.Mode;
import org.corfudb.universe.universe.node.server.corfu.ApplicationServer.Persistence;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Builder
@EqualsAndHashCode
@ToString
@Getter
@Slf4j
public class CorfuServerParams implements NodeParams {
    public static final String DOCKER_IMAGE_NAME = "corfudb-universe/corfu-server";

    @NonNull
    @Getter
    private final CommonNodeParams commonParams;

    @NonNull
    private final String streamLogDir = "db";

    @Default
    @NonNull
    private final Mode mode = Mode.CLUSTER;

    @Default
    @NonNull
    private final Persistence persistence = Persistence.DISK;

    /**
     * Corfu server version, for instance: 0.3.0-SNAPSHOT
     */
    @NonNull
    private final String serverVersion;

    @Default
    private final double logSizeQuotaPercentage = 100;

    public Path getStreamLogDir() {
        return Paths.get(FilenameUtils.getName(commonParams.getName()), FilenameUtils.getName(streamLogDir));
    }

    /**
     * Resolves path to the infrastructure jar
     *
     * @return path to infrastructure jar
     */
    public Path getInfrastructureJar() {
        return commonParams.getUniverseDirectory().resolve(
                String.format("infrastructure-%s-shaded.jar", serverVersion)
        );
    }

    /**
     * This method creates a command line string for starting Corfu server
     *
     * @return command line parameters
     */
    @Override
    public Optional<String> getCommandLine(IpAddress networkInterface) {
        String cmdLine = new StringBuilder()
                .append(String.format("mkdir -p %s", getStreamLogDir()))
                .append(" && ")
                .append(buildCorfuCmdLine(networkInterface))
                .toString();

        return Optional.of(cmdLine);
    }

    private String buildCorfuCmdLine(IpAddress networkInterface) {
        StringBuilder cmd = new StringBuilder()
                .append("java -cp *.jar ")
                .append(org.corfudb.infrastructure.CorfuServer.class.getCanonicalName())
                .append(" ");

        cmd.append("-a").append(" ").append(networkInterface);

        switch (persistence) {
            case DISK:
                cmd.append(" -l ").append(getStreamLogDir());
                break;
            case MEMORY:
                cmd.append(" -m");
                break;
            default:
                throw new IllegalStateException("Unknown persistence mode");
        }

        if (mode == Mode.SINGLE) {
            cmd.append(" -s");
        }

        cmd.append(" --log-size-quota-percentage=").append(logSizeQuotaPercentage).append(" ");

        cmd.append(" -d ").append(commonParams.getLogLevel().toString()).append(" ");

        cmd.append(commonParams.getPorts().iterator().next());

        String cmdLineParams = cmd.toString();
        log.trace("Corfu server. Command line parameters: {}", cmdLineParams);

        return cmdLineParams;
    }
}
