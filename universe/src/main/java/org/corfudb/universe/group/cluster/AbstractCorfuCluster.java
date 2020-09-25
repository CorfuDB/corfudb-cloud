package org.corfudb.universe.group.cluster;

import com.google.common.collect.ImmutableSortedSet;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.CorfuRuntime.CorfuRuntimeParameters.CorfuRuntimeParametersBuilder;
import org.corfudb.universe.api.deployment.DeploymentParams;
import org.corfudb.universe.api.group.cluster.AbstractCluster;
import org.corfudb.universe.api.group.cluster.CorfuCluster;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.logging.LoggingParams;
import org.corfudb.universe.node.client.LocalCorfuClient;
import org.corfudb.universe.node.server.CorfuServer;
import org.corfudb.universe.node.server.CorfuServerParams;

@Slf4j
public abstract class AbstractCorfuCluster<D extends DeploymentParams<CorfuServerParams>>
        extends AbstractCluster<CorfuServerParams, D, CorfuServer, CorfuClusterParams<D>>
        implements CorfuCluster<D> {

    @NonNull
    protected final LoggingParams loggingParams;

    public AbstractCorfuCluster(
            CorfuClusterParams<D> params, UniverseParams universeParams, LoggingParams loggingParams) {
        super(params, universeParams);
        this.loggingParams = loggingParams;
    }

    protected void init() {
        params.getNodesParams().forEach(serverParams -> {
            CorfuServer server = buildServer(serverParams);
            nodes.put(server.getEndpoint(), server);
        });
    }

    @Override
    public LocalCorfuClient getLocalCorfuClient() {
        return LocalCorfuClient.builder()
                .serverEndpoints(getClusterLayoutServers())
                .corfuRuntimeParams(CorfuRuntime.CorfuRuntimeParameters.builder())
                .build()
                .deploy();
    }

    @Override
    public LocalCorfuClient getLocalCorfuClient(
            CorfuRuntimeParametersBuilder runtimeParametersBuilder) {
        return LocalCorfuClient.builder()
                .corfuRuntimeParams(runtimeParametersBuilder)
                .serverEndpoints(getClusterLayoutServers())
                .build()
                .deploy();
    }

    protected abstract ImmutableSortedSet<String> getClusterLayoutServers();


}
