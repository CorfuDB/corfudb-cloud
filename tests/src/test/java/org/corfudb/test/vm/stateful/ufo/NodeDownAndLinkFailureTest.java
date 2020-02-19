package org.corfudb.test.vm.stateful.ufo;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.runtime.collections.Query;
import org.corfudb.runtime.collections.Table;
import org.corfudb.runtime.collections.TxBuilder;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.test.TestSchema;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.TestSchema.IdMessage;
import org.corfudb.test.TestSchema.ManagedResources;
import org.corfudb.universe.UniverseManager.UniverseWorkflow;
import org.corfudb.universe.group.cluster.CorfuCluster;
import org.corfudb.universe.node.client.CorfuClient;
import org.corfudb.universe.node.server.CorfuServer;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.UniverseParams;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusDegraded;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForNextEpoch;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;
import static org.corfudb.universe.test.util.ScenarioUtils.waitUninterruptibly;

@Slf4j
@Tag(TestGroups.BAT)
@Tag(TestGroups.STATEFUL)
public class NodeDownAndLinkFailureTest extends AbstractCorfuUniverseTest {
    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
     * <p>
     * Test cluster behavior after one node down and one link failure
     * 1) Deploy and bootstrap a three nodes cluster
     * 2) Create a table in corfu
     * 3) Add 100 Entries into table and verify count and data of table
     * 4) Stop one node
     * 5) Create a link failure between two nodes which results in a partial partition
     * 6) Verify layout, cluster status and data path
     * 7) Add 100 more Entries into table and verify count and data of table
     * 8) Update Records from 51 to 150 index and verify
     * 9) Start the stopped node
     * 10) Verify layout, cluster status and data path
     * 11) Remove the link failure
     * 12) Verify layout, cluster status and data path again
     * 13) Add 100 more Entries into table and verify count and data of table
     * 14) Clear the table and verify table contents are cleared
     */

    @Test
    public void test() {
        testRunner.executeTest(this::verifyNodeDownAndLinkFailure);
    }

    private void verifyNodeDownAndLinkFailure(UniverseWorkflow<Fixture<UniverseParams>> wf)
            throws Exception {
        UniverseParams params = wf.getFixture().data();
        CorfuCluster corfuCluster = wf.getUniverse()
                .getGroup(params.getGroupParamByIndex(0).getName());
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
        String tableName = "CorfuUFO_NodeDownAndLinkFailureTable";

        // Create & Register the table.
        // This is required to initialize the table for the current corfu client.
        final Table<IdMessage, EventInfo, ManagedResources> table = UfoUtils.createTable(
                corfuStore, manager, tableName
        );

        final int count = 100;
        List<TestSchema.IdMessage> uuids = new ArrayList<>();
        List<EventInfo> events = new ArrayList<>();
        TestSchema.ManagedResources metadata = TestSchema.ManagedResources.newBuilder()
                .setCreateUser("MrProto")
                .build();
        // Creating a transaction builder.
        final TxBuilder tx = corfuStore.tx(manager);

        // Add data in table (100 entries)
        log.info("**** Add 1st set of 100 entries ****");
        UfoUtils.generateDataAndCommit(0, count, tableName, uuids, events, tx, metadata, false);

        // Verify table row count (should be 100)
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count);
        log.info("**** First Insertion Verification:: Verify Table Data one by one ****");
        UfoUtils.verifyTableData(corfuStore, 0, count, manager, tableName, false);
        log.info("**** First Insertion Verified... ****");

        // Get all nodes of cluster in separate variables
        CorfuServer server0 = corfuCluster.getServerByIndex(0);
        CorfuServer server1 = corfuCluster.getServerByIndex(1);
        CorfuServer server2 = corfuCluster.getServerByIndex(2);

        long currEpoch = corfuClient.getLayout().getEpoch();

        log.info("**** Stop server2 ****");
        server2.stop(Duration.ofSeconds(10));
        log.info("**** Wait for layout's unresponsive servers to change ****");
        waitForNextEpoch(corfuClient, currEpoch + 1);
        assertThat(corfuClient.getLayout().getUnresponsiveServers()).containsExactly(server2.getEndpoint());

        // Create link failure between server0 and server1
        // After this, cluster becomes unavailable.
        // NOTE: cannot use waitForClusterDown() since the partition only happens on server side, client
        // can still connect to two nodes, write to table so system down handler will not be triggered.
        log.info("**** Create link failure between server0 and server1 ****");
        server0.disconnect(Collections.singletonList(server1));

        // Add 100 more entries in table
        log.info("**** Add 2nd set of 100 entries ****");
        UfoUtils.generateDataAndCommit(100, 200, tableName, uuids, events, tx, metadata, false);
        // Verify table row count (should be 200)
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, 200);
        log.info("**** Second Insertion Verification:: Verify Table Data one by one ****");
        UfoUtils.verifyTableData(corfuStore, 0, 200, manager, tableName, false);
        log.info("**** Second Insertion Verified... ****");
        //Update table records from 51 to 150
        log.info("**** Update the records ****");
        UfoUtils.generateDataAndCommit(51, 150, tableName, uuids, events, tx, metadata, true);
        // Verify table row count (should be 200)
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count * 2);
        log.info("**** Record Updation Verification:: Verify Table Data one by one ****");
        UfoUtils.verifyTableData(corfuStore, 51, 150, manager, tableName, true);
        log.info("**** Record Updation Verified ****");

        // Restart the stopped node
        log.info("**** Restart the stopped on node server2 ****");
        server2.start();

        log.info("**** Wait for cluster status become DEGRADED ****");
        waitUninterruptibly(Duration.ofSeconds(30));
        waitForClusterStatusDegraded(corfuClient);

        log.info("**** Repair the partition between server0 and server1 ****");
        server0.reconnect(Collections.singletonList(server1));
        waitForUnresponsiveServersChange(size -> size == 0, corfuClient);
        // Verify cluster status is STABLE
        waitForClusterStatusStable(corfuClient);

        // Add 100 more entries in table
        log.info("**** Add 3rd set of 100 entries ****");
        UfoUtils.generateDataAndCommit(200, 300, tableName, uuids, events, tx, metadata, false);
        // Verify table row count (should be 300)
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count * 3);

        // Verify all data in table
        log.info("**** Third Insertion Verification: Verify Table Data one by one ****");
        UfoUtils.verifyTableData(corfuStore, count * 2, count * 3, manager, tableName, false);
        UfoUtils.verifyTableData(corfuStore, 151, count * 3, manager, tableName, false);
        UfoUtils.verifyTableData(corfuStore, 51, 150, manager, tableName, true);

        // Clear table data and verify
        Query queryObj = corfuStore.query(manager);
        UfoUtils.clearTableAndVerify(table, tableName, queryObj);
    }
}
