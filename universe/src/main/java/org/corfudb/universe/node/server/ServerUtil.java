package org.corfudb.universe.node.server;

import org.corfudb.universe.node.NodeException;

import javax.net.ServerSocketFactory;
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
        try (ServerSocket socket = ServerSocketFactory.getDefault().createServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new NodeException("Can't get any open port", e);
        }
    }
}
