package org.corfudb.universe.infrastructure.docker.universe.node.server.prometheus;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.IpamConfig;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams;
import org.corfudb.universe.api.universe.group.GroupParams.GenericGroupParams;
import org.corfudb.universe.api.universe.node.Node;
import org.corfudb.universe.api.universe.node.NodeException;
import org.corfudb.universe.infrastructure.docker.DockerManager;
import org.corfudb.universe.universe.node.server.prometheus.PromServerParams;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Builder
public class DockerPrometheusServer implements Node<PromServerParams, DockerPrometheusServer> {
    private static final String LINUX_OS = "linux";

    @Getter
    @NonNull
    protected final PromServerParams params;

    @NonNull
    @Getter
    protected final DockerContainerParams<PromServerParams> containerParams;

    @NonNull
    private final DockerManager<PromServerParams> dockerManager;

    @NonNull
    private final DockerClient docker;

    @NonNull
    private final GenericGroupParams<PromServerParams, DockerContainerParams<PromServerParams>> clusterParams;

    @NonNull
    @Default
    private final List<Path> openedFiles = new ArrayList<>();

    @Override
    public DockerPrometheusServer deploy() {
        createConfiguration(params.getCommonParams().getPorts(), params.getPrometheusConfigPath());
        dockerManager.deployContainer();
        return this;
    }

    private void createConfiguration(Set<Integer> metricsPorts, Path tempConfiguration) {
        try {
            String corfuRuntimeIp = "host.docker.internal";
            if (System.getProperty("os.name").equalsIgnoreCase(LINUX_OS)) {
                corfuRuntimeIp = docker
                        .inspectNetwork(containerParams.getNetworkName())
                        .ipam()
                        .config()
                        .stream()
                        .findFirst()
                        .map(IpamConfig::gateway)
                        .orElseThrow(() -> new NodeException("Ip address not found"));
            }

            Files.write(
                    tempConfiguration,
                    PrometheusConfig.getConfig(corfuRuntimeIp, metricsPorts).getBytes(StandardCharsets.UTF_8)
            );

            openedFiles.add(tempConfiguration);
        } catch (Exception e) {
            throw new NodeException(e);
        }
    }

    /**
     * This method attempts to gracefully stop the Corfu server. After timeout, it will kill the Corfu server.
     *
     * @param timeout a duration after which the stop will kill the server
     * @throws NodeException this exception will be thrown if the server cannot be stopped.
     */
    @Override
    public DockerPrometheusServer stop(Duration timeout) {
        dockerManager.stop(timeout);
        return this;
    }

    /**
     * Immediately kill the Corfu server.
     *
     * @throws NodeException this exception will be thrown if the server can not be killed.
     */
    @Override
    public DockerPrometheusServer kill() {
        dockerManager.kill();
        return this;
    }

    /**
     * Immediately kill and remove the docker container
     *
     * @throws NodeException this exception will be thrown if the server can not be killed.
     */
    @Override
    public DockerPrometheusServer destroy() {
        dockerManager.destroy();
        openedFiles.forEach(path -> {
            if (!path.toFile().delete()) {
                log.warn("Can't delete a file: {}", path);
            }
        });

        return this;
    }

}
