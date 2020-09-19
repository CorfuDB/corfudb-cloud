package org.corfudb.universe.node.server.docker;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.AttachedNetwork;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.HostConfig;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.corfudb.universe.api.node.Node;
import org.corfudb.universe.api.node.NodeException;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.group.cluster.CorfuClusterParams;
import org.corfudb.universe.logging.LoggingParams;
import org.corfudb.universe.node.server.AbstractCorfuServer;
import org.corfudb.universe.node.server.CorfuServer;
import org.corfudb.universe.node.server.CorfuServerParams;
import org.corfudb.universe.util.DockerManager;
import org.corfudb.universe.util.IpAddress;
import org.corfudb.universe.util.IpTablesUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implements a docker instance representing a {@link CorfuServer}.
 */
@Slf4j
public class DockerCorfuServer extends AbstractCorfuServer<CorfuServerParams, UniverseParams> {

    @NonNull
    private final DockerManager dockerManager;

    @NonNull
    private final CorfuClusterParams<CorfuServerParams> clusterParams;

    private final AtomicBoolean destroyed = new AtomicBoolean();

    /**
     * Docker corfu server class
     *
     * @param docker         docker client
     * @param params         params
     * @param universeParams universe params
     * @param clusterParams  cluster params
     * @param loggingParams  logging params
     * @param dockerManager  docker manager
     */
    @Builder
    public DockerCorfuServer(
            DockerClient docker, CorfuServerParams params, UniverseParams universeParams,
            CorfuClusterParams<CorfuServerParams> clusterParams, LoggingParams loggingParams,
            DockerManager dockerManager) {
        super(params, universeParams, loggingParams);
        this.clusterParams = clusterParams;
        this.dockerManager = dockerManager;
    }

    /**
     * Deploys a Corfu server / docker container
     */
    @Override
    public DockerCorfuServer deploy() {
        log.info("Deploying the Corfu server. Docker container: {}", params.getName());

        dockerManager.deployContainer(buildContainerConfig());

        return this;
    }

    /**
     * This method attempts to gracefully stop the Corfu server. After timeout, it will kill the Corfu server.
     *
     * @param timeout a duration after which the stop will kill the server
     * @throws NodeException this exception will be thrown if the server cannot be stopped.
     */
    @Override
    public void stop(Duration timeout) {
        log.info("Stopping the Corfu server. Docker container: {}", params.getName());
        dockerManager.stop(timeout);
    }

    /**
     * Immediately kill the Corfu server.
     *
     * @throws NodeException this exception will be thrown if the server can not be killed.
     */
    @Override
    public void kill() {
        log.info("Killing the Corfu server. Docker container: {}", params.getName());
        dockerManager.kill();
    }

    /**
     * Immediately kill and remove the docker container
     *
     * @throws NodeException this exception will be thrown if the server can not be killed.
     */
    @Override
    public void destroy() {
        log.info("Destroying the Corfu server. Docker container: {}", params.getName());

        if (destroyed.getAndSet(true)) {
            log.debug("Already destroyed: {}", params.getName());
            return;
        }

        collectLogs();
        dockerManager.destroy();
    }

    /**
     * Symmetrically disconnect the server from the cluster.
     * This partitions the container from all the others.
     * The test runtime can still connect to this server.
     *
     * @throws NodeException this exception will be thrown if the server can not be disconnected
     */
    @Override
    public void disconnect() {
        log.info("Disconnecting the docker server: {} from the cluster ", params.getName());

        clusterParams.getNodesParams()
                .stream()
                .filter(neighbourServer -> !neighbourServer.equals(params))
                .forEach(neighbourServer -> {
                    try {
                        ContainerInfo server = dockerManager.inspectContainer(neighbourServer.getName());
                        String neighbourIp = server.networkSettings()
                                .networks()
                                .values()
                                .stream()
                                .findFirst()
                                .map(AttachedNetwork::ipAddress)
                                .orElseThrow(() -> {
                                    String err = "Empty ip address. Container: " + neighbourServer.getName();
                                    return new NodeException(err);
                                });

                        // iptables -A INPUT -s $neighbourIp -j DROP
                        dockerManager.execCommand("iptables", "-A", "INPUT", "-s", neighbourIp, "-j", "DROP");
                        // iptables -A OUTPUT -d $neighbourIp -j DROP
                        dockerManager.execCommand("iptables", "-A", "OUTPUT", "-d", neighbourIp, "-j", "DROP");
                    } catch (DockerException | InterruptedException ex) {
                        List<String> clusterNodes = clusterParams.getClusterNodes();
                        String err = String.format(
                                "Can't disconnect container: %s from docker network. Corfu cluster: %s",
                                params.getName(), clusterNodes
                        );
                        throw new NodeException(err, ex);
                    }
                });
    }

    /**
     * Symmetrically disconnect a server from a list of other servers,
     * which creates a partial partition.
     *
     * @param servers List of servers to disconnect from.
     * @throws NodeException this exception will be thrown if the server can not be disconnected
     */
    @Override
    public void disconnect(List<CorfuServer> servers) {
        log.info("Disconnecting the docker server: {} from specified servers: {}",
                params.getName(), servers);

        servers.stream()
                .filter(neighbourServer -> !neighbourServer.getParams().equals(params))
                .forEach(neighbourServer -> {
                    try {
                        IpAddress neighbourIp = neighbourServer.getIpAddress();
                        dockerManager.execCommand(IpTablesUtil.dropInput(neighbourIp));
                        dockerManager.execCommand(IpTablesUtil.dropOutput(neighbourIp));
                    } catch (DockerException | InterruptedException ex) {
                        String err = String.format(
                                "Can't disconnect container: %s from server: %s",
                                params.getName(), neighbourServer.getParams().getName()
                        );
                        throw new NodeException(err, ex);
                    }
                });
    }

    /**
     * Pause the container from docker network
     *
     * @throws NodeException this exception will be thrown if the server can not be paused
     */
    @Override
    public void pause() {
        log.info("Pausing the Corfu server: {}", params.getName());
        dockerManager.pause();
    }

    /**
     * Start a {@link Node}
     *
     * @throws NodeException this exception will be thrown if the server can not be started
     */
    @Override
    public void start() {
        log.info("Starting the corfu server: {}", params.getName());
        dockerManager.start();
    }

    /**
     * Restart a {@link Node}
     *
     * @throws NodeException this exception will be thrown if the server can not be restarted
     */
    @Override
    public void restart() {
        log.info("Restarting the corfu server: {}", params.getName());
        dockerManager.restart();
    }

    /**
     * Reconnect a server to the cluster
     *
     * @throws NodeException this exception will be thrown if the node can not be reconnected
     */
    @Override
    public void reconnect() {
        log.info("Reconnecting the docker server: {} to the network.", params.getName());

        try {
            dockerManager.execCommand(IpTablesUtil.cleanInput());
            dockerManager.execCommand(IpTablesUtil.cleanOutput());
        } catch (DockerException | InterruptedException e) {
            throw new NodeException("Can't reconnect container to docker network " + params.getName(), e);
        }
    }

    /**
     * Reconnect a server to a list of servers.
     *
     * @param servers List of servers to reconnect.
     */
    @Override
    public void reconnect(List<CorfuServer> servers) {
        log.info("Reconnecting the docker server: {} to specified servers: {}",
                params.getName(), servers);

        servers.stream()
                .filter(neighbourServer -> !neighbourServer.getParams().equals(params))
                .forEach(neighbourServer -> {
                    try {
                        dockerManager.execCommand(IpTablesUtil.revertDropInput(neighbourServer.getIpAddress()));
                        dockerManager.execCommand(IpTablesUtil.revertDropOutput(neighbourServer.getIpAddress()));
                    } catch (DockerException | InterruptedException ex) {
                        String err = String.format(
                                "Can't reconnect container: %s to server: %s",
                                params.getName(), neighbourServer.getParams().getName()
                        );
                        throw new NodeException(err, ex);
                    }
                });
    }

    @Override
    public String execute(String command) {
        try {
            return dockerManager.execCommand(command);
        } catch (DockerException | InterruptedException e) {
            throw new NodeException("Can't execute command: " + command, e);
        }
    }

    /**
     * Resume a {@link CorfuServer}
     *
     * @throws NodeException this exception will be thrown if the node can not be resumed
     */
    @Override
    public void resume() {
        log.info("Resuming the corfu server: {}", params.getName());
        dockerManager.resume();
    }

    @Override
    public IpAddress getIpAddress() {
        return dockerManager.getIpAddress().get();
    }

    private ContainerConfig buildContainerConfig() {
        HostConfig.Builder hostConfigBuilder = setupResourceLimits();
        dockerManager.portBindings(hostConfigBuilder);

        // Compose command line for starting Corfu
        String cmdLine = params.getCommandLineParams(getNetworkInterface());

        return ContainerConfig.builder()
                .hostConfig(hostConfigBuilder.build())
                .image(params.getDockerImageNameFullName())
                .hostname(params.getName())
                .exposedPorts(dockerManager.getPorts().toArray(new String[0]))
                .cmd("sh", "-c", cmdLine)
                .build();
    }

    private HostConfig.Builder setupResourceLimits() {
        HostConfig.Builder hostConfigBuilder = HostConfig.builder();
        params.getContainerResources()
                .ifPresent(limits -> hostConfigBuilder.memory(limits.getMemory()));
        return hostConfigBuilder;
    }

    /**
     * Collect logs from container and write to the log directory
     */
    @Override
    public void collectLogs() {
        if (!loggingParams.isEnabled()) {
            log.debug("Logging is disabled");
            return;
        }

        Path corfuLogDir = params
                .getUniverseDirectory()
                .resolve("logs")
                .resolve(loggingParams.getRelativeServerLogDir());

        File logDirFile = corfuLogDir.toFile();
        if (!logDirFile.exists() && logDirFile.mkdirs()) {
            log.info("Created new corfu log directory at {}.", corfuLogDir);
        }

        log.debug("Collect logs for: {}", params.getName());

        try (LogStream stream = dockerManager.logs()) {
            String logs = stream.readFully();

            if (StringUtils.isEmpty(logs)) {
                log.warn("Empty logs from container: {}", params.getName());
            }

            Files.write(
                    corfuLogDir.resolve(params.getName() + ".log"),
                    logs.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.SYNC
            );
        } catch (Exception e) {
            log.error("Can't collect logs from container: {}", params.getName(), e);
        }
    }

    @Override
    public IpAddress getNetworkInterface() {
        return IpAddress.builder().ip(params.getName()).build();
    }
}
