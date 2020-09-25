package org.corfudb.universe.infrastructure.docker.universe.group.cluster;

import com.spotify.docker.client.DockerClient;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams;
import org.corfudb.universe.api.universe.group.Group.GroupParams.GenericGroupParams;
import org.corfudb.universe.api.universe.group.cluster.AbstractCluster;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.universe.node.server.support.SupportServerParams;
import org.corfudb.universe.infrastructure.docker.universe.node.server.DockerSupportServer;
import org.corfudb.universe.infrastructure.docker.DockerManager;

/**
 * Provides Docker implementation of {@link CorfuCluster}.
 */
@Slf4j
public class DockerSupportCluster extends AbstractCluster<
        SupportServerParams,
        DockerContainerParams<SupportServerParams>,
        DockerSupportServer, GenericGroupParams<SupportServerParams, DockerContainerParams<SupportServerParams>>> {

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
    public DockerSupportCluster(
            DockerClient docker, UniverseParams universeParams,
            GenericGroupParams<SupportServerParams, DockerContainerParams<SupportServerParams>> supportParams) {
        super(supportParams, universeParams);
        this.docker = docker;
    }

    @Override
    public void bootstrap() {
        // NOOP
    }

    @Override
    protected DockerSupportServer buildServer(DockerContainerParams<SupportServerParams> deploymentParams) {

        DockerManager<SupportServerParams> dockerManager = DockerManager
                .<SupportServerParams>builder()
                .docker(docker)
                .containerParams(deploymentParams)
                .build();

        return DockerSupportServer.builder()
                .containerParams(deploymentParams)
                .clusterParams(params)
                .params(deploymentParams.getApplicationParams())
                .docker(docker)
                .dockerManager(dockerManager)
                .build();
    }
}
