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

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusDegraded;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;

@Slf4j
@Tag(TestGroups.BAT)
@Tag(TestGroups.STATEFUL)
public class StopFirstServerTest extends AbstractCorfuUniverseTest {
    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
     * <p>
     * Test cluster behavior after Stopping first server.
     * 1) Deploy and bootstrap a three nodes cluster
     * 2) Create a table in corfu
     * 3) Add 100 Entries into table and verify count and data of table
     * 4) Stop the first server
     * 5) Add 100 more Entries into table and verify count and data of table
     * 6) Verify layout, cluster status and data path
     * 7) Recover cluster by sequentially starting stopped node
     * 8) Verify layout, cluster status and data path again
     * 9) Update Records from 60 to 139 index and Verify
     * 10) Clear the table and verify table contents are cleared
     */
    @Test
    public void test() {
        testRunner.executeTest(this::verifyStopAndStartFirstNode);
    }

    private void verifyStopAndStartFirstNode(UniverseWorkflow<Fixture<UniverseParams>> wf)
            throws InterruptedException, NoSuchMethodException, IllegalAccessException,
            InvocationTargetException {

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
        List<TestSchema.IdMessage> uuids = new ArrayList<>();
        List<TestSchema.EventInfo> events = new ArrayList<>();
        TestSchema.ManagedResources metadata = TestSchema.ManagedResources.newBuilder()
                .setCreateUser("MrProto")
                .build();
        // Creating a transaction builder.
        final TxBuilder tx = corfuStore.tx(manager);

        // Fetch timestamp to perform snapshot queries or transactions at a particular timestamp.
        corfuStore.getTimestamp();

        // Add the entries again in Table
        UfoUtils.generateDataAndCommit(0, count, tableName, uuids, events, tx, metadata, false);
        Query q = corfuStore.query(manager);

        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count);

        log.info("First Insertion Verification:: Verify Table Data one by one");
        UfoUtils.verifyTableData(corfuStore, 0, count, manager, tableName, false);
        log.info("First Insertion Verified...");

        //Should stop one node and then restart
        CorfuServer server0 = corfuCluster.getFirstServer();

        //Stop one node and wait for layout's unresponsive servers to change
        server0.stop(Duration.ofSeconds(60));
        waitForUnresponsiveServersChange(size -> size == 1, corfuClient);

        log.info("Check cluster status in Degraded");
        waitForClusterStatusDegraded(corfuClient);

        // Add the entries again in Table after stopping first server
        UfoUtils.generateDataAndCommit(100, 200, tableName, uuids, events, tx, metadata, false);
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, 200);

        log.info("Second Insertion Verification:: Verify Table Data one by one");
        UfoUtils.verifyTableData(corfuStore, 0, 200, manager, tableName, false);
        log.info("Second Insertion Verified...");

        //Start the stopped node and wait for layout's unresponsive server to change
        server0.start();
        waitForUnresponsiveServersChange(size -> size == 0, corfuClient);

        // Verify cluster status is STABLE
        log.info("Verify cluster status at the end of test");
        waitForClusterStatusStable(corfuClient);

        //Update table records from 60 to 139
        log.info("Update the records");
        UfoUtils.generateDataAndCommit(60, 140, tableName, uuids, events, tx, metadata, true);
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count * 2);

        // Add the entries again in Table after cluster is back up
        log.info("Third Insertion Verification:: Verify Table Data one by one");
        UfoUtils.verifyTableData(corfuStore, 60, 140, manager, tableName, true);

        log.info("Clear the Table");
        UfoUtils.clearTableAndVerify(table, tableName, q);

    }
}