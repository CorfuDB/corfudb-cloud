package org.corfudb.universe.group.cluster.docker;

import com.spotify.docker.client.DockerClient;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams;
import org.corfudb.universe.api.group.Group.GroupParams.GenericGroupParams;
import org.corfudb.universe.api.group.cluster.AbstractCluster;
import org.corfudb.universe.api.group.cluster.CorfuCluster;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.node.server.SupportServerParams;
import org.corfudb.universe.node.server.docker.DockerSupportServer;
import org.corfudb.universe.util.DockerManager;

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
