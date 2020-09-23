package org.corfudb.universe.api.deployment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;

import java.nio.file.Path;
import java.util.Set;

@Builder
public class DockerContainerParams {
    @NonNull
    private final String image;
    @Default
    @NonNull
    private final String imageVersion = "latest";

    @Getter
    @Singular
    private final Set<PortBinding> ports;

    @Getter
    @Singular
    private final Set<VolumeBinding> volumes;

    @Getter
    @NonNull
    private final String networkName;

    /**
     * Provides full docker image name
     *
     * @return docker image name
     */
    public String getImageFullName() {
        return image + ":" + imageVersion;
    }

    @Builder
    @AllArgsConstructor
    @Getter
    public static class PortBinding {
        private final int hostPort;
        private final int containerPort;

        public PortBinding(int port) {
            hostPort = port;
            containerPort = port;
        }
    }

    @Builder
    @Getter
    public static class VolumeBinding {
        @NonNull
        private final Path hostPath;

        @NonNull
        private final Path containerPath;

        @Default
        private final BindMode mode = BindMode.READ_WRITE;

        public enum BindMode {
            READ_ONLY("ro"), READ_WRITE("rw");

            public final String accessMode;

            BindMode(String accessMode) {
                this.accessMode = accessMode;
            }
        }
    }
}
