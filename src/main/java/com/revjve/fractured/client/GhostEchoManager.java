package com.revjve.fractured.client;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Spawns, moves and removes {@link GhostEchoPlayer}s from the server's ghost-echo stream. */
@Environment(EnvType.CLIENT)
public final class GhostEchoManager {
    public record Snapshot(UUID uuid, String name, double x, double y, double z, float yaw, float pitch, float headYaw, boolean sneaking) {}

    private static final Map<UUID, GhostEchoPlayer> GHOSTS = new HashMap<>();
    private static int nextId = -1_000_000;

    private GhostEchoManager() {}

    static List<Snapshot> read(PacketByteBuf buf) {
        int count = buf.readVarInt();
        List<Snapshot> out = new ArrayList<>(count);
        for (int n = 0; n < count; n++) {
            out.add(new Snapshot(buf.readUuid(), buf.readString(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean()));
        }
        return out;
    }

    static void update(MinecraftClient client, List<Snapshot> snapshots) {
        ClientWorld world = client.world;
        if (world == null) return;
        Set<UUID> seen = new HashSet<>();
        for (Snapshot s : snapshots) {
            seen.add(s.uuid());
            GhostEchoPlayer ghost = GHOSTS.get(s.uuid());
            if (ghost == null || ghost.isRemoved() || ghost.getWorld() != world) {
                // A distinct entity UUID avoids clashing with the real player if they later enter this world.
                UUID ghostId = UUID.nameUUIDFromBytes(("fractured_reflections:ghost:" + s.uuid()).getBytes(StandardCharsets.UTF_8));
                ghost = new GhostEchoPlayer(world, new GameProfile(ghostId, s.name()), s.uuid());
                ghost.setId(nextId--);
                ghost.refreshPositionAndAngles(s.x(), s.y(), s.z(), s.yaw(), s.pitch());
                ghost.setHeadYaw(s.headYaw());
                world.addEntity(ghost.getId(), ghost);
                GHOSTS.put(s.uuid(), ghost);
            }
            ghost.updateTrackedPositionAndAngles(s.x(), s.y(), s.z(), s.yaw(), s.pitch(), 3, true);
            ghost.updateTrackedHeadRotation(s.headYaw(), 3);
            ghost.setSneaking(s.sneaking());
        }
        Iterator<Map.Entry<UUID, GhostEchoPlayer>> it = GHOSTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, GhostEchoPlayer> e = it.next();
            if (!seen.contains(e.getKey())) {
                discard(world, e.getValue());
                it.remove();
            }
        }
    }

    public static void clear(MinecraftClient client) {
        if (client.world != null) {
            for (GhostEchoPlayer ghost : GHOSTS.values()) discard(client.world, ghost);
        }
        GHOSTS.clear();
    }

    private static void discard(ClientWorld world, GhostEchoPlayer ghost) {
        if (ghost.getWorld() == world && !ghost.isRemoved()) world.removeEntity(ghost.getId(), Entity.RemovalReason.DISCARDED);
    }
}
