package org.corfudb.test.spec;

import com.google.common.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.protocols.wireprotocol.Token;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.MultiCheckpointWriter;
import org.corfudb.runtime.collections.ICorfuTable;
import org.corfudb.runtime.collections.PersistentCorfuTable;
import org.corfudb.runtime.view.ObjectOpenOption;
import org.corfudb.universe.api.deployment.DeploymentParams;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.cluster.Cluster;
import org.corfudb.universe.api.universe.node.ApplicationServers;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test cluster behavior upon checkpoint trim
 * <p>
 * 1) Deploy and bootstrap a three nodes cluster with default cross version setup
 * 2) Place 3 entries into the map
 * 3) Insert a checkpoint
 * 4) Get a new view of the map
 * 5) Reading an entry from scratch should be ok
 * </p>
 */
@Slf4j
public class CheckPointTrimSpec {

    /**
     * verifyClusterDetachRejoin
     *
     * @param wf universe workflow
     */
    public <P extends UniverseParams, F extends Fixture<P>, U extends UniverseWorkflow<P, F>> void checkPointTrim(
            U wf) {
        CorfuCluster<DeploymentParams<CorfuServerParams>, ApplicationServers.CorfuApplicationServer> corfuCluster =
                wf.getUniverse().getGroup(Cluster.ClusterType.CORFU);

        CorfuRuntime runtime = corfuCluster.getLocalCorfuClient().getRuntime();
        ICorfuTable<String, String> testMap = runtime.getObjectsView().build()
                .setTypeToken(new TypeToken<PersistentCorfuTable<String, String>>() {})
                .setStreamName("test")
                .open();

        // Place 3 entries into the map
        testMap.insert("a", "a");
        testMap.insert("b", "b");
        testMap.insert("c", "c");

        // Insert a checkpoint
        MultiCheckpointWriter mcw = new MultiCheckpointWriter();
        mcw.addMap(testMap);
        Token checkpointAddress = mcw.appendCheckpoints(runtime, "author");

        // Trim the log
        runtime.getAddressSpaceView().prefixTrim(checkpointAddress);
        runtime.getAddressSpaceView().gc();
        runtime.getAddressSpaceView().invalidateServerCaches();
        runtime.getAddressSpaceView().invalidateClientCache();

        // Get a new view of the map
        ICorfuTable<String, String> newTestMap = runtime.getObjectsView().build()
                .setTypeToken(new TypeToken<PersistentCorfuTable<String, String>>() {})
                .addOpenOption(ObjectOpenOption.NO_CACHE)
                .setStreamName("test")
                .open();

        // Reading an entry from scratch should be ok
        assertThat(newTestMap.keySet()).contains("a", "b", "c");
    }
}
