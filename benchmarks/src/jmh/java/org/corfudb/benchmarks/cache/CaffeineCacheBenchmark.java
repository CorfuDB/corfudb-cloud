package org.corfudb.benchmarks.cache;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.corfudb.benchmarks.util.DataGenerator;
import org.corfudb.infrastructure.LogUnitServer.LogUnitServerConfig;
import org.corfudb.infrastructure.LogUnitServerCache.DataCacheConfigurator;
import org.corfudb.protocols.CorfuProtocolCommon;
import org.corfudb.protocols.wireprotocol.DataType;
import org.corfudb.protocols.wireprotocol.ILogData;
import org.corfudb.protocols.wireprotocol.IMetadata;
import org.corfudb.protocols.wireprotocol.IToken;
import org.corfudb.runtime.CorfuRuntime;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class CaffeineCacheBenchmark {

    /**
     * <a href="https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_35_Profilers.java">
     * JMH sample
     * </a>
     * <p>
     * <a href="https://github.com/ben-manes/caffeine/wiki/Efficiency">TinyLFU</a>
     *
     * @param args args
     * @throws Exception ex
     */
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(CaffeineCacheBenchmark.class.getSimpleName())
                .shouldFailOnError(true)
                //.forks(1)
                //.jvmArgs("")
                .build();

        new Runner(opt).run();
    }

    @State(Scope.Benchmark)
    public static class DbState {
        ConcurrentMap<Long, ILogData> db = new ConcurrentHashMap<>();
    }


    private static final Random rnd = new Random();

    @Benchmark
    @Fork(jvmArgs = {"-Xmx5000m"}, jvmArgsAppend = "-XX:+PrintGCDetails")
    public void test(DbState dbState) {
        int total = 100_000;
        int cacheSize = total / 3;

        for (int i = 0; i < total; i++) {
            ILogData record = new TestLogData();
            dbState.db.put((long) i, record);
        }

        int entitySize = 5_000;
        LogUnitServerConfig config = LogUnitServerConfig.builder()
                .cacheSizeHeapRatio(0.1)
                .maxCacheSize(cacheSize * entitySize)
                .noSync(true)
                .memoryMode(true)
                .build();

        LoadingCache<Long, ILogData> cache = DataCacheConfigurator.builder()
                .config(config)
                .cacheLoader(address -> load(address, dbState))
                .build()
                .buildDataCache();

        for (int i = 0; i < 1_000_000; i++) {
            try {
                ILogData result = cache.get((long) rnd.nextInt(total));

                if ((i + 1) % 1000 == 0) {
                    System.out.println(cache.stats());
                    //System.out.println(result.getTotalSize());
                    System.out.println("Cache size: " + cache.estimatedSize());
                    if (cache.estimatedSize() < 500) {
                        throw new RuntimeException("errar");
                    }
                }
            } catch (Exception ex) {
                //ignore
            }
        }
    }

    private ILogData load(long address, DbState dbState) {
        return dbState.db.get(address);
    }

    private static class TestLogData implements ILogData {
        private final Random rnd = new Random();

        private final EnumMap<LogUnitMetadataType, Object> backPointerMap;
        private final String payload;

        public TestLogData() {
            EnumMap<LogUnitMetadataType, Object> metaDataMap = new EnumMap<>(LogUnitMetadataType.class);

            Map<UUID, Long> initialBackPointerMap = new HashMap<>();
            initialBackPointerMap.put(UUID.randomUUID(), (long)rnd.nextInt(100_000));

            metaDataMap.put(LogUnitMetadataType.BACKPOINTER_MAP, initialBackPointerMap);

            backPointerMap = metaDataMap;
            payload = DataGenerator.generateDataString(rnd.nextInt(10_000));
        }

        @Override
        public Object getPayload(CorfuRuntime t) {
            return payload;
        }

        @Override
        public DataType getType() {
            return DataType.DATA;
        }

        @Override
        public void releaseBuffer() {
            //ignore
        }

        @Override
        public void acquireBuffer(boolean b) {
            //ignore
        }

        @Override
        public int getSizeEstimate() {
            return rnd.nextInt(10_000);
        }

        @Override
        public void useToken(IToken token) {
            //ignore
        }

        @Override
        public EnumMap<LogUnitMetadataType, Object> getMetadataMap() {
            return backPointerMap;
        }
    }
}
