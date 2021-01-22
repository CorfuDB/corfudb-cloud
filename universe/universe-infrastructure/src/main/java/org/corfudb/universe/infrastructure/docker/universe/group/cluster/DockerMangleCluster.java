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
import org.corfudb.universe.infrastructure.docker.universe.node.server.DockerServers.DockerMangleServer;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster;
import org.corfudb.universe.universe.node.server.mangle.MangleServerParams;

/**
 * Provides Docker implementation of {@link CorfuCluster}.
 */
@Slf4j
public class DockerMangleCluster extends AbstractCluster<
        MangleServerParams,
        DockerContainerParams<MangleServerParams>,
        DockerMangleServer,
        GenericGroupParams<MangleServerParams, DockerContainerParams<MangleServerParams>>> {

    @NonNull
    private final DockerClient docker;

    /**
     * Support cluster
     *
     * @param docker          docker client
     * @param containerParams cassandra server params
     * @param universeParams  universe params
     */
    @Builder
    public DockerMangleCluster(
            DockerClient docker, UniverseParams universeParams,
            GenericGroupParams<MangleServerParams, DockerContainerParams<MangleServerParams>> containerParams,
            LoggingParams loggingParams) {
        super(containerParams, universeParams, loggingParams);
        this.docker = docker;
        init();
    }

    @Override
    public void bootstrap() {
        // NOOP
    }

    @Override
    protected DockerMangleServer buildServer(
            DockerContainerParams<MangleServerParams> deploymentParams) {

        DockerManager<MangleServerParams> dockerManager = DockerManager
                .<MangleServerParams>builder()
                .docker(docker)
                .containerParams(deploymentParams)
                .build();

        return DockerMangleServer.builder()
                .containerParams(deploymentParams)
                .groupParams(params)
                .docker(docker)
                .dockerManager(dockerManager)
                .loggingParams(loggingParams)
                .build();
    }
}
