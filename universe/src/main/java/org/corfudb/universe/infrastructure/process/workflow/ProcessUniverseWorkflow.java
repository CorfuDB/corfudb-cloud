package org.corfudb.universe.infrastructure.process.workflow;

import lombok.Builder;
import lombok.Getter;
import org.corfudb.universe.api.common.LoggingParams;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.infrastructure.process.universe.ProcessUniverse;
import org.corfudb.universe.scenario.fixture.UniverseFixture;

import java.util.Optional;

@Builder
public class ProcessUniverseWorkflow implements UniverseWorkflow<UniverseParams, UniverseFixture> {
    @Getter
    private final WorkflowContext<UniverseParams, UniverseFixture> context;

    @Override
    public UniverseWorkflow<UniverseParams, UniverseFixture> init() {
        context.getFixture().getCluster().serverVersion(context.getConfig().getCorfuServerVersion());
        return this;
    }

    @Override
    public UniverseWorkflow<UniverseParams, UniverseFixture> initUniverse() {
        if (context.isInitialized()) {
            return this;
        }

        LoggingParams loggingParams = context.getFixture()
                .getLogging()
                .testName(context.getConfig().getTestName())
                .build();

        //Assign universe variable before deploy prevents resources leaks
        ProcessUniverse universe = ProcessUniverse.builder()
                .universeParams(context.getFixture().data())
                .loggingParams(loggingParams)
                .build();

        context.setUniverse(Optional.of(universe));
        context.setInitialized(true);

        return this;
    }
}
