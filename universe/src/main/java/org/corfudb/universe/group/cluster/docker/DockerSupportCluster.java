package org.corfudb.universe.group.cluster.docker;

import com.spotify.docker.client.DockerClient;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.universe.api.deployment.DockerContainerParams;
import org.corfudb.universe.api.deployment.DockerContainerParams.PortBinding;
import org.corfudb.universe.api.deployment.DockerContainerParams.VolumeBinding;
import org.corfudb.universe.api.group.cluster.CorfuCluster;
import org.corfudb.universe.api.node.Node;
import org.corfudb.universe.api.node.NodeException;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.group.cluster.AbstractSupportCluster;
import org.corfudb.universe.group.cluster.SupportClusterParams;
import org.corfudb.universe.node.server.SupportServerParams;
import org.corfudb.universe.node.server.docker.DockerSupportServer;
import org.corfudb.universe.util.DockerManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

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
        List<PortBinding> ports = nodeParams.getPorts().stream()
                .map(PortBinding::new)
                .collect(Collectors.toList());

        VolumeBinding volume;
        try {
            volume = VolumeBinding.builder()
                    .containerPath(nodeParams.getPrometheusConfigPath())
                    .hostPath(File.createTempFile("prometheus", ".yml").toPath())
                    .build();
        } catch (IOException e) {
            throw new NodeException("Can't deploy docker support server. Can't create a tmp directory");
        }

        DockerContainerParams containerParams = DockerContainerParams.builder()
                .image("prom/prometheus")
                .networkName(universeParams.getNetworkName())
                .ports(ports)
                .volume(volume)
                .build();

        DockerManager dockerManager = DockerManager.builder()
                .docker(docker)
                .params(nodeParams)
                .containerParams(containerParams)
                .build();

        return DockerSupportServer.builder()
                .containerParams(containerParams)
                .clusterParams(params)
                .params(nodeParams)
                .docker(docker)
                .dockerManager(dockerManager)
                .build();
    }
}
