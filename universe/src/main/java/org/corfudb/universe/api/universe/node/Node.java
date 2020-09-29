package org.corfudb.universe.api.universe.node;

import org.corfudb.universe.api.common.IpAddress;
import org.corfudb.universe.api.universe.Universe;
import org.corfudb.universe.api.universe.group.Group;

import java.time.Duration;

/**
 * Represent nodes within {@link Group}s of {@link Universe}
 */
public interface Node<P extends NodeParams, S extends Node<P, S>> extends Comparable<Node<P, S>> {

    /**
     * Deploys a specific node into the {@link Universe}.
     *
     * @return current instance of the {@link Node} with the new state.
     * @throws NodeException thrown when can not deploy {@link Node}
     */
    S deploy();

    /**
     * Stops a {@link Node} gracefully within the timeout provided to this method.
     *
     * @param timeout a limit within which the method attempts to gracefully stop the {@link Node}.
     * @throws NodeException thrown in case of unsuccessful stop.
     */
    S stop(Duration timeout);

    /**
     * Kills a {@link Node} immediately.
     *
     * @throws NodeException thrown in case of unsuccessful kill.
     */
    S kill();

    /**
     * Destroy a {@link Node} completely.
     *
     * @throws NodeException thrown in case of unsuccessful destroy.
     */
    S destroy();

    P getParams();

    @Override
    default int compareTo(Node<P, S> other) {
        return getParams().compareTo(other.getParams());
    }

    IpAddress getNetworkInterface();

    default String getEndpoint() {
        return getNetworkInterface() + ":" + getParams().getCommonParams().getPorts().iterator().next();
    }

    enum NodeType {
        CORFU, CORFU_CLIENT, PROMETHEUS, CASSANDRA, MANGLE
    }
}
