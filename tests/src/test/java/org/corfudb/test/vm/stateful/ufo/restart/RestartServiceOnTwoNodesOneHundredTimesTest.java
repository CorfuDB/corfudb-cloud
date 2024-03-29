package org.corfudb.test.vm.stateful.ufo.restart;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.ExampleSchemas.Uuid;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.test.TestSchema;
import org.corfudb.test.spec.api.GenericSpec.SpecHelper;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.api.universe.node.ApplicationServers.CorfuApplicationServer;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster.GenericCorfuCluster;
import org.corfudb.universe.universe.node.client.CorfuClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;
import static org.corfudb.universe.test.util.ScenarioUtils.waitUninterruptibly;

@Slf4j
@Tag(TestGroups.STATEFUL)
public class RestartServiceOnTwoNodesOneHundredTimesTest extends AbstractCorfuUniverseTest {
    private static final int LOOP_COUNT = 100;

    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
     * <p>
     * Test cluster behavior after service restarted on two nodes 100 times
     * 1) Deploy and bootstrap a three nodes cluster
     * 2) Verify Cluster is stable after deployment
     * 3) Create a table in corfu
     * 4) Repeat the steps from (5 - 9) in loop, LOOP_COUNT i.e 100
     * 5) Add 100 Entries into table
     * 6) if (LOOP_COUNT mod 2) equals to 0 then update the table entries
     * 7) Verify the table rows and its contents
     * 8) Restart the "corfu" service on two nodes (rindex node and 3rd node)
     * 9) Again verify the table rows and its contents
     * 10) Once the loop is over, verify all data
     * 11) clear the table contents
     */
    @Test
    public void test() {
        testRunner.executeStatefulVmTest(this::verifyRestartService);
    }

    private void verifyRestartService(UniverseWorkflow<UniverseParams, Fixture<UniverseParams>> wf)
            throws InterruptedException, NoSuchMethodException, IllegalAccessException,
            InvocationTargetException {

        // create instance of Random class
        Random rand = new SecureRandom();
        int start;
        int end;
        int rindex;
        start = 0;
        boolean isTrue = false;

        GenericCorfuCluster corfuCluster = wf.getUniverse().getGroup(ClusterType.CORFU);

        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore = new CorfuStore(runtime);


        // Define table name
        String tableName = getClass().getSimpleName();
        SpecHelper helper = new SpecHelper(runtime, tableName);//Check CLUSTER STATUS
        log.info("**** Checking cluster status ****");
        waitForClusterStatusStable(corfuClient);


        final int count = 100;
        List<Uuid> uuids = new ArrayList<>();
        List<TestSchema.EventInfo> events = new ArrayList<>();
// Loop for 100 times restart service on two nodes serially
        for (int loopCount = 1; loopCount <= LOOP_COUNT; loopCount++) {

            // data insertion into the table
            end = count * loopCount;
            log.info(String.format("**** Required values like start::%s, end::%s and loopCount::%s ****",
                    start, end, loopCount));
            log.info("**** Insert the 100 enteries into the table ****");
            int finalStart = start;
            int finalEnd = end;
            boolean finalIsTrue = isTrue;
            helper.transactional((utils, txn) -> utils.generateData(finalStart,
                    finalEnd, uuids, events, txn, finalIsTrue));

            if (loopCount % 2 == 0) {
                isTrue = true;
                log.info(String.format("**** Updating the records with start::%s, end::%s and loopCount::%s ****",
                        start, end, loopCount));
                int tempStart = start;
                int tempEnd = end;
                boolean tempIsTrue = isTrue;
                helper.transactional((utils, txn) -> utils.generateData(tempStart,
                        tempEnd, uuids, events, txn, tempIsTrue));
            }

            // verification of table rows and it's content one by one
            log.info(String.format("**** Verify the rows count that should be %s ****", count * loopCount));
            int finalLoopCount = loopCount;
            helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count * finalLoopCount));
            log.info(String.format("**** Table has %s rows as expected ****", count * loopCount));
            helper.transactional((utils, txn) -> utils.verifyTableData(txn, finalStart, finalEnd, finalIsTrue));// Restart two nodes and wait for cluster become stable
            rindex = rand.nextInt(2);
            CorfuApplicationServer server = corfuCluster.getServerByIndex(rindex);
            log.info(String.format("**** Restarting server%s ****", rindex));
            server.restart();
            CorfuApplicationServer server2 = corfuCluster.getServerByIndex(2);
            log.info("**** Restarting server2 ****");
            server2.restart();
            waitForUnresponsiveServersChange(size -> size == 0, corfuClient);
            waitUninterruptibly(Duration.ofSeconds(30));
            log.info("**** Wait for cluster status STABLE :: after restarting service on all servers ****");
            waitForClusterStatusStable(corfuClient);

            // verification of table rows and it's content one by one
            helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count * finalLoopCount));
            helper.transactional((utils, txn) -> utils.verifyTableData(txn, finalStart, finalEnd, finalIsTrue));
            log.info(String.format("**** %s :: verification done ****", loopCount));

            start = end;
            isTrue = false;
        }

        log.info("**** Verifying the entire table contents one by one ****");
        start = 1;
        for (int idx = 1; idx < LOOP_COUNT; idx++) {
            end = count * idx;
            if (idx % 2 == 0) {
                log.info("**** Verifying updated data ****");
                log.info(String.format("**** Required values like start::%s, end::%s and loopCount::%s ****",
                        start, end, idx));
                int tempStart = start;
                int tempEnd = end;
                helper.transactional((utils, txn) -> utils.generateData(tempStart,
                        tempEnd, uuids, events, txn, true));
            } else {
                log.info("**** Verifying non-updated data ****");
                log.info(String.format("**** Required values like start::%s, end::%s and loopCount::%s ****",
                        start, end, idx));
                int tempStart = start;
                int tempEnd = end;
                helper.transactional((utils, txn) -> utils.generateData(tempStart,
                        tempEnd, uuids, events, txn, false));
            }
            start = end;
        }

        // Clear table data and verify
        log.info("**** Clear all data from table ****");

        helper.transactional(UfoUtils::clearTableAndVerify);
    }
}
