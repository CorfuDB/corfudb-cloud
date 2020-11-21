package org.corfudb.universe.group;

import com.google.common.collect.ImmutableSet;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams;
import org.corfudb.universe.api.universe.node.CommonNodeParams;
import org.corfudb.universe.api.universe.node.Node.NodeType;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuClusterParams;
import org.corfudb.universe.universe.node.server.ServerUtil;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams;
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
                .nodeType(NodeType.CORFU)
                .build();
        CorfuServerParams serverParams = CorfuServerParams.builder()
                .commonParams(commonParams)
                .serverVersion("1.0.0")
                .build();
        DockerContainerParams<CorfuServerParams> deployment = DockerContainerParams
                .<CorfuServerParams>builder()
                .applicationParams(serverParams)
                .image("test/image")
                .networkName("test-network")
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