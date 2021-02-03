package org.corfudb.universe.universe.node.server.corfu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.universe.api.common.IpAddress;
import org.corfudb.universe.api.universe.node.CommonNodeParams;
import org.corfudb.universe.api.universe.node.NodeParams;

import java.util.Optional;

@Builder
@EqualsAndHashCode
@ToString
@Getter
@Slf4j
public class LongevityAppParams implements NodeParams {
    /**
     * A docker image name of corfu longevity app
     */
    public static final String DOCKER_IMAGE_NAME = "corfudb-universe/generator";

    @NonNull
    @Getter
    private final CommonNodeParams commonParams;

    /**
     * Corfu server version, for instance: 0.3.0-SNAPSHOT
     */
    @NonNull
    private final String serverVersion;

    @NonNull
    private final CorfuServerParams corfuServer;

    @Builder.Default
    private final int timeAmount = 1;

    @NonNull
    @Builder.Default
    private final LongevityAppTimeUnit timeUnit = LongevityAppTimeUnit.MINUTES;

    /**
     * This method creates a command line string for starting Corfu server
     *
     * @return command line parameters
     */
    @Override
    public Optional<String> getCommandLine(IpAddress networkInterface) {
        String cmdLine = buildCmdLine(networkInterface);

        return Optional.of(cmdLine);
    }

    /**
     * usage: longevity
     * -c,--corfu_endpoint <arg> corfu server to connect to
     * -cp,--checkpoint enable checkpoint
     * -t,--time_amount <arg> time amount
     * -u,--time_unit <arg> time unit (s, m, h)
     */
    private String buildCmdLine(IpAddress networkInterface) {
        int corfuServerPort = corfuServer.getCommonParams().getPorts().iterator().next();
        return "java -cp *.jar" +
                " " +
                "org.corfudb.generator.LongevityRun" +
                " " +
                "--corfu_endpoint " + corfuServer.getName() + ":" + corfuServerPort +
                " " +
                "--time_amount " + timeAmount +
                " " +
                "--time_unit " + timeUnit.unit;
    }

    /**
     * LongevityApp time units
     */
    @AllArgsConstructor
    public enum LongevityAppTimeUnit {
        SECONDS("s"), MINUTES("m"), HOURS("h");

        private final String unit;
    }
}
