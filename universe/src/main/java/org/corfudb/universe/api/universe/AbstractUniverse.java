package org.corfudb.universe.api.universe;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.common.util.ClassUtils;
import org.corfudb.universe.api.deployment.DeploymentParams;
import org.corfudb.universe.api.group.Group;
import org.corfudb.universe.api.group.Group.GroupParams;
import org.corfudb.universe.api.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.api.node.Node.NodeParams;
import org.corfudb.universe.logging.LoggingParams;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
public abstract class AbstractUniverse implements Universe {
    @Getter
    @NonNull
    protected final UniverseParams universeParams;
    @Getter
    @NonNull
    protected final UUID universeId;

    @NonNull
    protected final LoggingParams loggingParams;

    protected final ConcurrentMap<String, Group<NodeParams, ?, ?, ?>> groups = new ConcurrentHashMap<>();

    protected AbstractUniverse(UniverseParams universeParams, LoggingParams loggingParams) {
        this.universeParams = universeParams;
        this.loggingParams = loggingParams;
        this.universeId = UUID.randomUUID();

        if (universeParams.isCleanUpEnabled()) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        }
    }

    protected void init() {
        universeParams
                .getGroups()
                .keySet()
                .forEach(groupName -> {
                    GroupParams groupParams = universeParams
                            .getGroupParams(groupName, GroupParams.class);
                    Group group = buildGroup(groupParams);
                    groups.put(groupParams.getName(), group);
                });
    }

    protected void deployGroups() {
        log.info("Deploy groups: {}", universeParams.getGroups().keySet());

        groups.values().forEach(Group::deploy);
    }

    protected abstract <P extends NodeParams, D extends DeploymentParams<P>> Group buildGroup(
            GroupParams<P, D> groupParams);

    @Override
    public ImmutableMap<String, Group> groups() {
        return ImmutableMap.copyOf(groups);
    }

    @Override
    public <T extends Group> T getGroup(String groupName) {
        return ClassUtils.cast(groups.get(groupName));
    }

    /**
     * Find group by type
     * @param clusterType cluster type
     * @param <T> group type
     * @return group
     */
    public <T extends Group> T getGroup(ClusterType clusterType) {
        for (Group<NodeParams, ?, ?, ?> group : groups.values()) {
            if (group.getParams().getType() == clusterType) {
                return ClassUtils.cast(group);
            }
        }
        throw new IllegalArgumentException("Group not found: " + clusterType);
    }

    protected void shutdownGroups() {
        ImmutableSet<String> groupNames = universeParams.getGroups().keySet();
        if (groupNames.isEmpty()) {
            log.warn("Empty universe, nothing to shutdown");
            return;
        }

        log.info("Shutdown all universe groups: [{}]", String.join(", ", groupNames));

        groupNames.forEach(groupName -> {
            try {
                Group group = groups.get(groupName);
                if (group == null) {
                    log.warn("Can't shutdown a group! The group doesn't exists in the universe: {}", groupName);
                    return;
                }

                group.destroy();
            } catch (Exception ex) {
                log.info("Can't stop group: {}", groupName, ex);
            }
        });
    }
}
