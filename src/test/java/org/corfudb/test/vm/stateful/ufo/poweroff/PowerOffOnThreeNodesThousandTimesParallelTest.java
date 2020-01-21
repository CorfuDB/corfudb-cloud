package org.corfudb.test.vm.stateful.ufo.poweroff;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.runtime.collections.Query;
import org.corfudb.runtime.collections.Table;
import org.corfudb.runtime.collections.TxBuilder;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.test.TestSchema;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.TestSchema.IdMessage;
import org.corfudb.test.TestSchema.ManagedResources;
import org.corfudb.universe.UniverseManager;
import org.corfudb.universe.UniverseManager.UniverseWorkflow;
import org.corfudb.universe.group.cluster.CorfuCluster;
import org.corfudb.universe.node.client.CorfuClient;
import org.corfudb.universe.node.server.CorfuServer;
import org.corfudb.universe.node.server.vm.VmCorfuServer;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.UniverseConfigurator;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.UniverseParams;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;
import static org.corfudb.universe.test.util.ScenarioUtils.waitUninterruptibly;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@Tag(TestGroups.STATEFUL)
public class PowerOffOnThreeNodesThousandTimesParallelTest extends AbstractCorfuUniverseTest {
    private static final int LOOP_COUNT = 1000;

    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
     * <p>
     * Test cluster behavior after PowerOff/On all cluster nodes
     * 1)  Deploy and bootstrap a three nodes cluster
     * 2)  Create a table in corfu
     * 3)  Repeat the steps from (4 - 9) in loop, LOOP_COUNT i.e 1000
     * 4)  Add 100 Entries into table
     * 5)  if (LOOP_COUNT mod 2) equals to 0 then update the table entries
     * 6)  Verify the table rows and its contents
     * 7)  PowerOff all the cluster nodes (parallel)
     * 8)  PowerOn all the cluster nodes (parallel)
     * 9)  Start corfu process on all cluster nodes (parallel)
     * 10) Wait till cluster status become "STABLE"
     * 11) Verify the table rows and its content
     * 12) Once the loop is over, verify all data
     * 13) Clear the table contents
     */
    @Test
    public void test() {
        testRunner.executeTest(this::verifyPowerOffOnThreeNodesThousandTimesParallelTest);
    }

    private void verifyPowerOffOnThreeNodesThousandTimesParallelTest(UniverseWorkflow<Fixture<UniverseParams>> wf)
            throws Exception {


        int start = 0;
        int end;
        int count = 100;
        int rindex;
        boolean isTrue = false;

        final int numNodes = 3;

        UniverseParams params = wf.getFixture().data();
        CorfuCluster corfuCluster = wf.getUniverse()
                .getGroup(params.getGroupParamByIndex(0).getName());
        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        // Get the servers list
        List<CorfuServer> corfuServers = IntStream.range(0, numNodes)
                .mapToObj(corfuCluster::getServerByIndex)
                .collect(Collectors.toList());
        List<VmCorfuServer> servers = corfuServers.stream()
                .filter(server -> server instanceof VmCorfuServer)
                .map(server -> (VmCorfuServer) server)
                .collect(Collectors.toList());

        //Check CLUSTER STATUS
        log.info("**** before running testcase, verify cluster status ****");
        waitForClusterStatusStable(corfuClient);
        log.info("*** cluster status is STABLE, executing testcase ***");

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore = new CorfuStore(runtime);

        // Define a namespace for the table.
        String manager = "manager";
        // Define table name
        String tableName = "CorfuUFO_PowerOffOnThreeNodesThousandTimesParallelTable";

        // Create & Register the table.
        // This is required to initialize the table for the current corfu client.
        final Table<IdMessage, EventInfo, ManagedResources> table = UfoUtils.createTable(
                corfuStore, manager, tableName
        );

        List<TestSchema.IdMessage> uuids = new ArrayList<>();
        List<TestSchema.EventInfo> events = new ArrayList<>();
        TestSchema.ManagedResources metadata = TestSchema.ManagedResources.newBuilder()
                .setCreateUser("MrProto")
                .build();
        // Creating a transaction builder.
        final TxBuilder tx = corfuStore.tx(manager);

        for (int lcount = 1; lcount <= LOOP_COUNT; lcount++) {

            end = count * lcount;
            log.info("*********************");
            log.info(String.format("*** required values like start::%s, end::%s and lcount::%s", start, end, lcount));
            log.info("*********************");
            log.info("*** insert the 100 enteries inot the table ***");
            UfoUtils.generateDataAndCommit(start, end, tableName, uuids, events, tx, metadata, isTrue);

            if (lcount % 2 == 0) {
                isTrue = true;
                log.info(" *** updating the records with start::{}, end::{} and lcount::{} ***", start, end, lcount);
                UfoUtils.generateDataAndCommit(start, end, tableName, uuids, events, tx, metadata, isTrue);
            }

            // verification of table rows and it's content one by one
            log.info(String.format("*** verify the rows count that should be %s ***", count * lcount));
            UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count * lcount);
            log.info(String.format("table has %s rows as expected", count * lcount));
            UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, isTrue);

            // Concurrently execute the command (powerOff) all cluster nodes
            log.info("*** power-off all cluster nodes in parallel ***");
            ExecutorService executor = Executors.newFixedThreadPool(numNodes);
            servers.forEach(server -> {
                Runnable powerOffNode = () -> server.getVmManager().powerOff();
                executor.submit(powerOffNode);
            });

            log.info("*** wait for 10 seconds before PowerOn VMs ***");
            waitUninterruptibly(Duration.ofSeconds(10));

            // Concurrently execute the command (start service) on all cluster nodes
            log.info("*** power-on all cluster nodes in parallel ***");
            servers.forEach(server -> {
                Runnable powerOnNode = () -> server.getVmManager().powerOn();
                executor.submit(powerOnNode);
            });
            log.info("*** start corfu on all cluster nodes in parallel ***");
            servers.forEach(server -> {
                Runnable startCorfu = server::start;
                executor.submit(startCorfu);
            });

            waitForUnresponsiveServersChange(size -> size == 0, corfuClient);
            log.info("*** wait for cluster status to become STABLE ***");
            waitForClusterStatusStable(corfuClient);

            // verification of table rows and it's content one by one
            UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count * lcount);
            UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, isTrue);
            log.info(String.format("**** %s:: verification done ****", lcount));

            start = end;
            isTrue = false;
        }

        log.info(" *** at last, verifying the entire table contents on by one ***");
        start = 1;
        for (int idx = 1; idx <= LOOP_COUNT; idx++) {
            end = count * idx;
            if (idx % 2 == 0) {
                log.info("*** verifying updated data ***");
                log.info(String.format("*** required values like start::%s, end::%s and lcount::%s", start, end, idx));
                UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, true);
            } else {
                log.info("*** verifying non-updated data ***");
                log.info(String.format("*** required values like start::%s, end::%s and lcount::%s", start, end, idx));
                UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, false);
            }
            start = end;
        }

        log.info("*** clearing up the table contents ***");
        Query q = corfuStore.query(manager);
        UfoUtils.clearTableAndVerify(table, tableName, q);
    }
}