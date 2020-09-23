package org.corfudb.universe.workflow;

import lombok.Builder;
import lombok.Getter;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.logging.LoggingParams;
import org.corfudb.universe.scenario.fixture.Fixtures;
import org.corfudb.universe.universe.vm.ApplianceManager;
import org.corfudb.universe.universe.vm.VmUniverse;
import org.corfudb.universe.api.deployment.vm.VmUniverseParams;

@Builder
public class VmUniverseWorkflow implements UniverseWorkflow<VmUniverseParams, Fixtures.VmUniverseFixture> {
    @Getter
    private final WorkflowContext<VmUniverseParams, Fixtures.VmUniverseFixture> context;

    @Override
    public UniverseWorkflow<VmUniverseParams, Fixtures.VmUniverseFixture> initUniverse() {
        if (context.isInitialized()) {
            return this;
        }

        VmUniverseParams universeParams = context.getFixture().data();

        ApplianceManager manager = ApplianceManager.builder()
                .universeParams(universeParams)
                .build();

        LoggingParams loggingParams = context.getFixture()
                .getLogging()
                .testName(context.getConfig().getTestName())
                .build();

        //Assign universe variable before deploy prevents resources leaks
        VmUniverse universe = VmUniverse.builder()
                .universeParams(universeParams)
                .loggingParams(loggingParams)
                .applianceManager(manager)
                .build();
        context.setUniverse(universe);

        context.setInitialized(true);

        return this;
    }

    @Override
    public UniverseWorkflow<VmUniverseParams, Fixtures.VmUniverseFixture> init() {
        context.getFixture().getCluster().serverVersion(context.getConfig().getCorfuServerVersion());
        return this;
    }
}
