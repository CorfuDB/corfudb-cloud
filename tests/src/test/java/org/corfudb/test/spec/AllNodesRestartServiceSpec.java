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
import org.corfudb.universe.UniverseManager.UniverseWorkflow;
import org.corfudb.universe.group.cluster.CorfuCluster;
import org.corfudb.universe.node.client.CorfuClient;
import org.corfudb.universe.node.server.CorfuServer;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.UniverseParams;

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
     * @param wf universe workflow
     * @throws Exception error
     */
    public void verifyRestartService(UniverseWorkflow<Fixture<UniverseParams>> wf) throws Exception {
        String groupName = wf.getFixture().data().getGroupParamByIndex(0).getName();
        CorfuCluster corfuCluster = wf.getUniverse().getGroup(groupName);

        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore = new CorfuStore(runtime);

        // Define a namespace for the table.
        String namespace = "manager";
        // Define table name
        String tableName = getClass().getSimpleName();

        log.info("Verify cluster status is stable");
        waitForClusterStatusStable(corfuClient);

        final Table<IdMessage, EventInfo, ManagedResources> table = UfoUtils.createTable(
                corfuStore, namespace, tableName
        );

        final int count = 100;
        final List<IdMessage> uuids = new ArrayList<>();
        final List<EventInfo> events = new ArrayList<>();
        final ManagedResources metadata = ManagedResources.newBuilder()
                .setCreateUser("MrProto")
                .build();
        // Creating a transaction builder.
        TxBuilder tx = corfuStore.tx(namespace);

        final Query q = corfuStore.query(namespace);
        UfoUtils.generateDataAndCommit(0, count, tableName, uuids, events, tx, metadata, false);

        log.info("First Verification:: Verify table row count");
        UfoUtils.verifyTableRowCount(corfuStore, namespace, tableName, count);
        log.info("First Verification:: Verify Table Data one by one");
        UfoUtils.verifyTableData(corfuStore, 0, count, namespace, tableName, false);
        log.info("First Verification:: Completed");


        for (int index = 0; index < 3; index++) {
            CorfuServer server = corfuCluster.getServerByIndex(index);
            // First it'll stop and then start service
            server.restart();
        }
        waitForUnresponsiveServersChange(size -> size == 0, corfuClient);
        UfoUtils.generateDataAndCommit(
                100, count * 2, tableName, uuids, events, tx, metadata, false
        );
        log.info("Second Verification:: Verify table row count");
        UfoUtils.verifyTableRowCount(corfuStore, namespace, tableName, count * 2);
        log.info("Update the records");
        UfoUtils.generateDataAndCommit(60, 90, tableName, uuids, events, tx, metadata, true);

        log.info("Third Verification:: Verify the data");
        UfoUtils.verifyTableData(corfuStore, 0, 60, namespace, tableName, false);
        UfoUtils.verifyTableData(corfuStore, 91, count * 2, namespace, tableName, false);
        log.info("Third Verification:: Verify the updated data");
        UfoUtils.verifyTableData(corfuStore, 60, 90, namespace, tableName, true);
        log.info("Third Verification:: Completed");
        waitForClusterStatusStable(corfuClient);
        UfoUtils.clearTableAndVerify(table, tableName, q);
    }
}
