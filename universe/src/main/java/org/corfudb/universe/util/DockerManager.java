package org.corfudb.universe.util;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.ListImagesParam;
import com.spotify.docker.client.DockerClient.LogsParam;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.ExecCreation;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.Image;
import com.spotify.docker.client.messages.PortBinding;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams;
import org.corfudb.universe.api.node.Node.NodeParams;
import org.corfudb.universe.api.node.NodeException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Manages docker containers
 */
@Builder
@Slf4j
public class DockerManager<P extends NodeParams> {
    private static final String ALL_NETWORK_INTERFACES = "0.0.0.0";

    @NonNull
    private final DockerClient docker;

    @NonNull
    protected final DockerContainerParams<P> containerParams;

    @Getter
    private final AtomicReference<IpAddress> ipAddress = new AtomicReference<>();

    /**
     * Deploy and start docker container, expose ports, connect to a network
     *
     * @return docker container id
     */
    public String deployContainer() {
        P params = containerParams.getApplicationParams();

        ContainerConfig containerConfig = buildContainerConfig();

        String id;
        try {
            downloadImage();

            ContainerCreation container = docker.createContainer(containerConfig, params.getName());
            id = container.id();

            addShutdownHook();

            docker.disconnectFromNetwork(id, "bridge");
            docker.connectToNetwork(id, docker.inspectNetwork(containerParams.getNetworkName()).id());

            start();

            String ipAddr = docker.inspectContainer(id)
                    .networkSettings()
                    .networks()
                    .values()
                    .asList()
                    .get(0)
                    .ipAddress();

            if (StringUtils.isEmpty(ipAddr)) {
                throw new NodeException("Empty Ip address for container: " + params.getName());
            }

            ipAddress.set(IpAddress.builder().ip(ipAddr).build());
        } catch (InterruptedException | DockerException e) {
            throw new NodeException("Can't start a container", e);
        }

        return id;
    }

    private HostConfig buildHostConfig() {
        HostConfig.Builder hostConfigBuilder = HostConfig.builder();
        portBindings(hostConfigBuilder);
        volumeBindings(hostConfigBuilder);

        return hostConfigBuilder.build();
    }

    /**
     * Bind ports
     *
     * @param hostConfigBuilder docker host config
     */
    public void portBindings(HostConfig.Builder hostConfigBuilder) {
        P params = containerParams.getApplicationParams();
        // Bind ports
        Map<String, List<PortBinding>> portBindings = new HashMap<>();
        for (Integer port : params.getCommonParams().getPorts()) {
            List<PortBinding> hostPorts = new ArrayList<>();
            hostPorts.add(PortBinding.of(ALL_NETWORK_INTERFACES, port));
            portBindings.put(port.toString(), hostPorts);
        }

        hostConfigBuilder
                .privileged(true)
                .portBindings(portBindings);
    }

    private void downloadImage() throws DockerException, InterruptedException {
        ListImagesParam corfuImageQuery = ListImagesParam
                .byName(containerParams.getImageFullName());

        List<Image> corfuImages = docker.listImages(corfuImageQuery);
        if (corfuImages.isEmpty()) {
            docker.pull(containerParams.getImageFullName());
        }
    }

    /**
     * This method attempts to gracefully stop a container and kill it after timeout.
     *
     * @param timeout a duration after which the stop will kill the container
     * @throws NodeException this exception will be thrown if a container cannot be stopped.
     */
    public void stop(Duration timeout) {
        P params = containerParams.getApplicationParams();

        String containerName = params.getName();
        log.info("Stopping the Corfu server. Docker container: {}", containerName);

        try {
            ContainerInfo container = docker.inspectContainer(containerName);
            if (!container.state().running() && !container.state().paused()) {
                log.warn("The container `{}` is already stopped", container.name());
                return;
            }
            docker.stopContainer(containerName, (int) timeout.getSeconds());
        } catch (DockerException | InterruptedException e) {
            throw new NodeException("Can't stop Corfu server: " + containerName, e);
        }
    }

    /**
     * Immediately kill a docker container.
     *
     * @throws NodeException this exception will be thrown if the container can not be killed.
     */
    public void kill() {
        P params = containerParams.getApplicationParams();

        String containerName = params.getName();
        log.info("Killing docker container: {}", containerName);

        try {
            ContainerInfo container = docker.inspectContainer(containerName);

            if (!container.state().running() && !container.state().paused()) {
                log.warn("The container `{}` is not running", container.name());
                return;
            }
            docker.killContainer(containerName);
        } catch (DockerException | InterruptedException ex) {
            throw new NodeException("Can't kill Corfu server: " + containerName, ex);
        }
    }

    /**
     * Immediately kill and remove a docker container
     *
     * @throws NodeException this exception will be thrown if the container can not be killed.
     */
    public void destroy() {
        P params = containerParams.getApplicationParams();

        String containerName = params.getName();
        log.info("Destroying docker container: {}", containerName);

        try {
            kill();
        } catch (NodeException ex) {
            log.warn("Can't kill container: {}", containerName);
        }

        try {
            docker.removeContainer(containerName);
        } catch (DockerException | InterruptedException ex) {
            throw new NodeException("Can't destroy Corfu server. Already deleted. Container: " + containerName, ex);
        }
    }

    /**
     * Pause a container from the docker network
     *
     * @throws NodeException this exception will be thrown if the container can not be paused
     */
    public void pause() {
        P params = containerParams.getApplicationParams();

        String containerName = params.getName();
        log.info("Pausing container: {}", containerName);

        try {
            ContainerInfo container = docker.inspectContainer(containerName);
            if (!container.state().running()) {
                log.warn("The container `{}` is not running", container.name());
                return;
            }
            docker.pauseContainer(containerName);
        } catch (DockerException | InterruptedException ex) {
            throw new NodeException("Can't pause container " + containerName, ex);
        }
    }

    /**
     * Start a docker container
     *
     * @throws NodeException this exception will be thrown if the container can not be started
     */
    public void start() {
        P params = containerParams.getApplicationParams();

        String containerName = params.getName();
        log.info("Starting docker container: {}", containerName);

        try {
            ContainerInfo container = docker.inspectContainer(containerName);
            if (container.state().running() || container.state().paused()) {
                log.warn("The container `{}` already running, should stop before start", container.name());
                return;
            }
            docker.startContainer(containerName);
        } catch (DockerException | InterruptedException ex) {
            throw new NodeException("Can't start container " + containerName, ex);
        }
    }

    /**
     * Restart a docker container
     *
     * @throws NodeException this exception will be thrown if the container can not be restarted
     */
    public void restart() {
        P params = containerParams.getApplicationParams();

        String containerName = params.getName();
        log.info("Restarting the corfu server: {}", containerName);

        try {
            ContainerInfo container = docker.inspectContainer(containerName);
            if (container.state().restarting()) {
                log.warn("The container `{}` is already restarting", container.name());
                return;
            }
            docker.restartContainer(containerName);
        } catch (DockerException | InterruptedException ex) {
            throw new NodeException("Can't restart container " + containerName, ex);
        }
    }

    /**
     * Resume a docker container
     *
     * @throws NodeException this exception will be thrown if the container can not be resumed
     */
    public void resume() {
        P params = containerParams.getApplicationParams();

        String containerName = params.getName();
        log.info("Resuming docker container: {}", containerName);

        try {
            ContainerInfo container = docker.inspectContainer(containerName);
            if (!container.state().paused()) {
                log.warn("The container `{}` is not paused, should pause before resuming", container.name());
                return;
            }
            docker.unpauseContainer(containerName);
        } catch (DockerException | InterruptedException ex) {
            throw new NodeException("Can't resume container " + containerName, ex);
        }
    }

    /**
     * Run `docker exec` on a container
     */
    public String execCommand(String... command) {
        P params = containerParams.getApplicationParams();

        String containerName = params.getName();
        log.info("Executing docker command: {}", String.join(" ", command));

        try {
            ExecCreation execCreation = docker.execCreate(
                    containerName,
                    command,
                    DockerClient.ExecCreateParam.attachStdout(),
                    DockerClient.ExecCreateParam.attachStderr()
            );

            return docker.execStart(execCreation.id()).readFully();
        } catch (DockerException | InterruptedException e) {
            throw new NodeException("Can't reconnect container to docker network " + params.getName(), e);
        }
    }

    /**
     * Adds a shutdown hook
     */
    public void addShutdownHook() {
        P params = containerParams.getApplicationParams();

        String containerName = params.getName();

        // Just in case a test failed and didn't kill the container
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                destroy();
            } catch (Exception e) {
                log.debug("Corfu server shutdown hook. Can't kill container: {}", containerName);
            }
        }));
    }

    public ContainerInfo inspectContainer(String containerName) throws DockerException, InterruptedException {
        return docker.inspectContainer(containerName);
    }

    public LogStream logs() throws DockerException, InterruptedException {
        P params = containerParams.getApplicationParams();
        return docker.logs(params.getName(), LogsParam.stdout(), LogsParam.stderr());
    }

    private void volumeBindings(HostConfig.Builder hostConfigBuilder) {
        containerParams.getVolumes().forEach(vol -> {
            HostConfig.Bind bind = HostConfig.Bind.builder()
                    .from(vol.getHostPath().toFile().getAbsolutePath())
                    .to(vol.getContainerPath().toFile().getAbsolutePath())
                    .build();
            hostConfigBuilder.binds(bind);
        });
    }

    private ContainerConfig buildContainerConfig() {
        P params = containerParams.getApplicationParams();

        IpAddress networkInterface = IpAddress.builder().ip(params.getName()).build();

        // Compose command line for starting Corfu
        Optional<String> cmdLine = params.getCommandLine(networkInterface);

        Set<String> ports = containerParams.getPorts().stream()
                .map(DockerContainerParams.PortBinding::getContainerPort)
                .map(Object::toString)
                .collect(Collectors.toSet());

        ContainerConfig.Builder containerConfigBuilder = ContainerConfig.builder()
                .hostConfig(buildHostConfig())
                .image(containerParams.getImageFullName())
                .hostname(params.getName())
                .exposedPorts(ports);

        cmdLine.ifPresent(cmd -> containerConfigBuilder.cmd("sh", "-c", cmd));

        return containerConfigBuilder.build();
    }
}
