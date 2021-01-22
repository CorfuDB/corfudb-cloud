package org.corfudb.universe.infrastructure.docker.universe.group.cluster;

import com.google.common.collect.ImmutableSortedSet;
import com.spotify.docker.client.DockerClient;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.BootstrapUtil;
import org.corfudb.runtime.view.Layout;
import org.corfudb.runtime.view.Layout.LayoutSegment;
import org.corfudb.universe.api.common.LoggingParams;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.node.ApplicationServer;
import org.corfudb.universe.infrastructure.docker.DockerManager;
import org.corfudb.universe.infrastructure.docker.universe.node.server.DockerCorfuServer;
import org.corfudb.universe.universe.group.cluster.corfu.AbstractCorfuCluster;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuClusterParams;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Provides Docker implementation of {@link CorfuCluster}.
 */
@Slf4j
public class DockerCorfuCluster extends AbstractCorfuCluster<
        DockerContainerParams<CorfuServerParams>, DockerCorfuServer> {

    @NonNull
    private final DockerClient docker;

    /**
     * Corfu docker cluster
     *
     * @param docker         docker client
     * @param params         params
     * @param universeParams universe params
     * @param loggingParams  logging params
     */
    @Builder
    public DockerCorfuCluster(DockerClient docker, CorfuClusterParams<DockerContainerParams<CorfuServerParams>> params,
                              UniverseParams universeParams, LoggingParams loggingParams) {
        super(params, universeParams, loggingParams);
        this.docker = docker;

        init();
    }

    @Override
    protected DockerCorfuServer buildServer(DockerContainerParams<CorfuServerParams> deploymentParams) {
        DockerManager<CorfuServerParams> dockerManager = DockerManager
                .<CorfuServerParams>builder()
                .docker(docker)
                .containerParams(deploymentParams)
                .build();

        return DockerCorfuServer.builder()
                .containerParams(deploymentParams)
                .loggingParams(loggingParams)
                .dockerManager(dockerManager)
                .groupParams(params)
                .docker(docker)
                .build();
    }

    @Override
    protected ImmutableSortedSet<String> getClusterLayoutServers() {
        List<String> servers = nodes()
                .values()
                .stream()
                .map(ApplicationServer::getEndpoint)
                .collect(Collectors.toList());

        return ImmutableSortedSet.copyOf(servers);
    }

    @Override
    public void bootstrap() {
        Layout layout = getLayout();
        log.info("Bootstrap docker corfu cluster. Cluster: {}. layout: {}", params.getName(), layout.asJSONString());

        BootstrapUtil.bootstrap(layout, params.getBootStrapRetries(), params.getRetryDuration());
    }

    private Layout getLayout() {
        long epoch = 0;
        UUID clusterId = UUID.randomUUID();
        List<String> servers = getClusterLayoutServers().asList();

        LayoutSegment segment = new LayoutSegment(
                Layout.ReplicationMode.CHAIN_REPLICATION,
                0L,
                -1L,
                Collections.singletonList(new Layout.LayoutStripe(servers))
        );
        return new Layout(servers, servers, Collections.singletonList(segment), epoch, clusterId);
    }
}
