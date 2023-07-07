package org.corfudb.benchmarks.runtime.collections.helper;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NonNull;
import org.corfudb.common.util.ClassUtils;
import org.corfudb.runtime.collections.ICorfuTable;

import java.util.Map;
import java.util.Random;

/**
 * Common corfu table configuration parameters
 */
@Builder
@Getter
public class CorfuTableBenchmarkHelper {

    @Default
    private final Random random = new Random();

    @NonNull
    private final ICorfuTable<Integer, String> table;

    @NonNull
    protected ValueGenerator valueGenerator;

    private final int dataSize;

    private final int tableSize;

    /**
     * Generate a random number
     *
     * @return random number
     */
    public int generate() {
        check();
        return random.nextInt(getTableSize() - 1);
    }

    /**
     * Fill corfu table with random values
     *
     * @return benchmark helper
     */
    public CorfuTableBenchmarkHelper fillTable() {
        check();

        for (int i = 0; i < getTableSize(); i++) {
            table.insert(i, valueGenerator.value());
        }

        return this;
    }

    public String generateValue() {
        check();
        return valueGenerator.value();
    }

    protected void checkDataSize() {
        if (getDataSize() == 0) {
            throw new IllegalStateException("dataSize parameter is not initialized");
        }
    }

    protected void checkTableSize() {
        if (getTableSize() == 0) {
            throw new IllegalStateException("tableSize parameter is not initialized");
        }
    }

    /**
     * Checks corfu table expected state
     *
     * @return benchmark helper
     */
    public CorfuTableBenchmarkHelper check() {
        checkDataSize();
        checkTableSize();

        return this;
    }
}
