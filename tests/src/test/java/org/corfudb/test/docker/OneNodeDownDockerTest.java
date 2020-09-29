package org.corfudb.test.docker;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.test.spec.OneNodeDownSpec;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.infrastructure.docker.workflow.DockerUniverseWorkflow;
import org.corfudb.universe.scenario.fixture.Fixtures;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Slf4j
@Tag(TestGroups.BAT_DOCKER)
public class OneNodeDownDockerTest extends AbstractCorfuUniverseTest {
    private final OneNodeDownSpec spec = new OneNodeDownSpec();

    @Test
    public void test() {
        testRunner.executeDockerTest(spec::oneNodeDownTest);
    }

}
