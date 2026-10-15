package com.revjve.fractured.game;

import com.revjve.fractured.FRConfig;
import com.revjve.fractured.block.RitualAltarBlockEntity;
import com.revjve.fractured.network.ServerNetworking;
import com.revjve.fractured.registry.ModDimensions;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Server-wide owner of the (single) running match and the entry point for Fabric event hooks. */
public final class MatchManager {
    private static final MatchManager INSTANCE = new MatchManager();

    @Nullable
    private Match match;

    private MatchManager() {}

    public static MatchManager get() {
        return INSTANCE;
    }

    @Nullable
    public Match getMatch() {
        return match;
    }

    /**
     * Starts a match with {@code players}. If {@code fractured} is null a random player is chosen;
     * with a single player (and minPlayers = 1) the match runs without a killer for testing.
     *
     * @return an error message, or null on success
     */
    @Nullable
    public Text start(MinecraftServer server, List<ServerPlayerEntity> players, @Nullable ServerPlayerEntity fractured) {
        if (match != null) return Text.translatable("command.fractured_reflections.already_running");
        FRConfig cfg = FRConfig.get();
        if (players.size() < Math.max(1, cfg.minPlayers)) {
            return Text.translatable("command.fractured_reflections.not_enough_players", cfg.minPlayers);
        }
        ServerWorld main = server.getWorld(ModDimensions.MANOR);
        ServerWorld mirror = server.getWorld(ModDimensions.MIRROR);
        if (main == null || mirror == null) return Text.translatable("command.fractured_reflections.missing_dimensions");

        List<ServerPlayerEntity> pool = new ArrayList<>(players);
        Collections.shuffle(pool);
        if (fractured == null && pool.size() >= 2) fractured = pool.get(0);
        List<ServerPlayerEntity> survivors = new ArrayList<>();
        for (ServerPlayerEntity p : pool) {
            if (p != fractured && survivors.size() < cfg.maxSurvivors) survivors.add(p);
        }
        match = new Match(server, main, mirror, survivors, fractured);
        return null;
    }

    public void stop(MinecraftServer server) {
        if (match == null) return;
        match.cleanup();
        match = null;
        ServerNetworking.sendRoleSync(server.getPlayerManager().getPlayerList(), null);
    }

    public void tick(MinecraftServer server) {
        if (match != null && !match.tick()) {
            match = null;
            ServerNetworking.sendRoleSync(server.getPlayerManager().getPlayerList(), null);
        }
    }

    public void shutdown(MinecraftServer server) {
        stop(server);
    }

    // --- Event hooks ---------------------------------------------------------------------------

    public void onJoin(ServerPlayerEntity player) {
        ServerNetworking.sendRoleSync(List.of(player), match);
        if (match != null && match.state(player.getUuid()) != null) {
            // a returning participant watches the rest of the match
            match.removeParticipant(player);
            if (!match.state(player.getUuid()).isFractured()) player.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
            return;
        }
        if (ModDimensions.isArena(player.getWorld())) {
            ServerWorld overworld = player.getServer().getOverworld();
            BlockPos spawn = overworld.getSpawnPos();
            player.changeGameMode(player.getServer().getDefaultGameMode());
            player.teleport(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYaw(), player.getPitch());
        }
    }

    public void onDisconnect(ServerPlayerEntity player) {
        if (match != null) match.removeParticipant(player);
    }

    public boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (match == null || !(entity instanceof ServerPlayerEntity player)) return true;
        return match.allowDamage(player, source);
    }

    public boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
        if (match == null || !(entity instanceof ServerPlayerEntity player)) return true;
        PlayerState state = match.state(player.getUuid());
        if (state == null) return true;
        if (state.isActiveSurvivor()) {
            match.eliminate(player, "killed");
        } else {
            player.setHealth(player.getMaxHealth());
        }
        return false;
    }

    // --- Callbacks from blocks / systems ------------------------------------------------------

    public void onRitualProgress() {
        if (match != null) match.markTasksDirty();
    }

    public void onAltarCleansed(RitualAltarBlockEntity altar) {
        if (match != null) match.onAltarCleansed(altar);
    }

    public void onMirrorTravel(ServerPlayerEntity player, Direction facing) {
        if (match != null) match.onMirrorTravel(player, facing);
    }

    /** Pings the cleansing altar's position on The Fractured's HUD. */
    public void revealToFractured(ServerWorld world, BlockPos pos, int ticks) {
        if (match == null) return;
        ServerPlayerEntity fractured = match.fracturedPlayer();
        if (fractured != null) ServerNetworking.sendAltarReveal(fractured, pos, ticks);
    }

    public void broadcast(MinecraftServer server, Text text) {
        if (match != null) match.broadcast(text);
        else for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) p.sendMessage(text, false);
    }
}
