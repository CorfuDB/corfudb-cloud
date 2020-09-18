package org.corfudb.universe.workflow;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import lombok.Builder;
import lombok.Getter;
import org.corfudb.universe.api.universe.UniverseException;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.logging.LoggingParams;
import org.corfudb.universe.scenario.fixture.Fixtures.UniverseFixture;
import org.corfudb.universe.universe.docker.DockerUniverse;

@Builder
public class DockerUniverseWorkflow implements UniverseWorkflow<UniverseParams, UniverseFixture> {
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

        DefaultDockerClient docker;
        try {
            docker = DefaultDockerClient.fromEnv().build();
        } catch (DockerCertificateException e) {
            throw new UniverseException("Can't initialize docker client");
        }

        LoggingParams loggingParams = context.getFixture()
                .getLogging()
                .testName(context.getConfig().getTestName())
                .build();

        DockerUniverse universe = DockerUniverse.builder()
                .universeParams(context.getFixture().data())
                .loggingParams(loggingParams)
                .docker(docker)
                .build();

        context.setUniverse(universe);

        context.setInitialized(true);

        return this;
    }
}
