package com.revjve.fractured.game;

import com.revjve.fractured.FRConfig;
import com.revjve.fractured.network.FRPackets;
import com.revjve.fractured.network.ServerNetworking;
import com.revjve.fractured.registry.ModSounds;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wardrobe hiding. A hidden survivor is pinned inside the wardrobe, invisible, slowly calming
 * down (sanity regenerates) — but if The Fractured lingers within a few blocks it hears the
 * survivor's heartbeat thudding from inside. Hiding is limited in time; the killer can search.
 */
public final class HidingManager {
    private static final double HEARTBEAT_RANGE = 5.0;

    private record Hide(ServerWorld world, BlockPos lower, Direction facing, long since) {}

    private final Map<UUID, Hide> hidden = new HashMap<>();

    /** Entry point from WardrobeBlock#onUse. */
    public static void onWardrobeUse(ServerPlayerEntity player, BlockPos lower, Direction facing) {
        Match match = MatchManager.get().getMatch();
        if (match == null || match.phase() != MatchPhase.HUNT) {
            rattle(player.getServerWorld(), lower);
            return;
        }
        match.hiding().use(match, player, lower, facing);
    }

    private void use(Match match, ServerPlayerEntity player, BlockPos lower, Direction facing) {
        PlayerState state = match.state(player.getUuid());
        ServerWorld world = player.getServerWorld();
        if (state == null) {
            rattle(world, lower);
        } else if (state.isFractured()) {
            search(match, player, lower, facing);
        } else if (state.isActiveSurvivor()) {
            if (hidden.containsKey(player.getUuid())) {
                exit(match, player, false);
            } else if (occupant(world, lower) != null) {
                player.sendMessage(Text.translatable("message.fractured_reflections.wardrobe_occupied").formatted(Formatting.GRAY), true);
            } else {
                enter(match, player, lower, facing);
            }
        }
    }

    private void enter(Match match, ServerPlayerEntity player, BlockPos lower, Direction facing) {
        ServerWorld world = player.getServerWorld();
        hidden.put(player.getUuid(), new Hide(world, lower, facing, world.getTime()));
        Vec3d center = Vec3d.ofBottomCenter(lower);
        player.teleport(world, center.x, center.y, center.z, facing.asRotation(), player.getPitch());
        applyHiddenEffects(player);
        world.playSound(null, lower, SoundEvents.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.BLOCKS, 0.6f, 0.7f);
        player.sendMessage(Text.translatable("message.fractured_reflections.hiding").formatted(Formatting.DARK_GRAY), true);
        refreshRegistry();
        match.markRolesDirty();
    }

    void exit(Match match, ServerPlayerEntity player, boolean forced) {
        Hide hide = hidden.remove(player.getUuid());
        if (hide == null) return;
        player.removeStatusEffect(StatusEffects.INVISIBILITY);
        player.removeStatusEffect(StatusEffects.SLOWNESS);
        player.removeStatusEffect(StatusEffects.JUMP_BOOST);
        Vec3d out = Vec3d.ofBottomCenter(hide.lower().offset(hide.facing()));
        if (player.getWorld() == hide.world()) {
            player.teleport(hide.world(), out.x, out.y, out.z, hide.facing().asRotation(), player.getPitch());
        }
        hide.world().playSound(null, hide.lower(), SoundEvents.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.BLOCKS, forced ? 1.2f : 0.6f, forced ? 0.6f : 0.8f);
        refreshRegistry();
        match.markRolesDirty();
    }

    /** The Fractured tears the wardrobe open. */
    private void search(Match match, ServerPlayerEntity fractured, BlockPos lower, Direction facing) {
        ServerWorld world = fractured.getServerWorld();
        UUID found = occupant(world, lower);
        ServerPlayerEntity victim = match.player(found);
        if (victim == null) {
            rattle(world, lower);
            fractured.sendMessage(Text.translatable("message.fractured_reflections.wardrobe_empty").formatted(Formatting.GRAY), true);
            return;
        }
        exit(match, victim, true);
        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 3, false, false));
        PlayerState vs = match.state(found);
        if (vs != null) SurvivorSystems.shock(vs, 20f);
        world.playSound(null, lower, ModSounds.STING, SoundCategory.HOSTILE, 2.0f, 0.8f);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.DARK_OAK_PLANKS.getDefaultState()),
                lower.getX() + 0.5, lower.getY() + 1.0, lower.getZ() + 0.5, 40, 0.3, 0.6, 0.3, 0.2);
        ServerNetworking.sendScreenEffect(victim, FRPackets.ScreenEffect.STING, 1.0f);
        victim.sendMessage(Text.translatable("message.fractured_reflections.found").formatted(Formatting.RED), true);
    }

    public void tick(Match match) {
        FRConfig cfg = FRConfig.get();
        List<UUID> leave = new ArrayList<>();
        List<UUID> forced = new ArrayList<>();
        ServerPlayerEntity fractured = match.fracturedPlayer();
        for (Map.Entry<UUID, Hide> e : hidden.entrySet()) {
            ServerPlayerEntity player = match.player(e.getKey());
            PlayerState state = match.state(e.getKey());
            Hide hide = e.getValue();
            if (player == null || state == null || !state.isActiveSurvivor() || player.getWorld() != hide.world() || !isWardrobe(hide)) {
                leave.add(e.getKey());
                continue;
            }
            if (player.isSneaking()) {
                leave.add(e.getKey());
                continue;
            }
            if (hide.world().getTime() - hide.since() > cfg.wardrobeMaxSeconds * 20L) {
                player.sendMessage(Text.translatable("message.fractured_reflections.hiding_breath").formatted(Formatting.GRAY), true);
                forced.add(e.getKey());
                continue;
            }
            // pin inside the wardrobe without fighting the camera
            Vec3d center = Vec3d.ofBottomCenter(hide.lower());
            if (player.getPos().squaredDistanceTo(center) > 0.09) {
                player.networkHandler.requestTeleport(center.x, center.y, center.z, 0f, 0f, PositionFlag.ROT);
            }
            if (player.age % 20 == 0) applyHiddenEffects(player);

            boolean killerNear = fractured != null && fractured.getWorld() == hide.world()
                    && fractured.squaredDistanceTo(center) < HEARTBEAT_RANGE * HEARTBEAT_RANGE;
            if (killerNear) {
                if (player.age % 18 == 0) {
                    fractured.networkHandler.sendPacket(new PlaySoundS2CPacket(Registries.SOUND_EVENT.getEntry(ModSounds.HEARTBEAT),
                            SoundCategory.HOSTILE, center.x, center.y + 1, center.z, 0.9f, 1.1f, hide.world().getRandom().nextLong()));
                }
                SurvivorSystems.shock(state, 0.03f);
            } else {
                state.sanity = Math.min(cfg.sanityMax, state.sanity + cfg.wardrobeSanityRegenPerSecond / 20f);
            }
        }
        for (UUID uuid : leave) {
            ServerPlayerEntity p = match.player(uuid);
            if (p != null) exit(match, p, false);
            else hidden.remove(uuid);
        }
        for (UUID uuid : forced) {
            ServerPlayerEntity p = match.player(uuid);
            if (p != null) exit(match, p, true);
        }
        if (!leave.isEmpty()) refreshRegistry();
    }

    private static boolean isWardrobe(Hide hide) {
        return hide.world().getBlockState(hide.lower()).isOf(com.revjve.fractured.registry.ModBlocks.WARDROBE);
    }

    private static void applyHiddenEffects(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 40, 0, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 6, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 40, 128, false, false));
    }

    private static void rattle(ServerWorld world, BlockPos lower) {
        world.playSound(null, lower, SoundEvents.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.BLOCKS, 0.8f, 1.3f);
        world.playSound(null, lower, SoundEvents.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.BLOCKS, 0.8f, 1.1f);
    }

    @Nullable
    private UUID occupant(ServerWorld world, BlockPos lower) {
        for (Map.Entry<UUID, Hide> e : hidden.entrySet()) {
            if (e.getValue().world() == world && e.getValue().lower().equals(lower)) return e.getKey();
        }
        return null;
    }

    public boolean isHiding(UUID uuid) {
        return hidden.containsKey(uuid);
    }

    public void clear(Match match) {
        for (UUID uuid : new ArrayList<>(hidden.keySet())) {
            ServerPlayerEntity p = match.player(uuid);
            if (p != null) exit(match, p, false);
        }
        hidden.clear();
        refreshRegistry();
    }

    private void refreshRegistry() {
        RoleRegistry.setServerHiding(hidden.keySet());
    }
}
