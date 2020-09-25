package org.corfudb.universe.group;

import com.google.common.collect.ImmutableSet;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams;
import org.corfudb.universe.api.node.Node.NodeParams.CommonNodeParams;
import org.corfudb.universe.api.node.Node.NodeType;
import org.corfudb.universe.group.cluster.CorfuClusterParams;
import org.corfudb.universe.node.server.CorfuServerParams;
import org.corfudb.universe.node.server.ServerUtil;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

public class CorfuClusterParamsTest {

    @Test
    public void testFullNodeName() {
        final String clusterName = "mycluster";
        final int port = ServerUtil.getRandomOpenPort();

        CommonNodeParams commonParams = CommonNodeParams.builder()
                .clusterName(clusterName)
                .ports(ImmutableSet.of(123))
                .nodeType(NodeType.CORFU_SERVER)
                .nodeNamePrefix("corfu")
                .build();
        CorfuServerParams serverParams = CorfuServerParams.builder()
                .commonParams(commonParams)
                .serverVersion("1.0.0")
                .build();
        DockerContainerParams<CorfuServerParams> deployment = DockerContainerParams
                .<CorfuServerParams>builder()
                .applicationParams(serverParams)
                .build();

        SortedSet<DockerContainerParams<CorfuServerParams>> corfuServers =
                new TreeSet<>(Collections.singletonList(deployment));

        CorfuClusterParams<DockerContainerParams<CorfuServerParams>> clusterParams = CorfuClusterParams
                .<DockerContainerParams<CorfuServerParams>>builder()
                .name(clusterName)
                .nodes(corfuServers)
                .serverVersion("1.0.0")
                .build();

        String fqdn = clusterParams.getFullNodeName("node" + port);

        assertThat(fqdn).isEqualTo(clusterName + "-corfu-" + "node" + port);
    }
}