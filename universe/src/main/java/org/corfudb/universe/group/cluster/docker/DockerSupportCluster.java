package org.corfudb.universe.group.cluster.docker;

import com.spotify.docker.client.DockerClient;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.universe.group.cluster.AbstractSupportCluster;
import org.corfudb.universe.api.group.cluster.CorfuCluster;
import org.corfudb.universe.group.cluster.SupportClusterParams;
import org.corfudb.universe.api.node.Node;
import org.corfudb.universe.node.server.SupportServerParams;
import org.corfudb.universe.node.server.docker.DockerSupportServer;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.util.DockerManager;

/**
 * Provides Docker implementation of {@link CorfuCluster}.
 */
@Slf4j
public class DockerSupportCluster extends AbstractSupportCluster {
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
    public DockerSupportCluster(DockerClient docker, SupportClusterParams supportParams,
                                UniverseParams universeParams) {
        super(universeParams, supportParams);
        this.docker = docker;
    }

    @Override
    public void bootstrap() {
        // NOOP
    }

    @Override
    protected Node buildServer(SupportServerParams nodeParams) {
        DockerManager dockerManager = DockerManager.builder()
                .docker(docker)
                .params(nodeParams)
                .universeParams(universeParams)
                .build();

        return DockerSupportServer.builder()
                .universeParams(universeParams)
                .clusterParams(params)
                .params(nodeParams)
                .docker(docker)
                .dockerManager(dockerManager)
                .build();
    }
}
