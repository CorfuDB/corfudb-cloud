package org.corfudb.universe.node.server.docker;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.IpamConfig;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams;
import org.corfudb.universe.api.group.Group.GroupParams.GenericGroupParams;
import org.corfudb.universe.api.node.Node;
import org.corfudb.universe.api.node.NodeException;
import org.corfudb.universe.node.server.SupportServerParams;
import org.corfudb.universe.util.DockerManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Builder
public class DockerSupportServer implements Node<SupportServerParams, DockerSupportServer> {
    private static final String LINUX_OS = "linux";

    @Getter
    @NonNull
    protected final SupportServerParams params;

    @NonNull
    @Getter
    protected final DockerContainerParams<SupportServerParams> containerParams;

    @NonNull
    private final DockerManager<SupportServerParams> dockerManager;

    @NonNull
    private final DockerClient docker;

    @NonNull
    private final GenericGroupParams<SupportServerParams, DockerContainerParams<SupportServerParams>> clusterParams;

    @NonNull
    @Default
    private final List<Path> openedFiles = new ArrayList<>();

    @Override
    public DockerSupportServer deploy() {
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
    public DockerSupportServer stop(Duration timeout) {
        dockerManager.stop(timeout);
        return this;
    }

    /**
     * Immediately kill the Corfu server.
     *
     * @throws NodeException this exception will be thrown if the server can not be killed.
     */
    @Override
    public DockerSupportServer kill() {
        dockerManager.kill();
        return this;
    }

    /**
     * Immediately kill and remove the docker container
     *
     * @throws NodeException this exception will be thrown if the server can not be killed.
     */
    @Override
    public DockerSupportServer destroy() {
        dockerManager.destroy();
        openedFiles.forEach(path -> {
            if (!path.toFile().delete()) {
                log.warn("Can't delete a file: {}", path);
            }
        });

        return this;
    }

}
