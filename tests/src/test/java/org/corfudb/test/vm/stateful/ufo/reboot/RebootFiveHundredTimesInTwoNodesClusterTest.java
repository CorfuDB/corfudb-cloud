package org.corfudb.test.vm.stateful.ufo.reboot;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.test.TestSchema;
import org.corfudb.test.spec.api.GenericSpec.SpecHelper;
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
import static org.corfudb.universe.test.util.ScenarioUtils.waitUninterruptibly;

@Slf4j
@Tag(TestGroups.STATEFUL)
public class RebootFiveHundredTimesInTwoNodesClusterTest extends AbstractCorfuUniverseTest {
    private static final int LOOP_COUNT = 500;

    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
     * <p>
     * Test cluster behavior after reboot two nodes in loop for 500 times
     * 1) Deploy and bootstrap a three nodes cluster
     * 2) Create a table in corfu
     * 3) Verify cluster status (should be STABLE)
     * 4) Add 100 Entries into table and verify count and data of table
     * 5) Detach a node to create two nodes cluster
     * 6) Reboot the nodes, then wait for 30sec before starting the 'corfu' process
     * 7) Wait for Cluster status (should be STABLE)
     * 8) Add Entry into Table
     * 9) Repeat steps 6-8 500 Times
     * 10) Verify cluster status (should be STABLE)
     * 11) Add 100 more entries into Table and verify count and data of table
     * 12) Add node to Cluster
     * 13) Add 100 more entries into Table and verify
     * 14) Update Records from 60 to 139 index and Verify
     * 15) Clear the table and verify table contents are cleared
     */
    @Test
    public void test() {
        testRunner.executeStatefulVmTest(this::verifyRebootNode);
    }

    private void verifyRebootNode(UniverseWorkflow<UniverseParams, Fixture<UniverseParams>> wf)
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
        List<TestSchema.IdMessage> uuids = new ArrayList<>();
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
        log.info("**** Detach the node server0 from cluster ****");
        ScenarioUtils.detachNodeAndVerify(corfuClient, server0, clientFixture);

        // Loop reboot node after detachment of other nodes
        for (int loopCount = 1; loopCount <= LOOP_COUNT; loopCount++) {
            log.info("**** In Loop :: " + loopCount + " ****");

            for (int nodeIndex = 1; nodeIndex <= 2; nodeIndex++) {
                VmCorfuServer server = (VmCorfuServer) corfuCluster.getServerByIndex(nodeIndex);
                log.info(String.format("**** Rebooting node:: %s ****", server.getIpAddress()));
                server.getVmManager().reboot();
            }

            log.info(" *** wait for 30sec, after rebooting the node ***");
            waitUninterruptibly(Duration.ofSeconds(30));

            for (int nodeIndex = 1; nodeIndex <= 2; nodeIndex++) {
                VmCorfuServer vmServer = (VmCorfuServer) corfuCluster.getServerByIndex(nodeIndex);
                log.info(String.format(" *** starting 'corfu' process on node:: %s ***", vmServer.getIpAddress()));
                vmServer.start();
            }

            log.info("**** Verify cluster status STABLE :: after rebooting two nodes ****");
            waitForClusterStatusStable(corfuClient);

            log.info("**** Add entry whilst in loop ****");
            int finalLoopCount = loopCount;
            helper.transactional((utils, txn) -> utils.generateData((count + finalLoopCount) - 1,
                    count + finalLoopCount, uuids, events, txn, false));
        }
        log.info("**** Check cluster status STABLE :: after rebooting two nodes {} times ****", LOOP_COUNT);
        waitForClusterStatusStable(corfuClient);

        log.info("**** Second Insertion Verification:: Verify Table Rows and Data one by one ****");
        helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count + LOOP_COUNT));
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, count, count + LOOP_COUNT, false));
        log.info("**** Second Insertion Verified... ****");

        log.info("**** Add the node server0 to the cluster ****");
        ScenarioUtils.addNodeAndVerify(corfuClient, server0, clientFixture);

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
