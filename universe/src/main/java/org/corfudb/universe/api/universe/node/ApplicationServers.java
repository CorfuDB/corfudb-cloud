package org.corfudb.universe.api.universe.node;

import org.corfudb.universe.universe.node.client.LocalCorfuClient;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams;

public interface ApplicationServers {

    interface CorfuApplicationServer extends ApplicationServer<CorfuServerParams> {

        LocalCorfuClient getLocalCorfuClient();
    }
}
