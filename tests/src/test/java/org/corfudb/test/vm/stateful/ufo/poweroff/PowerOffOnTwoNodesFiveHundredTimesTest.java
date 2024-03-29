package org.corfudb.test.vm.stateful.ufo.poweroff;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.ExampleSchemas.Uuid;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestSchema;
import org.corfudb.test.spec.api.GenericSpec.SpecHelper;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.infrastructure.vm.universe.node.server.VmCorfuServer;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster.GenericCorfuCluster;
import org.corfudb.universe.universe.node.client.CorfuClient;
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
public class PowerOffOnTwoNodesFiveHundredTimesTest extends AbstractCorfuUniverseTest {
    private static final int LOOP_COUNT = 500;

    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
     * <p>
     * Test cluster behavior after power OFF/ON two nodes
     * 1)  Deploy and bootstrap a three nodes cluster
     * 2)  Create a table in corfu
     * 3)  Repeat the steps from (4 - 10) in loop, LOOP_COUNT i.e 500
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
        testRunner.executeStatefulVmTest(this::verifyVmOperations);
    }

    private void verifyVmOperations(UniverseWorkflow<UniverseParams, Fixture<UniverseParams>> wf)
            throws Exception {

        // create instance of Random class
        Random rand = new SecureRandom();
        int start = 0;
        int end;
        int count = 100;
        int rindex;
        boolean isTrue = false;

        GenericCorfuCluster corfuCluster = wf.getUniverse().getGroup(ClusterType.CORFU);
        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        //Check CLUSTER STATUS
        log.info("**** before running testcase, verify cluster status ****");
        waitForClusterStatusStable(corfuClient);
        log.info("*** cluster status is STABLE, executing testcase ***");

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore = new CorfuStore(runtime);


        // Define table name
        String tableName = getClass().getSimpleName();
        SpecHelper helper = new SpecHelper(runtime, tableName);
        List<Uuid> uuids = new ArrayList<>();
        List<TestSchema.EventInfo> events = new ArrayList<>();
        for (int lcount = 1; lcount <= LOOP_COUNT; lcount++) {
            // data insertion into the table
            end = count * lcount;
            log.info("*********************");
            log.info("**** required values like start::{}, end::{} and lcount::{} ****", start, end, lcount);
            log.info("*********************");
            log.info("**** insert the 100 enteries inot the table ****");
            int finalStart = start;
            int finalEnd = end;
            boolean finalIsTrue = isTrue;
            helper.transactional((utils, txn) -> utils.generateData(finalStart,
                    finalEnd, uuids, events, txn, finalIsTrue));

            if (lcount % 2 == 0) {
                isTrue = true;
                log.info("**** updating the records with start::{}, end::{} and lcount::{} ****", start, end, lcount);
                int tempStart = start;
                int tempEnd = end;
                boolean tempIsTrue = isTrue;
                helper.transactional((utils, txn) -> utils.generateData(tempStart,
                        tempEnd, uuids, events, txn, tempIsTrue));
            }

            // verification of table rows and it's content one by one
            log.info(String.format("**** verify the rows count that should be %s ****", count * lcount));
            int finalLcount = lcount;
            helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count * finalLcount));
            log.info(String.format("**** table has %s rows as expected ****", count * lcount));
            helper.transactional((utils, txn) -> utils.verifyTableData(txn, finalStart, finalEnd, finalIsTrue));// get the random node
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
            helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count * finalLcount));
            helper.transactional((utils, txn) -> utils.verifyTableData(txn, finalStart, finalEnd, finalIsTrue));
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
                log.info("*** required values like start::{}, end::{} and lcount::{}",
                        start, end, idx);
                int tempStart = start;
                int tempEnd = end;
                helper.transactional((utils, txn) -> utils.generateData(tempStart,
                        tempEnd, uuids, events, txn, true));
            } else {
                log.info("*** verifying non-updated data ***");
                log.info("*** required values like start::{}, end::{} and lcount::{}",
                        start, end, idx);
                int tempStart = start;
                int tempEnd = end;
                helper.transactional((utils, txn) -> utils.generateData(tempStart,
                        tempEnd, uuids, events, txn, false));
            }
            start = end;
        }

        log.info("*** clearing up the table contents ***");

        helper.transactional(UfoUtils::clearTableAndVerify);
    }
}

