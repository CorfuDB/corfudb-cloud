package org.corfudb.universe.infrastructure.docker.universe.node.server.prometheus;

import java.util.Set;
import java.util.stream.Collectors;

public class PrometheusConfig {

    private PrometheusConfig() {
        //prevent creating PrometheusConfig instances
    }

    private static final String CONFIG =
            "global:%n" +
                    "  scrape_interval:     15s # Set the scrape interval to every 15 seconds. " +
                    "Default is every 1 minute.%n" +
                    "  evaluation_interval: 15s # Evaluate rules every 15 seconds. " +
                    "The default is every 1 minute.%n" +
                    "%n" +
                    "alerting:%n" +
                    "  alertmanagers:%n" +
                    "  - static_configs:%n" +
                    "    - targets:%n" +
                    "      # - alertmanager:9093%n" +
                    "%n" +
                    "rule_files:%n" +
                    "  # - \"first_rules.yml\"%n" +
                    "  # - \"second_rules.yml\"%n" +
                    "%n" +
                    "scrape_configs:%n" +
                    "  - job_name: 'prometheus'%n" +
                    "%n" +
                    "    # metrics_path defaults to '/metrics'%n" +
                    "    # scheme defaults to 'http'.%n" +
                    "%n" +
                    "    static_configs:%n" +
                    "    - targets: ['localhost:9090', %s]%n";

    /**
     * Prometheus config file
     *
     * @param hostname     host name
     * @param metricsPorts metrics port
     * @return prometheus config
     */
    public static String getConfig(String hostname, Set<Integer> metricsPorts) {
        if (metricsPorts.isEmpty()) {
            throw new IllegalStateException("Missing open ports");
        }
        final String metricsExporters = metricsPorts.stream()
                .map(port -> String.format("'%s:%d'", hostname, port))
                .collect(Collectors.joining(","));
        return String.format(CONFIG, metricsExporters);
    }
}
