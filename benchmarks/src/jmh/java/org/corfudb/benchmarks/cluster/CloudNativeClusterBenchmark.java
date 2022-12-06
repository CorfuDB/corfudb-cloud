package org.corfudb.benchmarks.cluster;

import com.google.common.reflect.TypeToken;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.benchmarks.util.DataGenerator;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.CorfuRuntime.CorfuRuntimeParameters.CorfuRuntimeParametersBuilder;
import org.corfudb.runtime.collections.CorfuTable;
import org.corfudb.universe.api.UniverseManager;
import org.corfudb.universe.api.universe.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.api.workflow.UniverseWorkflow.WorkflowConfig;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster.GenericCorfuCluster;
import org.corfudb.universe.universe.node.client.CorfuClient;
import org.corfudb.util.NodeLocator;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The benchmark measures corfu's table performance (put operation).
 * see: docs/benchmarks/corfu-table.md
 */
@Slf4j
public class CloudNativeClusterBenchmark {
    // Default name of the CorfuTable created by CorfuClient
    private static final String DEFAULT_STREAM_NAME = "stream";

    /**
     * Cluster benchmark
     *
     * @param args args
     * @throws RunnerException jmh exception
     */
    public static void main(String[] args) throws Exception {
        String benchmarkName = CloudNativeClusterBenchmark.class.getSimpleName();

        Path benchmarksReportFile = Paths.get("benchmarks", "report", benchmarkName + ".csv");
        benchmarksReportFile.toFile().mkdirs();

        log.info("Start {}", benchmarkName);

        Options opt = new OptionsBuilder()
                .include(benchmarkName)
                .shouldFailOnError(true)
                .resultFormat(ResultFormatType.CSV)
                .result(benchmarksReportFile.toString())
                .build();

        new Runner(opt).run();
    }

    /**
     * Measure corfu table `put` operation performance
     *
     * @param state benchmark state
     */
    @Benchmark
    @Warmup(iterations = 0)
    @Measurement(iterations = 1, time = 30)
    @BenchmarkMode(Mode.Throughput)
    @Threads(4)
    @Fork(1)
    public void clusterBenchmark(ClusterBenchmarkState state) {
        String key = String.valueOf(state.counter.getAndIncrement());
        CorfuTable<String, String> table = state.getRandomTable();
        table.put(key, state.data);
    }

    @State(Scope.Benchmark)
    @Getter
    @Slf4j
    public static class ClusterBenchmarkState {

        public final Random rnd = new Random();

        /**
         * 4kb and 8rb
         */
        @Param({"4096", "8096"})
        private int dataSize;

        @Param({"1"})
        public int numRuntime;

        @Param({"4"})
        public int numTables;

        @Param({"1", "3"})
        public int numServers;

        private String data;

        public final List<CorfuRuntime> corfuClients = new ArrayList<>();
        public final List<CorfuTable<String, String>> tables = new ArrayList<>();
        public final AtomicInteger counter = new AtomicInteger(1);

        /**
         * Init benchmark state
         */
        @Setup
        public void init() {

            data = DataGenerator.generateDataString(dataSize);

            for (int i = 0; i < numRuntime; i++) {
                corfuClients.add(buildCorfuClient());
            }

            for (int i = 0; i < numTables; i++) {
                CorfuRuntime runtime = getRandomCorfuClient();
                CorfuTable<String, String> table = createDefaultCorfuTable(runtime, DEFAULT_STREAM_NAME + i);
                tables.add(table);
            }
        }

        private CorfuTable<String, String> createDefaultCorfuTable(CorfuRuntime runtime, String tableName) {
            return runtime.getObjectsView()
                    .build()
                    .setStreamName(tableName)
                    .setTypeToken(new TypeToken<CorfuTable<String, String>>() {})
                    .open();
        }

        private CorfuRuntime buildCorfuClient() {
            NodeLocator loc = NodeLocator.builder()
                    .host("corfu-0")
                    .port(9000)
                    .build();

            CorfuRuntimeParametersBuilder builder = CorfuRuntime.CorfuRuntimeParameters
                    .builder()
                    .connectionTimeout(Duration.ofSeconds(5))
                    .layoutServers(Collections.singletonList(loc));

            CorfuRuntime runtime = CorfuRuntime.fromParameters(builder.build());
            runtime.connect();
            return runtime;
        }

        public CorfuRuntime getRandomCorfuClient() {
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
            for (CorfuRuntime corfuClient : corfuClients) {
                corfuClient.shutdown();
            }
        }
    }
}
