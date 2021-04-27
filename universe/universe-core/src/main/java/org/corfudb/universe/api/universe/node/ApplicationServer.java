package org.corfudb.universe.api.universe.node;

import org.corfudb.universe.api.common.IpAddress;
import org.corfudb.universe.api.universe.Universe;

import java.nio.file.Path;
import java.util.List;

/**
 * Represent a Corfu server implementation of {@link Node} used in the {@link Universe}.
 */
public interface ApplicationServer<P extends NodeParams> extends Node<P> {

    /**
     * Symmetrically disconnect a ApplicationServer from a list of other servers,
     * which creates a partial partition.
     *
     * @param servers List of servers to disconnect from
     * @throws NodeException thrown in case of unsuccessful disconnect.
     */
    void disconnect(List<ApplicationServer<P>> servers);

    /**
     * Pause a ApplicationServer
     *
     * @throws NodeException thrown in case of unsuccessful pause.
     */
    void pause();

    /**
     * Restart a {@link ApplicationServer}
     *
     * @throws NodeException this exception will be thrown if the node can not be restarted
     */
    void restart();

    /**
     * Start a {@link ApplicationServer}
     *
     * @throws NodeException this exception will be thrown if the node can not be started
     */
    void start();

    /**
     * Reconnect a {@link ApplicationServer} to the cluster
     *
     * @throws NodeException this exception will be thrown if the node can not be reconnected
     */
    void reconnect();

    /**
     * Reconnect a {@link ApplicationServer} to the list of servers
     *
     * @param servers List of servers to reconnect.
     * @throws NodeException this exception will be thrown if the node can not be reconnected
     */
    void reconnect(List<ApplicationServer<P>> servers);

    /**
     * Execute a shell command on a vm
     *
     * @param command a shell command
     * @return command output
     */
    String execute(String command);

    /**
     * Resume a {@link ApplicationServer}
     *
     * @throws NodeException this exception will be thrown if the node can not be unpaused
     */
    void resume();

    /**
     * Indicates if a docker container is running
     * @return is a node is running
     */
    boolean isRunning();

    /**
     * Returns an ip address of a server
     * @return an ip address
     */
    IpAddress getIpAddress();

    /**
     * Save server logs in the server logs directory
     */
    void collectLogs();

    Path getLogDir();
}
