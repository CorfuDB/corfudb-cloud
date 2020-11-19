package org.corfudb.universe.api.universe.node;

import org.corfudb.universe.api.common.IpAddress;
import org.corfudb.universe.api.universe.Universe;
import org.corfudb.universe.api.universe.group.Group;

import java.time.Duration;

/**
 * Represent nodes within {@link Group}s of {@link Universe}
 */
public interface Node<P extends NodeParams> extends Comparable<Node<P>> {

    /**
     * Deploys a specific node into the {@link Universe}.
     *
     * @throws NodeException thrown when can not deploy {@link Node}
     */
    void deploy();

    /**
     * Stops a {@link Node} gracefully within the timeout provided to this method.
     *
     * @param timeout a limit within which the method attempts to gracefully stop the {@link Node}.
     * @throws NodeException thrown in case of unsuccessful stop.
     */
    void stop(Duration timeout);

    /**
     * Kills a {@link Node} immediately.
     *
     * @throws NodeException thrown in case of unsuccessful kill.
     */
    void kill();

    /**
     * Destroy a {@link Node} completely.
     *
     * @throws NodeException thrown in case of unsuccessful destroy.
     */
    void destroy();

    P getParams();

    @Override
    default int compareTo(Node<P> other) {
        return getParams().compareTo(other.getParams());
    }

    IpAddress getNetworkInterface();

    default String getEndpoint() {
        return getNetworkInterface() + ":" + getParams().getCommonParams().getPorts().iterator().next();
    }

    enum NodeType {
        CORFU, CORFU_LONGEVITY_APP, CORFU_CLIENT, PROMETHEUS, CASSANDRA, MANGLE
    }
}
