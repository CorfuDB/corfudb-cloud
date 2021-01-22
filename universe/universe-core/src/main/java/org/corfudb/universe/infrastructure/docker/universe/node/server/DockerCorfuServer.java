package org.corfudb.universe.infrastructure.docker.universe.node.server;

import com.google.common.collect.ImmutableSortedSet;
import lombok.experimental.SuperBuilder;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.universe.api.universe.node.ApplicationServer;
import org.corfudb.universe.api.universe.node.ApplicationServers.CorfuApplicationServer;
import org.corfudb.universe.universe.node.client.LocalCorfuClient;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams;
import org.corfudb.universe.universe.node.server.corfu.LongevityAppParams;

/**
 * Implements a docker instance representing a {@link ApplicationServer}.
 */
@SuperBuilder
public class DockerCorfuServer extends DockerNode<CorfuServerParams> implements CorfuApplicationServer {

    @Override
    public LocalCorfuClient getLocalCorfuClient() {
        LocalCorfuClient corfuClient = LocalCorfuClient.builder()
                .serverEndpoints(ImmutableSortedSet.of(getEndpoint()))
                .corfuRuntimeParams(CorfuRuntime.CorfuRuntimeParameters.builder())
                .build();

        corfuClient.deploy();
        return corfuClient;
    }

    @SuperBuilder
    public static class DockerCorfuLongevityApp extends DockerNode<LongevityAppParams> {

    }
}
