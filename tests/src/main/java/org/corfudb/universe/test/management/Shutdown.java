package org.corfudb.universe.test.management;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.universe.api.UniverseManager;
import org.corfudb.universe.api.workflow.UniverseWorkflow.WorkflowConfig;
import org.corfudb.universe.test.UniverseConfigurator;

import static org.corfudb.universe.test.UniverseConfigurator.getServerVersion;

@Slf4j
public class Shutdown {

    private final UniverseConfigurator configurator = UniverseConfigurator.builder().build();

    /**
     * Shutdown corfu cluster: stop corfu processes and delete corfu directory.
     *
     * @param args args
     */
    public static void main(String[] args) {
        log.info("Corfu cluster is being shutdown...");
        new Shutdown().shutdown();
        log.info("Corfu cluster shutdown has finished ");

        System.exit(0);
    }

    private Shutdown shutdown() {
        WorkflowConfig config = WorkflowConfig.builder()
                .testName("corfu_stateful_cluster")
                .corfuServerVersion(getServerVersion())
                .build();
        UniverseManager universeManager = UniverseManager.builder()
                .config(config)
                .build();

        universeManager.vmWorkflow(wf -> {
            wf.setup(configurator.vmSetup);
            wf.initUniverse();
            wf.getUniverse().shutdown();
        });

        return this;
    }
}
