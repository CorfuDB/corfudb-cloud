package org.corfudb.test.spec;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.ExampleSchemas.Uuid;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.spec.api.GenericSpec;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.api.universe.node.ApplicationServers.CorfuApplicationServer;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster.GenericCorfuCluster;
import org.corfudb.universe.universe.node.client.CorfuClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusDegraded;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;

/**
 * Cluster deployment/shutdown for a stateful test (on demand):
 * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
 * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
 * <p>
 * Test cluster behavior after Stopping first server.
 * 1) Deploy and bootstrap a three nodes cluster
 * 2) Create a table in corfu
 * 3) Add 100 Entries into table and verify count and data of table
 * 4) Stop the first server
 * 5) Add 100 more Entries into table and verify count and data of table
 * 6) Verify layout, cluster status and data path
 * 7) Recover cluster by sequentially starting stopped node
 * 8) Verify layout, cluster status and data path again
 * 9) Update Records from 60 to 139 index and Verify
 * 10) Clear the table and verify table contents are cleared
 */
@Slf4j
public class StopFirstServerSpec implements GenericSpec {

    /**
     * verifyStopAndStartFirstNode
     *
     * @param wf universe workflow
     * @throws Exception error
     */
    public <P extends UniverseParams, F extends Fixture<P>, U extends UniverseWorkflow<P, F>> void stopAndStartNode(
            U wf) throws Exception {

        GenericCorfuCluster corfuCluster = wf.getUniverse().getGroup(ClusterType.CORFU);

        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        //Check CLUSTER STATUS
        log.info("Check cluster status");
        waitForClusterStatusStable(corfuClient);

        CorfuRuntime runtime = corfuClient.getRuntime();
        String tableName = getClass().getSimpleName();

        SpecHelper helper = new SpecHelper(runtime, tableName);

        int count = 100;
        List<Uuid> uuids = new ArrayList<>();
        List<EventInfo> events = new ArrayList<>();

        // Fetch timestamp to perform snapshot queries or transactions at a particular timestamp.
        runtime.getSequencerView().query().getToken();

        // Add the entries in Table
        helper.transactional((utils, txn) -> {
            utils.generateData(0, count, uuids, events, txn, false);
        });

        helper.transactional((utils, txn) -> {
            utils.verifyTableRowCount(txn, count);
            log.info("First Insertion Verification:: Verify Table Data one by one");
            utils.verifyTableData(txn, 0, count, false);
            log.info("First Insertion Verified...");
        });

        //Should stop one node and then restart
        CorfuApplicationServer server0 = corfuCluster.getFirstServer();

        //Stop one node and wait for layout's unresponsive servers to change
        server0.stop(Duration.ofSeconds(60));
        waitForUnresponsiveServersChange(size -> size == 1, corfuClient);

        log.info("Check cluster status in Degraded");
        waitForClusterStatusDegraded(corfuClient);

        // Add the entries again in Table after stopping first server
        helper.transactional((utils, txn) -> {
            utils.generateData(100, 200, uuids, events, txn, false);
        });

        helper.transactional((utils, txn) -> {
            utils.verifyTableRowCount(txn, 200);
            log.info("Second Insertion Verification:: Verify Table Data one by one");
            utils.verifyTableData(txn, 0, 200, false);
            log.info("Second Insertion Verified...");
        });

        //Start the stopped node and wait for layout's unresponsive server to change
        server0.start();
        waitForUnresponsiveServersChange(size -> size == 0, corfuClient);

        // Verify cluster status is STABLE
        log.info("Verify cluster status at the end of test");
        waitForClusterStatusStable(corfuClient);

        //Update table records from 60 to 139
        helper.transactional((utils, txn) -> utils.generateData(60, 140, uuids, events, txn, true));
        log.info("Update the records");

        helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count * 2));

        // Add the entries again in Table after cluster is back up
        log.info("Third Insertion Verification:: Verify Table Data one by one");
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, 60, 140, true));

        log.info("Clear the Table");
        helper.transactional(UfoUtils::clearTableAndVerify);
    }
}
