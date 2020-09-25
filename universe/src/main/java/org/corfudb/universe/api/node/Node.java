package org.corfudb.universe.api.node;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSet;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import org.corfudb.universe.api.group.Group;
import org.corfudb.universe.api.universe.Universe;
import org.corfudb.universe.node.server.ServerUtil;
import org.corfudb.universe.util.IpAddress;
import org.slf4j.event.Level;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Represent nodes within {@link Group}s of {@link Universe}
 */
public interface Node<P extends Node.NodeParams, S extends Node<P, S>> extends Comparable<Node<P, S>> {

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

    /**
     * Common interface for the configurations of different implementation of {@link Node}.
     */
    interface NodeParams extends Comparable<NodeParams> {

        default String getName() {
            return getCommonParams().getName();
        }

        CommonNodeParams getCommonParams();

        Optional<String> getCommandLine(IpAddress networkInterface);

        default int compareTo(NodeParams other) {
            return getCommonParams().compareTo(other.getCommonParams());
        }

        @Builder
        @EqualsAndHashCode
        @ToString
        class CommonNodeParams implements Comparable<CommonNodeParams> {

            @Builder.Default
            @Getter
            @NonNull
            private final Set<Integer> ports = ImmutableSet.of(ServerUtil.getRandomOpenPort());

            @NonNull
            private final String nodeNamePrefix;

            @Builder.Default
            @NonNull
            @Getter
            @EqualsAndHashCode.Exclude
            private final Level logLevel = Level.INFO;

            @NonNull
            @Getter
            private final NodeType nodeType;

            @Getter
            @NonNull
            protected final String clusterName;

            @Getter
            @Builder.Default
            @NonNull
            @EqualsAndHashCode.Exclude
            private final Duration stopTimeout = Duration.ofSeconds(1);

            public String getName() {
                return String.format("%s-%s%d", clusterName, nodeNamePrefix, ports.stream().findFirst().orElse(-1));
            }

            @Override
            public int compareTo(CommonNodeParams other) {
                return ComparisonChain.start()
                        .compare(this.getName(), other.getName())
                        .compare(this.getNodeType(), other.getNodeType())
                        .result();
            }
        }
    }

    enum NodeType {
        CORFU_SERVER, CORFU_CLIENT, METRICS_SERVER, SHELL_NODE
    }
}
