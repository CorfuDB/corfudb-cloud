package org.corfudb.universe.infrastructure.docker.universe.node.server;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LogStream;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.corfudb.universe.api.common.IpAddress;
import org.corfudb.universe.api.common.LoggingParams;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams;
import org.corfudb.universe.api.universe.group.GroupParams;
import org.corfudb.universe.api.universe.node.ApplicationServer;
import org.corfudb.universe.api.universe.node.Node;
import org.corfudb.universe.api.universe.node.NodeException;
import org.corfudb.universe.api.universe.node.NodeParams;
import org.corfudb.universe.infrastructure.docker.DockerManager;
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
 * A unit of a node deployment to docker. Deploys docker containers
 *
 * @param <P> node params
 */
@SuperBuilder
@Slf4j
public class DockerNode<P extends NodeParams> implements ApplicationServer<P> {

    @NonNull
    @Getter
    private final DockerContainerParams<P> containerParams;

    @NonNull
    private final DockerManager<P> dockerManager;

    @Getter
    @NonNull
    private final DockerClient docker;

    @NonNull
    private final GroupParams<P, DockerContainerParams<P>> groupParams;

    @NonNull
    protected final LoggingParams loggingParams;

    private final AtomicBoolean destroyed = new AtomicBoolean();

    /**
     * Deploys a docker container
     */
    @Override
    public void deploy() {
        dockerManager.deployContainer();
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
    }

    /**
     * Immediately kill the Corfu server.
     *
     * @throws NodeException this exception will be thrown if the server can not be killed.
     */
    @Override
    public void kill() {
        dockerManager.kill();
    }

    /**
     * Immediately kill and remove the docker container
     *
     * @throws NodeException this exception will be thrown if the server can not be killed.
     */
    @Override
    public void destroy() {
        log.info("Destroying the Corfu server. Docker container: {}", appParams().getName());

        if (destroyed.getAndSet(true)) {
            log.debug("Already destroyed: {}", appParams().getName());
            return;
        }

        collectLogs();
        dockerManager.destroy();
    }

    /**
     * Pause the container from docker network
     *
     * @throws NodeException this exception will be thrown if the server can not be paused
     */
    @Override
    public void pause() {
        log.info("Pausing the Corfu server: {}", appParams().getName());
        dockerManager.pause();
    }

    /**
     * Start a {@link Node}
     *
     * @throws NodeException this exception will be thrown if the server can not be started
     */
    @Override
    public void start() {
        log.info("Starting the corfu server: {}", appParams().getName());
        dockerManager.start();
    }

    /**
     * Restart a {@link Node}
     *
     * @throws NodeException this exception will be thrown if the server can not be restarted
     */
    @Override
    public void restart() {
        log.info("Restarting the corfu server: {}", appParams().getName());
        dockerManager.restart();
    }

    /**
     * Symmetrically disconnect a server from a list of other servers,
     * which creates a partial partition.
     *
     * @param servers List of servers to disconnect from.
     * @throws NodeException this exception will be thrown if the server can not be disconnected
     */
    @Override
    public void disconnect(List<ApplicationServer<P>> servers) {
        log.info("Disconnecting the docker server: {} from specified servers: {}",
                appParams().getName(), servers
        );

        servers.stream()
                .filter(neighbourServer -> !neighbourServer.getParams().equals(appParams()))
                .forEach(neighbourServer -> {
                    IpAddress neighbourIp = neighbourServer.getIpAddress();
                    dockerManager.execCommand(IpTablesUtil.dropInput(neighbourIp));
                    dockerManager.execCommand(IpTablesUtil.dropOutput(neighbourIp));
                });
    }

    /**
     * Reconnect a server to a list of servers.
     *
     * @param servers List of servers to reconnect.
     */
    @Override
    public void reconnect(List<ApplicationServer<P>> servers) {
        log.info("Reconnecting the docker server: {} to specified servers: {}",
                appParams().getName(), servers
        );

        servers.stream()
                .filter(neighbourServer -> !neighbourServer.getParams().equals(appParams()))
                .forEach(neighbourServer -> {
                    dockerManager.execCommand(IpTablesUtil.revertDropInput(neighbourServer.getIpAddress()));
                    dockerManager.execCommand(IpTablesUtil.revertDropOutput(neighbourServer.getIpAddress()));
                });
    }

    /**
     * Reconnect a server to the cluster
     *
     * @throws NodeException this exception will be thrown if the node can not be reconnected
     */
    @Override
    public void reconnect() {
        log.info("Reconnecting the docker server: {} to the network.", appParams().getName());

        dockerManager.execCommand(IpTablesUtil.cleanInput());
        dockerManager.execCommand(IpTablesUtil.cleanOutput());
    }

    @Override
    public P getParams() {
        return appParams();
    }

    @Override
    public String execute(String command) {
        return dockerManager.execCommand(command);
    }

    /**
     * Resume a {@link ApplicationServer}
     *
     * @throws NodeException this exception will be thrown if the node can not be resumed
     */
    @Override
    public void resume() {
        log.info("Resuming the corfu server: {}", appParams().getName());
        dockerManager.resume();
    }

    /**
     * Indicates if a docker container is running
     * @return is a node is running
     */
    @Override
    public boolean isRunning() {
        return dockerManager.isRunning();
    }

    @Override
    public IpAddress getIpAddress() {
        return dockerManager.getIpAddress().get();
    }

    @Override
    public IpAddress getNetworkInterface() {
        return IpAddress.builder().ip(appParams().getName()).build();
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

        Path logDir = getLogDir();

        log.debug("Collect logs for: {}", appParams().getName());

        try {
            LogStream stream = dockerManager.logs();
            String logs = stream.readFully();

            if (StringUtils.isEmpty(logs)) {
                log.warn("Empty logs from container: {}", appParams().getName());
            }

            Files.write(
                    logDir.resolve(appParams().getName() + ".log"),
                    logs.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.SYNC
            );
            stream.close();
        } catch (Exception e) {
            log.error("Can't collect logs from container: {}", appParams().getName(), e);
        }
    }

    @Override
    public Path getLogDir() {
        Path logDir = appParams().getCommonParams()
                .getUniverseDirectory()
                .resolve("logs")
                .resolve(loggingParams.getRelativeServerLogDir());

        File logDirFile = logDir.toFile();
        if (!logDirFile.exists() && logDirFile.mkdirs()) {
            log.info("Created new corfu log directory at {}.", logDir);
        }
        return logDir;
    }

    private P appParams() {
        return containerParams.getApplicationParams();
    }
}
