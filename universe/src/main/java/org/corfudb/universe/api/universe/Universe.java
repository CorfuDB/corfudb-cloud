package org.corfudb.universe.api.universe;

import com.google.common.collect.ImmutableMap;
import org.corfudb.universe.api.deployment.DeploymentParams;
import org.corfudb.universe.api.group.Group;
import org.corfudb.universe.api.group.Group.GroupParams;
import org.corfudb.universe.api.group.cluster.Cluster;
import org.corfudb.universe.api.node.Node;
import org.corfudb.universe.api.node.Node.NodeParams;

/**
 * A Universe represents a common notion of a universe of nodes.
 * The architecture of a universe is composed of collections of {@link Group}s and {@link Node}s.
 * Each instance of service in the universe is composed of a collection of {@link Node}s which are a subset of the
 * entire nodes included in the universe.
 * {@link Universe} configuration is provided by an instance of {@link UniverseParams}.
 * <p>
 * The following are the main functionalities provided by this class:
 * DEPLOY: create a {@link Universe} according to the {@link Universe} parameters.
 * SHUTDOWN: shutdown a {@link Universe}.
 * Depending on the underlying deployment this might translate into
 * stopping the {@link Node}-s and/or shutting down the network.
 */
public interface Universe {

    /**
     * Create a {@link Universe} according to the desired state mentioned by {@link UniverseParams}
     *
     * @return an instance of applied change in the {@link Universe}
     * @throws UniverseException universe exception
     */
    Universe deploy();

    /**
     * Shutdown the entire {@link Universe} by shutting down all the {@link Group}s in {@link Universe}
     *
     * @throws UniverseException universe exception
     */
    void shutdown();

    /**
     * Add a group into the universe
     *
     * @param groupParams group params
     * @return universe
     */
    <P extends NodeParams, D extends DeploymentParams<P>> Universe add(GroupParams<P, D> groupParams);

    /**
     * Returns an instance of {@link UniverseParams} representing the configuration for the {@link Universe}.
     *
     * @return an instance of {@link UniverseParams}
     */
    UniverseParams getUniverseParams();

    /**
     * Returns an {@link ImmutableMap} of {@link Group}s contained in the {@link Universe}.
     *
     * @return {@link Group}s in the {@link Universe}
     */
    ImmutableMap<String, Group> groups();

    <T extends Group> T getGroup(String groupName);

    <T extends Group> T getGroup(Cluster.ClusterType clusterType);
}