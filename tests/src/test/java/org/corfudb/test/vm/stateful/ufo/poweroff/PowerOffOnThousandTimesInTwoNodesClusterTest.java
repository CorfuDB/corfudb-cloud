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
import org.corfudb.universe.UniverseManager.UniverseWorkflow;
import org.corfudb.universe.group.cluster.CorfuCluster;
import org.corfudb.universe.node.client.ClientParams;
import org.corfudb.universe.node.client.CorfuClient;
import org.corfudb.universe.node.server.CorfuServer;
import org.corfudb.universe.node.server.vm.VmCorfuServer;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.ScenarioUtils;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.UniverseParams;
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
public class PowerOffOnThousandTimesInTwoNodesClusterTest extends AbstractCorfuUniverseTest {
    private static final int LOOP_COUNT = 1000;

    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
     * <p>
     * Test cluster behavior after power off/power on two nodes in loop for 1000 times
     * 1) Deploy and bootstrap a three nodes cluster
     * 2) Create a table in corfu
     * 3) Verify cluster status (should be STABLE)
     * 4) Add 100 Entries into table and verify count and data of table
     * 5) Detach node to create two nodes cluster
     * 6) PowerOn/PowerOff two nodes and start corfu process on two nodes
     * 7) Wait for Cluster status (should be STABLE)
     * 8) Add 100 more entries into Table
     * 9) Repeat steps 6-8 1000 Times
     * 9) Verify cluster status (should be STABLE)
     * 10) Verify count and data of table
     * 11) Add node to Cluster
     * 12) Add 100 more entries into Table and verify
     * 13) Update Records from 60 to 139 index and Verify
     * 14) Clear the table and verify table contents are cleared
     */
    @Test
    public void test() {
        testRunner.executeTest(this::verifyPowerOnPowerOffNode);
    }

    private void verifyPowerOnPowerOffNode(UniverseWorkflow<Fixture<UniverseParams>> wf)
            throws Exception {

        UniverseParams params = wf.getFixture().data();
        CorfuCluster corfuCluster = wf.getUniverse()
                .getGroup(params.getGroupParamByIndex(0).getName());
        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();
        ClientParams clientFixture = ClientParams.builder().build();

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore = new CorfuStore(runtime);

        // Define a namespace for the table.
        String manager = "manager";
        // Define table name
        String tableName = "CorfuUFO_PowerOffOnThousandTimesInTwoNodesClusterTable";

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

        log.info("**** Detach the node server2 from cluster ****");
        CorfuServer server2 = corfuCluster.getServerByIndex(2);
        ScenarioUtils.detachNodeAndVerify(corfuClient, server2, clientFixture);

        // Loop poweroff/poweron on two nodes after detachment of one node from cluster
        for (int loopCount = 1; loopCount <= LOOP_COUNT; loopCount++) {
            log.info("**** In Loop :: " + loopCount + " ****");

            for (int nodeIndex = 0; nodeIndex <= 1; nodeIndex++) {
                log.info(String.format("**** PowerOff node server%s ****", nodeIndex));
                VmCorfuServer server = (VmCorfuServer) corfuCluster.getServerByIndex(nodeIndex);
                server.getVmManager().powerOff();
                waitUninterruptibly(Duration.ofSeconds(5));
                log.info(String.format("**** PowerOn node server%s ****", nodeIndex));
                server.getVmManager().powerOn();
                log.info(String.format("**** Start corfu process on node %s ****", server.getIpAddress()));
                server.start();
            }
            waitUninterruptibly(Duration.ofSeconds(30));
            log.info("**** Verify unresponsive server count is 0 :: after power Off/On two nodes {} times ****",
                    loopCount);
            waitForUnresponsiveServersChange(size -> size == 0, corfuClient);
            log.info("**** Verify cluster status STABLE :: after power OFF/ON two nodes {} times ****", loopCount);
            waitForClusterStatusStable(corfuClient);

            log.info("**** Add entry whilst in loop ****");
            UfoUtils.generateDataAndCommit(
                    (count + loopCount) - 1, count + loopCount, tableName,
                    uuids, events, tx, metadata, false
            );
        }
        log.info("**** Verify cluster status STABLE :: after power OFF/ON two nodes {} times ****", LOOP_COUNT);
        waitForClusterStatusStable(corfuClient);

        log.info("**** Second Insertion Verification:: Verify Table Rows and Data one by one ****");
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count + LOOP_COUNT);
        UfoUtils.verifyTableData(corfuStore, count, count + LOOP_COUNT, manager, tableName, false);
        log.info("**** Second Insertion Verified... ****");

        log.info("**** Add the node server2 to the cluster ****");
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
