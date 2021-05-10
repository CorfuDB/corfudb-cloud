package org.corfudb.universe.infrastructure.docker.universe.group.cluster;

import com.spotify.docker.client.DockerClient;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.universe.api.common.LoggingParams;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.GroupParams.GenericGroupParams;
import org.corfudb.universe.api.universe.group.cluster.AbstractCluster;
import org.corfudb.universe.infrastructure.docker.DockerManager;
import org.corfudb.universe.infrastructure.docker.universe.node.server.DockerServers.DockerPrometheusServer;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster;
import org.corfudb.universe.universe.node.server.prometheus.PromServerParams;

/**
 * Provides Docker implementation of {@link CorfuCluster}.
 */
@Slf4j
public class DockerPrometheusCluster extends AbstractCluster<
        PromServerParams,
        DockerContainerParams<PromServerParams>,
        DockerPrometheusServer,
        GenericGroupParams<PromServerParams, DockerContainerParams<PromServerParams>>> {

    @NonNull
    private final DockerClient docker;

    /**
     * Support cluster
     *
     * @param docker         docker client
     * @param supportParams  support params
     * @param universeParams universe params
     */
    @Builder
    public DockerPrometheusCluster(
            DockerClient docker, UniverseParams universeParams,
            GenericGroupParams<PromServerParams, DockerContainerParams<PromServerParams>> supportParams,
            LoggingParams loggingParams) {
        super(supportParams, universeParams, loggingParams);
        this.docker = docker;
    }

    @Override
    public void bootstrap() {
        // NOOP
    }

    @Override
    public void bootstrap(boolean force) {
        // NOOP
    }

    @Override
    protected DockerPrometheusServer buildServer(DockerContainerParams<PromServerParams> deploymentParams) {

        DockerManager<PromServerParams> dockerManager = DockerManager
                .<PromServerParams>builder()
                .docker(docker)
                .containerParams(deploymentParams)
                .build();

        return DockerPrometheusServer.builder()
                .containerParams(deploymentParams)
                .groupParams(params)
                .docker(docker)
                .dockerManager(dockerManager)
                .loggingParams(loggingParams)
                .build();
    }
}
