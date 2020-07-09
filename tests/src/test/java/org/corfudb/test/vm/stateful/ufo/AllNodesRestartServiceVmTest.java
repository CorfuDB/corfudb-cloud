package org.corfudb.test.vm.stateful.ufo;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.test.spec.AllNodesRestartServiceSpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Slf4j
@Tag(TestGroups.BAT)
@Tag(TestGroups.STATEFUL)
public class AllNodesRestartServiceVmTest extends AbstractCorfuUniverseTest {

    private final AllNodesRestartServiceSpec spec = new AllNodesRestartServiceSpec();

    @Test
    public void test() {
        testRunner.executeStatefulVmTest(spec::verifyRestartService);
    }
}
