package org.corfudb.test.vm.stateful.ufo;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.test.spec.NodeUpAndPartitionedSpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Slf4j
@Tag(TestGroups.BAT)
@Tag(TestGroups.STATEFUL)
public class NodeUpAndPartitionedVmTest extends AbstractCorfuUniverseTest {
    private final NodeUpAndPartitionedSpec spec = new NodeUpAndPartitionedSpec();

    @Test
    public void test() {
        testRunner.executeStatefulVmTest(spec::nodeUpAndPartitioned);
    }

}
