package org.corfudb.test.docker;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.test.spec.NodeDownAndPartitionedSpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Slf4j
@Tag(TestGroups.BAT_DOCKER)
public class NodeDownAndPartitionedDockerTest extends AbstractCorfuUniverseTest {

    private final NodeDownAndPartitionedSpec spec = new NodeDownAndPartitionedSpec();

    @Test
    public void test() {
        testRunner.executeDockerTest(spec::verifyNodeDownAndPartitioned);
    }

}
