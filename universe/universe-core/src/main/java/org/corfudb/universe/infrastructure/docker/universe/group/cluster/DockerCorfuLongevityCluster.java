package org.corfudb.universe.infrastructure.docker.universe.group.cluster;

import com.github.dockerjava.api.DockerClient;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.universe.api.common.LoggingParams;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.GroupParams.GenericGroupParams;
import org.corfudb.universe.api.universe.group.cluster.AbstractCluster;
import org.corfudb.universe.infrastructure.docker.DockerManager;
import org.corfudb.universe.infrastructure.docker.universe.node.server.DockerCorfuServer;
import org.corfudb.universe.infrastructure.docker.universe.node.server.DockerCorfuServer.DockerCorfuLongevityApp;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster;
import org.corfudb.universe.universe.node.server.corfu.LongevityAppParams;

/**
 * Provides Docker implementation of {@link CorfuCluster}.
 */
@Slf4j
public class DockerCorfuLongevityCluster extends AbstractCluster<
        LongevityAppParams,
        DockerContainerParams<LongevityAppParams>,
        DockerCorfuLongevityApp,
        GenericGroupParams<LongevityAppParams, DockerContainerParams<LongevityAppParams>>> {

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
    public DockerCorfuLongevityCluster(
            DockerClient docker, UniverseParams universeParams,
            GenericGroupParams<LongevityAppParams, DockerContainerParams<LongevityAppParams>> containerParams,
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
    public void bootstrap(boolean force) {
        // NOOP
    }

    @Override
    protected DockerCorfuLongevityApp buildServer(DockerContainerParams<LongevityAppParams> deploymentParams) {

        DockerManager<LongevityAppParams> dockerManager = DockerManager
                .<LongevityAppParams>builder()
                .docker(docker)
                .containerParams(deploymentParams)
                .build();

        return DockerCorfuServer.DockerCorfuLongevityApp.builder()
                .containerParams(deploymentParams)
                .groupParams(params)
                .docker(docker)
                .dockerManager(dockerManager)
                .loggingParams(loggingParams)
                .build();
    }
}
