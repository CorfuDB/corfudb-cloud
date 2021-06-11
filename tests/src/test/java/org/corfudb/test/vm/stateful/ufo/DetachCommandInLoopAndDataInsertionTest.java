package org.corfudb.test.vm.stateful.ufo;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.TestSchema.IdMessage;
import org.corfudb.test.TestSchema.ManagedResources;
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

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;

@Slf4j
@Tag(TestGroups.STATEFUL)
public class DetachCommandInLoopAndDataInsertionTest extends AbstractCorfuUniverseTest {
    private static final int LOOP_COUNT = 1000;

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
        testRunner.executeStatefulVmTest(this::verifyDetachRejoin);
    }

    private void verifyDetachRejoin(UniverseWorkflow<UniverseParams, Fixture<UniverseParams>> wf)
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
        final List<IdMessage> uuids = new ArrayList<>();
        final List<EventInfo> events = new ArrayList<>();
        final ManagedResources metadata = ManagedResources.newBuilder()
                .setCreateUser("MrProto")
                .build();


        // Actual Testcase Starts and defining initial Row count for Table
        Random rand = new SecureRandom();
        int rindex;
        final int count = 100;

        log.info("**** Checking cluster status ****");
        waitForClusterStatusStable(corfuClient);

        log.info("**** Add 1st set of 100 entries ****");
        helper.transactional((utils, txn) -> utils.generateData(0, count, uuids, events, txn, false));
        helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count));

        log.info("**** First Insertion Verification:: Verify Table Data one by one ****");
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, 0, count, false));
        log.info("**** First Insertion Verified... ****");


        // Loop detach/rejoin node in loop
        for (int loopCount = 1; loopCount <= LOOP_COUNT; loopCount++) {
            log.info("**** In Loop :: " + loopCount + " ****");

            rindex = rand.nextInt(3);
            log.info(String.format(" *** Random Server value is: %s ***", rindex));
            CorfuApplicationServer server = corfuCluster.getServerByIndex(rindex);

            log.info("**** Detach the second node from cluster ****");
            ScenarioUtils.detachNodeAndVerify(corfuClient, server, clientFixture);

            log.info("**** Add entry whilst in loop ****");
            int finalLoopCount = loopCount;
            helper.transactional((utils, txn) -> utils.generateData((count + finalLoopCount) - 1,
                    count + finalLoopCount, uuids, events, txn, false));

            log.info("**** Add the second node back to the cluster ****");
            ScenarioUtils.addNodeAndVerify(corfuClient, server, clientFixture);
        }
        log.info("**** Wait for cluster status STABLE after detach loop finishes ****");
        waitForClusterStatusStable(corfuClient);

        log.info("**** Second Insertion Verification:: Verify Table Rows and Data one by one ****");
        helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count + LOOP_COUNT));
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, count, count + LOOP_COUNT, false));
        log.info("**** Second Insertion Verified... ****");

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
