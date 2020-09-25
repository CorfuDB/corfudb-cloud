package org.corfudb.universe.node.server;

import com.google.common.collect.ImmutableSet;
import org.corfudb.universe.api.node.Node;
import org.corfudb.universe.api.node.Node.NodeParams.CommonNodeParams;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

public class CorfuServerParamsTest {

    @Test
    public void testEquals() {
        CorfuServerParams p1 = CorfuServerParams.builder()
                .commonParams(getCommonNodeParams())
                .mode(CorfuServer.Mode.CLUSTER)
                .persistence(CorfuServer.Persistence.DISK)

                .serverVersion("1.0.0")
                .build();

        CorfuServerParams p2 = CorfuServerParams.builder()
                .commonParams(getCommonNodeParams())
                .mode(CorfuServer.Mode.CLUSTER)
                .persistence(CorfuServer.Persistence.DISK)
                .serverVersion("1.0.0")
                .build();

        assertThat(p1).isEqualTo(p2);
    }

    private CommonNodeParams getCommonNodeParams() {
        return CommonNodeParams.builder()
                .clusterName("test-cluster")
                .ports(ImmutableSet.of(123))
                .nodeType(Node.NodeType.CORFU_SERVER)
                .nodeNamePrefix("corfu")
                .logLevel(Level.TRACE)
                .stopTimeout(Duration.ofSeconds(123))
                .build();
    }


}