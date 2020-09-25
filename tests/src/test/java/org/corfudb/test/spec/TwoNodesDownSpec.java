package org.corfudb.test.spec;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.runtime.collections.Query;
import org.corfudb.runtime.collections.Table;
import org.corfudb.runtime.collections.TxBuilder;
import org.corfudb.runtime.view.ClusterStatusReport;
import org.corfudb.runtime.view.ClusterStatusReport.ClusterStatus;
import org.corfudb.runtime.view.ClusterStatusReport.ClusterStatusReliability;
import org.corfudb.runtime.view.ClusterStatusReport.ConnectivityStatus;
import org.corfudb.runtime.view.ClusterStatusReport.NodeStatus;
import org.corfudb.runtime.view.Layout;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.TestSchema.IdMessage;
import org.corfudb.test.TestSchema.ManagedResources;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster;
import org.corfudb.universe.universe.node.client.CorfuClient;
import org.corfudb.universe.universe.node.server.corfu.CorfuServer;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.api.universe.UniverseParams;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusUnavailable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForLayoutChange;

/**
 * Cluster deployment/shutdown for a stateful test (on demand):
 * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
 * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
 * <p>
 * Test cluster behavior after two nodes down.
 * 1) Deploy and bootstrap a three nodes cluster
 * 2) Create a table in corfu
 * 3) Add 100 Entries into table and verify count and data of table
 * 2) Sequentially stop two nodes
 * 3) Verify layout, cluster status and data path
 * 4) Recover cluster by sequentially restarting stopped nodes
 * 5) Verify layout, cluster status and data path again
 * 6) Add 100 more Entries into table and verify count and data of table
 * 7) Update Records from 60 to 139 index and Verify
 * 8) Clear the table and verify table contents are cleared
 */
@Slf4j
public class TwoNodesDownSpec {

    /**
     * verifyTwoNodesDown
     * @param wf universe workflow
     * @throws Exception error
     */
    public void verifyTwoNodesDown(UniverseWorkflow<UniverseParams, Fixture<UniverseParams>> wf) throws Exception {

        UniverseParams params = wf.getFixture().data();

        CorfuCluster corfuCluster = wf.getUniverse()
                .getGroup(params.getGroupParamByIndex(0).getName());

        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        //Check CLUSTER STATUS
        log.info("Check cluster status");
        waitForClusterStatusStable(corfuClient);

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore = new CorfuStore(runtime);

        // Define a namespace for the table.
        String manager = "manager";
        // Define table name
        String tableName = getClass().getSimpleName();

        // Create & Register the table.
        // This is required to initialize the table for the current corfu client.

        final Table<IdMessage, EventInfo, ManagedResources> table = UfoUtils.createTable(
                corfuStore, manager, tableName
        );

        final int count = 100;
        List<IdMessage> uuids = new ArrayList<>();
        List<EventInfo> events = new ArrayList<>();
        ManagedResources metadata = ManagedResources.newBuilder()
                .setCreateUser("MrProto")
                .build();
        // Creating a transaction builder.
        final TxBuilder tx = corfuStore.tx(manager);

        // Fetch timestamp to perform snapshot queries or transactions at a particular timestamp.
        corfuStore.getTimestamp();

        UfoUtils.generateDataAndCommit(0, count, tableName, uuids, events, tx, metadata, false);
        Query q = corfuStore.query(manager);

        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count);

        log.info("First Insertion Verification:: Verify Table Data one by one");
        UfoUtils.verifyTableData(corfuStore, 0, count, manager, tableName, false);
        log.info("First Insertion Verified...");

        //Should stop two nodes and then restart
        CorfuServer server0 = corfuCluster.getServerByIndex(0);
        CorfuServer server1 = corfuCluster.getServerByIndex(1);
        CorfuServer server2 = corfuCluster.getServerByIndex(2);

        // Sequentially stop two nodes
        server1.stop(Duration.ofSeconds(10));
        server2.stop(Duration.ofSeconds(10));

        // Verify cluster status is UNAVAILABLE with two node down and one node up
        corfuClient.invalidateLayout();
        ClusterStatusReport clusterStatusReport = corfuClient
                .getManagementView()
                .getClusterStatus();

        Map<String, NodeStatus> nodeStatusMap = clusterStatusReport.getClusterNodeStatusMap();
        Map<String, ConnectivityStatus> connectivityStatusMap = clusterStatusReport
                .getClientServerConnectivityStatusMap();
        ClusterStatusReliability reliability = clusterStatusReport.getClusterStatusReliability();

        assertThat(connectivityStatusMap.get(server0.getEndpoint()))
                .isEqualTo(ConnectivityStatus.RESPONSIVE);
        assertThat(connectivityStatusMap.get(server1.getEndpoint()))
                .isEqualTo(ConnectivityStatus.UNRESPONSIVE);
        assertThat(connectivityStatusMap.get(server2.getEndpoint()))
                .isEqualTo(ConnectivityStatus.UNRESPONSIVE);

        assertThat(nodeStatusMap.get(server0.getEndpoint())).isEqualTo(NodeStatus.NA);
        assertThat(nodeStatusMap.get(server1.getEndpoint())).isEqualTo(NodeStatus.NA);
        assertThat(nodeStatusMap.get(server2.getEndpoint())).isEqualTo(NodeStatus.NA);

        assertThat(clusterStatusReport.getClusterStatus()).isEqualTo(ClusterStatus.UNAVAILABLE);
        assertThat(reliability).isEqualTo(ClusterStatusReliability.WEAK_NO_QUORUM);

        // Wait for failure detector finds cluster is down before recovering
        log.info("Check cluster status in Unavailable");
        waitForClusterStatusUnavailable(corfuClient);

        // Sequentially restart two nodes and wait for layout's unresponsive servers to change
        server1.start();
        server2.start();

        Layout initialLayout = clusterStatusReport.getLayout();
        waitForLayoutChange(layout -> layout.getEpoch() > initialLayout.getEpoch()
                && layout.getUnresponsiveServers().size() == 0, corfuClient);

        // Verify cluster status is STABLE
        log.info("Verify cluster status at the end of test");
        waitForClusterStatusStable(corfuClient);

        // Add the entries again in Table after starting servers

        UfoUtils.generateDataAndCommit(100, 200, tableName, uuids, events, tx, metadata, false);
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, 200);

        log.info("Second Insertion Verification:: Verify Table Data one by one");
        UfoUtils.verifyTableData(corfuStore, 0, 200, manager, tableName, false);
        log.info("Second Insertion Verified...");

        //Update table records from 60 to 139
        log.info("Update the records");
        UfoUtils.generateDataAndCommit(60, 140, tableName, uuids, events, tx, metadata, true);
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count * 2);

        log.info("Third Insertion Verification:: Verify Table Data one by one");
        UfoUtils.verifyTableData(corfuStore, 60, 140, manager, tableName, true);

        log.info("Clear the Table");
        UfoUtils.clearTableAndVerify(table, tableName, q);
    }
}
