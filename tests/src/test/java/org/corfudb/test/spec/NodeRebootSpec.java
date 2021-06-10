package org.corfudb.test.spec;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.RebootUtil;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.runtime.collections.Query;
import org.corfudb.runtime.collections.Table;
import org.corfudb.runtime.collections.TxBuilder;
import org.corfudb.test.TestSchema;
import org.corfudb.test.spec.api.GenericSpec;
import org.corfudb.universe.api.deployment.DeploymentParams;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.cluster.Cluster;
import org.corfudb.universe.api.universe.node.ApplicationServers.CorfuApplicationServer;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster;
import org.corfudb.universe.universe.node.client.CorfuClient;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static java.util.Optional.ofNullable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;

/**
 * Test cluster behavior upon rebooting the nodes
 * <p>
 *     1) Deploy and bootstrap a three nodes cluster
 *     2) Create a table in corfu
 *     3) Add 100 Entries into table and verify count and data of table
 *     4) Reboot all the three nodes in the cluster without reset data
 *     5) Verify the data is still there
 * </p>
 */
@Slf4j
public class NodeRebootSpec {
    /**
     * verifyClusterDetachRejoin
     *
     * @param wf universe workflow
     * @throws Exception error
     */
    public <P extends UniverseParams, F extends Fixture<P>, U extends UniverseWorkflow<P, F>> void nodeReboot(
            U wf) throws InterruptedException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        CorfuCluster<DeploymentParams<CorfuServerParams>, CorfuApplicationServer> corfuCluster =
                wf.getUniverse().getGroup(Cluster.ClusterType.CORFU);


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
        List<TestSchema.IdMessage> uuids = new ArrayList<>();
        List<TestSchema.EventInfo> events = new ArrayList<>();
        TestSchema.ManagedResources metadata = TestSchema.ManagedResources.newBuilder()
                .setCreateUser("MrProto")
                .build();

        // Fetch timestamp to perform snapshot queries or transactions at a particular timestamp.
        runtime.getSequencerView().query().getToken();

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

        //Should stop two nodes and then restart
        CorfuApplicationServer server0 = corfuCluster.getServerByIndex(0);
        CorfuApplicationServer server1 = corfuCluster.getServerByIndex(1);
        CorfuApplicationServer server2 = corfuCluster.getServerByIndex(2);

        RebootUtil.restart(server0.getEndpoint(), 10, Duration.ofMinutes(2), ofNullable(runtime.getClusterId()));
        RebootUtil.restart(server1.getEndpoint(), 10, Duration.ofMinutes(2), ofNullable(runtime.getClusterId()));
        RebootUtil.restart(server2.getEndpoint(), 10, Duration.ofMinutes(2), ofNullable(runtime.getClusterId()));

        helper.transactional((utils, txn) -> {
            utils.verifyTableData(txn, 0, count, false);
        });
        log.info("Clear the Table");
        helper.transactional(UfoUtils::clearTableAndVerify);
    }
}
