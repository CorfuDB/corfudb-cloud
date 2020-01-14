package org.corfudb.test.vm.stateful.ufo;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.runtime.collections.Query;
import org.corfudb.runtime.collections.Table;
import org.corfudb.runtime.collections.TxBuilder;
import org.corfudb.test.TestGroups;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.TestSchema.IdMessage;
import org.corfudb.test.TestSchema.ManagedResources;
import org.corfudb.universe.UniverseManager;
import org.corfudb.universe.UniverseManager.UniverseWorkflow;
import org.corfudb.universe.group.cluster.CorfuCluster;
import org.corfudb.universe.node.client.ClientParams;
import org.corfudb.universe.node.client.CorfuClient;
import org.corfudb.universe.node.server.CorfuServer;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.UniverseConfigurator;
import org.corfudb.universe.test.util.ScenarioUtils;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.UniverseParams;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@Tag(TestGroups.STATEFUL)
public class CorfuUfoDetachCommandInLoopAndDataInsertionTest {
    private static final int LOOP_COUNT = 1000;
    private final UniverseConfigurator configurator = UniverseConfigurator.builder().build();
    private final UniverseManager universeManager = configurator.universeManager;

    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
     * <p>
     * Test cluster behavior after detach node/attach in loop for 1000 times
     * 1) Deploy and bootstrap a three nodes cluster
     * 2) Create a table in corfu
     * 3) Add 100 Entries into table and verify count and data of table
     * 4) Detach Node
     * 5) Wait for Cluster status ( should be STABLE) and add Entry into Table
     * 6) Attach Node
     * 7) Wait for Cluster status
     * 8) Repeat steps 6-7 1000 Times
     * 9) Verify count and data of table
     * 11) Verify cluster status (should be STABLE)
     * 12) Update Records from 60 to 139 index and Verify
     * 13) Clear the table and verify table contents are cleared
     */
    @Test
    public void test() {

        universeManager.workflow(wf -> {
            wf.setupVm(configurator.vmSetup);
            wf.setupVm(fixture -> {
                //don't stop corfu cluster after the test
                fixture.getUniverse().cleanUpEnabled(false);
            });
            wf.initUniverse();
            try {
                verifyDetachRejoin(wf);
            } catch (Exception e) {
                fail("Failed", e);
            }
        });
    }

    private void verifyDetachRejoin(UniverseWorkflow<Fixture<UniverseParams>> wf)
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
        String tableName = "CorfuUFO_DetachCommandInLoopAndDataInsertionTable";

        // Create & Register the table.
        // This is required to initialize the table for the current corfu client.
        final Table<IdMessage, EventInfo, ManagedResources> table = UfoUtils.createTable(
                corfuStore, manager, tableName
        );

        final List<IdMessage> uuids = new ArrayList<>();
        final List<EventInfo> events = new ArrayList<>();
        final ManagedResources metadata = ManagedResources.newBuilder()
                .setCreateUser("MrProto")
                .build();
        // Creating a transaction builder.
        final TxBuilder tx = corfuStore.tx(manager);

        // Actual Testcase Starts and defining initial Row count for Table
        Random rand = new SecureRandom();
        int rindex;
        final int count = 100;

        log.info("**** Checking cluster status ****");
        waitForClusterStatusStable(corfuClient);

        log.info("**** Add 1st set of 100 entries ****");
        UfoUtils.generateDataAndCommit(0, count, tableName, uuids, events, tx, metadata, false);
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count);

        log.info("**** First Insertion Verification:: Verify Table Data one by one ****");
        UfoUtils.verifyTableData(corfuStore, 0, count, manager, tableName, false);
        log.info("**** First Insertion Verified... ****");


        // Loop detach/rejoin node in loop
        for (int loopCount = 1; loopCount <= LOOP_COUNT; loopCount++) {
            log.info("**** In Loop :: " + loopCount + " ****");

            rindex = rand.nextInt(3);
            log.info(String.format(" *** Random Server value is: %s ***", rindex));
            CorfuServer server = corfuCluster.getServerByIndex(rindex);

            log.info("**** Detach the second node from cluster ****");
            ScenarioUtils.detachNodeAndVerify(corfuClient, server, clientFixture);

            log.info("**** Add entry whilst in loop ****");
            UfoUtils.generateDataAndCommit(
                    (count + loopCount) - 1, count + loopCount, tableName,
                    uuids, events, tx, metadata, false
            );

            log.info("**** Add the second node back to the cluster ****");
            ScenarioUtils.addNodeAndVerify(corfuClient, server, clientFixture);
        }
        log.info("**** Wait for cluster status STABLE after detach loop finishes ****");
        waitForClusterStatusStable(corfuClient);

        log.info("**** Second Insertion Verification:: Verify Table Rows and Data one by one ****");
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count + LOOP_COUNT);
        UfoUtils.verifyTableData(corfuStore, count, count + LOOP_COUNT, manager, tableName, false);
        log.info("**** Second Insertion Verified... ****");

        log.info("**** Add last set of 100 entries ****");
        UfoUtils.generateDataAndCommit(
                count + LOOP_COUNT, count * 2 + LOOP_COUNT, tableName,
                uuids, events, tx, metadata, false
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
