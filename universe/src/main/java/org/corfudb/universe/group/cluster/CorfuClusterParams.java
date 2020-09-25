package org.corfudb.universe.group.cluster;

import com.google.common.collect.ImmutableSortedSet;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import org.apache.commons.lang3.RandomStringUtils;
import org.corfudb.common.util.ClassUtils;
import org.corfudb.universe.api.deployment.DeploymentParams;
import org.corfudb.universe.api.group.Group.GroupParams;
import org.corfudb.universe.api.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.api.node.Node.NodeType;
import org.corfudb.universe.node.server.CorfuServerParams;

import java.time.Duration;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Builder
@EqualsAndHashCode
@ToString
public class CorfuClusterParams<D extends DeploymentParams<CorfuServerParams>>
        implements GroupParams<CorfuServerParams, D> {

    @Getter
    @Default
    @NonNull
    private final String name = RandomStringUtils.randomAlphabetic(6).toLowerCase();

    @Default
    @Getter
    private final int numNodes = 3;

    /**
     * Corfu server version, for instance: 0.3.0-SNAPSHOT
     */
    @NonNull
    @Getter
    private final String serverVersion;

    @Default
    @NonNull
    private final SortedSet<D> nodes = new TreeSet<>();

    @Getter
    @Default
    @NonNull
    private final NodeType nodeType = NodeType.CORFU_SERVER;

    @Default
    @Getter
    private final int bootStrapRetries = 20;

    @Default
    @Getter
    @NonNull
    private final Duration retryDuration = Duration.ofSeconds(3);

    @Override
    public ClusterType getType() {
        return ClusterType.CORFU_CLUSTER;
    }

    @Override
    public ImmutableSortedSet<D> getNodesParams() {
        return ImmutableSortedSet.copyOf(nodes);
    }

    @Override
    public GroupParams<CorfuServerParams, D> add(D deploymentParams) {
        nodes.add(ClassUtils.cast(deploymentParams));
        return this;
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
     * Returns full node name
     *
     * @param nodeName simple node name
     * @return full node name
     */
    public String getFullNodeName(String nodeName) {
        return name + "-corfu-" + nodeName;
    }

}
