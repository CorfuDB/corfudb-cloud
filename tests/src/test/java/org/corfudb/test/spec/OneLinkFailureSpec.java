package org.corfudb.test.spec;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.runtime.collections.Query;
import org.corfudb.runtime.collections.Table;
import org.corfudb.runtime.collections.TxBuilder;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.TestSchema.IdMessage;
import org.corfudb.test.TestSchema.ManagedResources;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.corfudb.universe.test.util.ScenarioUtils.verifyUnresponsiveServers;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusDegraded;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;

/**
 * Cluster deployment/shutdown for a stateful test (on demand):
 * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
 * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
 * <p>
 * Test cluster behavior after one link failure
 * 1) Deploy and bootstrap a three nodes cluster
 * 2) Create a table in corfu
 * 3) Add 100 Entries into table and verify count and data of table
 * 4) Create a link failure between two nodes which
 * results in a partial partition
 * 5) Verify layout, cluster status is DEGRADED
 * 6) Add 100 more Entries into table and verify count and data of table
 * 7) Recover cluster by removing the link failures
 * 8) Verify layout, cluster status and data path again
 * 9) Update Records from 60 to 139 index and Verify
 * 10) Clear the table and verify table contents are cleared
 */
@Slf4j
public class OneLinkFailureSpec {

    /**
     * verifyOneLinkFailure
     *
     * @param wf universe workflow
     * @throws Exception error
     */
    public <P extends UniverseParams, F extends Fixture<P>, U extends UniverseWorkflow<P, F>> void oneLinkFailure(
            U wf) throws Exception {

        GenericCorfuCluster corfuCluster = wf.getUniverse().getGroup(ClusterType.CORFU);

        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        //Check CLUSTER STATUS
        log.info("Check cluster status");
        waitForClusterStatusStable(corfuClient);

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore = new CorfuStore(runtime);

        // Define a namespace for the table.
        String manager = "manager";
        // Define table name
        String tableName = getClass().getSimpleName();

        final int count = 100;
        List<IdMessage> uuids = new ArrayList<>();
        List<EventInfo> events = new ArrayList<>();
        ManagedResources metadata = ManagedResources.newBuilder()
                .setCreateUser("MrProto")
                .build();

        GenericSpec.SpecHelper helper = new GenericSpec.SpecHelper(runtime, tableName);
        helper.transactional((utils, txn) -> {
            utils.generateData(0, count, uuids, events, txn, false);
        });

        helper.transactional((utils, txn) -> {
            utils.verifyTableRowCount(txn, count);
            log.info("First Insertion Verification:: Verify Table Data one by one");
            utils.verifyTableData(txn, 0, count, false);
            log.info("First Insertion Verified...");
        });

        //Should fail one link and then heal"
        CorfuApplicationServer server0 = corfuCluster.getFirstServer();
        CorfuApplicationServer server2 = corfuCluster.getServerByIndex(2);

        // Create link failure between server0 and server2
        server0.disconnect(Collections.singletonList(server2));
        // Server0 and server2 has same number of link failure ie. 1, the one with
        // larger endpoint should be marked as unresponsive.
        String serverToKick = Collections.max(
                Arrays.asList(server0.getEndpoint(), server2.getEndpoint())
        );
        waitForUnresponsiveServersChange(size -> size == 1, corfuClient);

        log.info("Verify Layout Server List");
        verifyUnresponsiveServers(corfuClient, serverToKick);

        // Cluster status should be DEGRADED after one node is marked unresponsive
        log.info("Verify Cluster status is Degraded...");
        waitForClusterStatusDegraded(corfuClient);

        // Add the entries again in Table
        log.info("Add data into the table");
        helper.transactional((utils, txn) -> {
            utils.generateData(100, 200, uuids, events, txn, false);
        });
        helper.transactional((utils, txn) -> {
            utils.verifyTableRowCount(txn, 200);
            log.info("Second Insertion Verification:: Verify Table Data one by one");
            utils.verifyTableData(txn, 0, 200, false);
            log.info("Second Insertion Verified...");
        });

        // Repair the partition between server0 and server2
        server0.reconnect(Collections.singletonList(server2));
        waitForUnresponsiveServersChange(size -> size == 0, corfuClient);

        // Verify cluster status is STABLE
        log.info("Verify cluster status");
        waitForClusterStatusStable(corfuClient);

        //Update table records from 60 to 139
        log.info("Update the records");
        log.info("Update the records");
        helper.transactional((utils, txn) -> {
            utils.generateData(60, 140, uuids, events, txn, true);
        });

        helper.transactional((utils, txn) -> {
            utils.verifyTableRowCount(txn, count * 2);
            log.info("Third Insertion Verification:: Verify Table Data one by one");
            utils.verifyTableData(txn, 0, 140, true);
            log.info("Third Insertion Verified...");
        });

        log.info("Clear the Table");
        helper.transactional(UfoUtils::clearTableAndVerify);
    }
}
