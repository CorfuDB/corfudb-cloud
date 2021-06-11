package org.corfudb.test.vm.stateful.ufo;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.runtime.view.ClusterStatusReport;
import org.corfudb.runtime.view.ClusterStatusReport.ConnectivityStatus;
import org.corfudb.runtime.view.ClusterStatusReport.NodeStatus;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.TestSchema.IdMessage;
import org.corfudb.test.spec.api.GenericSpec.SpecHelper;
import org.corfudb.universe.api.deployment.DeploymentParams;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.api.universe.node.ApplicationServer;
import org.corfudb.universe.api.universe.node.ApplicationServers.CorfuApplicationServer;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster.GenericCorfuCluster;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuClusterParams;
import org.corfudb.universe.universe.node.client.CorfuClient;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.corfudb.runtime.view.ClusterStatusReport.ClusterStatus;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;
import static org.corfudb.universe.test.util.ScenarioUtils.waitUninterruptibly;

@Slf4j
@Tag(TestGroups.STATEFUL)
public class AllNodesPartitionedTest extends AbstractCorfuUniverseTest {
    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
     * <p>
     * Test cluster behavior after all nodes are partitioned symmetrically
     * 1) Deploy and bootstrap a three nodes cluster
     * 2) Verify Cluster is stable after deployment
     * 3) Create a table in corfu i.e. "CorfuUFO_AllNodesPartitionedTable"
     * 4) Add 100 Entries into table
     * 5) Verification by number of rows count i.e (Total rows: 100) and verify table content
     * 6) Symmetrically partition all nodes so that they can't communicate
     * to any other node in cluster and vice versa
     * 7) Verify Layout, in layout there should be entry of node which we removed
     * 8) Recover cluster by reconnecting the partitioned node
     * 9) Verify layout, cluster status and data path again
     * 10) Add more 100 Entries into table and verify count and data of table
     * 11) Verification by number of rows count i.e (Total rows: 200) and verify table content
     * 12) Update the table entries from 60 to 90
     * 13) verify table content updated content
     * 14) clear the contents of the table
     */

    @Test
    public void test() {
        testRunner.executeStatefulVmTest(this::verifyAllNodesPartitioned);
    }

    private void verifyAllNodesPartitioned(UniverseWorkflow<UniverseParams, Fixture<UniverseParams>> wf)
            throws Exception {

        GenericCorfuCluster corfuCluster = wf.getUniverse().getGroup(ClusterType.CORFU);

        CorfuClusterParams<DeploymentParams<CorfuServerParams>> corfuClusterParams = corfuCluster.getParams();

        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore = new CorfuStore(runtime);

        // Define a namespace for the table.
        String namespace = "manager";
        // Define table name
        String tableName = getClass().getSimpleName();
        SpecHelper helper = new SpecHelper(runtime, tableName);
        assertThat(corfuCluster.nodes().size()).isEqualTo(3);
        assertThat(corfuCluster.nodes().size()).isEqualTo(corfuClusterParams.size());

        assertThat(corfuCluster.getParams().getNodesParams().size())
                .as("Invalid cluster: %s, but expected 3 nodes",
                        corfuClusterParams.getClusterNodes()
                )
                .isEqualTo(3);


        final int count = 100;
        final List<IdMessage> uuids = new ArrayList<>();
        final List<EventInfo> events = new ArrayList<>();

        helper.transactional((utils, txn) -> utils.generateData(0, count, uuids, events, txn, false));

        log.info("First Verification:: Verify table row count");
        helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count));
        log.info("First Verification:: Verify Table Data one by one");
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, 0, count, false));
        log.info("First Verification:: Completed");

        // Symmetrically partition all nodes and wait for failure
        // detector to work and cluster to stabilize
        List<CorfuApplicationServer> allServers = corfuCluster.nodes().values().asList();
        allServers.forEach(server -> {
            List<ApplicationServer<CorfuServerParams>> otherServers = new ArrayList<>(allServers);
            otherServers.remove(server);
            server.disconnect(otherServers);
        });

        waitUninterruptibly(Duration.ofSeconds(20));

        // Verify cluster and node status
        ClusterStatusReport clusterStatusReport = corfuClient
                .getManagementView()
                .getClusterStatus();
        assertThat(clusterStatusReport.getClusterStatus()).isEqualTo(ClusterStatus.STABLE);

        Map<String, NodeStatus> statusMap = clusterStatusReport.getClusterNodeStatusMap();
        corfuCluster.nodes()
                .values()
                .forEach(node -> assertThat(statusMap.get(node.getEndpoint())).isEqualTo(NodeStatus.UP));

        Map<String, ConnectivityStatus> connectivityMap = clusterStatusReport
                .getClientServerConnectivityStatusMap();

        corfuCluster.nodes().values().forEach(node -> {
            assertThat(connectivityMap.get(node.getEndpoint()))
                    .isEqualTo(ConnectivityStatus.RESPONSIVE);
        });

        // Remove partitions and wait for layout's unresponsive servers to change
        waitUninterruptibly(Duration.ofSeconds(10));
        corfuCluster.nodes().values().forEach(ApplicationServer::reconnect);

        waitForUnresponsiveServersChange(size -> size == 0, corfuClient);

        // Verify cluster status is STABLE
        log.info("Verify Check cluster status");
        waitForClusterStatusStable(corfuClient);

        helper.transactional((utils, txn) -> utils.generateData(100, count * 2, uuids, events, txn, false));

        log.info("Second Verification:: Verify table row count");
        helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count * 2));

        log.info("Second Verification:: Verify Table Data one by one");
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, 100, count * 2, false));
        log.info("Second Verification:: Completed");

        log.info("Update the records");
        helper.transactional((utils, txn) -> utils.generateData(60, 90, uuids, events, txn, true));

        log.info("Third Verification:: Verify the data");
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, 0, 60, false));
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, 91, count * 2, false));

        log.info("Third Verification:: Verify the updated data");
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, 60, 90, false));
        log.info("Third Verification:: Completed");

        helper.transactional(UfoUtils::clearTableAndVerify);

    }
}
