package org.corfudb.universe.api.universe.node;

import org.corfudb.universe.api.common.IpAddress;

import java.util.Optional;

/**
 * Common interface for the configurations of different implementation of {@link Node}.
 */
public interface NodeParams extends Comparable<NodeParams> {

    /**
     * Name of the node
     *
     * @return node name
     */
    default String getName() {
        return getCommonParams().getName();
    }

    /**
     * Common node params
     *
     * @return common params
     */
    CommonNodeParams getCommonParams();

    /**
     * The application command line
     *
     * @param networkInterface network ip address or dna name
     * @return app command line
     */
    Optional<String> getCommandLine(IpAddress networkInterface);

    /**
     * Compare two node params
     *
     * @param other another node params to compare
     * @return comparison result
     */
    default int compareTo(NodeParams other) {
        return getCommonParams().compareTo(other.getCommonParams());
    }

}
