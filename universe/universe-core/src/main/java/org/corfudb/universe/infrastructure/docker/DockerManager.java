package org.corfudb.universe.infrastructure.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.corfudb.universe.api.common.IpAddress;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams;
import org.corfudb.universe.api.universe.node.NodeException;
import org.corfudb.universe.api.universe.node.NodeParams;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

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

    private String dockerContainerId;
    private String dockerNetworkId;

    /**
     * Deploy and start docker container, expose ports, connect to a network
     *
     * @return docker container id
     */
    public String deployContainer() {
        P params = containerParams.getApplicationParams();

        try {
            downloadImage();

            CreateContainerCmd container = docker.createContainerCmd(params.getName());
            CreateContainerCmd containerWithParams = buildContainer(container);

            dockerContainerId = containerWithParams.exec().getId();

            addShutdownHook();

            dockerNetworkId = docker.inspectNetworkCmd()
                    .withNetworkId(containerParams.getNetworkName())
                    .exec()
                    .getId();

            docker.disconnectFromNetworkCmd()
                    .withContainerId(dockerContainerId)
                    .withNetworkId("bridge")
                    .withForce(true)
                    .exec();

            docker.connectToNetworkCmd()
                    .withContainerId(dockerContainerId)
                    .withNetworkId(dockerNetworkId)
                    .exec();

            start();

            String ipAddr = docker.inspectContainerCmd(dockerContainerId)
                    .exec()
                    .getNetworkSettings()
                    .getNetworks()
                    .get(containerParams.getNetworkName())
                    .getIpAddress();

            if (StringUtils.isEmpty(ipAddr)) {
                throw new NodeException("Empty Ip address for container: " + params.getName());
            }

            ipAddress.set(IpAddress.builder().ip(ipAddr).build());
        } catch (InterruptedException | DockerException e) {
            throw new NodeException("Can't start a container", e);
        }

        return dockerContainerId;
    }

    private HostConfig buildHostConfig() {
        HostConfig hostConfig = new HostConfig();
        portBindings(hostConfig);
        volumeBindings(hostConfig);

        return hostConfig;
    }

    /**
     * Bind ports
     *
     * @param hostConfig docker host config
     */
    public void portBindings(HostConfig hostConfig) {
        P params = containerParams.getApplicationParams();
        // Bind ports
        List<PortBinding> portBindings = params.getCommonParams()
                .getPorts()
                .stream()
                .map(port -> PortBinding.parse(ALL_NETWORK_INTERFACES + ":" + port))
                .toList();

        hostConfig.withPrivileged(true)
                .withPortBindings(portBindings);
    }

    private void downloadImage() throws DockerException, InterruptedException {
        docker.pullImageCmd(containerParams.getImageFullName())
                .start()
                .awaitCompletion();
        log.info("Image pulled successfully: {}", containerParams.getImageFullName());
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
            var container = docker.inspectContainerCmd(dockerContainerId).exec();
            var state = container.getState();
            if (!state.getRunning() && !state.getPaused()) {
                log.warn("The container `{}` is already stopped", container.getName());
                return;
            }
            docker.stopContainerCmd(dockerContainerId).exec();
        } catch (DockerException e) {
            throw new NodeException("Can't stop Corfu server: " + containerName, e);
        }
    }

    /**
     * Indicates if a corfu node is running
     *
     * @return whether a docker container is running or not
     */
    public boolean isRunning() {
        P params = containerParams.getApplicationParams();
        String containerName = params.getName();

        try {
            return docker.inspectContainerCmd(dockerContainerId).exec().getState().getRunning();
        } catch (DockerException ex) {
            throw new NodeException("Docker client error: " + containerName, ex);
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
            var container = docker.inspectContainerCmd(dockerContainerId).exec();

            if (!container.getState().getRunning() && !container.getState().getPaused()) {
                log.warn("The container `{}` is not running", container.getName());
                return;
            }
            docker.killContainerCmd(dockerContainerId).exec();
        } catch (DockerException ex) {
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
            docker.removeContainerCmd(dockerContainerId).exec();
        } catch (DockerException ex) {
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
            var container = docker.inspectContainerCmd(dockerContainerId).exec();
            if (!container.getState().getRunning()) {
                log.warn("The container `{}` is not running", container.getName());
                return;
            }
            docker.pauseContainerCmd(dockerContainerId).exec();
        } catch (DockerException ex) {
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
            var container = docker.inspectContainerCmd(dockerContainerId).exec();
            if (container.getState().getRunning() || container.getState().getPaused()) {
                log.warn("The container `{}` already running, should stop before start", container.getName());
                return;
            }
            docker.startContainerCmd(dockerContainerId).exec();
        } catch (DockerException ex) {
            throw new NodeException("Can't start container. Inspect command failed " + containerName, ex);
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
            var container = docker.inspectContainerCmd(dockerContainerId).exec();
            if (container.getState().getRestarting()) {
                log.warn("The container `{}` is already restarting", container.getName());
                return;
            }
            docker.restartContainerCmd(dockerContainerId).exec();
        } catch (DockerException ex) {
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
            var container = docker.inspectContainerCmd(dockerContainerId).exec();
            if (!container.getState().getPaused()) {
                log.warn("The container `{}` is not paused, should pause before resuming", container.getName());
                return;
            }
            docker.unpauseContainerCmd(dockerContainerId).exec();
        } catch (DockerException ex) {
            throw new NodeException("Can't resume container " + containerName, ex);
        }
    }

    /**
     * Run `docker exec` on a container
     */
    public String execCommand(String... command) {
        P params = containerParams.getApplicationParams();

        log.info("Executing docker command: {}", String.join(" ", command));

        try {
            var execCreation = docker.execCreateCmd(dockerContainerId)
                    .withCmd(command)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();

            var outputStream = new ByteArrayOutputStream();
            var callback = new ExecStartResultCallback(outputStream, System.err);

            var execResult = docker.execStartCmd(execCreation.getId())
                    .exec(callback)
                    .awaitCompletion();

            return execResult.toString();
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

    public String logs() throws DockerException, InterruptedException {
        P params = containerParams.getApplicationParams();

        var outputStream = new ByteArrayOutputStream();

        docker.logContainerCmd(dockerContainerId)
                .withStdOut(true)
                .withStdErr(true)
                .withTimestamps(true)
                .exec(new LogContainerResultCallback() {
                    @Override
                    public void onNext(Frame frame) {
                        try {
                            outputStream.write(frame.getPayload()); // Write logs to the output stream
                        } catch (Exception e) {
                            throw new NodeException("Error reading logs", e);
                        }
                    }
                })
                .awaitCompletion();
        return outputStream.toString();
    }

    private void volumeBindings(HostConfig hostConfig) {
        containerParams.getVolumes().forEach(vol -> {
            var hostPath = vol.getHostPath().toFile().getAbsolutePath();
            var containerPath = vol.getContainerPath().toFile().getAbsolutePath();

            Volume volume = new Volume(containerPath);
            Bind bind = new Bind(hostPath, volume);
            hostConfig.withBinds(bind);
        });
    }

    private CreateContainerCmd buildContainer(CreateContainerCmd container) {
        P params = containerParams.getApplicationParams();

        IpAddress networkInterface = IpAddress.builder().ip(params.getName()).build();

        // Compose command line for starting Corfu
        Optional<String> cmdLine = params.getCommandLine(networkInterface);

        List<ExposedPort> ports = containerParams.getPorts().stream()
                .map(DockerContainerParams.PortBinding::getContainerPort)
                .map(ExposedPort::tcp)
                .toList();

        container
                .withImage(containerParams.getImageFullName())
                .withHostConfig(buildHostConfig())
                .withHostName(params.getName())
                .withEnv(containerParams.getEnvs().toArray(new String[]{}))
                .withExposedPorts(ports);

        cmdLine.ifPresent(cmd -> container.withCmd(new String[]{"sh", "-c", cmd}));

        return container;
    }
}
