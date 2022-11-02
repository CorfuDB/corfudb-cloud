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
import static org.corfudb.universe.test.util.ScenarioUtils.waitUninterruptibly;

@Slf4j
@Tag(TestGroups.STATEFUL)
public class RestartServiceOneHundredTimesInSingleNodeClusterTest extends AbstractCorfuUniverseTest {
    private static final int LOOP_COUNT = 100;

    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
     * <p>
     * Test cluster behavior after restart service on one node in loop for 100 times
     * 1) Deploy and bootstrap a three nodes cluster
     * 2) Create a table in corfu
     * 3) Add 100 Entries into table and verify count and data of table
     * 4) Detach nodes to create Single node cluster.
     * 5) Verify cluster status (should be STABLE)
     * 6) Restart the service
     * 7) Wait for Cluster status ( should be STABLE) and add Entry into Table
     * 8) Repeat steps 6-7 100 Times
     * 9) Verify count and data of table
     * 10) Add nodes to Cluster
     * 11) Verify cluster status (should be STABLE)
     * 12) Update Records from 60 to 139 index and Verify
     * 13) Clear the table and verify table contents are cleared
     */
    @Test
    public void test() {
        testRunner.executeStatefulVmTest(this::verifyRestartService);
    }

    private void verifyRestartService(UniverseWorkflow<UniverseParams, Fixture<UniverseParams>> wf)
            throws Exception {

        GenericCorfuCluster corfuCluster = wf.getUniverse().getGroup(ClusterType.CORFU);
        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();
        ClientParams clientFixture = ClientParams.builder().build();

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore = new CorfuStore(runtime);


        // Define table name
        String tableName = getClass().getSimpleName();
        SpecHelper helper = new SpecHelper(runtime, tableName);
        List<Uuid> uuids = new ArrayList<>();
        List<TestSchema.EventInfo> events = new ArrayList<>();
// Actual Testcase Starts and defining initial Row count for Table
        final int count = 100;

        log.info("**** Checking cluster status ****");
        waitForClusterStatusStable(corfuClient);

        log.info("**** Add 1st set of 100 entries ****");
        helper.transactional((utils, txn) -> utils.generateData(0, count, uuids, events, txn, false));
        helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count));

        log.info("**** First Insertion Verification:: Verify Table Data one by one ****");
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, 0, count, false));
        log.info("**** First Insertion Verified... ****");

        CorfuApplicationServer server0 = corfuCluster.getFirstServer();
        CorfuApplicationServer server1 = corfuCluster.getServerByIndex(1);
        CorfuApplicationServer server2 = corfuCluster.getServerByIndex(2);

        log.info("**** Detach the second node from cluster ****");
        ScenarioUtils.detachNodeAndVerify(corfuClient, server1, clientFixture);

        log.info("**** Detach the third node from cluster ****");
        ScenarioUtils.detachNodeAndVerify(corfuClient, server2, clientFixture);

        // Loop kill service on single node after detachment of other nodes
        for (int loopCount = 1; loopCount <= LOOP_COUNT; loopCount++) {
            log.info("**** In Loop :: " + loopCount + " ****");

            log.info("**** Restart service on Single node ****");
            server0.restart();
            waitUninterruptibly(Duration.ofSeconds(30));

            log.info("**** Wait for cluster status STABLE after restarting service ****");
            waitForClusterStatusStable(corfuClient);

            log.info("**** Add entry whilst in loop ****");
            int finalLoopCount = loopCount;
            helper.transactional((utils, txn) -> utils.generateData((count + finalLoopCount) - 1,
                    count + finalLoopCount, uuids, events, txn, false));
        }
        log.info("**** Wait for cluster status STABLE after looped Restarts ****");
        waitForClusterStatusStable(corfuClient);

        log.info("**** Second Insertion Verification:: Verify Table Rows and Data one by one ****");
        helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count + LOOP_COUNT));
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, count, count + LOOP_COUNT, false));
        log.info("**** Second Insertion Verified... ****");

        log.info("**** Add the second node to the cluster ****");
        ScenarioUtils.addNodeAndVerify(corfuClient, server1, clientFixture);

        log.info("**** Add the third node to the cluster ****");
        ScenarioUtils.addNodeAndVerify(corfuClient, server2, clientFixture);

        log.info("**** Add last set of 100 entries ****");
        helper.transactional((utils, txn) -> utils.generateData(count + LOOP_COUNT,
                count * 2 + LOOP_COUNT, uuids, events, txn, false));

        log.info("**** Third Insertion Verification:: Verify Table Rows and Data one by one ****");
        helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count * 2 + LOOP_COUNT));
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, 0, count * 2 + LOOP_COUNT, false));
        log.info("**** Third Insertion Verified... **** ");

        log.info("**** Update the records **** ");
        helper.transactional((utils, txn) -> utils.generateData(60, 140, uuids, events, txn, true));

        log.info("**** Fourth Insertion Verification:: Verify Table Data one by one ****");
        helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count * 2 + LOOP_COUNT));
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, 60, 140, true));

        log.info("**** Clear the Table ****");

        helper.transactional(UfoUtils::clearTableAndVerify);
    }
}
