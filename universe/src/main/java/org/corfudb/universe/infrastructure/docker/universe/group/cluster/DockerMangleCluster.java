package org.corfudb.universe.infrastructure.docker.universe.group.cluster;

import com.spotify.docker.client.DockerClient;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.GroupParams.GenericGroupParams;
import org.corfudb.universe.api.universe.group.cluster.AbstractCluster;
import org.corfudb.universe.infrastructure.docker.DockerManager;
import org.corfudb.universe.infrastructure.docker.universe.node.server.DockerNode;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster;
import org.corfudb.universe.universe.node.server.mangle.MangleServerParams;

/**
 * Provides Docker implementation of {@link CorfuCluster}.
 */
@Slf4j
public class DockerMangleCluster extends AbstractCluster<
        MangleServerParams,
        DockerContainerParams<MangleServerParams>,
        DockerNode<MangleServerParams>,
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
            GenericGroupParams<MangleServerParams, DockerContainerParams<MangleServerParams>> containerParams) {
        super(containerParams, universeParams);
        this.docker = docker;
        init();
    }

    @Override
    public void bootstrap() {
        // NOOP
    }

    @Override
    protected DockerNode<MangleServerParams> buildServer(
            DockerContainerParams<MangleServerParams> deploymentParams) {

        DockerManager<MangleServerParams> dockerManager = DockerManager
                .<MangleServerParams>builder()
                .docker(docker)
                .containerParams(deploymentParams)
                .build();

        return DockerNode.<MangleServerParams>builder()
                .containerParams(deploymentParams)
                .groupParams(params)
                .appParams(deploymentParams.getApplicationParams())
                .docker(docker)
                .dockerManager(dockerManager)
                .build();
    }
}
