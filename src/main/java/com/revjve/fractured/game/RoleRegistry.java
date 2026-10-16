package com.revjve.fractured.game;

import net.minecraft.entity.Entity;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Side-aware lookup of who is The Fractured, usable from shared code such as block collision
 * shapes (which run on both the logical client and server). The server set is maintained by
 * {@link MatchManager}; the client set is filled from the role-sync packet.
 */
public final class RoleRegistry {
    private static final Set<UUID> SERVER = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> CLIENT = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> SERVER_HIDING = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> CLIENT_HIDING = ConcurrentHashMap.newKeySet();

    private RoleRegistry() {}

    public static boolean isFractured(Entity entity) {
        return (entity.getWorld().isClient ? CLIENT : SERVER).contains(entity.getUuid());
    }

    public static boolean isFracturedClient(UUID uuid) {
        return CLIENT.contains(uuid);
    }

    public static void setServer(Collection<UUID> fractured) {
        SERVER.clear();
        SERVER.addAll(fractured);
    }

    public static void setClient(Collection<UUID> fractured) {
        CLIENT.clear();
        CLIENT.addAll(fractured);
    }

    /** True while a survivor is hidden inside a wardrobe (no collision with it on either side). */
    public static boolean isHiding(Entity entity) {
        return (entity.getWorld().isClient ? CLIENT_HIDING : SERVER_HIDING).contains(entity.getUuid());
    }

    public static void setServerHiding(Collection<UUID> hiding) {
        SERVER_HIDING.clear();
        SERVER_HIDING.addAll(hiding);
    }

    public static void setClientHiding(Collection<UUID> hiding) {
        CLIENT_HIDING.clear();
        CLIENT_HIDING.addAll(hiding);
    }
}
