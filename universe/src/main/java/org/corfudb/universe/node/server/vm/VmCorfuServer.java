package org.corfudb.universe.node.server.vm;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.universe.api.deployment.vm.VmParams;
import org.corfudb.universe.api.deployment.vm.VmParams.VsphereParams;
import org.corfudb.universe.api.node.NodeException;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.group.cluster.vm.RemoteOperationHelper;
import org.corfudb.universe.logging.LoggingParams;
import org.corfudb.universe.node.server.AbstractCorfuServer;
import org.corfudb.universe.node.server.CorfuServer;
import org.corfudb.universe.node.server.CorfuServerParams;
import org.corfudb.universe.node.server.process.CorfuProcessManager;
import org.corfudb.universe.node.server.process.CorfuServerPath;
import org.corfudb.universe.node.stress.vm.VmStress;
import org.corfudb.universe.universe.vm.VmManager;
import org.corfudb.universe.util.IpAddress;
import org.corfudb.universe.util.IpTablesUtil;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Implements a {@link CorfuServer} instance that is running on VM.
 */
@Slf4j
public class VmCorfuServer extends AbstractCorfuServer {

    @NonNull
    @Getter
    private final VmManager vmManager;

    @NonNull
    private final IpAddress ipAddress;

    @Getter
    @NonNull
    private final RemoteOperationHelper remoteOperationHelper;

    @NonNull
    private final VmStress stress;

    @NonNull
    private final CorfuProcessManager processManager;

    @NonNull
    private final CorfuServerPath serverPath;

    @NonNull
    private final VmParams<CorfuServerParams> deploymentParams;

    @NonNull
    private final VsphereParams vsphereParams;

    /**
     * VmCorfuServer constructor
     *
     * @param vmManager             vm manager
     * @param universeParams        universe params
     * @param stress                stress
     * @param remoteOperationHelper remote operation helper
     * @param loggingParams         logging params
     */
    @Builder
    public VmCorfuServer(
            VmParams<CorfuServerParams> deploymentParams, VmManager vmManager, UniverseParams universeParams,
            VmStress stress, RemoteOperationHelper remoteOperationHelper, LoggingParams loggingParams,
            VsphereParams vsphereParams) {
        super(deploymentParams.getApplicationParams(), universeParams, loggingParams);
        this.deploymentParams = deploymentParams;
        this.vsphereParams = vsphereParams;
        this.vmManager = vmManager;
        this.ipAddress = getIpAddress();
        this.stress = stress;
        this.remoteOperationHelper = remoteOperationHelper;
        this.serverPath = new CorfuServerPath(params);
        this.processManager = new CorfuProcessManager(serverPath, params);
    }

    /**
     * Deploys a Corfu server on the VM as specified, including the following steps:
     * a) Copy the corfu jar file under the working directory to the VM
     * b) Run that jar file using java on the VM
     */
    @Override
    public CorfuServer deploy() {
        log.info("Deploy vm server: {}", deploymentParams.getVmName());

        executeCommand(processManager.createServerDirCommand());
        executeCommand(processManager.createStreamLogDirCommand());

        remoteOperationHelper.copyFile(
                params.getInfrastructureJar(),
                serverPath.getServerJar()
        );

        start();

        return this;
    }

    /**
     * Symmetrically disconnect the server from the cluster,
     * which creates a complete partition.
     */
    @Override
    public void disconnect() {
        log.info("Disconnecting the VM server: {} from the network.", deploymentParams.getVmName());

        vsphereParams.getVmIpAddresses().values().stream()
                .filter(addr -> !addr.equals(getIpAddress()))
                .forEach(addr -> {
                    executeSudoCommand(String.join(" ", IpTablesUtil.dropInput(addr)));
                    executeSudoCommand(String.join(" ", IpTablesUtil.dropOutput(addr)));
                });
    }

    /**
     * Symmetrically disconnect a server from a list of other servers,
     * which creates a partial partition.
     *
     * @param servers List of servers to disconnect from
     */
    @Override
    public void disconnect(List<CorfuServer> servers) {
        log.info("Disconnecting the VM server: {} from the specified servers: {}",
                params.getName(), servers);

        servers.stream()
                .filter(s -> !s.getParams().equals(params))
                .forEach(s -> {
                    executeSudoCommand(String.join(" ", IpTablesUtil.dropInput(s.getIpAddress())));
                    executeSudoCommand(String.join(" ", IpTablesUtil.dropOutput(s.getIpAddress())));
                });
    }

    /**
     * Pause the {@link CorfuServer} process on the VM
     */
    @Override
    public void pause() {
        log.info("Pausing the VM Corfu server: {}", params.getName());

        executeCommand(processManager.pauseCommand());
    }

    /**
     * Start a {@link CorfuServer} process on the VM
     */
    @Override
    public void start() {
        // Compose command line for starting Corfu
        Optional<String> cmdLine = params.getCommandLine(getNetworkInterface());
        if (!cmdLine.isPresent()) {
            throw new NodeException("Command line not set");
        }

        String cmd = String.format(
                "sh -c '%s'",
                processManager.startCommand(cmdLine.get())
        );
        executeCommand(cmd);
    }

    /**
     * Restart the {@link CorfuServer} process on the VM
     */
    @Override
    public void restart() {
        stop(params.getCommonParams().getStopTimeout());
        start();
    }

    /**
     * Reconnect a server to the cluster
     */
    @Override
    public void reconnect() {
        log.info("Reconnecting the VM server: {} to the cluster.", deploymentParams.getVmName());

        executeSudoCommand(String.join(" ", IpTablesUtil.cleanInput()));
        executeSudoCommand(String.join(" ", IpTablesUtil.cleanOutput()));
    }

    /**
     * Reconnect a server to a list of servers.
     *
     * @param servers list of corfu servers
     */
    @Override
    public void reconnect(List<CorfuServer> servers) {
        log.info("Reconnecting the VM server: {} to specified servers: {}",
                params.getName(), servers);

        servers.stream()
                .filter(s -> !s.getParams().equals(params))
                .forEach(s -> {
                    executeSudoCommand(String.join(" ", IpTablesUtil.revertDropInput(s.getIpAddress())));
                    executeSudoCommand(String.join(" ", IpTablesUtil.revertDropOutput(s.getIpAddress())));
                });
    }

    /**
     * Execute a shell command in a vm
     *
     * @param command a shell command
     * @return command output
     */
    @Override
    public String execute(String command) {
        return executeCommand(command);
    }

    /**
     * Resume a {@link CorfuServer}
     */
    @Override
    public void resume() {
        log.info("Resuming the corfu server: {}", params.getName());
        executeCommand(processManager.resumeCommand());
    }

    /**
     * Executes a certain command on the VM.
     */
    private String executeCommand(String cmdLine) {
        return remoteOperationHelper.executeCommand(cmdLine);
    }

    /**
     * Executes a certain Sudo command on the VM.
     */
    private String executeSudoCommand(String cmdLine) {
        return remoteOperationHelper.executeSudoCommand(cmdLine);
    }

    /**
     * Get vm ip address
     *
     * @return the IpAddress of this VM.
     */
    @Override
    public IpAddress getIpAddress() {
        return vmManager.getIpAddress();
    }

    /**
     * Stop corfu server
     *
     * @param timeout a limit within which the method attempts to gracefully stop the {@link CorfuServer}.
     */
    @Override
    public VmCorfuServer stop(Duration timeout) {
        log.info("Stop corfu server on vm: {}, params: {}", deploymentParams.getVmName(), params);

        try {
            executeCommand(processManager.stopCommand());
        } catch (Exception e) {
            String err = String.format("Can't STOP corfu: %s. Process not found on vm: %s, ip: %s",
                    params.getName(), deploymentParams.getVmName(), ipAddress
            );
            throw new NodeException(err, e);
        }

        return this;
    }

    /**
     * Kill the {@link CorfuServer} process on the VM directly.
     */
    @Override
    public VmCorfuServer kill() {
        log.info("Kill the corfu server. Params: {}", params);
        try {
            executeCommand(processManager.killCommand());
        } catch (Exception e) {
            String err = String.format("Can't KILL corfu: %s. Process not found on vm: %s, ip: %s",
                    params.getName(), deploymentParams.getVmName(), ipAddress
            );
            throw new NodeException(err, e);
        }

        return this;
    }

    /**
     * Destroy the {@link CorfuServer} by killing the process and removing the files
     *
     * @throws NodeException this exception will be thrown if the server can not be destroyed.
     */
    @Override
    public VmCorfuServer destroy() {
        log.info("Destroy node: {}", params.getName());
        kill();
        try {
            executeSudoCommand(IpTablesUtil.cleanAll());
            collectLogs();
            removeAppDir();
        } catch (Exception e) {
            throw new NodeException("Can't clean corfu directories", e);
        }

        return this;
    }

    /**
     * Remove corfu server application dir.
     * AppDir is a directory that contains corfu-infrastructure jar file and could have log files, stream-log files and
     * so on, whatever used by the application.
     */
    private void removeAppDir() {
        executeCommand(processManager.removeServerDirCommand());
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

        log.info("Download corfu server logs: {}", params.getName());

        Path corfuLogDir = params
                .getUniverseDirectory()
                .resolve("logs")
                .resolve(loggingParams.getRelativeServerLogDir());

        File logDirFile = corfuLogDir.toFile();
        if (!logDirFile.exists() && logDirFile.mkdirs()) {
            log.info("Created new corfu log directory at {}.", corfuLogDir);
        }

        try {
            remoteOperationHelper.downloadFile(
                    corfuLogDir.resolve(params.getName() + ".log"),
                    serverPath.getCorfuLogFile()
            );
        } catch (Exception e) {
            log.error("Can't download logs for corfu server: {}", params.getName(), e);
        }
    }
}
