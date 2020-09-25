package org.corfudb.universe.api.universe.group;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.lang3.RandomStringUtils;
import org.corfudb.universe.api.deployment.DeploymentParams;
import org.corfudb.universe.api.universe.group.cluster.Cluster;
import org.corfudb.universe.api.universe.node.Node;
import org.corfudb.universe.api.universe.Universe;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.corfudb.universe.api.universe.node.Node.NodeParams;

/**
 * This provides an abstraction for a group of {@link Node}s that come together to provide a logical service.
 * <p>
 * The following are the main functionalities provided by this class:
 * <p>
 * DEPLOY: deploys a {@link Group} representing a collection of {@link Node}-s using
 * the provided configuration in {@link GroupParams}
 * STOP: stops a {@link Group} gracefully within the provided timeout
 * KILL: kills a {@link Group} immediately
 */
public interface Group<
        P extends NodeParams,
        D extends DeploymentParams<P>,
        N extends Node<P, N>,
        G extends Group.GroupParams<P, D>> {

    /**
     * Deploy the {@link Group} into the {@link Universe}.
     *
     * @return current instance of deployed {@link Group}
     */
    Group<P, D, N, G> deploy();

    /**
     * Stop the {@link Group} by stopping all individual {@link Node}-s of the group.
     * Must happened within the limit of provided timeout.
     *
     * @param timeout allowed time to gracefully stop the {@link Group}
     */
    void stop(Duration timeout);

    /**
     * Kill the {@link Group} immediately by killing all the {@link Node}-s of the group.
     * Kill - means stop or interrupt immediately
     */
    void kill();

    /**
     * Destroy the {@link Group} immediately by destroying all the {@link Node}-s of the group.
     * Destroy - means kill and clean up the node directory which contains the application itself and could contain
     * config files, db files and so on.
     */
    void destroy();

    N add(D deploymentParams);

    /**
     * Provides {@link GroupParams} used for configuring a {@link Group}
     *
     * @return a Group parameters
     */
    G getParams();

    /**
     * Provide the nodes that the {@link Group} is composed of.
     *
     * @return an {@link ImmutableList} of {@link Node}s.
     */
    ImmutableSortedMap<String, N> nodes();

    interface GroupParams<P extends NodeParams, D extends DeploymentParams<P>> {
        String getName();

        Cluster.ClusterType getType();

        ImmutableSortedSet<D> getNodesParams();

        GroupParams<P, D> add(D deploymentParams);

        String getFullNodeName(String nodeName);

        /**
         * Cluster size
         *
         * @return number of nodes in the cluster
         */
        default int size() {
            return getNodesParams().size();
        }

        /**
         * Returns list of cluster nodes
         *
         * @return list of servers
         */
        default List<String> getClusterNodes() {
            return getNodesParams().stream()
                    .map(deployment -> deployment.getApplicationParams().getName())
                    .collect(Collectors.toList());
        }

        @Builder
        class GenericGroupParams<P extends NodeParams, D extends DeploymentParams<P>> implements GroupParams<P, D> {

            @Builder.Default
            @Getter
            @NonNull
            private final String name = RandomStringUtils.randomAlphabetic(6).toLowerCase();

            @NonNull
            private final String nodeNamePrefix;

            @Getter
            @NonNull
            private final Cluster.ClusterType type;

            @Builder.Default
            @Getter
            private final int numNodes = 3;

            @Builder.Default
            @NonNull
            private final SortedSet<D> nodes = new TreeSet<>();

            @Override
            public ImmutableSortedSet<D> getNodesParams() {
                return ImmutableSortedSet.copyOf(nodes);
            }

            @Override
            public GroupParams<P, D> add(D deploymentParams) {
                nodes.add(deploymentParams);
                return this;
            }

            /**
             * Returns full node name
             *
             * @param nodeName simple node name
             * @return full node name
             */
            public String getFullNodeName(String nodeName) {
                return String.format("%s-%s-%s", name, nodeNamePrefix, nodeName);
            }

            /**
             * Get corfu node by name
             *
             * @param serverName server name
             * @return corfu node
             */
            public synchronized D getNode(String serverName) {
                Map<String, D> nodesMap = nodes
                        .stream()
                        .collect(Collectors.toMap(deployment -> deployment.getApplicationParams().getName(), n -> n));

                return nodesMap.get(serverName);
            }

            /**
             * Cluster size
             *
             * @return number of nodes in the cluster
             */
            public int size() {
                return getNodesParams().size();
            }

            /**
             * Returns list of cluster nodes
             *
             * @return list of servers
             */
            public List<String> getClusterNodes() {
                return getNodesParams().stream()
                        .map(deployment -> deployment.getApplicationParams().getName())
                        .collect(Collectors.toList());
            }
        }
    }
}
