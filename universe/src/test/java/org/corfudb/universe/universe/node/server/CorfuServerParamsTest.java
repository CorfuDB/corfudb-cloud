package org.corfudb.universe.universe.node.server;

import com.google.common.collect.ImmutableSet;
import org.corfudb.universe.api.universe.node.CommonNodeParams;
import org.corfudb.universe.api.universe.node.Node;
import org.corfudb.universe.universe.node.server.corfu.ApplicationServer;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

public class CorfuServerParamsTest {

    @Test
    public void testEquals() {
        CorfuServerParams p1 = CorfuServerParams.builder()
                .commonParams(getCommonNodeParams())
                .mode(ApplicationServer.Mode.CLUSTER)
                .persistence(ApplicationServer.Persistence.DISK)

                .serverVersion("1.0.0")
                .build();

        CorfuServerParams p2 = CorfuServerParams.builder()
                .commonParams(getCommonNodeParams())
                .mode(ApplicationServer.Mode.CLUSTER)
                .persistence(ApplicationServer.Persistence.DISK)
                .serverVersion("1.0.0")
                .build();

        assertThat(p1).isEqualTo(p2);
    }

    private CommonNodeParams getCommonNodeParams() {
        return CommonNodeParams.builder()
                .clusterName("test-cluster")
                .ports(ImmutableSet.of(123))
                .nodeType(Node.NodeType.CORFU)
                .logLevel(Level.TRACE)
                .stopTimeout(Duration.ofSeconds(123))
                .build();
    }

}