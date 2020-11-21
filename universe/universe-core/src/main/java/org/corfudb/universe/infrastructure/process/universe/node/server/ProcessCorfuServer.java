package org.corfudb.universe.infrastructure.process.universe.node.server;

import com.google.common.collect.ImmutableSortedSet;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.universe.api.common.IpAddress;
import org.corfudb.universe.api.common.LoggingParams;
import org.corfudb.universe.api.universe.node.ApplicationServer;
import org.corfudb.universe.api.universe.node.ApplicationServers.CorfuApplicationServer;
import org.corfudb.universe.api.universe.node.NodeException;
import org.corfudb.universe.universe.node.client.LocalCorfuClient;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Implements a {@link ApplicationServer} instance that is running on a host machine.
 */
@Slf4j
public class ProcessCorfuServer implements CorfuApplicationServer {
    private static final IpAddress LOCALHOST = IpAddress.builder().ip("127.0.0.1").build();

    @NonNull
    @Getter
    private final IpAddress ipAddress = LOCALHOST;

    @NonNull
    private final CorfuProcessManager processManager;

    @NonNull
    private final CorfuServerPath serverPath;

    @NonNull
    private final LoggingParams loggingParams;

    private final ExecutionHelper commandHelper = ExecutionHelper.getInstance();

    /**
     * Manages corfu server (process mode)
     *
     * @param params        params
     * @param loggingParams logging params
     */
    @Builder
    public ProcessCorfuServer(CorfuServerParams params, LoggingParams loggingParams) {
        this.serverPath = new CorfuServerPath(params);
        this.loggingParams = loggingParams;
        this.processManager = new CorfuProcessManager(serverPath, params);
    }

    /**
     * Deploys a Corfu server on the target directory as specified, including the following steps:
     * a) Copy the corfu jar file under the working directory to the target directory
     * b) Run that jar file using java on the local machine
     */
    @Override
    public void deploy() {
        executeCommand(Optional.empty(), processManager.createServerDirCommand());
        executeCommand(Optional.empty(), processManager.createStreamLogDirCommand());

        commandHelper.copyFile(
                getParams().getInfrastructureJar(),
                serverPath.getServerJar()
        );
        start();
    }

    /**
     * Symmetrically disconnect a server from a list of other servers,
     * which creates a partial partition.
     *
     * @param servers List of servers to disconnect from
     */
    @Override
    public void disconnect(List<ApplicationServer<CorfuServerParams>> servers) {
        throw new UnsupportedOperationException("Not supported");
    }

    /**
     * Pause the {@link ApplicationServer} process on the localhost
     */
    @Override
    public void pause() {
        log.info("Pausing the Corfu server: {}", getParams().getName());

        executeCommand(Optional.empty(), processManager.pauseCommand());
    }

    /**
     * Start a {@link ApplicationServer} process on the localhost
     */
    @Override
    public void start() {
        Optional<String> cmdLine = getParams().getCommandLine(getNetworkInterface());
        if (!cmdLine.isPresent()) {
            throw new NodeException("Command line not set");
        }

        executeCommand(
                Optional.of(serverPath.getCorfuDir()),
                processManager.startCommand(cmdLine.get())
        );
    }

    /**
     * Restart the {@link ApplicationServer} process on the localhost
     */
    @Override
    public void restart() {
        stop(getParams().getCommonParams().getStopTimeout());
        start();
    }

    /**
     * Reconnect a server to the cluster
     */
    @Override
    public void reconnect() {
        throw new UnsupportedOperationException("Not supported");
    }

    /**
     * Reconnect a server to a list of servers.
     */
    @Override
    public void reconnect(List<ApplicationServer<CorfuServerParams>> servers) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public String execute(String command) {
        return executeCommand(Optional.empty(), command);
    }

    /**
     * Resume a {@link ApplicationServer}
     */
    @Override
    public void resume() {
        log.info("Resuming the corfu server: {}", getParams().getName());

        executeCommand(Optional.empty(), processManager.resumeCommand());
    }

    /**
     * Executes a certain command on the local machine.
     */
    private String executeCommand(Optional<Path> workDir, String cmdLine) {
        try {
            return commandHelper.executeCommand(workDir, cmdLine);
        } catch (IOException e) {
            throw new NodeException("Execution error. Cmd: " + cmdLine, e);
        }
    }

    /**
     * Stop corfu server
     *
     * @param timeout a limit within which the method attempts to gracefully stop the {@link ApplicationServer}.
     */
    @Override
    public void stop(Duration timeout) {
        log.info("Stop corfu server. Params: {}", getParams());

        try {
            executeCommand(Optional.empty(), processManager.stopCommand());
        } catch (Exception e) {
            String err = String.format("Can't STOP corfu: %s. Process not found", getParams().getName());
            throw new NodeException(err, e);
        }
    }

    /**
     * Kill corfu server
     * <p>
     * Kill the {@link ApplicationServer} process on the local machine directly.
     */
    @Override
    public void kill() {
        log.info("Kill the corfu server. Params: {}", getParams());
        try {
            executeCommand(Optional.empty(), processManager.killCommand());
        } catch (Exception e) {
            String err = String.format("Can't KILL corfu: %s. Process not found, ip: %s",
                    getParams().getName(), ipAddress
            );
            throw new NodeException(err, e);
        }
    }

    /**
     * Destroy the {@link ApplicationServer} by killing the process and removing the files
     *
     * @throws NodeException this exception will be thrown if the server can not be destroyed.
     */
    @Override
    public void destroy() {
        log.info("Destroy node: {}", getParams().getName());
        kill();
        try {
            collectLogs();
            removeAppDir();
        } catch (Exception e) {
            throw new NodeException("Can't clean corfu directories", e);
        }
    }

    @Override
    public CorfuServerParams getParams() {
        return serverPath.getParams();
    }

    /**
     * Remove corfu server application dir.
     * AppDir is a directory that contains corfu-infrastructure jar file and could have log files,
     * stream-log files and so on, whatever used by the application.
     */
    private void removeAppDir() {
        executeCommand(Optional.empty(), processManager.removeServerDirCommand());
    }

    @Override
    public IpAddress getNetworkInterface() {
        return ipAddress;
    }

    @Override
    public void collectLogs() {
        if (!loggingParams.isEnabled()) {
            log.debug("Logging is disabled");
            return;
        }

        log.info("Download corfu server logs: {}", getParams().getName());

        Path corfuLogDir = getLogDir();

        try {
            commandHelper.copyFile(
                    serverPath.getCorfuLogFile(),
                    corfuLogDir.resolve(getParams().getName() + ".log")
            );
        } catch (Exception e) {
            log.error("Can't download logs for corfu server: {}", getParams().getName(), e);
        }
    }

    @Override
    public Path getLogDir() {
        Path corfuLogDir = getParams()
                .getCommonParams()
                .getUniverseDirectory()
                .resolve("logs")
                .resolve(loggingParams.getRelativeServerLogDir());

        File logDirFile = corfuLogDir.toFile();
        if (!logDirFile.exists() && logDirFile.mkdirs()) {
            log.info("Created new corfu log directory at {}.", corfuLogDir);
        }
        return corfuLogDir;
    }

    @Override
    public LocalCorfuClient getLocalCorfuClient() {
        LocalCorfuClient localClient = LocalCorfuClient.builder()
                .serverEndpoints(ImmutableSortedSet.of(getEndpoint()))
                .corfuRuntimeParams(CorfuRuntime.CorfuRuntimeParameters.builder())
                .build();

        localClient.deploy();
        return localClient;
    }
}
