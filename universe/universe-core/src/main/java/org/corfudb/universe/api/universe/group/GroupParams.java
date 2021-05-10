package org.corfudb.universe.api.universe.group;

import com.google.common.collect.ImmutableSortedSet;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import org.apache.commons.lang3.RandomStringUtils;
import org.corfudb.universe.api.deployment.DeploymentParams;
import org.corfudb.universe.api.universe.group.cluster.Cluster;
import org.corfudb.universe.api.universe.node.NodeParams;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Universe group params
 *
 * @param <P> application params
 * @param <D> deployment params
 */
public interface GroupParams<P extends NodeParams, D extends DeploymentParams<P>> {
    /**
     * Group name
     *
     * @return group name
     */
    String getName();

    /**
     * cluster type
     *
     * @return cluster type
     */
    Cluster.ClusterType getType();

    /**
     * List of Node parameters
     *
     * @return node params
     */
    ImmutableSortedSet<D> getNodesParams();

    /**
     * Add a node (parameters) into a group
     *
     * @param deploymentParams node deployment params
     * @return a group with added node
     */
    GroupParams<P, D> add(D deploymentParams);

    /**
     * Full node name (unique)
     *
     * @param nodeName node nane
     * @return full node name
     */
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
    @ToString
    class GenericGroupParams<P extends NodeParams, D extends DeploymentParams<P>> implements GroupParams<P, D> {

        @Builder.Default
        @Getter
        @NonNull
        private final String name = RandomStringUtils.randomAlphabetic(6).toLowerCase();

        @Getter
        @NonNull
        private final Cluster.ClusterType type;

        @Builder.Default
        @Getter
        private final int numNodes = 3;

        @Builder.Default
        @NonNull
        @Getter
        private final BootstrapParams bootstrapParams = BootstrapParams.builder().build();

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
            return String.format("%s-%s-%s", name, type.name().toLowerCase(), nodeName);
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

    @Builder
    class BootstrapParams {

        @Builder.Default
        @Getter
        private final boolean bootstrapEnabled = true;

        @Builder.Default
        @Getter
        private final int bootStrapRetries = 20;

        @Builder.Default
        @Getter
        @NonNull
        private final Duration retryDuration = Duration.ofSeconds(3);
    }
}
