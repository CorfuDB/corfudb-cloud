package org.corfudb.universe.api.universe.node.stress;

public interface Stress {

    /**
     * To stress CPU usage on a node.
     */
    void stressCpuLoad();

    /**
     * To stress IO usage on a node.
     */
    void stressIoLoad();

    /**
     * To stress memory (RAM) usage on a node.
     */
    void stressMemoryLoad();

    /**
     * To stress disk usage on a node.
     */
    void stressDiskLoad();

    /**
     * To release the existing stress load on a node.
     */
    void releaseStress();
}
