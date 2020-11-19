package org.corfudb.universe.universe.node.server.corfu;

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
    public static final String DOCKER_IMAGE_NAME = "corfudb-universe/generator";

    @NonNull
    @Getter
    private final CommonNodeParams commonParams;

    /**
     * Corfu server version, for instance: 0.3.0-SNAPSHOT
     */
    @NonNull
    private final String serverVersion;

    /**
     * This method creates a command line string for starting Corfu server
     *
     * @return command line parameters
     */
    @Override
    public Optional<String> getCommandLine(IpAddress networkInterface) {
        String cmdLine = new StringBuilder()
                .append("")
                .toString();

        return Optional.of(cmdLine);
    }

    private String buildCorfuCmdLine(IpAddress networkInterface) {
        StringBuilder cmd = new StringBuilder()
                //.append("java -cp *.jar ")
                //.append(org.corfudb.infrastructure.CorfuServer.class.getCanonicalName())
                .append(" ");

        return cmd.toString();
    }
}
