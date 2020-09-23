package org.corfudb.universe.node.server;

import com.google.common.collect.ImmutableSet;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.corfudb.universe.api.node.Node.NodeParams;
import org.corfudb.universe.api.node.Node.NodeType;
import org.corfudb.universe.node.server.CorfuServer.Mode;
import org.corfudb.universe.node.server.CorfuServer.Persistence;
import org.corfudb.universe.util.IpAddress;
import org.slf4j.event.Level;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

@Builder
@EqualsAndHashCode
@ToString
@Getter
@Slf4j
public class CorfuServerParams implements NodeParams {
    public static final String DOCKER_IMAGE_NAME = "corfudb-universe/corfu-server";

    @NonNull
    private final String streamLogDir = "db";

    @Default
    private final int port = ServerUtil.getRandomOpenPort();

    @Default
    @NonNull
    private final Mode mode = Mode.CLUSTER;

    @Default
    @NonNull
    private final Persistence persistence = Persistence.DISK;

    @Default
    @NonNull
    @EqualsAndHashCode.Exclude
    private final Level logLevel = Level.INFO;

    @NonNull
    private final NodeType nodeType = NodeType.CORFU_SERVER;

    /**
     * A name of the Corfu cluster
     */
    @NonNull
    private final String clusterName;

    @Default
    @NonNull
    @EqualsAndHashCode.Exclude
    private final Duration stopTimeout = Duration.ofSeconds(1);

    /**
     * Corfu server version, for instance: 0.3.0-SNAPSHOT
     */
    @NonNull
    private final String serverVersion;

    /**
     * The directory where the universe framework keeps files needed for the framework functionality.
     * By default the directory is equal to the build directory of a build tool
     * ('target' directory in case of maven, 'build' directory in case of gradle)
     */
    @NonNull
    @Default
    private final Path universeDirectory = Paths.get("target");

    @Default
    private final double logSizeQuotaPercentage = 100;

    @Override
    public String getName() {
        return clusterName + "-corfu-node" + getPort();
    }

    public Path getStreamLogDir() {
        return Paths.get(FilenameUtils.getName(getName()), FilenameUtils.getName(streamLogDir));
    }


    @Override
    public Set<Integer> getPorts() {
        return ImmutableSet.of(port);
    }

    /**
     * Resolves path to the infrastructure jar
     *
     * @return path to infrastructure jar
     */
    public Path getInfrastructureJar() {
        return universeDirectory.resolve(
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

        cmd.append(" -d ").append(logLevel.toString()).append(" ");

        cmd.append(port);

        String cmdLineParams = cmd.toString();
        log.trace("Corfu server. Command line parameters: {}", cmdLineParams);

        return cmdLineParams;
    }
}
