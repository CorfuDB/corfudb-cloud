package org.corfudb.test.vm.stateful.ufo.restart;

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
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster.GenericCorfuCluster;
import org.corfudb.universe.universe.node.client.CorfuClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;

@Slf4j
@Tag(TestGroups.STATEFUL)
public class RestartServiceOnTwoNodesTest extends AbstractCorfuUniverseTest {
    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
     * <p>
     * <p>
     * Test cluster behavior after restart of corfu service on two nodes
     * 1) Deploy and bootstrap a three nodes cluster
     * 2) Create a table in corfu
     * 3) Add 100 Entries into table and verify count and data of table
     * 4) Restart corfu service on two nodes of cluster
     * 5) Add 100 more Entries into table and verify count and data of table
     * 6) Update Records from 60 to 139 index and Verify
     * 7) Clear the table and verify table contents are cleared
     */

    @Test
    public void test() {
        testRunner.executeStatefulVmTest(this::verifyRestartService);
    }

    private void verifyRestartService(UniverseWorkflow<UniverseParams, Fixture<UniverseParams>> wf)
            throws InterruptedException, NoSuchMethodException, IllegalAccessException,
            InvocationTargetException {

        GenericCorfuCluster corfuCluster = wf.getUniverse().getGroup(ClusterType.CORFU);

        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        //Check CLUSTER STATUS
        log.info("**** Checking cluster status ****");
        waitForClusterStatusStable(corfuClient);

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore = new CorfuStore(runtime);


        // Define table name
        String tableName = getClass().getSimpleName();
        SpecHelper helper = new SpecHelper(runtime, tableName);
        final int count = 100;
        List<TestSchema.IdMessage> uuids = new ArrayList<>();
        List<TestSchema.EventInfo> events = new ArrayList<>();
// Add data in table (100 entries)
        log.info("**** Add 1st set of 100 entries ****");
        helper.transactional((utils, txn) -> utils.generateData(0, count, uuids, events, txn, false));
        // Verify table row count (should be 100)
        helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count));

        log.info("**** First Insertion Verification:: Verify Table Data one by one ****");
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, 0, count, false));
        log.info("**** First Insertion Verified. ****");

        log.info("**** Restarting Corfu server on two nodes ****");
        for (int index = 0; index < 2; index++) {
            CorfuApplicationServer server = corfuCluster.getServerByIndex(index);
            // First it'll stop and then start service
            log.info(String.format("**** Restarting server %s  ****", index));
            server.restart();
        }
        log.info("**** Wait for cluster status STABLE :: after restarting service on two servers ****");
        waitForUnresponsiveServersChange(size -> size == 0, corfuClient);
        waitForClusterStatusStable(corfuClient);

        // Add 100 more entries in table after service restart
        log.info("**** Add 2nd set of 100 entries ****");
        helper.transactional((utils, txn) -> utils.generateData(100, 200, uuids, events, txn, false));
        helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, 200));

        log.info("**** Second Insertion Verification:: Verify Table Data one by one ****");
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, 0, 200, false));
        log.info("**** Second Insertion Verified. ****");

        //Update table records from 60 to 139
        log.info("**** Update the records ****");
        helper.transactional((utils, txn) -> utils.generateData(60, 140, uuids, events, txn, true));
        helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count * 2));

        log.info("**** Third Insertion Verification:: Verify Table Data one by one ****");
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, 60, 140, true));

        log.info("**** Clear the Table ****");

        helper.transactional(UfoUtils::clearTableAndVerify);
    }
}
