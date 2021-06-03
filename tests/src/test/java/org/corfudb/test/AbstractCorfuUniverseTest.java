package org.corfudb.test;

import com.google.common.collect.ImmutableSet;
import lombok.Builder;
import lombok.NonNull;
import org.corfudb.universe.api.UniverseManager;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.cluster.Cluster;
import org.corfudb.universe.api.universe.node.CommonNodeParams;
import org.corfudb.universe.api.universe.node.Node;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.api.workflow.UniverseWorkflow.WorkflowConfig;
import org.corfudb.universe.infrastructure.docker.universe.FakeDns;
import org.corfudb.universe.infrastructure.docker.universe.group.cluster.DockerCorfuCluster;
import org.corfudb.universe.infrastructure.docker.workflow.DockerUniverseWorkflow;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.scenario.fixture.UniverseFixture;
import org.corfudb.universe.test.UniverseConfigurator;
import org.corfudb.universe.test.log.TestLogHelper;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import java.net.InetAddress;
import java.nio.file.Path;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.fail;
import static org.corfudb.universe.test.UniverseConfigurator.getServerVersion;

public abstract class AbstractCorfuUniverseTest {
    protected CorfuUniverseTestRunner testRunner;

    /**
     * testSetUp method is used for setting up configurations before each test.
     * It will be called before each test and register the test with MDC, so
     * that each test can log into different files.
     */
    @BeforeEach
    public void testSetUp(TestInfo testInfo) {
        TestLogHelper.startTestLogging(getClass());
        testRunner = CorfuUniverseTestRunner.builder()
                .testName(getClass().getSimpleName() + "#" + testInfo.getDisplayName())
                .build();
    }

    /**
     * testCleanUp method is used to do clean up after each test executed.
     * It will be called after each test and remove the test from MDC.
     */
    @AfterEach
    public void testCleanUp() {
        TestLogHelper.stopTestLogging();
    }

    /**
     * Get Docker Corfu container params with specified version and port.
     */
    public DockerContainerParams<CorfuServerParams> getContainerParams(DockerCorfuCluster corfuCluster,
            String networkName, int serverPort, String serverVersion) {
        Path universeDir = corfuCluster.getParams()
                .getNodesParams().first()
                .getApplicationParams()
                .getCommonParams()
                .getUniverseDirectory();

        CommonNodeParams commonParams  = CommonNodeParams.builder()
                .ports(ImmutableSet.of(serverPort))
                .clusterName(corfuCluster.getParams().getName())
                .nodeType(Node.NodeType.CORFU)
                .universeDirectory(universeDir)
                .build();

        DockerContainerParams.PortBinding dockerPort = DockerContainerParams.PortBinding.builder()
                .hostPort(serverPort)
                .containerPort(serverPort)
                .build();

        CorfuServerParams appParams = CorfuServerParams.builder()
                .commonParams(commonParams)
                .serverVersion(serverVersion)
                .build();

        DockerContainerParams<CorfuServerParams> containerParams = DockerContainerParams
                .<CorfuServerParams>builder()
                .image("corfudb/corfu-server")
                .imageVersion(serverVersion)
                .networkName(networkName)
                .applicationParams(appParams)
                .port(dockerPort)
                .build();

        FakeDns.getInstance().addForwardResolution(appParams.getName(), InetAddress.getLoopbackAddress());

        return containerParams;
    }

    /**
     * Get a 3 node Docker Corfu server cluster with cross version setup. The node with primary sequencer
     * has the latest Corfu server version, and the other 2 nodes have older version. This is a default
     * setup for cross version testing.
     */
    public DockerCorfuCluster getDefaultCrossVersionCluster(DockerUniverseWorkflow wf) {
        DockerCorfuCluster corfuCluster = wf
                .getUniverse()
                .getGroup(Cluster.ClusterType.CORFU);

        String networkName = wf.getUniverse().getUniverseParams().getNetworkName();

        DockerContainerParams<CorfuServerParams> containerParamsV1 = getContainerParams(
                corfuCluster, networkName, 9005, "BASE");
        DockerContainerParams<CorfuServerParams> containerParamsV2 = getContainerParams(
                corfuCluster, networkName, 9006, "BASE");

        corfuCluster.add(containerParamsV1);
        corfuCluster.add(containerParamsV2);

        return corfuCluster;
    }

    @Builder
    public static class CorfuUniverseTestRunner {
        private final UniverseConfigurator configurator = UniverseConfigurator.builder().build();
        @NonNull
        private final String testName;

        /**
         * executeTest method is used for executing different tests with given workflows.
         * This method will set up VM environment and run the test.
         *
         * @param test The test function need to be executed.
         */
        public void executeStatefulVmTest(TestAction test) {
            WorkflowConfig config = getConfig();

            UniverseManager.builder()
                    .config(config)
                    .build()
                    .vmWorkflow(wf -> {
                        wf.setup(configurator.vmSetup);
                        wf.setup(fixture -> {
                            //don't stop corfu cluster after the test
                            fixture.getUniverse().cleanUpEnabled(false);
                        });
                        wf.initUniverse();
                        try {
                            test.execute(wf);
                        } catch (Exception e) {
                            fail("Failed: ", e);
                        }
                    });
        }

        private WorkflowConfig getConfig() {
            return WorkflowConfig.builder()
                    .testName(testName)
                    .corfuServerVersion(getServerVersion())
                    .build();
        }

        /**
         * Execute tests with custom "setup" strategy
         * @param test universe test
         * @param customSetup custom setup
         */
        public void executeDockerTest(Consumer<UniverseFixture> customSetup,
                                      TestAction<UniverseParams, UniverseFixture, DockerUniverseWorkflow> test) {
            WorkflowConfig config = getConfig();

            UniverseManager.builder()
                    .config(config)
                    .build()
                    .dockerWorkflow(wf -> {
                        wf.setup(configurator.dockerSetup);
                        wf.setup(customSetup);
                        wf.deploy();
                        try {
                            test.execute(wf);
                        } catch (Exception e) {
                            fail("Failed: ", e);
                        }
                        wf.getUniverse().shutdown();
                    });
        }

        /**
         * executeTest method is used for executing different tests with given workflows.
         * This method will set up docker environment and run the test.
         *
         * @param test The test function need to be executed.
         */
        public void executeDockerTest(TestAction<UniverseParams, UniverseFixture, DockerUniverseWorkflow> test) {

            /*
              An example of a custom setup
             */
            Consumer<UniverseFixture> setup = fixture -> {
                //GroupParams.BootstrapParams bootstrapParams =
                //        GroupParams.BootstrapParams.builder().enabled(false).build();
                //fixture.getCassandraCommonParams().enabled(true);
                //fixture.getMangleCommonParams().enabled(true);
                //fixture.getLongevityAppCommonParams().enabled(true);
                //fixture.getCluster().numNodes(1);
                //fixture.getCluster().bootstrapParams(bootstrapParams);
            };

            executeDockerTest(setup, test);
        }

        @FunctionalInterface
        public interface TestAction<P extends UniverseParams, F extends Fixture<P>, U extends UniverseWorkflow<P, F>> {
            void execute(U wf) throws Exception;
        }
    }
}
