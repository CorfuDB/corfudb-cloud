package org.corfudb.test.spec;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.ExampleSchemas.Uuid;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.spec.api.GenericSpec;
import org.corfudb.universe.api.deployment.DeploymentParams;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.api.universe.node.ApplicationServers.CorfuApplicationServer;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster;
import org.corfudb.universe.universe.node.client.ClientParams;
import org.corfudb.universe.universe.node.client.CorfuClient;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForStandaloneNodeClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitUninterruptibly;

/**
 * Cluster deployment/shutdown for a stateful test (on demand):
 * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
 * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
 * <p>
 * Test cluster behavior after add/remove nodes
 * 1) Deploy and bootstrap a three nodes cluster
 * 2) Create a table in corfu
 * 3) Add 100 Entries into table and verify count and data of table
 * 4) Remove two nodes from cluster
 * 5) Verify Layout
 * 6) Add 100 more Entries into table and verify count and data of table
 * 7) Reattach the two detached nodes into cluster
 * 8) Verify Layout
 * 9) Update Records from 60 to 139 index and Verify
 * 10) Verify the table contents and updated data
 * 11) Clear the table and verify table contents are cleared
 */
@Slf4j
public class ClusterDetachRejoinTwoNodesSpec {

    /**
     * verifyClusterDetachRejoin
     *
     * @param wf universe workflow
     * @throws Exception error
     */
    public <P extends UniverseParams, F extends Fixture<P>, U extends UniverseWorkflow<P, F>> void clusterDetachRejoin(
            U wf) throws Exception {
        ClientParams clientFixture = ClientParams.builder().build();

        CorfuCluster<DeploymentParams<CorfuServerParams>, CorfuApplicationServer> corfuCluster =
                wf.getUniverse().getGroup(ClusterType.CORFU);

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

        helper.transactional(UfoUtils::clearTableAndVerify);
        helper.transactional((utils, txn) -> utils.generateData(0, count, uuids, events, txn, false));
        helper.transactional((utils, txn) -> {
            log.info("First Verification:: Verify table row count");
            utils.verifyTableRowCount(txn, count);
            log.info("First Verification:: Verify Table Data one by one");
            utils.verifyTableData(txn, 0, count, false);
            log.info("First Verification:: Completed");
        });


        CorfuApplicationServer server0 = corfuCluster.getFirstServer();

        List<CorfuApplicationServer> servers = Arrays.asList(
                corfuCluster.getServerByIndex(1),
                corfuCluster.getServerByIndex(2)
        );

        //should remove two nodes from corfu cluster
        {
            log.info("Detaching Two Nodes...");
            // Sequentially remove two nodes from cluster
            for (CorfuApplicationServer candidate : servers) {
                log.info("Removing Node: {}", candidate);
                corfuClient.getManagementView().removeNode(
                        candidate.getEndpoint(),
                        clientFixture.getNumRetry(),
                        clientFixture.getTimeout(),
                        clientFixture.getPollPeriod()
                );
            }

            log.info("Check Cluster status of Detached Nodes");
            for (CorfuApplicationServer candidate : servers) {
                log.info("Cluster status check of Node: {}", candidate);
                // Check cluster status of detached node
                waitForStandaloneNodeClusterStatusStable(corfuClient, candidate);
            }

            log.info("Verify Layout After Detaching Two Nodes");
            // Verify layout contains only the node that is not removed
            corfuClient.invalidateLayout();
            assertThat(corfuClient.getLayout().getAllServers())
                    .containsExactly(server0.getEndpoint());

            log.info("insert 100 more rows into table");
            helper.transactional((utils, txn) -> utils.generateData(100, count * 2, uuids, events, txn, false));

            helper.transactional((utils, txn) -> {
                log.info("Second Verification:: Verify table row count");
                utils.verifyTableRowCount(txn, count * 2);
                log.info("Second Verification:: Verify the data");
                utils.verifyTableData(txn, 100, count * 2, false);
                log.info("Second Verification:: Completed");
            });

            waitUninterruptibly(Duration.ofSeconds(15));
        }

        //should add two nodes back to corfu cluster
        {
            log.info("Add the detached nodes back to cluster...");
            // Sequentially add two nodes back into cluster
            for (CorfuApplicationServer candidate : servers) {
                corfuClient.getManagementView().addNode(
                        candidate.getEndpoint(),
                        clientFixture.getNumRetry(),
                        clientFixture.getTimeout(),
                        clientFixture.getPollPeriod()
                );
            }

            log.info("After Rejoining Cluster Verify Layout");
            // Verify layout should contain all three nodes
            corfuClient.invalidateLayout();
            assertThat(corfuClient.getLayout().getAllServers().size())
                    .isEqualTo(corfuCluster.nodes().size());

            log.info("After Rejoining the detached nodes Check cluster status");
            waitForClusterStatusStable(corfuClient);

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

            helper.transactional(UfoUtils::clearTableAndVerify);
        }
    }
}
