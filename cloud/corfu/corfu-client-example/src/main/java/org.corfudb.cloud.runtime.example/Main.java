package org.corfudb.cloud.runtime.example;

import com.google.common.reflect.TypeToken;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.CorfuTable;
import org.corfudb.util.NodeLocator;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;


/**
 * This tutorial demonstrates a simple Corfu application.
 *
 */
public class Main {
    private static final String USAGE = "Usage: HelloCorfu [-c <conf>]\n"
            + "Options:\n"
            + " -c <conf>     Set the configuration host and port  [default: localhost:9000]\n";

    /**
     * Internally, the corfuRuntime interacts with the CorfuDB service over TCP/IP sockets.
     *
     * @param host specifies the IP:port of the CorfuService
     *                            The configuration string has format "hostname:port", for example, "localhost:9090".
     * @return a CorfuRuntime object, with which Corfu applications perform all Corfu operations
     */
    private static CorfuRuntime getRuntimeAndConnect(String host) {

        NodeLocator loc = NodeLocator.builder().host(host).port(9000).build();

        CorfuRuntime.CorfuRuntimeParameters params = CorfuRuntime.CorfuRuntimeParameters
                .builder()
                .connectionTimeout(Duration.ofSeconds(2))
                .layoutServers(Collections.singletonList(loc))
                .build();

        CorfuRuntime runtime = CorfuRuntime.fromParameters(params);
        runtime.connect();
        return runtime;
    }

    // Sample code
    public static void main(String[] args) {
        System.out.println("Start application");
        // Parse the options given, using docopt.
        /*
        Map<String, Object> opts =
                new Docopt(USAGE)
                        .withVersion(GitRepositoryState.getRepositoryState().describe)
                        .parse(args);
        String corfuConfigurationString = (String) opts.get("-c");
        */
        /**
         * First, the application needs to instantiate a CorfuRuntime,
         * which is a Java object that contains all of the Corfu utilities exposed to applications.
         */
        String ip  = "localhost";
        if(args.length>=1) {
            ip  = args[0];

        }

        CorfuRuntime runtime = getRuntimeAndConnect(ip);

        /**
         * Obviously, this application is not doing much yet,
         * but you can already invoke getRuntimeAndConnect to test if you can connect to a deployed Corfu service.
         *
         * Above, you will need to point it to a host and port which is running the service.
         * See {@link https://github.com/CorfuDB/CorfuDB} for instructions on how to deploy Corfu.
         */

        /**
         * Next, we will illustrate how to declare a Java object backed by a Corfu Stream.
         * A Corfu Stream is a log dedicated specifically to the history of updates of one object.
         * We will instantiate a stream by giving it a name "A",
         * and then instantiate an object by specifying its class
         */
        Map<String, Integer> map = runtime.getObjectsView()
                .build()
                .setStreamName("A")     // stream name
                .setTypeToken(new TypeToken<CorfuTable<String, Integer>>() {})
                .open();                // instantiate the object!

        /**
         * The magic has already happened! map is an in-memory view of a shared map, backed by the Corfu log.
         * The application can perform put and get on this map from different application instances,
         * crash and restart applications, and so on.
         * The map will persist and be consistent across all applications.
         *
         * For example, try the following code repeatedly in a sequence, in between run/exit,
         * from multiple instances, and see the different interleaving of values that result.
         */
        Integer previous = map.get("a");
        if (previous == null) {
            System.out.println("This is the first time we were run!");
            map.put("a", 1);
        }
        else {
            map.put("a", ++previous);
            System.out.println("This is the " + previous + " time we were run!");
        }
    }
}