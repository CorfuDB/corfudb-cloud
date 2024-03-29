package org.corfudb.test.spec;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.ExampleSchemas.Uuid;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.TestSchema.ManagedResources;
import org.corfudb.test.spec.api.GenericSpec;
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
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusDegraded;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForLayoutChange;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForNextEpoch;

/**
 * Cluster deployment/shutdown for a stateful test (on demand):
 * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
 * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
 * <p>
 * Test cluster behavior after an unresponsive node becomes available (up) and at the same
 * time a previously responsive node starts to have two link failures. One of which to a
 * responsive node and the other to an unresponsive. This tests asserts that regardless of
 * equal number of observed link failures for each of nodes in the responsive set towards the
 * other responsive nodes, the node which also has the most number of link failures to the
 * unresponsive set (potentially healed) will be taken out. In other word, it makes sure that
 * we don't remove a responsive node in a way that eliminates the possibility of future healing
 * of unresponsive nodes.
 * <p>
 * Test cluster behavior after one paused and another node partitioned
 * 1) Deploy and bootstrap a three nodes cluster
 * 2) Create a table in corfu
 * 3) Add 100 Entries into table and verify count and data of table
 * 4) Stop one node
 * 5) Create two link failures between a responsive node with smaller endpoint name and the
 * * rest of the cluster AND restart the unresponsive node.
 * 6) Verify layout, cluster status and data path
 * 7) Add 100 more Entries into table and verify count and data of table
 * 8) Update Records from 51 to 150 index and verify
 * 9) Recover cluster by restart the paused node and fix partition
 * 10) Verify layout, cluster status and data path
 * 11) Add 100 more Entries into table and verify count and data of table
 * 12) Clear the table and verify table contents are cleared
 */
@Slf4j
public class NodeUpAndPartitionedSpec {

    /**
     * verifyNodeUpAndPartitioned
     *
     * @param wf universe workflow
     * @throws Exception error
     */
    public <P extends UniverseParams, F extends Fixture<P>, U extends UniverseWorkflow<P, F>> void nodeUpAndPartitioned(
            U wf) throws Exception {

        GenericCorfuCluster corfuCluster = wf.getUniverse().getGroup(ClusterType.CORFU);
        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        //Check CLUSTER STATUS
        log.info("**** Checking cluster status ****");
        waitForClusterStatusStable(corfuClient);

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore = new CorfuStore(runtime);

        // Define a namespace for the table.
        String manager = "manager";
        // Define table name
        String tableName = getClass().getSimpleName();

        final int count = 100;
        List<Uuid> uuids = new ArrayList<>();
        List<EventInfo> events = new ArrayList<>();
        ManagedResources metadata = ManagedResources.newBuilder()
                .setCreateUser("MrProto")
                .build();

        GenericSpec.SpecHelper helper = new GenericSpec.SpecHelper(runtime, tableName);

        // Add data in table (100 entries)
        log.info("**** Add 1st set of 100 entries ****");
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

        // Get all nodes of cluster in separate variables
        CorfuApplicationServer server0 = corfuCluster.getFirstServer();
        CorfuApplicationServer server1 = corfuCluster.getServerByIndex(1);
        CorfuApplicationServer server2 = corfuCluster.getServerByIndex(2);

        long currEpoch = corfuClient.getLayout().getEpoch();

        // Stop one node and partition another one
        log.info("**** Stop node server1 ****");
        server1.stop(Duration.ofSeconds(60));
        waitForNextEpoch(corfuClient, currEpoch + 1);
        log.info("**** Verify layout after server1 stopped ****");
        assertThat(corfuClient.getLayout().getUnresponsiveServers())
                .containsExactly(server1.getEndpoint());
        currEpoch++;

        // Partition the responsive server0 from both unresponsive server1
        // and responsive server2 and reconnect server 1. Wait for layout's unresponsive
        // servers to change After this, cluster becomes unavailable.
        // NOTE: cannot use waitForClusterDown() since the partition only happens on server side,
        // client can still connect to two nodes, write to table,
        // so system down handler will not be triggered.
        log.info("**** Disconnect node server0 ****");
        server0.disconnect(Arrays.asList(server1, server2));
        log.info("**** Start node server1 ****");
        server1.start();

        log.info("**** Verify layout after server0 disconnect and server1 start ****");
        waitForLayoutChange(l -> {
            List<String> unresponsive = l.getUnresponsiveServers();
            return unresponsive.size() == 1 && unresponsive.contains(server0.getEndpoint());
        }, corfuClient);
        currEpoch += 2;
        // Verify cluster status. Cluster status should be DEGRADED after one node is
        // marked unresponsive
        log.info("**** Verify cluster status is DEGRADED ****");
        waitForClusterStatusDegraded(corfuClient);

        waitForLayoutChange(l -> {
            List<String> unresponsive = l.getUnresponsiveServers();
            List<String> activeServers = l.getActiveLayoutServers();
            return unresponsive.size() == 1 && activeServers.size() == 2;
        }, corfuClient);

        // Add 100 more entries in table
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

        //Update table records from 51 to 150
        log.info("**** Update the records ****");
        helper.transactional((utils, txn) -> {
            utils.generateData(51, 150, uuids, events, txn, true);
        });

        helper.transactional((utils, txn) -> {
            utils.verifyTableRowCount(txn, count * 2);
            log.info("Third Insertion Verification:: Verify Table Data one by one");
            utils.verifyTableData(txn, 51, 150, true);
            log.info("Third Insertion Verified...");
        });

        // Reconnect the disconnected server
        log.info("**** Reconnect server0 ****");
        server0.reconnect(Arrays.asList(server1, server2));
        // Verify cluster status is STABLE
        log.info("**** Verify cluster status :: after pausing and disconnecting node ****");
        waitForClusterStatusStable(corfuClient);

        // Add 100 more entries in table
        log.info("**** Add 3rd set of 100 entries ****");
        helper.transactional((utils, txn) -> {
            utils.generateData(200, 300, uuids, events, txn, false);
        });
        helper.transactional((utils, txn) -> {
            // Verify table row count (should be 300)
            utils.verifyTableRowCount(txn, count * 3);
            log.info("Fourth Insertion Verification:: Verify Table Data one by one");

            // Verify all data in table
            utils.verifyTableData(txn, 0, 50, false);
            utils.verifyTableData(txn, 151, count * 3, false);
            utils.verifyTableData(txn, 51, 150, true);
            log.info("Fourth Insertion Verified...");
        });

        // Clear table data and verify
        log.info("Clear the Table");
        helper.transactional(UfoUtils::clearTableAndVerify);
    }
}
