package org.corfudb.universe.group.cluster.process;

import com.google.common.collect.ImmutableSortedSet;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.common.util.ClassUtils;
import org.corfudb.runtime.BootstrapUtil;
import org.corfudb.runtime.view.Layout;
import org.corfudb.runtime.view.Layout.LayoutSegment;
import org.corfudb.universe.api.deployment.DeploymentParams;
import org.corfudb.universe.api.deployment.DeploymentParams.EmptyDeploymentParams;
import org.corfudb.universe.api.group.cluster.CorfuCluster;
import org.corfudb.universe.api.node.Node;
import org.corfudb.universe.api.node.Node.NodeParams;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.group.cluster.AbstractCorfuCluster;
import org.corfudb.universe.group.cluster.CorfuClusterParams;
import org.corfudb.universe.logging.LoggingParams;
import org.corfudb.universe.node.server.CorfuServer;
import org.corfudb.universe.node.server.CorfuServerParams;
import org.corfudb.universe.node.server.process.ProcessCorfuServer;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Provides `Process` implementation of a {@link CorfuCluster}.
 */
@Slf4j
public class ProcessCorfuCluster extends AbstractCorfuCluster<EmptyDeploymentParams<CorfuServerParams>> {

    @Builder
    protected ProcessCorfuCluster(
            CorfuClusterParams<EmptyDeploymentParams<CorfuServerParams>> corfuClusterParams,
            UniverseParams universeParams, LoggingParams loggingParams) {
        super(corfuClusterParams, universeParams, loggingParams);
        init();
    }


    /**
     * Deploys a Corfu server node according to the provided parameter.
     *
     * @return an instance of {@link Node}
     */
    @Override
    protected CorfuServer buildServer(EmptyDeploymentParams<CorfuServerParams> deploymentParams) {
        log.info("Deploy corfu server: {}", deploymentParams);

        return ProcessCorfuServer.builder()
                .universeParams(universeParams)
                .params(deploymentParams.getApplicationParams())
                .build();
    }

    @Override
    protected ImmutableSortedSet<String> getClusterLayoutServers() {
        return ImmutableSortedSet.copyOf(buildLayout().getLayoutServers());
    }

    @Override
    public void bootstrap() {
        Layout layout = buildLayout();
        log.info("Bootstrap corfu cluster. Cluster: {}. layout: {}", params.getName(), layout.asJSONString());

        BootstrapUtil.bootstrap(layout, params.getBootStrapRetries(), params.getRetryDuration());
    }

    /**
     * Build a layout from params
     *
     * @return an instance of {@link Layout} that is built from the existing parameters.
     */
    private Layout buildLayout() {
        long epoch = 0;
        UUID clusterId = UUID.randomUUID();

        List<String> servers = params.getNodesParams()
                .stream()
                .map(params -> "127.0.0.1:" + params.getApplicationParams().getCommonParams().getPorts().iterator().next())
                .collect(Collectors.toList());

        LayoutSegment segment = new LayoutSegment(
                Layout.ReplicationMode.CHAIN_REPLICATION,
                0L,
                -1L,
                Collections.singletonList(new Layout.LayoutStripe(servers))
        );
        return new Layout(servers, servers, Collections.singletonList(segment), epoch, clusterId);
    }
}
