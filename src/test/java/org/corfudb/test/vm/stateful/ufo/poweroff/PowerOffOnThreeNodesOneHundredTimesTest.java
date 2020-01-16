package org.corfudb.test.vm.stateful.ufo.poweroff;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.runtime.collections.Query;
import org.corfudb.runtime.collections.Table;
import org.corfudb.runtime.collections.TxBuilder;
import org.corfudb.test.TestSchema;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.TestSchema.IdMessage;
import org.corfudb.test.TestSchema.ManagedResources;
import org.corfudb.universe.UniverseManager;
import org.corfudb.universe.group.cluster.CorfuCluster;
import org.corfudb.universe.node.client.CorfuClient;
import org.corfudb.universe.node.server.vm.VmCorfuServer;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.UniverseConfigurator;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.UniverseParams;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;
import static org.corfudb.universe.test.util.ScenarioUtils.waitUninterruptibly;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
public class PowerOffOnThreeNodesOneHundredTimesTest {
    private static final int LOOP_COUNT = 100;
    private final UniverseConfigurator configurator = UniverseConfigurator.builder().build();
    private final UniverseManager universeManager = configurator.universeManager;

    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
     * <p>
     * Test cluster behavior after power OFF/ON all three nodes
     * 1)  Deploy and bootstrap a three nodes cluster
     * 2)  Create a table in corfu
     * 3)  Repeat the steps from (4 - 9) in loop, LOOP_COUNT i.e 100
     * 4)  Add 100 Entries into table
     * 5)  if (LOOP_COUNT mod 2) equals to 0 then update the table entries
     * 6)  Verify the table rows and its contents
     * 7)  Power Off and power ON all three nodes one by one
     * 8)  Verify cluster status become "STABLE"
     * 9)  Verify the table rows and its contents
     * 10) Once the loop is over, verify all data
     * 11) Clear the table contents
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
                verifyVmOperations(wf);
            } catch (Exception e) {
                fail("Failed", e);
            }
        });
    }

    private void verifyVmOperations(UniverseManager.UniverseWorkflow<Fixture<UniverseParams>> wf)
            throws Exception {

        int start;
        int end;
        int count;
        end = count = 100;
        start = 0;
        boolean isTrue = false;

        UniverseParams params = wf.getFixture().data();
        CorfuCluster corfuCluster = wf.getUniverse()
                .getGroup(params.getGroupParamByIndex(0).getName());
        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        //Check CLUSTER STATUS
        log.info("**** before running testcase, verify cluster status ****");
        waitForClusterStatusStable(corfuClient);
        log.info("**** cluster status is STABLE, executing testcase ****");

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore = new CorfuStore(runtime);

        // Define a namespace for the table.
        String manager = "manager";
        // Define table name
        String tableName = "CorfuUFO_PowerOffOnThreeNodesOneHundredTimesTable";

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

        for (int lcount = 1; lcount <= LOOP_COUNT; lcount++) {
            // data insertion into the table
            end = count * lcount;
            log.info("*********************");
            log.info("**** required values like start::{}, end::{} and lcount::{} ****", start, end, lcount);
            log.info("*********************");
            log.info("**** insert the 100 enteries not the table ****");
            UfoUtils.generateDataAndCommit(start, end, tableName, uuids, events, tx, metadata, isTrue);

            if (lcount % 2 == 0) {
                isTrue = true;
                log.info("**** updating the records with start::{}, end::{} and lcount::{} ****", start, end, lcount);
                UfoUtils.generateDataAndCommit(start, end, tableName, uuids, events, tx, metadata, isTrue);
            }

            // verification of table rows and it's content one by one
            log.info(String.format("**** verify the rows count that should be %s ****", count * lcount));
            UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count * lcount);
            log.info(String.format("**** table has %s rows as expected ****", count * lcount));
            UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, isTrue);

            // Loop for all 3 nodes power OFF/ON
            for (int nodeIndex = 0; nodeIndex < 3; nodeIndex++) {
                // Power Off node
                log.info(String.format("**** PowerOff node :: server%s ****", nodeIndex));
                VmCorfuServer server = (VmCorfuServer) corfuCluster.getServerByIndex(nodeIndex);
                server.getVmManager().powerOff();

                // power ON node
                log.info(String.format("**** PowerON node :: server%s ****", nodeIndex));
                server.getVmManager().powerOn();
                log.info(String.format("**** Start corfu process on node %s ****", server.getIpAddress()));
                server.start();
            }
            waitUninterruptibly(Duration.ofSeconds(30));
            log.info("**** Checking cluster status :: after all nodes power Off/On ****");
            waitForUnresponsiveServersChange(size -> size == 0, corfuClient);
            waitForClusterStatusStable(corfuClient);

            // verification of table rows and it's content one by one
            UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count * lcount);
            UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, isTrue);
            log.info(String.format("**** %s :: verification done ****", lcount));

            start = end;
            isTrue = false;
        }

        log.info("**** at last, verifying the entire table contents one by one ****");
        start = 1;
        for (int idx = 1; idx <= LOOP_COUNT; idx++) {
            end = count * idx;
            if (idx % 2 == 0) {
                log.info("**** verifying updated data ****");
                log.info("**** required values like start::{}, end::{} and lcount::{} ****", start, end, idx);
                UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, true);
            } else {
                log.info("**** verifying non-updated data ****");
                log.info("**** required values like start::{}, end::{} and lcount::{} ****", start, end, idx);
                UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, false);
            }
            start = end;
        }

        log.info("**** clearing up the table contents ****");
        Query q = corfuStore.query(manager);
        UfoUtils.clearTableAndVerify(table, tableName, q);
    }
}

