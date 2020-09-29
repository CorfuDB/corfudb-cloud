package org.corfudb.universe.universe.node.server.corfu;

import org.corfudb.universe.api.common.IpAddress;
import org.corfudb.universe.api.universe.Universe;
import org.corfudb.universe.api.universe.node.Node;
import org.corfudb.universe.api.universe.node.NodeException;
import org.corfudb.universe.universe.node.client.LocalCorfuClient;

import java.util.List;

/**
 * Represent a Corfu server implementation of {@link Node} used in the {@link Universe}.
 */
public interface CorfuServer extends Node<CorfuServerParams, CorfuServer> {

    @Override
    CorfuServer deploy();

    CorfuServerParams getParams();

    /**
     * Symmetrically disconnect a CorfuServer from the cluster,
     * which creates a complete partition.
     *
     * @throws NodeException thrown in case of unsuccessful disconnect.
     */
    void disconnect();

    /**
     * Symmetrically disconnect a CorfuServer from a list of other servers,
     * which creates a partial partition.
     *
     * @param servers List of servers to disconnect from
     * @throws NodeException thrown in case of unsuccessful disconnect.
     */
    void disconnect(List<CorfuServer> servers);

    /**
     * Pause a CorfuServer
     *
     * @throws NodeException thrown in case of unsuccessful pause.
     */
    void pause();

    /**
     * Restart a {@link CorfuServer}
     *
     * @throws NodeException this exception will be thrown if the node can not be restarted
     */
    void restart();

    /**
     * Start a {@link CorfuServer}
     *
     * @throws NodeException this exception will be thrown if the node can not be started
     */
    void start();

    /**
     * Reconnect a {@link CorfuServer} to the cluster
     *
     * @throws NodeException this exception will be thrown if the node can not be reconnected
     */
    void reconnect();

    /**
     * Reconnect a {@link CorfuServer} to the list of servers
     *
     * @param servers List of servers to reconnect.
     * @throws NodeException this exception will be thrown if the node can not be reconnected
     */
    void reconnect(List<CorfuServer> servers);

    /**
     * Execute a shell command on a vm
     *
     * @param command a shell command
     * @return command output
     */
    String execute(String command);

    /**
     * Resume a {@link CorfuServer}
     *
     * @throws NodeException this exception will be thrown if the node can not be unpaused
     */
    void resume();

    IpAddress getIpAddress();

    LocalCorfuClient getLocalCorfuClient();

    /**
     * Save server logs in the server logs directory
     */
    void collectLogs();

    enum Mode {
        SINGLE, CLUSTER
    }

    enum Persistence {
        DISK, MEMORY
    }

}
