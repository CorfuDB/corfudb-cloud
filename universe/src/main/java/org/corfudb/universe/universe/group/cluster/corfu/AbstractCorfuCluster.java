package org.corfudb.universe.universe.group.cluster.corfu;

import com.google.common.collect.ImmutableSortedSet;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.CorfuRuntime.CorfuRuntimeParameters.CorfuRuntimeParametersBuilder;
import org.corfudb.universe.api.common.LoggingParams;
import org.corfudb.universe.api.deployment.DeploymentParams;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.cluster.AbstractCluster;
import org.corfudb.universe.universe.node.client.LocalCorfuClient;
import org.corfudb.universe.universe.node.server.corfu.CorfuServer;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams;

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
