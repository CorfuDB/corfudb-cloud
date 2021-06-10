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
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.TestSchema.IdMessage;
import org.corfudb.test.TestSchema.ManagedResources;
import org.corfudb.test.spec.api.GenericSpec;
import org.corfudb.test.spec.api.GenericSpec.SpecHelper;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.api.universe.node.ApplicationServers.CorfuApplicationServer;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster.GenericCorfuCluster;
import org.corfudb.universe.universe.node.client.CorfuClient;

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
     *
     * @param wf universe workflow
     * @throws Exception error
     */
    public <P extends UniverseParams, F extends Fixture<P>, U extends UniverseWorkflow<P, F>> void twoNodesDown(
            U wf) throws Exception {

        GenericCorfuCluster corfuCluster = wf.getUniverse().getGroup(ClusterType.CORFU);

        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        //Check CLUSTER STATUS
        log.info("Check cluster status");
        waitForClusterStatusStable(corfuClient);

        CorfuRuntime runtime = corfuClient.getRuntime();

        // Define a namespace for the table.
        String manager = "manager";
        // Define table name
        String tableName = getClass().getSimpleName();
        SpecHelper helper = new SpecHelper(runtime, tableName);

        final int count = 100;
        List<IdMessage> uuids = new ArrayList<>();
        List<EventInfo> events = new ArrayList<>();
        ManagedResources metadata = ManagedResources.newBuilder()
                .setCreateUser("MrProto")
                .build();

        // Fetch timestamp to perform snapshot queries or transactions at a particular timestamp.
        runtime.getSequencerView().query().getToken();

        helper.transactional((utils, txn) -> {
            // Add the entries in Table
            utils.generateData(0, count, uuids, events, txn, false);
        });

        helper.transactional((utils, txn) -> {
            utils.verifyTableRowCount(txn, count);
            log.info("First Insertion Verification:: Verify Table Data one by one");
            utils.verifyTableData(txn, 0, count, false);
            log.info("First Insertion Verified...");
        });

        //Should stop two nodes and then restart
        CorfuApplicationServer server0 = corfuCluster.getFirstServer();
        CorfuApplicationServer server1 = corfuCluster.getServerByIndex(1);
        CorfuApplicationServer server2 = corfuCluster.getServerByIndex(2);

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

        helper.transactional((utils, txn) -> {
            // Add the entries again in Table
            utils.generateData(100, 200, uuids, events, txn, false);
        });

        helper.transactional((utils, txn) -> {
            utils.verifyTableRowCount(txn, 200);
            log.info("Second Insertion Verification:: Verify Table Data one by one");
            utils.verifyTableData(txn, 0, 200, false);
            log.info("Second Insertion Verified...");
        });

        //Update table records from 60 to 139
        log.info("Update the records");
        helper.transactional((utils, txn) -> {
            utils.generateData(60, 140, uuids, events, txn, true);
        });
        helper.transactional((utils, txn) -> {
            utils.verifyTableRowCount(txn, count * 2);
            log.info("Third Insertion Verification:: Verify Table Data one by one");
            utils.verifyTableData(txn, 60, 140, true);
            log.info("Third Insertion Verified...");
        });

        log.info("Clear the Table");
        helper.transactional(UfoUtils::clearTableAndVerify);
    }
}
