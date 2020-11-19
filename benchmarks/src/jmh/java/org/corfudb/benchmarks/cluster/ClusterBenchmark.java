package org.corfudb.benchmarks.cluster;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.benchmarks.util.DataGenerator;
import org.corfudb.runtime.collections.CorfuTable;
import org.corfudb.universe.api.UniverseManager;
import org.corfudb.universe.api.universe.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.api.workflow.UniverseWorkflow.WorkflowConfig;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster.GenericCorfuCluster;
import org.corfudb.universe.universe.node.client.CorfuClient;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.corfudb.benchmarks.util.DataUnit.KB;
import static org.corfudb.benchmarks.util.DataUnit.MB;

/**
 * The benchmark measures corfu's table performance (put operation).
 * see: docs/benchmarks/corfu-table.md
 */
@Slf4j
public class ClusterBenchmark {
    // Default name of the CorfuTable created by CorfuClient
    private static final String DEFAULT_STREAM_NAME = "stream";

    /**
     * Cluster benchmark
     *
     * @param args args
     * @throws RunnerException jmh exception
     */
    public static void main(String[] args) throws RunnerException {
        String benchmarkName = ClusterBenchmark.class.getSimpleName();

        int warmUpIterations = 0;

        int measurementIterations = 1;
        TimeValue measurementTime = TimeValue.seconds(30);

        int threads = 4;
        int forks = 1;

        String[] dataSizes = Stream
                .of(KB.toBytes(4), MB.toBytes(2))
                .map(String::valueOf)
                .toArray(String[]::new);

        String[] numRuntime = {"1"};
        String[] numTables = {"4"};

        String[] numServers = {"1", "3"};

        Options opt = new OptionsBuilder()
                .include(benchmarkName)

                .mode(Mode.Throughput)
                .timeUnit(TimeUnit.SECONDS)

                .warmupIterations(warmUpIterations)

                .measurementIterations(measurementIterations)
                .measurementTime(measurementTime)

                .param("dataSize", dataSizes)
                .param("numRuntime", numRuntime)
                .param("numTables", numTables)
                .param("numServers", numServers)

                .threads(threads)
                .forks(forks)

                .shouldFailOnError(true)

                .resultFormat(ResultFormatType.CSV)
                .result(Paths.get("benchmarks", "build", benchmarkName + ".csv").toString())

                .build();

        new Runner(opt).run();
    }

    @State(Scope.Benchmark)
    @Getter
    @Slf4j
    public static class ClusterBenchmarkState {

        public final Random rnd = new Random();

        private UniverseWorkflow workflow;

        @Param({})
        private int dataSize;

        @Param({})
        public int numRuntime;

        @Param({})
        public int numTables;

        @Param({})
        public int numServers;

        private String data;

        public final List<CorfuClient> corfuClients = new ArrayList<>();
        public final List<CorfuTable<String, String>> tables = new ArrayList<>();
        public final AtomicInteger counter = new AtomicInteger(1);

        /**
         * Init benchmark state
         */
        @Setup
        public void init() {

            data = DataGenerator.generateDataString(dataSize);

            WorkflowConfig config = WorkflowConfig.builder()
                    .testName("corfu_cluster_benchmark")
                    .corfuServerVersion(getAppVersion())
                    .build();

            UniverseManager universeManager = UniverseManager.builder()
                    .config(config)
                    .build();

            workflow = universeManager.dockerWorkflow(wf -> {
                wf.setup(fixture -> {
                    fixture.getCluster().numNodes(numServers);
                    fixture.getCommonServerParams().universeDirectory(Paths.get("benchmarks", "build"));

                    //disable automatic shutdown
                    fixture.getUniverse().cleanUpEnabled(false);
                });

                wf.deploy();

                GenericCorfuCluster corfuCluster = wf.getUniverse().getGroup(ClusterType.CORFU);

                for (int i = 0; i < numRuntime; i++) {
                    corfuClients.add(corfuCluster.getLocalCorfuClient());
                }

                for (int i = 0; i < numTables; i++) {
                    CorfuClient corfuClient = getRandomCorfuClient();
                    CorfuTable<String, String> table = corfuClient
                            .createDefaultCorfuTable(DEFAULT_STREAM_NAME + i);
                    tables.add(table);
                }
            });
        }

        public CorfuClient getRandomCorfuClient() {
            return corfuClients.get(rnd.nextInt(numRuntime));
        }

        /**
         * Returns a random table from the list of corfu tables generated during initialization
         *
         * @return random corfu table from the list
         */
        public CorfuTable<String, String> getRandomTable() {
            return tables.get(rnd.nextInt(numTables));
        }

        /**
         * Tear down the state after the benchmark is finished
         */
        @TearDown
        public void tearDown() {

            for (CorfuClient corfuClient : corfuClients) {
                corfuClient.shutdown();
            }
            workflow.getUniverse().shutdown();
        }

        /**
         * Provides a current version of this project. It parses the version from pom.xml
         *
         * @return maven/project version
         */
        private String getAppVersion() {
            //Temporary limitation, will de fixed soon.
            // The problem is that the resource files are in a different directory: build/resources,
            // to move them to classes directory as IDEA expected we need to change output dir for resources
            //https://docs.gradle.org/current/dsl/org.gradle.api.tasks.SourceSetOutput.html

            //return new UniverseAppUtil().getAppVersion();
            return "0.3.0-SNAPSHOT";
        }
    }

    /**
     * Measure corfu table `put` operation performance
     *
     * @param state benchmark state
     */
    @Benchmark
    public void clusterBenchmark(ClusterBenchmarkState state) {
        String key = String.valueOf(state.counter.getAndIncrement());
        CorfuTable<String, String> table = state.getRandomTable();
        table.put(key, state.data);
    }
}
