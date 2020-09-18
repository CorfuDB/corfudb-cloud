package org.corfudb.universe.node.server.docker;

import com.google.common.collect.ImmutableMap;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.AttachedNetwork;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.IpamConfig;
import com.spotify.docker.client.messages.PortBinding;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.universe.group.cluster.SupportClusterParams;
import org.corfudb.universe.api.node.NodeException;
import org.corfudb.universe.node.server.SupportServer;
import org.corfudb.universe.node.server.SupportServerParams;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.util.DockerManager;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Builder
public class DockerSupportServer<N extends SupportServerParams> implements SupportServer {
    private static final String ALL_NETWORK_INTERFACES = "0.0.0.0";
    private static final String LINUX_OS = "linux";

    @Getter
    @NonNull
    protected final N params;

    @NonNull
    @Getter
    protected final UniverseParams universeParams;

    @NonNull
    private final DockerClient docker;

    @NonNull
    private final DockerManager dockerManager;

    @NonNull
    private final SupportClusterParams clusterParams;

    @NonNull
    @Default
    private final List<Path> openedFiles = new ArrayList<>();

    @NonNull
    private final AtomicReference<String> ipAddress = new AtomicReference<>();

    @Override
    public SupportServer deploy() {
        dockerManager.deployContainer(buildContainerConfig());

        return this;
    }

    private ContainerConfig buildContainerConfig() {
        HostConfig.Builder hostConfigBuilder = HostConfig.builder();
        dockerManager.portBindings(hostConfigBuilder);

        HostConfig.Bind configurationFile = HostConfig.Bind.builder()
                .from(createConfiguration(params.getMetricPorts()))
                .to(params.getPrometheusConfigPath())
                .build();

        HostConfig hostConfig = hostConfigBuilder
                .binds(configurationFile)
                .build();

        ContainerConfig.Builder builder = ContainerConfig.builder()
                .hostConfig(hostConfig)
                .exposedPorts(dockerManager.getPorts().toArray(new String[0]))
                .image(params.getDockerImageNameFullName());

        return builder.build();
    }

    private String createConfiguration(Set<Integer> metricsPorts) {
        try {
            String corfuRuntimeIp = "host.docker.internal";
            if (System.getProperty("os.name").compareToIgnoreCase(LINUX_OS) == 0) {
                corfuRuntimeIp = docker
                        .inspectNetwork(universeParams.getNetworkName())
                        .ipam()
                        .config()
                        .stream()
                        .findFirst()
                        .map(IpamConfig::gateway)
                        .orElseThrow(() -> new NodeException("Ip address not found"));
            }
            Path tempConfiguration = File.createTempFile("prometheus", ".yml").toPath();

            Files.write(
                    tempConfiguration,
                    PrometheusConfig.getConfig(corfuRuntimeIp, metricsPorts).getBytes(StandardCharsets.UTF_8)
            );

            openedFiles.add(tempConfiguration);
            return tempConfiguration.toFile().getAbsolutePath();
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
    public void stop(Duration timeout) {
        dockerManager.stop(timeout);
        openedFiles.forEach(path -> {
            if (!path.toFile().delete()) {
                log.warn("Can't delete a file: {}", path);
            }
        });
    }

    /**
     * Immediately kill the Corfu server.
     *
     * @throws NodeException this exception will be thrown if the server can not be killed.
     */
    @Override
    public void kill() {
        dockerManager.kill();
        openedFiles.forEach(path -> {
            if (!path.toFile().delete()) {
                log.warn("Can't delete a file: {}", path);
            }
        });
    }

    /**
     * Immediately kill and remove the docker container
     *
     * @throws NodeException this exception will be thrown if the server can not be killed.
     */
    @Override
    public void destroy() {
        dockerManager.destroy();
        openedFiles.forEach(path -> {
            if (!path.toFile().delete()) {
                log.warn("Can't delete a file: {}", path);
            }
        });
    }

}
