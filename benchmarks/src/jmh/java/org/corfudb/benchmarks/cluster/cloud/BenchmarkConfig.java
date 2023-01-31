package org.corfudb.benchmarks.cluster.cloud;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
public class BenchmarkConfig {
    public BenchmarkTlsConfig tls;
    public BenchmarkParams benchmark;

    @Getter
    @ToString
    @NoArgsConstructor
    public static class BenchmarkParams {
        public PutParams put;
        public GetParams get;

        public MeasurementParams warmup;
        public MeasurementParams measurement;

        public RandomizedPutParams randomizedPut;

        public int threads;
        public int forks;

        public int coolOffPeriodMinutes;
    }

    @Getter
    @ToString
    @NoArgsConstructor
    public static class MeasurementParams {
        public int iterations;
        public int timeInMinutes;
    }

    @Getter
    @ToString
    @NoArgsConstructor
    public static class BenchmarkTlsConfig {
        public String keystore;
        public String keystorePassword;

        public String truststore;
        public String truststorePassword;
    }

    @Getter
    @ToString
    @NoArgsConstructor
    public static class PutParams {
        public String[] dataSize;
        public String[] putNumRuntimes;
        public String[] putNumTables;
    }

    @Getter
    @ToString
    @NoArgsConstructor
    public static class GetParams {
        public String[] dataSizeForGetOperation;
        public String[] getNumRuntimes;
        public String[] getNumTables;
    }

    @Getter
    @ToString
    @NoArgsConstructor
    public static class RandomizedPutParams {
        public String minDataSize;
        public String maxDataSize;
        public String[] numRuntimes;
        public String[] numTables;
    }
}
