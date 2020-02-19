package org.corfudb.test.vm.stateful.ufo;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.runtime.collections.Query;
import org.corfudb.runtime.collections.Table;
import org.corfudb.runtime.collections.TxBuilder;
import org.corfudb.runtime.view.Layout;
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
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusDegraded;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForLayoutChange;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;
import static org.corfudb.universe.test.util.ScenarioUtils.waitUninterruptibly;

@Slf4j
@Tag(TestGroups.BAT)
@Tag(TestGroups.STATEFUL)
public class RotateLinkFailureTest extends AbstractCorfuUniverseTest {
    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
     * <p>
     * Test cluster behavior when rotating link failure among nodes
     * 1) Deploy and bootstrap a three nodes cluster
     * 2) Create a table in corfu
     * 3) Add 100 Entries into table and verify count and data of table
     * 4) Create a link failure between node0 and node1
     * 5) Create a link failure between node1 and node2
     * and heal previous link failure
     * 6) Create a link failure between node2 and node0
     * and heal previous link failure
     * 7) Reverse rotation direction, create a link failure
     * between node1 and node2 and heal previous link failure
     * 8) Verify layout and data path after each rotation
     * 9) Recover cluster by removing all link failures
     * 10) Verify layout, cluster status and data path
     * 11) Add 100 more Entries into table and verify count and data of table
     * 12) Update Records from 60 to 139 index and Verify
     * 13) Clear the table and verify table contents are cleared
     */

    @Test
    public void test() {
        testRunner.executeTest(this::verifyRotateLinkFailure);
    }

    private void verifyRotateLinkFailure(UniverseWorkflow<Fixture<UniverseParams>> wf) throws Exception {
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
        String tableName = "CorfuUFO_RotateLinkFailureTable";

        // Create & Register the table.
        // This is required to initialize the table for the current corfu client.

        final Table<IdMessage, EventInfo, ManagedResources> table = UfoUtils.createTable(
                corfuStore, manager, tableName
        );

        final int count = 100;
        List<TestSchema.IdMessage> uuids = new ArrayList<>();
        List<TestSchema.EventInfo> events = new ArrayList<>();
        TestSchema.ManagedResources metadata = TestSchema.ManagedResources.newBuilder()
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

        //Should rotate link failures among cluster
        CorfuServer server0 = corfuCluster.getServerByIndex(0);
        CorfuServer server1 = corfuCluster.getServerByIndex(1);
        CorfuServer server2 = corfuCluster.getServerByIndex(2);

        log.info("1st link failure rotation, disconnect between server0 and server1. Current layout: {}",
                corfuClient.getLayout());

        server0.disconnect(Collections.singletonList(server1));
        Predicate<Layout> checkServer1 = layout -> {
            List<String> expected = Collections.singletonList(server1.getEndpoint());
            return layout.getUnresponsiveServers().equals(expected);
        };
        waitForLayoutChange(checkServer1, corfuClient);

        log.info("Verify Cluster status is Degraded...");
        waitForClusterStatusDegraded(corfuClient);

        // Add the entries again in Table after restart

        UfoUtils.generateDataAndCommit(100, 200, tableName, uuids, events, tx, metadata, false);
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, 200);

        log.info("Second Insertion Verification:: Verify Table Data one by one");
        UfoUtils.verifyTableData(corfuStore, 0, 200, manager, tableName, false);
        log.info("Second Insertion Verified...");

        Layout latestLayout = corfuClient.getLayout();

        log.info("2nd link failure rotation, disconnect between server1 and server2 "
                + "and heal previous link failure between server0 and server1");
        server1.disconnect(Collections.singletonList(server2));
        server0.reconnect(Collections.singletonList(server1));

        log.info("Wait for some time to ensure cluster stabilizes Server1 should stay "
                + "in unresponsive set, no layout change");
        waitUninterruptibly(Duration.ofSeconds(30));
        assertThat(corfuClient.getLayout()).isEqualTo(latestLayout);

        log.info("Verify Cluster status is Degraded...");
        waitForClusterStatusDegraded(corfuClient);

        // Add the entries again in Table after restart

        UfoUtils.generateDataAndCommit(200, 300, tableName, uuids, events, tx, metadata, false);
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, 300);

        log.info("Third Insertion Verification:: Verify Table Data one by one");
        UfoUtils.verifyTableData(corfuStore, 0, 300, manager, tableName, false);
        log.info("Third Insertion Verified...");


        log.info("3rd link failure rotation, disconnect between server2 and server0 "
                + "and heal previous link failure between server1 and server2");
        server2.disconnect(Collections.singletonList(server0));
        server1.reconnect(Collections.singletonList(server2));

        log.info("Server0 and server2 has same number of link failure ie. 1, "
                + "the one with larger endpoint should be marked as unresponsive.");
        Predicate<Layout> checkServer2 = layout -> {
            List<String> expected = Collections.singletonList(server2.getEndpoint());
            return layout.getUnresponsiveServers().equals(expected);
        };
        waitForLayoutChange(checkServer2, corfuClient);

        log.info("Verify data path working fine");
        waitUninterruptibly(Duration.ofSeconds(20));

        log.info("Verify Cluster status is Degraded...");
        waitForClusterStatusDegraded(corfuClient);

        // Add the entries again in Table after restart

        UfoUtils.generateDataAndCommit(300, 400, tableName, uuids, events, tx, metadata, false);
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, 400);

        log.info("Fourth Insertion Verification:: Verify Table Data one by one");
        UfoUtils.verifyTableData(corfuStore, 0, 400, manager, tableName, false);
        log.info("Fourth Insertion Verified...");

        log.info("4th link failure rotation, reverse the rotating direction, "
                + "disconnect between server1 and server2 "
                + "and heal previous link failure between server1 and server2");
        server1.disconnect(Collections.singletonList(server2));
        server2.reconnect(Collections.singletonList(server0));

        log.info("Wait for some time to ensure cluster stabilizes "
                + "Server1 should stay in unresponsive set, no layout change");
        waitUninterruptibly(Duration.ofSeconds(30));

        log.info("Verify Cluster status is Degraded...");
        waitForClusterStatusDegraded(corfuClient);

        // Add the entries again in Table after restart

        UfoUtils.generateDataAndCommit(400, 500, tableName, uuids, events, tx, metadata, false);
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, 500);

        log.info("Fifth Insertion Verification:: Verify Table Data one by one");
        UfoUtils.verifyTableData(corfuStore, 0, 500, manager, tableName, false);
        log.info("Fifth Insertion Verified...");

        log.info("Finally stop rotation and heal all link failures.");
        server1.reconnect(Collections.singletonList(server2));
        waitForUnresponsiveServersChange(size -> size == 0, corfuClient);

        // Verify cluster status is STABLE
        log.info("Verify cluster status at the end of test");
        waitForClusterStatusStable(corfuClient);

        // Add the entries again in Table after restart

        UfoUtils.generateDataAndCommit(500, 600, tableName, uuids, events, tx, metadata, false);
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, 600);

        log.info("Sixth Insertion Verification:: Verify Table Data one by one");
        UfoUtils.verifyTableData(corfuStore, 0, 600, manager, tableName, false);
        log.info("Sixth Insertion Verified...");

        //Update table records from 60 to 139
        log.info("Update the records");
        UfoUtils.generateDataAndCommit(60, 140, tableName, uuids, events, tx, metadata, true);
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count * 6);

        log.info("Third Insertion Verification:: Verify Table Data one by one");
        UfoUtils.verifyTableData(corfuStore, 60, 140, manager, tableName, true);

        log.info("Clear the Table");
        UfoUtils.clearTableAndVerify(table, tableName, q);

    }
}