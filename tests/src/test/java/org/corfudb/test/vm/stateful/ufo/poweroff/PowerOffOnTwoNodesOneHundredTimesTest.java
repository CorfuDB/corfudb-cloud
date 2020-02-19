package org.corfudb.test.vm.stateful.ufo.poweroff;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.runtime.collections.Query;
import org.corfudb.runtime.collections.Table;
import org.corfudb.runtime.collections.TxBuilder;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestSchema;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.TestSchema.IdMessage;
import org.corfudb.test.TestSchema.ManagedResources;
import org.corfudb.universe.UniverseManager;
import org.corfudb.universe.group.cluster.CorfuCluster;
import org.corfudb.universe.node.client.CorfuClient;
import org.corfudb.universe.node.server.vm.VmCorfuServer;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.UniverseParams;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;
import static org.corfudb.universe.test.util.ScenarioUtils.waitUninterruptibly;

@Slf4j
public class PowerOffOnTwoNodesOneHundredTimesTest extends AbstractCorfuUniverseTest {
    private static final int LOOP_COUNT = 100;

    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
     * <p>
     * Test cluster behavior after power OFF/ON two nodes
     * 1)  Deploy and bootstrap a three nodes cluster
     * 2)  Create a table in corfu
     * 3)  Repeat the steps from (4 - 10) in loop, LOOP_COUNT i.e 100
     * 4)  Get node Index randomly to power On/Off two nodes
     * 5)  PowerOFF corfu VM (two nodes) wait till cluster goes into "DEGRADED" state
     * 6)  Add 100 Entries into table
     * 7)  if (LOOP_COUNT mod 2) equals to 0 then update the table entries
     * 8)  Verify the table rows and its contents
     * 9)  PowerON VM (two node)
     * 10) Start corfu process on two nodes and wait till cluster status become "STABLE"
     * 11) Verify the table rows and its contents
     * 12) Once the loop is over, verify all data
     * 13) Clear the table contents
     */
    @Test
    public void test() {
        testRunner.executeTest(this::verifyVmOperations);
    }

    private void verifyVmOperations(UniverseManager.UniverseWorkflow<Fixture<UniverseParams>> wf)
            throws Exception {

        // create instance of Random class
        Random rand = new SecureRandom();
        int start = 0;
        int end;
        int count = 100;
        int rindex;
        boolean isTrue = false;

        UniverseParams params = wf.getFixture().data();
        CorfuCluster corfuCluster = wf.getUniverse()
                .getGroup(params.getGroupParamByIndex(0).getName());
        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        //Check CLUSTER STATUS
        log.info("**** before running testcase, verify cluster status ****");
        waitForClusterStatusStable(corfuClient);
        log.info("*** cluster status is STABLE, executing testcase ***");

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore = new CorfuStore(runtime);

        // Define a namespace for the table.
        String manager = "manager";
        // Define table name
        String tableName = "CorfuUFO_PowerOffOnOneNodeOneHundredTimesTable";

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
            log.info("**** insert the 100 enteries inot the table ****");
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

            // get the random node
            rindex = rand.nextInt(2);
            log.info(String.format(" **** rindex value is: %s ****", rindex));
            // power off two nodes and wait for layout's unresponsive servers to change & cluster to become UNAVAILABLE
            log.info(String.format("**** PowerOff node :: server%s ****", rindex));
            VmCorfuServer server = (VmCorfuServer) corfuCluster.getServerByIndex(rindex);
            server.getVmManager().powerOff();
            log.info("**** PowerOff node :: server2 ****");
            VmCorfuServer server2 = (VmCorfuServer) corfuCluster.getServerByIndex(2);
            server2.getVmManager().powerOff();

            log.info("**** Wait for 30 seconds after PowerOff nodes ****");
            waitUninterruptibly(Duration.ofSeconds(30));

            // power ON node and wait for cluster to become stable
            log.info(String.format("**** PowerON node :: server%s ****", rindex));
            server.getVmManager().powerOn();
            log.info("**** PowerON node :: server2 ****");
            server2.getVmManager().powerOn();
            log.info("**** Wait for 10 seconds before starting corfu process ****");
            waitUninterruptibly(Duration.ofSeconds(10));
            log.info(String.format("**** Start corfu process on node %s ****", server.getIpAddress()));
            server.start();
            log.info("**** Start corfu process on node :: server2 ****");
            server2.start();

            waitUninterruptibly(Duration.ofSeconds(30));
            waitForUnresponsiveServersChange(size -> size == 0, corfuClient);
            log.info(String.format("**** Checking cluster status :: after %s powerON ****", rindex));
            waitForClusterStatusStable(corfuClient);
            log.info(String.format("**** Cluster status STABLE :: after %s powerON ****", rindex));

            // verification of table rows and it's content one by one
            UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count * lcount);
            UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, isTrue);
            log.info(String.format("**** %s :: verification done ****", lcount));

            start = end;
            isTrue = false;
        }

        log.info(" *** at last, verifying the entire table contents one by one ***");
        start = 1;
        for (int idx = 1; idx <= LOOP_COUNT; idx++) {
            end = count * idx;
            if (idx % 2 == 0) {
                log.info("*** verifying updated data ***");
                log.info(String.format("*** required values like start::%s, end::%s and lcount::%s", start, end, idx));
                UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, true);
            } else {
                log.info("*** verifying non-updated data ***");
                log.info(String.format("*** required values like start::%s, end::%s and lcount::%s", start, end, idx));
                UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, false);
            }
            start = end;
        }

        log.info("*** clearing up the table contents ***");
        Query q = corfuStore.query(manager);
        UfoUtils.clearTableAndVerify(table, tableName, q);
    }
}

