package org.corfudb.universe.api.deployment.docker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.corfudb.universe.api.deployment.DeploymentParams;
import org.corfudb.universe.api.universe.node.NodeParams;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Docker related settings to deploy docker containers and dockerized applications
 *
 * @param <P> node params
 */
@Builder
@EqualsAndHashCode
@ToString
public class DockerContainerParams<P extends NodeParams> implements DeploymentParams<P> {
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
    @Singular
    private final List<String> envs;

    @Getter
    @NonNull
    private final String networkName;

    @Getter
    @NonNull
    @EqualsAndHashCode.Exclude
    private final P applicationParams;

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
    @ToString
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
    @ToString
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
