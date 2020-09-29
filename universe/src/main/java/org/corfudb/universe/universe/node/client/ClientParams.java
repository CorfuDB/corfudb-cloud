package org.corfudb.universe.universe.node.client;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import org.corfudb.universe.api.common.IpAddress;
import org.corfudb.universe.api.universe.node.CommonNodeParams;
import org.corfudb.universe.api.universe.node.Node.NodeType;
import org.corfudb.universe.api.universe.node.NodeParams;
import org.corfudb.universe.universe.node.server.ServerUtil;

import java.time.Duration;
import java.util.Optional;

@Builder
@Getter
@EqualsAndHashCode
public class ClientParams implements NodeParams {

    @Default
    @NonNull
    private final String name = "corfuClient";

    @NonNull
    private final NodeType nodeType = NodeType.CORFU_CLIENT;

    @Default
    @Getter
    private final int port = ServerUtil.getRandomOpenPort();

    /**
     * Total number of times to retry a workflow if it fails
     */
    @Default
    @EqualsAndHashCode.Exclude
    private final int numRetry = 5;

    /**
     * Total time to wait before the workflow times out
     */
    @Default
    @NonNull
    @EqualsAndHashCode.Exclude
    private final Duration timeout = Duration.ofSeconds(30);

    /**
     * Poll period to query the completion of the workflow
     */
    @Default
    @NonNull
    @EqualsAndHashCode.Exclude
    private final Duration pollPeriod = Duration.ofMillis(50);

    @Default
    @EqualsAndHashCode.Exclude
    private final int order = 0;

    @Override
    public CommonNodeParams getCommonParams() {
        throw new IllegalStateException("Client doesn't provide this type of params");
    }

    @Override
    public Optional<String> getCommandLine(IpAddress networkInterface) {
        return Optional.empty();
    }

}
