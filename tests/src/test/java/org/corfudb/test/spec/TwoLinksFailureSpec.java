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

import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusDegraded;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForLayoutChange;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;

/**
 * Cluster deployment/shutdown for a stateful test (on demand):
 * - deploy a cluster: run org.corfudb.universe.test.management.Deployment
 * - Shutdown the cluster org.corfudb.universe.test.management.Shutdown
 * <p>
 * Test cluster behavior after two links failure.
 * 1) Deploy and bootstrap a three nodes cluster
 * 2) Create a table in corfu
 * 3) Add 100 Entries into table and verify count and data of table
 * 4) Create two link failure between one node and
 * the other two, which results in a partial partition
 * 5) Verify layout, cluster status is DEGRADED
 * 6) Add 100 more Entries into table and verify count and data of table
 * 7) Recover cluster by removing all link failures
 * 8) Verify layout, cluster status
 * 9) Update Records from 60 to 139 index and Verify
 * 10) Clear the table and verify table contents are cleared
 */
@Slf4j
public class TwoLinksFailureSpec {

    /**
     * verifyTwoLinksFailure
     *
     * @param wf universe workflow
     * @throws Exception error
     */
    public <P extends UniverseParams, F extends Fixture<P>, U extends UniverseWorkflow<P, F>> void twoLinksFailure(
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

        // Create & Register the table.
        // This is required to initialize the table for the current corfu client.

        final Table<IdMessage, EventInfo, ManagedResources> table = UfoUtils.createTable(
                corfuStore, manager, tableName
        );

        final int count = 100;
        List<IdMessage> uuids = new ArrayList<>();
        List<EventInfo> events = new ArrayList<>();
        ManagedResources metadata = ManagedResources.newBuilder()
                .setCreateUser("MrProto")
                .build();
        // Creating a transaction builder.
        final TxBuilder tx = corfuStore.tx(manager);

        // Fetch timestamp to perform snapshot queries or transactions at a particular timestamp.
        corfuStore.getTimestamp();

        UfoUtils.generateDataAndCommit(0, count, tableName, uuids, events, tx, metadata, false);
        Query q = corfuStore.query(manager);

        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count);

        log.info("First Insertion Verification:: Verify Table Data one by one");
        UfoUtils.verifyTableData(corfuStore, 0, count, manager, tableName, false);
        log.info("First Insertion Verified...");

        //Should fail two links and then heal
        CorfuApplicationServer server0 = corfuCluster.getFirstServer();
        CorfuApplicationServer server1 = corfuCluster.getServerByIndex(1);
        CorfuApplicationServer server2 = corfuCluster.getServerByIndex(2);

        // Disconnect server0 with server1 and server2
        server0.disconnect(Arrays.asList(server1, server2));
        waitForLayoutChange(layout -> layout.getUnresponsiveServers()
                .equals(Collections.singletonList(server0.getEndpoint())), corfuClient);

        // Cluster status should be DEGRADED after one node is marked unresponsive
        log.info("Verify Cluster status is Degraded...");
        waitForClusterStatusDegraded(corfuClient);

        // Add the entries again in Table
        UfoUtils.generateDataAndCommit(100, 200, tableName, uuids, events, tx, metadata, false);
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, 200);

        log.info("Second Insertion Verification:: Verify Table Data one by one");
        UfoUtils.verifyTableData(corfuStore, 0, 200, manager, tableName, false);
        log.info("Second Insertion Verified...");

        // Repair the link failure between server0 and others
        server0.reconnect(Arrays.asList(server1, server2));
        waitForUnresponsiveServersChange(size -> size == 0, corfuClient);

        // Verify cluster status is STABLE
        log.info("Verify cluster status at the end of test");
        waitForClusterStatusStable(corfuClient);

        //Update table records from 60 to 139
        log.info("Update the records");
        UfoUtils.generateDataAndCommit(60, 140, tableName, uuids, events, tx, metadata, true);
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count * 2);

        log.info("Third Insertion Verification:: Verify Table Data one by one");
        UfoUtils.verifyTableData(corfuStore, 60, 140, manager, tableName, true);

        log.info("Clear the Table");
        UfoUtils.clearTableAndVerify(table, tableName, q);
    }
}
