package org.corfudb.benchmarks.runtime.collections.state;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.benchmarks.runtime.collections.helper.CorfuTableBenchmarkHelper;
import org.corfudb.benchmarks.runtime.collections.helper.ValueGenerator.StaticValueGenerator;
import org.corfudb.benchmarks.util.SizeUnit;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.ICorfuTable;
import org.corfudb.runtime.collections.PersistedCorfuTable;
import org.corfudb.runtime.collections.PersistentCorfuTable;
import org.corfudb.util.serializer.ISerializer;
import org.corfudb.util.serializer.Serializers;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;


@Slf4j
public abstract class HashMapState {

    @Getter
    CorfuRuntime corfuRuntime;

    @Getter
    CorfuTableBenchmarkHelper helper;

    private final String tableName = "InMemoryTable";

    void init(int dataSize, int tableSize) {
        log.info("Initialization...");

        StaticValueGenerator valueGenerator = new StaticValueGenerator(dataSize);
        ICorfuTable<Integer, String> table = corfuRuntime.getObjectsView().build()
                .setTypeToken(PersistentCorfuTable.<Integer, String>getTypeToken())
                .setStreamName(tableName)
                .setSerializer(Serializers.PRIMITIVE)
                .open();

        helper = CorfuTableBenchmarkHelper.builder()
                .valueGenerator(valueGenerator)
                .table(table)
                .dataSize(dataSize)
                .tableSize(tableSize)
                .build()
                .check();
    }

    @State(Scope.Benchmark)
    @Getter
    @Slf4j
    public static class HashMapStateForGet extends HashMapState {

        @Param({"64", "256", "1024"})
        @Getter
        public int dataSize;

        @Getter
        @Param({"10000"})
        protected int inMemTableSize;

        @Setup
        public void init() {
            init(dataSize, inMemTableSize);
            helper.fillTable();
        }
    }

    @State(Scope.Benchmark)
    @Slf4j
    public static class HashMapStateForPut extends HashMapState {

        @Param({"64", "256"})
        @Getter
        public int dataSize;

        @Getter
        protected int tableSize = SizeUnit.HUNDRED_K.getValue();

        @Setup
        public void init() {
            init(dataSize, tableSize);
        }
    }
}
