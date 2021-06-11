package org.corfudb.test.spec;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.TestSchema.IdMessage;
import org.corfudb.test.spec.api.GenericSpec.SpecHelper;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.api.universe.node.ApplicationServers.CorfuApplicationServer;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster.GenericCorfuCluster;
import org.corfudb.universe.universe.node.client.ClientParams;
import org.corfudb.universe.universe.node.client.CorfuClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;

/**
 * Cluster deployment/shutdown for a stateful test (on demand):
 * - deploy a cluster: run org.corfudb.universe.test.management.Deployment
 * - Shutdown the cluster org.corfudb.universe.test.management.Shutdown
 * <p>
 * Test cluster behavior after add/remove nodes
 * 1) Deploy and bootstrap a three nodes cluster
 * 2) Verify Cluster is stable after deployment
 * 3) Create a table in corfu i.e. "CorfuUFO_AddAndRemoveServerTable"
 * 4) Add 100 Entries into table
 * 5) Verification by number of rows count i.e (Total rows: 100) and verify table content
 * 6) Remove one node from cluster
 * 7) Verify Layout, in layout there should be entry of node which we removed
 * 8) Verfication by number of rows count i.e (Total rows: 100) and verify table content
 * 9) Update the table enteirs from 60 to 90
 * 10) Add node back into cluster
 * 11) Add more 100 Entries into table and verify count and data of table
 * 12) Verfication by number of rows count i.e (Total rows: 200) and verify table content and
 * updated content as well
 * 13) Verify layout, detached node entry should be there
 * 14) Verify cluster status is stable or not
 * 15) Clear the table and verify table contents are cleared
 */
@Slf4j
public class AddAndRemoveServerSpec {

    /**
     * verifyAddAndRemoveNode
     *
     * @param wf universe workflow
     * @throws Exception error
     */
    @Test
    public <P extends UniverseParams, F extends Fixture<P>, U extends UniverseWorkflow<P, F>> void addAndRemoveNode(
            U wf) throws Exception {

        GenericCorfuCluster corfuCluster = wf.getUniverse().getGroup(ClusterType.CORFU);

        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Define table name
        String tableName = getClass().getSimpleName();

        SpecHelper helper = new SpecHelper(runtime, tableName);


        log.info("Verify cluster status is stable");
        waitForClusterStatusStable(corfuClient);

        final int count = 100;
        final List<IdMessage> uuids = new ArrayList<>();
        final List<EventInfo> events = new ArrayList<>();

        // Fetch timestamp to perform snapshot queries or transactions at a particular timestamp.
        runtime.getSequencerView().query().getToken();

        // Add the entries again in Table
        helper.transactional((utils, txn) -> utils.generateData(0, count, uuids, events, txn, false));
        helper.transactional((utils, txn) -> {
            log.info("First Verification:: Verify table row count");
            utils.verifyTableRowCount(txn, count);
            log.info("First Verification:: Verify Table Data one by one");
            utils.verifyTableData(txn, 0, count, false);
            log.info("First Verification:: Completed");
        });

        //Remove corfu node from the corfu cluster (layout)
        CorfuApplicationServer server0 = corfuCluster.getFirstServer();
        ClientParams clientFixture = ClientParams.builder().build();
        corfuClient.getManagementView().removeNode(
                server0.getEndpoint(),
                clientFixture.getNumRetry(),
                clientFixture.getTimeout(),
                clientFixture.getPollPeriod()
        );

        // Verify layout contains only the nodes that is not removed
        corfuClient.invalidateLayout();
        assertThat(corfuClient.getLayout().getAllServers())
                .doesNotContain(server0.getEndpoint());

        helper.transactional((utils, txn) -> {
            log.info("Second Verification:: Verify the data after detach the node");
            utils.verifyTableData(txn, 0, count, false);
            log.info("Second Verification:: There is data change after detached the node");
        });

        log.info("Update the records");
        helper.transactional((utils, txn) -> utils.generateData(60, 90, uuids, events, txn, true));

        //Add corfu node back to the cluster
        corfuClient.getManagementView().addNode(
                server0.getEndpoint(),
                clientFixture.getNumRetry(),
                clientFixture.getTimeout(),
                clientFixture.getPollPeriod()
        );

        log.info("After rejoin the detached node, insert the data into the table");
        helper.transactional((utils, txn) -> utils.generateData(100, 200, uuids, events, txn, false));

        // Verify layout should contain all three nodes
        corfuClient.invalidateLayout();
        assertThat(corfuClient.getLayout().getAllServers().size())
                .isEqualTo(corfuCluster.nodes().size());

        log.info("Verify cluster status is stable");
        waitForClusterStatusStable(corfuClient);

        helper.transactional((utils, txn) -> {
            log.info("Third Verification:: verify the table rows count after insertion of 100 rows");
            utils.verifyTableRowCount(txn, count * 2);
            log.info("Third Verification:: table has 200 entries as expected");

            log.info("Third Verification:: Verify the data");
            utils.verifyTableData(txn, 0, 60, false);
            utils.verifyTableData(txn, 91, count * 2, false);

            log.info("Third Verification:: Verify the updated data");
            utils.verifyTableData(txn, 60, 90, true);
            log.info("Third Verification:: Completed");

            utils.clearTableAndVerify(txn);
        });
    }
}
