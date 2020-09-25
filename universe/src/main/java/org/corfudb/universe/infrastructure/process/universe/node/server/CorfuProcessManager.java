package org.corfudb.universe.infrastructure.process.universe.node.server;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams;

/**
 * Provides executable commands for the CorfuServer-s to create a directory structure,
 * copy a corfu server jar file from the source to the target directory,
 * to manage corfu server (start, stop, kill)
 */
@Slf4j
public class CorfuProcessManager {

    @NonNull
    private final CorfuServerParams params;

    @NonNull
    private final CorfuServerPath serverPath;

    /**
     * Provides command line command to manage corfu servers
     *
     * @param serverPath corfu server path
     * @param params     params
     */
    public CorfuProcessManager(CorfuServerPath serverPath, @NonNull CorfuServerParams params) {
        this.params = params;
        this.serverPath = serverPath;
    }

    /**
     * Creates server dir
     *
     * @return creating server dir command
     */
    public String createServerDirCommand() {
        return "mkdir -p " + serverPath.getServerDir();
    }

    /**
     * Creates stream log directory
     *
     * @return creating a stream log command
     */
    public String createStreamLogDirCommand() {
        return "mkdir -p " + serverPath.getDbDir();
    }

    /**
     * Pauses corfu process
     *
     * @return cmd line
     */
    public String pauseCommand() {
        return String.format(
                "ps -ef | grep -v grep | %s | awk '{print $2}' | xargs kill -STOP",
                String.format("grep \"%s\"", params.getName())
        );
    }

    /**
     * Start corfu server
     *
     * @param commandLineParams command line params
     * @return a start command
     */
    public String startCommand(String commandLineParams) {

        // Compose command line for starting Corfu
        return "java -cp " +
                serverPath.getServerJarRelativePath() +
                " " +
                org.corfudb.infrastructure.CorfuServer.class.getName() +
                " " +
                commandLineParams +
                " > " +
                serverPath.getCorfuLogFile() +
                " 2>&1 &";
    }

    /**
     * Resume corfu server process
     *
     * @return resume command
     */
    public String resumeCommand() {
        log.info("Resuming the corfu server: {}", params.getName());

        return String.format(
                "ps -ef | grep -v grep | %s | awk '{print $2}' | xargs kill -CONT",
                String.format("grep \"%s\"", params.getName())
        );
    }

    /**
     * Stop corfu server command
     *
     * @return stop command
     */
    public String stopCommand() {
        log.info("Stop corfu server. Params: {}", params);

        return String.format(
                "ps -ef | grep -v grep | %s | awk '{print $2}' | xargs kill -15",
                String.format("grep \"%s\"", params.getName())
        );
    }

    /**
     * Kill corfu server command
     *
     * @return kill command
     */
    public String killCommand() {
        log.info("Kill the corfu server. Params: {}", params);

        return String.format(
                "ps -ef | grep -v grep | %s | awk '{print $2}' | xargs kill -9",
                String.format("grep \"%s\"", params.getName())
        );
    }

    /**
     * Remove corfu directory
     *
     * @return remove command
     */
    public String removeServerDirCommand() {
        return String.format("rm -rf %s", serverPath.getServerDir());
    }
}
