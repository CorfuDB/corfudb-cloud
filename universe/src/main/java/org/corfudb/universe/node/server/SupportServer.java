package org.corfudb.universe.node.server;

import org.corfudb.universe.api.node.Node;

public interface SupportServer extends Node {
    @Override
    SupportServer deploy();
}
