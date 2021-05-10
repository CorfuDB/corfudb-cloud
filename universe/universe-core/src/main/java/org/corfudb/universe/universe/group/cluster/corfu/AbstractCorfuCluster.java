package org.corfudb.universe.universe.group.cluster.corfu;

import com.google.common.collect.ImmutableSortedSet;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.BootstrapUtil;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.CorfuRuntime.CorfuRuntimeParameters.CorfuRuntimeParametersBuilder;
import org.corfudb.runtime.view.Layout;
import org.corfudb.universe.api.common.LoggingParams;
import org.corfudb.universe.api.deployment.DeploymentParams;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.cluster.AbstractCluster;
import org.corfudb.universe.api.universe.node.ApplicationServer;
import org.corfudb.universe.universe.node.client.LocalCorfuClient;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams;

import java.time.Duration;

@Slf4j
public abstract class AbstractCorfuCluster<
        D extends DeploymentParams<CorfuServerParams>,
        S extends ApplicationServer<CorfuServerParams>>
        extends AbstractCluster<CorfuServerParams, D, S, CorfuClusterParams<D>>
        implements CorfuCluster<D, S> {

    public AbstractCorfuCluster(
            CorfuClusterParams<D> params, UniverseParams universeParams, LoggingParams loggingParams) {
        super(params, universeParams, loggingParams);
    }

    @Override
    public LocalCorfuClient getLocalCorfuClient() {
        LocalCorfuClient localClient = LocalCorfuClient.builder()
                .serverEndpoints(getClusterLayoutServers())
                .corfuRuntimeParams(CorfuRuntime.CorfuRuntimeParameters.builder())
                .build();

        localClient.deploy();
        return localClient;
    }

    @Override
    public LocalCorfuClient getLocalCorfuClient(
            CorfuRuntimeParametersBuilder runtimeParametersBuilder) {
        LocalCorfuClient localClient = LocalCorfuClient.builder()
                .corfuRuntimeParams(runtimeParametersBuilder)
                .serverEndpoints(getClusterLayoutServers())
                .build();

        localClient.deploy();
        return localClient;
    }

    protected abstract ImmutableSortedSet<String> getClusterLayoutServers();

    @Override
    public void bootstrap() {
        bootstrap(params.getBootstrapParams().isBootstrapEnabled());
    }

    @Override
    public void bootstrap(boolean enabled) {
        if (!enabled) {
            log.warn("Bootstrap disabled for: {}", params.getName());
            return;
        }

        Layout layout = buildLayout();
        log.info("Bootstrap docker corfu cluster. Cluster: {}. layout: {}", params.getName(), layout.asJSONString());

        int retries = params.getBootstrapParams().getBootStrapRetries();
        Duration duration = params.getBootstrapParams().getRetryDuration();
        BootstrapUtil.bootstrap(layout, retries, duration);
    }

    protected abstract Layout buildLayout();
}
