package org.corfudb.universe.universe.node.server;

import org.corfudb.universe.api.universe.node.NodeException;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.IOException;
import java.net.ServerSocket;

public class ServerUtil {

    private ServerUtil() {
        //prevent creating class instances
    }

    /**
     * Generates random open tcp port
     *
     * @return random tcp port
     */
    public static int getRandomOpenPort() {
        try {
            ServerSocket socket = SSLServerSocketFactory.getDefault().createServerSocket(0);
            int localPort = socket.getLocalPort();
            socket.close();
            return localPort;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
