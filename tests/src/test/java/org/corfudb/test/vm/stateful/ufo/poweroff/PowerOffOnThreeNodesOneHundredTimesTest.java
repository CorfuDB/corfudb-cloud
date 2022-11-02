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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;
import static org.corfudb.universe.test.util.ScenarioUtils.waitUninterruptibly;

@Slf4j
public class PowerOffOnThreeNodesOneHundredTimesTest extends AbstractCorfuUniverseTest {
    private static final int LOOP_COUNT = 100;

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
        testRunner.executeStatefulVmTest(this::verifyVmOperations);
    }

    private void verifyVmOperations(UniverseWorkflow<UniverseParams, Fixture<UniverseParams>> wf)
            throws Exception {

        int start;
        int end;
        int count;
        end = count = 100;
        start = 0;
        boolean isTrue = false;

        GenericCorfuCluster corfuCluster = wf.getUniverse().getGroup(ClusterType.CORFU);
        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        //Check CLUSTER STATUS
        log.info("**** before running testcase, verify cluster status ****");
        waitForClusterStatusStable(corfuClient);
        log.info("**** cluster status is STABLE, executing testcase ****");

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
            log.info("**** insert the 100 enteries not the table ****");
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
            helper.transactional((utils, txn) -> utils.verifyTableData(txn, finalStart, finalEnd, finalIsTrue));// Loop for all 3 nodes power OFF/ON
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
            helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count * finalLcount));
            helper.transactional((utils, txn) -> utils.verifyTableData(txn, finalStart, finalEnd, finalIsTrue));
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
                int tempStart = start;
                int tempEnd = end;
                helper.transactional((utils, txn) -> utils.generateData(tempStart,
                        tempEnd, uuids, events, txn, true));
            } else {
                log.info("**** verifying non-updated data ****");
                log.info("**** required values like start::{}, end::{} and lcount::{} ****", start, end, idx);
                int tempStart = start;
                int tempEnd = end;
                helper.transactional((utils, txn) -> utils.generateData(tempStart,
                        tempEnd, uuids, events, txn, false));
            }
            start = end;
        }

        log.info("**** clearing up the table contents ****");

        helper.transactional(UfoUtils::clearTableAndVerify);
    }
}

