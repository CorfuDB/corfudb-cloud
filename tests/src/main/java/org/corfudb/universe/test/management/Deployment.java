package org.corfudb.universe.test.management;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.universe.UniverseManager;
import org.corfudb.universe.test.UniverseConfigurator;
import org.corfudb.universe.api.universe.Universe.UniverseMode;

import static org.corfudb.universe.test.UniverseConfigurator.getServerVersion;

@Slf4j
public class Deployment {

    private final UniverseConfigurator configurator = UniverseConfigurator.builder().build();

    /**
     * Deploying a corfu cluster:
     * - disable shutdown logic to prevent the universe stop and clean corfu servers
     * - deploy corfu server
     */
    public static void main(String[] args) {
        log.info("Deploying corfu cluster...");

        Deployment deployment = new Deployment();
        deployment.deploy();

        log.info("Corfu cluster has deployed");

        System.exit(0);
    }

    private Deployment deploy() {
        UniverseManager universeManager = UniverseManager.builder()
                .testName("corfu_stateful_cluster")
                .universeMode(UniverseMode.VM)
                .corfuServerVersion(getServerVersion())
                .build();

        universeManager.workflow(wf -> {
            wf.setupVm(configurator.vmSetup);
            //disable shutdown logic
            wf.setupVm(fixture -> fixture.getUniverse().cleanUpEnabled(false));
            wf.deploy();
        });

        return this;
    }
}
