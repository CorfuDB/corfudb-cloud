package org.corfudb.test.vm.stateful.ufo.poweroff;

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
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.api.universe.node.ApplicationServers.CorfuApplicationServer;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.infrastructure.vm.universe.node.server.VmCorfuServer;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.ScenarioUtils;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster.GenericCorfuCluster;
import org.corfudb.universe.universe.node.client.ClientParams;
import org.corfudb.universe.universe.node.client.CorfuClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;
import static org.corfudb.universe.test.util.ScenarioUtils.waitUninterruptibly;

@Slf4j
@Tag(TestGroups.STATEFUL)
public class PowerOffOnThousandTimesInSingleNodeClusterTest extends AbstractCorfuUniverseTest {
    private static final int LOOP_COUNT = 1000;

    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
     * <p>
     * Test cluster behavior after power off/power on node in loop for 1000 times
     * 1) Deploy and bootstrap a three nodes cluster
     * 2) Create a table in corfu
     * 3) Add 100 Entries into table and verify count and data of table
     * 4) Detach nodes to create Single node cluster.
     * 5) Verify cluster status (should be STABLE)
     * 6) PowerOn/PowerOff the node and start corfu process on node
     * 7) Wait for Cluster status (should be STABLE) and add Entry into Table
     * 8) Repeat steps 6-7 1000 Times
     * 9) Verify count and data of table
     * 10) Add nodes to Cluster
     * 11) Verify cluster status (should be STABLE)
     * 12) Update Records from 60 to 139 index and Verify
     * 13) Clear the table and verify table contents are cleared
     */
    @Test
    public void test() {
        testRunner.executeStatefulVmTest(this::verifyPowerOnPowerOffNode);
    }

    private void verifyPowerOnPowerOffNode(UniverseWorkflow<UniverseParams, Fixture<UniverseParams>> wf)
            throws Exception {

        GenericCorfuCluster corfuCluster = wf.getUniverse().getGroup(ClusterType.CORFU);
        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();
        ClientParams clientFixture = ClientParams.builder().build();

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

        List<TestSchema.IdMessage> uuids = new ArrayList<>();
        List<TestSchema.EventInfo> events = new ArrayList<>();
        TestSchema.ManagedResources metadata = TestSchema.ManagedResources.newBuilder()
                .setCreateUser("MrProto")
                .build();
        // Creating a transaction builder.
        final TxBuilder tx = corfuStore.tx(manager);

        // Actual Testcase Starts and defining initial Row count for Table
        final int count = 100;

        log.info("**** Checking cluster status ****");
        waitForClusterStatusStable(corfuClient);

        log.info("**** Add 1st set of 100 entries ****");
        UfoUtils.generateDataAndCommit(0, count, tableName, uuids, events, tx, metadata, false);
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count);

        log.info("**** First Insertion Verification:: Verify Table Data one by one ****");
        UfoUtils.verifyTableData(corfuStore, 0, count, manager, tableName, false);
        log.info("**** First Insertion Verified... ****");

        CorfuApplicationServer server1 = corfuCluster.getServerByIndex(1);
        CorfuApplicationServer server2 = corfuCluster.getServerByIndex(2);

        log.info("**** Detach the second node from cluster ****");
        ScenarioUtils.detachNodeAndVerify(corfuClient, server1, clientFixture);

        log.info("**** Detach the third node from cluster ****");
        ScenarioUtils.detachNodeAndVerify(corfuClient, server2, clientFixture);

        // Loop poweroff/poweron on single node after detachment of other nodes
        for (int loopCount = 1; loopCount <= LOOP_COUNT; loopCount++) {
            log.info("**** In Loop :: " + loopCount + " ****");

            log.info("**** PowerOff Single node ****");
            VmCorfuServer vmServer = (VmCorfuServer) corfuCluster.getServerByIndex(0);
            vmServer.getVmManager().powerOff();

            log.info("**** PowerOn Single node ****");
            vmServer.getVmManager().powerOn();
            log.info("**** Start corfu process on Single node ****");
            vmServer.start();
            waitUninterruptibly(Duration.ofSeconds(30));
            waitForUnresponsiveServersChange(size -> size == 0, corfuClient);

            log.info("**** Wait for cluster status STABLE after restarting service ****");
            waitForClusterStatusStable(corfuClient);

            log.info("**** Add entry whilst in loop ****");
            UfoUtils.generateDataAndCommit(
                    (count + loopCount) - 1, count + loopCount, tableName,
                    uuids, events, tx, metadata, false
            );
        }
        log.info("**** Wait for cluster status STABLE after looped Restarts ****");
        waitForClusterStatusStable(corfuClient);

        log.info("**** Second Insertion Verification:: Verify Table Rows and Data one by one ****");
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count + LOOP_COUNT);
        UfoUtils.verifyTableData(corfuStore, count, count + LOOP_COUNT, manager, tableName, false);
        log.info("**** Second Insertion Verified... ****");

        log.info("**** Add the second node to the cluster ****");
        ScenarioUtils.addNodeAndVerify(corfuClient, server1, clientFixture);

        log.info("**** Add the third node to the cluster ****");
        ScenarioUtils.addNodeAndVerify(corfuClient, server2, clientFixture);

        log.info("**** Add last set of 100 entries ****");
        UfoUtils.generateDataAndCommit(
                count + LOOP_COUNT, count * 2 + LOOP_COUNT, tableName, uuids, events, tx, metadata, false
        );

        log.info("**** Third Insertion Verification:: Verify Table Rows and Data one by one ****");
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count * 2 + LOOP_COUNT);
        UfoUtils.verifyTableData(corfuStore, 0, count * 2 + LOOP_COUNT, manager, tableName, false);
        log.info("**** Third Insertion Verified... **** ");

        log.info("**** Update the records **** ");
        UfoUtils.generateDataAndCommit(60, 140, tableName, uuids, events, tx, metadata, true);

        log.info("**** Fourth Insertion Verification:: Verify Table Data one by one ****");
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count * 2 + LOOP_COUNT);
        UfoUtils.verifyTableData(corfuStore, 60, 140, manager, tableName, true);

        log.info("**** Clear the Table ****");
        Query q = corfuStore.query(manager);
        UfoUtils.clearTableAndVerify(table, tableName, q);
    }
}
