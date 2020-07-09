package org.corfudb.test.docker;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.test.spec.ClusterDetachRejoinSpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Slf4j
@Tag(TestGroups.BAT_DOCKER)
public class ClusterDetachRejoinDockerTest extends AbstractCorfuUniverseTest {

    private final ClusterDetachRejoinSpec spec = new ClusterDetachRejoinSpec();

    @Test
    public void test() {
        testRunner.executeDockerTest(spec::verifyClusterDetachRejoin);
    }
}
