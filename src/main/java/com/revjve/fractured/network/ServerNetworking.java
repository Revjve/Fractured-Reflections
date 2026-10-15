package com.revjve.fractured.network;

import com.revjve.fractured.block.PuzzleChestBlockEntity;
import com.revjve.fractured.block.PuzzleSymbol;
import com.revjve.fractured.game.Ability;
import com.revjve.fractured.game.FracturedAbilities;
import com.revjve.fractured.game.Match;
import com.revjve.fractured.game.PlayerState;
import com.revjve.fractured.item.FlashlightItem;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ServerNetworking {
    private ServerNetworking() {}

    // --- Receivers -------------------------------------------------------------------------

    public static void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(FRPackets.USE_ABILITY, (server, player, handler, buf, sender) -> {
            Ability ability = Ability.byOrdinal(buf.readVarInt());
            if (ability != null) server.execute(() -> FracturedAbilities.use(player, ability));
        });

        ServerPlayNetworking.registerGlobalReceiver(FRPackets.TOGGLE_FLASHLIGHT, (server, player, handler, buf, sender) ->
                server.execute(() -> FlashlightItem.toggleFor(player)));

        ServerPlayNetworking.registerGlobalReceiver(FRPackets.PUZZLE_SUBMIT, (server, player, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            int count = Math.min(buf.readVarInt(), PuzzleSymbol.DIALS);
            int[] attempt = new int[PuzzleSymbol.DIALS];
            for (int n = 0; n < count; n++) attempt[n] = buf.readVarInt();
            server.execute(() -> {
                if (player.squaredDistanceTo(Vec3d.ofCenter(pos)) > 64) return;
                if (player.getWorld().getBlockEntity(pos) instanceof PuzzleChestBlockEntity chest) {
                    chest.trySolve(player, attempt);
                }
            });
        });
    }

    // --- Senders ---------------------------------------------------------------------------

    private static void broadcast(Collection<ServerPlayerEntity> players, Identifier id, PacketByteBuf buf) {
        Packet<ClientPlayPacketListener> packet = ServerPlayNetworking.createS2CPacket(id, buf);
        for (ServerPlayerEntity p : players) p.networkHandler.sendPacket(packet);
    }

    public static void sendRoleSync(Collection<ServerPlayerEntity> to, Match match) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(match == null ? 0 : match.phase().ordinal());
        buf.writeVarInt(match == null ? 0 : match.phaseTicksRemaining());
        Collection<PlayerState> states = match == null ? List.of() : match.states();
        buf.writeVarInt(states.size());
        for (PlayerState s : states) {
            buf.writeUuid(s.uuid);
            buf.writeVarInt(s.role.ordinal());
            buf.writeBoolean(s.eliminated);
        }
        broadcast(to, FRPackets.ROLE_SYNC, buf);
    }

    public static void sendSurvivorStats(ServerPlayerEntity to, PlayerState state, boolean trapped, float staminaMax, float sanityMax) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(state.stamina);
        buf.writeFloat(staminaMax);
        buf.writeFloat(state.sanity);
        buf.writeFloat(sanityMax);
        buf.writeBoolean(state.exhausted);
        buf.writeBoolean(trapped);
        ServerPlayNetworking.send(to, FRPackets.SURVIVOR_STATS, buf);
    }

    public static void sendFracturedHud(ServerPlayerEntity to, PlayerState state, String disguiseName) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(state.cooldowns.length);
        for (int n = 0; n < state.cooldowns.length; n++) {
            buf.writeVarInt(state.cooldowns[n]);
            buf.writeVarInt(Ability.values()[n].cooldownTicks());
        }
        buf.writeVarInt(state.disguiseTicks);
        buf.writeString(disguiseName == null ? "" : disguiseName);
        buf.writeVarInt(state.dragWindup);
        ServerPlayNetworking.send(to, FRPackets.FRACTURED_HUD, buf);
    }

    /** Altar progress for the task list. {@code altars} rows: refracted, forged, cleansing, completed, progress. */
    public static void sendTasks(Collection<ServerPlayerEntity> to, List<float[]> altars, int fragmentsPerAltar) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(fragmentsPerAltar);
        buf.writeVarInt(altars.size());
        for (float[] a : altars) {
            buf.writeBoolean(a[0] > 0);
            buf.writeBoolean(a[1] > 0);
            buf.writeBoolean(a[2] > 0);
            buf.writeBoolean(a[3] > 0);
            buf.writeFloat(a[4]);
        }
        broadcast(to, FRPackets.TASKS, buf);
    }

    public static void sendDisguises(Collection<ServerPlayerEntity> to, Map<UUID, UUID> disguises) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(disguises.size());
        disguises.forEach((fractured, target) -> {
            buf.writeUuid(fractured);
            buf.writeUuid(target);
        });
        broadcast(to, FRPackets.DISGUISES, buf);
    }

    /** Spectral positions of living teammates in the Main World, for survivors trapped in the mirror. */
    public static void sendGhostEchoes(ServerPlayerEntity to, Collection<ServerPlayerEntity> teammates) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(teammates.size());
        for (ServerPlayerEntity p : teammates) {
            buf.writeUuid(p.getUuid());
            buf.writeString(p.getGameProfile().getName());
            buf.writeDouble(p.getX());
            buf.writeDouble(p.getY());
            buf.writeDouble(p.getZ());
            buf.writeFloat(p.getYaw());
            buf.writeFloat(p.getPitch());
            buf.writeFloat(p.getHeadYaw());
            buf.writeBoolean(p.isSneaking());
        }
        ServerPlayNetworking.send(to, FRPackets.GHOST_ECHOES, buf);
    }

    public static void sendMirrorCrawl(Collection<ServerPlayerEntity> to, int entityId, Direction facing, int duration) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(entityId);
        buf.writeVarInt(facing.getHorizontal());
        buf.writeVarInt(duration);
        broadcast(to, FRPackets.MIRROR_CRAWL, buf);
    }

    public static void sendAltarReveal(ServerPlayerEntity to, BlockPos pos, int ticks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeVarInt(ticks);
        ServerPlayNetworking.send(to, FRPackets.ALTAR_REVEAL, buf);
    }

    public static void sendScreenEffect(ServerPlayerEntity to, FRPackets.ScreenEffect effect, float intensity) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(effect.ordinal());
        buf.writeFloat(intensity);
        ServerPlayNetworking.send(to, FRPackets.SCREEN_EFFECT, buf);
    }

    public static void sendOpenPuzzle(ServerPlayerEntity to, BlockPos pos, int dials) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeVarInt(dials);
        ServerPlayNetworking.send(to, FRPackets.OPEN_PUZZLE, buf);
    }
}
