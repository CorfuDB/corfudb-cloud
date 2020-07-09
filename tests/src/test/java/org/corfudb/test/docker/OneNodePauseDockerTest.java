package org.corfudb.test.docker;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.test.spec.OneNodePauseSpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Slf4j
@Tag(TestGroups.BAT_DOCKER)
public class OneNodePauseDockerTest extends AbstractCorfuUniverseTest {
    private final OneNodePauseSpec spec = new OneNodePauseSpec();

    @Test
    public void test() {
        testRunner.executeDockerTest(spec::verifyOneNodePause);
    }
}