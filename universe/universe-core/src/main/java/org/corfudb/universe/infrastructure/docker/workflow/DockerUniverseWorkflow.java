package org.corfudb.universe.infrastructure.docker.workflow;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DockerClientBuilder;
import lombok.Builder;
import lombok.Getter;
import org.corfudb.universe.api.common.LoggingParams;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.infrastructure.docker.universe.DockerUniverse;
import org.corfudb.universe.scenario.fixture.UniverseFixture;

import java.util.Optional;

@Builder
public class DockerUniverseWorkflow implements UniverseWorkflow<UniverseParams, UniverseFixture> {
    @Getter
    private final WorkflowContext<UniverseParams, UniverseFixture> context;

    @Override
    public UniverseWorkflow<UniverseParams, UniverseFixture> init() {
        UniverseFixture fixture = context.getFixture();
        String corfuServerVersion = context.getConfig().getCorfuServerVersion();
        fixture.getCluster().serverVersion(corfuServerVersion);
        fixture.getLongevityContainerParams().imageVersion(corfuServerVersion);
        fixture.getLongevityApp().serverVersion(corfuServerVersion);
        return this;
    }

    @Override
    public UniverseWorkflow<UniverseParams, UniverseFixture> initUniverse() {

        if (context.isInitialized()) {
            return this;
        }

        DockerClient docker = DockerClientBuilder.getInstance().build();

        LoggingParams loggingParams = context.getFixture()
                .getLogging()
                .testName(context.getConfig().getTestName())
                .build();

        DockerUniverse universe = DockerUniverse.builder()
                .universeParams(context.getFixture().data())
                .loggingParams(loggingParams)
                .docker(docker)
                .build();

        context.setUniverse(Optional.of(universe));

        context.setInitialized(true);

        return this;
    }
}
