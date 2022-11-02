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

import java.util.ArrayList;
import java.util.List;

import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;

/**
 * Cluster deployment/shutdown for a stateful test (on demand):
 * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
 * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
 * <p>
 * Test cluster behavior after all nodes are partitioned symmetrically
 * 1) Deploy and bootstrap a three nodes cluster
 * 2) Verify Cluster is stable after deployment
 * 3) Create a table in corfu i.e. "CorfuUFO_AllNodesRestartServiceTable"
 * 4) Add 100 Entries into table
 * 5) Verification by number of rows count i.e (Total rows: 100) and verify table content
 * 6) restart (stop/start) the "corfu" service on all cluster nodes
 * 7) Wait for nodes to get responsive
 * 8) Add more 100 Entries into table and verify count and data of table
 * 9) Update the table entries from 60 to 90
 * 10) Verification by number of rows count i.e (Total rows: 200) and verify table content
 * 11) verify table content updated content
 * 12) clear the contents of the table
 */
@Slf4j
public class AllNodesRestartServiceSpec {

    /**
     * verifyRestartService
     *
     * @param wf universe workflow
     * @throws Exception error
     */
    public <P extends UniverseParams, F extends Fixture<P>, U extends UniverseWorkflow<P, F>> void restartService(U wf)
            throws Exception {
        GenericCorfuCluster corfuCluster = wf.getUniverse().getGroup(ClusterType.CORFU);

        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Define table name
        String tableName = getClass().getSimpleName();


        GenericSpec.SpecHelper helper = new GenericSpec.SpecHelper(runtime, tableName);

        log.info("Verify cluster status is stable");
        waitForClusterStatusStable(corfuClient);

        final int count = 100;
        final List<Uuid> uuids = new ArrayList<>();
        final List<EventInfo> events = new ArrayList<>();

        helper.transactional((utils, txn) -> utils.generateData(0, count, uuids, events, txn, false));

        helper.transactional((utils, txn) -> {
            log.info("First Verification:: Verify table row count");
            utils.verifyTableRowCount(txn, count);
            log.info("First Verification:: Verify Table Data one by one");
            utils.verifyTableData(txn, 0, count, false);
            log.info("First Verification:: Completed");
        });


        for (int index = 0; index < 3; index++) {
            CorfuApplicationServer server = corfuCluster.getServerByIndex(index);
            // First it'll stop and then start service
            server.restart();
        }
        waitForUnresponsiveServersChange(size -> size == 0, corfuClient);

        helper.transactional((utils, txn) -> utils.generateData(100, count * 2, uuids, events, txn, false));
        helper.transactional((utils, txn) -> {
            log.info("Second Verification:: Verify table row count");
            utils.verifyTableRowCount(txn, count * 2);
        });
        log.info("Update the records");
        helper.transactional((utils, txn) -> utils.generateData(60, 90, uuids, events, txn, true));

        helper.transactional((utils, txn) -> {
            log.info("Third Verification:: Verify the data");
            utils.verifyTableData(txn, 0, 60, false);
            utils.verifyTableData(txn, 91, count * 2, false);
            log.info("Third Verification:: Verify the updated data");
            utils.verifyTableData(txn, 60, 90, true);
            log.info("Third Verification:: Completed");
        });
        waitForClusterStatusStable(corfuClient);
        helper.transactional(UfoUtils::clearTableAndVerify);
    }
}
