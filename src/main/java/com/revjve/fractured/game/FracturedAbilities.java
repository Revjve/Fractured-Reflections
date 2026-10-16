package com.revjve.fractured.game;

import com.revjve.fractured.FRConfig;
import com.revjve.fractured.block.MirrorBlock;
import com.revjve.fractured.entity.GlassEchoEntity;
import com.revjve.fractured.network.FRPackets;
import com.revjve.fractured.network.ServerNetworking;
import com.revjve.fractured.registry.ModBlocks;
import com.revjve.fractured.registry.ModDimensions;
import com.revjve.fractured.registry.ModEntities;
import com.revjve.fractured.registry.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.joml.Vector3f;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** The Fractured's kit: Portal Transformation, Dimension Drag, Shapeshifting, Glass Echo, Shatter Blackout. */
public final class FracturedAbilities {
    private static final DustParticleEffect TENDRIL = new DustParticleEffect(new Vector3f(0.85f, 0.04f, 0.05f), 1.3f);

    private FracturedAbilities() {}

    // --- Per-tick upkeep -------------------------------------------------------------------

    public static void tick(Match match, ServerPlayerEntity player, PlayerState state) {
        for (int n = 0; n < state.cooldowns.length; n++) {
            if (state.cooldowns[n] > 0) state.cooldowns[n]--;
        }
        if (state.disguiseTarget != null) {
            ServerPlayerEntity target = match.player(state.disguiseTarget);
            PlayerState targetState = match.state(state.disguiseTarget);
            if (--state.disguiseTicks <= 0 || target == null || targetState == null || targetState.eliminated) {
                endDisguise(match, player, state);
            }
        }
        if (state.dragTarget != null) {
            drawTendrils(match, player, state);
            if (--state.dragWindup <= 0) finishDrag(match, player, state);
        }
        if (player.age % 20 == 0) grantFlight(player);
    }

    public static void grantFlight(ServerPlayerEntity player) {
        PlayerAbilities abilities = player.getAbilities();
        float speed = FRConfig.get().fracturedFlySpeed;
        if (!abilities.allowFlying || abilities.getFlySpeed() != speed) {
            abilities.allowFlying = true;
            abilities.setFlySpeed(speed);
            player.sendAbilitiesUpdate();
        }
    }

    // --- Activation ------------------------------------------------------------------------

    public static void use(ServerPlayerEntity player, Ability ability) {
        Match match = MatchManager.get().getMatch();
        if (match == null || match.phase() != MatchPhase.HUNT) return;
        PlayerState state = match.state(player.getUuid());
        if (state == null || !state.isFractured()) return;
        if (ability == Ability.SHAPESHIFT && state.disguiseTarget != null) {
            endDisguise(match, player, state);
            return;
        }
        if (state.onCooldown(ability)) {
            int seconds = (state.cooldowns[ability.ordinal()] + 19) / 20;
            player.sendMessage(Text.translatable("message.fractured_reflections.cooldown", Text.translatable(ability.translationKey()), seconds)
                    .formatted(Formatting.GRAY), true);
            return;
        }
        boolean used = switch (ability) {
            case PORTAL -> portal(match, player);
            case DRAG -> drag(match, player, state);
            case SHAPESHIFT -> shapeshift(match, player, state);
            case GLASS_ECHO -> glassEcho(match, player, state);
            case BLACKOUT -> blackout(match, player);
        };
        if (used) state.startCooldown(ability);
    }

    private static boolean portal(Match match, ServerPlayerEntity player) {
        HitResult hit = player.raycast(8.0, 0f, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            ServerWorld world = player.getServerWorld();
            BlockState state = world.getBlockState(blockHit.getBlockPos());
            if (state.isOf(ModBlocks.MIRROR) && !state.get(MirrorBlock.ACTIVE)) {
                return match.portals().open(world, blockHit.getBlockPos());
            }
        }
        player.sendMessage(Text.translatable("message.fractured_reflections.need_mirror").formatted(Formatting.GRAY), true);
        return false;
    }

    private static boolean drag(Match match, ServerPlayerEntity player, PlayerState state) {
        if (state.dragTarget != null) return false;
        if (ModDimensions.isMirror(player.getWorld())) {
            player.sendMessage(Text.translatable("message.fractured_reflections.drag_wrong_world").formatted(Formatting.GRAY), true);
            return false;
        }
        ServerPlayerEntity target = findSurvivorInView(match, player, FRConfig.get().dragRange);
        if (target == null) {
            player.sendMessage(Text.translatable("message.fractured_reflections.no_target").formatted(Formatting.GRAY), true);
            return false;
        }
        int windup = Math.max(1, FRConfig.get().dragWindupTicks);
        state.dragTarget = target.getUuid();
        state.dragWindup = windup;
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, windup + 5, 4, false, false));
        player.getServerWorld().playSound(null, target.getBlockPos(), ModSounds.DIMENSION_DRAG, SoundCategory.HOSTILE, 2.0f, 1.0f);
        ServerNetworking.sendScreenEffect(target, FRPackets.ScreenEffect.DRAGGED, 1.0f);
        return false; // cooldown starts when the drag resolves
    }

    /** Glass tendrils lash from the killer's claws to the victim while the drag winds up. */
    private static void drawTendrils(Match match, ServerPlayerEntity player, PlayerState state) {
        ServerPlayerEntity target = match.player(state.dragTarget);
        if (target == null || target.getWorld() != player.getWorld()) return;
        ServerWorld world = player.getServerWorld();
        Vec3d from = player.getPos().add(0, 1.1, 0);
        Vec3d to = target.getPos().add(0, 1.0, 0);
        Vec3d span = to.subtract(from);
        int points = Math.max(4, (int) (span.length() * 4));
        float wave = player.age * 0.6f;
        for (int n = 0; n <= points; n++) {
            double k = n / (double) points;
            double wobble = Math.sin(k * Math.PI) * 0.25;
            Vec3d p = from.add(span.multiply(k)).add(Math.sin(wave + k * 9) * wobble, Math.cos(wave + k * 7) * wobble, Math.sin(wave * 1.3 + k * 5) * wobble);
            world.spawnParticles(TENDRIL, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.GLASS.getDefaultState()), to.x, to.y, to.z, 4, 0.25, 0.4, 0.25, 0.05);
    }

    private static void finishDrag(Match match, ServerPlayerEntity player, PlayerState state) {
        ServerPlayerEntity target = match.player(state.dragTarget);
        PlayerState targetState = state.dragTarget == null ? null : match.state(state.dragTarget);
        state.dragTarget = null;
        state.dragWindup = 0;
        double reach = FRConfig.get().dragRange + 1.5;
        boolean valid = target != null && targetState != null && targetState.isActiveSurvivor()
                && target.getWorld() == player.getWorld() && target.squaredDistanceTo(player) <= reach * reach;
        if (!valid) {
            state.cooldowns[Ability.DRAG.ordinal()] = Ability.DRAG.cooldownTicks() / 3;
            return;
        }
        ServerWorld mirror = match.mirrorWorld();
        target.teleport(mirror, target.getX(), target.getY(), target.getZ(), target.getYaw(), target.getPitch());
        mirror.playSound(null, target.getBlockPos(), ModSounds.STING, SoundCategory.HOSTILE, 1.5f, 0.8f);
        mirror.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.RED_STAINED_GLASS.getDefaultState()),
                target.getX(), target.getBodyY(0.5), target.getZ(), 80, 0.6, 0.9, 0.6, 0.25);
        SurvivorSystems.shock(targetState, 10f);
        target.sendMessage(Text.translatable("message.fractured_reflections.dragged").formatted(Formatting.DARK_AQUA), false);
        state.startCooldown(Ability.DRAG);
        match.markRolesDirty();
    }

    private static boolean shapeshift(Match match, ServerPlayerEntity player, PlayerState state) {
        ServerPlayerEntity target = findSurvivorInView(match, player, FRConfig.get().shapeshiftRange);
        if (target == null) {
            player.sendMessage(Text.translatable("message.fractured_reflections.no_target").formatted(Formatting.GRAY), true);
            return false;
        }
        state.disguiseTarget = target.getUuid();
        state.disguiseTicks = FRConfig.get().shapeshiftDurationSeconds * 20;
        player.getServerWorld().playSound(null, player.getBlockPos(), ModSounds.SHAPESHIFT, SoundCategory.HOSTILE, 1.0f, 1.0f);
        player.sendMessage(Text.translatable("message.fractured_reflections.disguised", target.getDisplayName()).formatted(Formatting.LIGHT_PURPLE), true);
        match.markDisguisesDirty();
        return true;
    }

    public static void endDisguise(Match match, ServerPlayerEntity player, PlayerState state) {
        if (state.disguiseTarget == null) return;
        state.disguiseTarget = null;
        state.disguiseTicks = 0;
        player.getServerWorld().playSound(null, player.getBlockPos(), ModSounds.SHAPESHIFT, SoundCategory.HOSTILE, 1.0f, 0.6f);
        match.markDisguisesDirty();
    }

    private static boolean glassEcho(Match match, ServerPlayerEntity player, PlayerState state) {
        ServerPlayerEntity mimic = state.disguiseTarget != null ? match.player(state.disguiseTarget) : null;
        if (mimic == null) {
            List<ServerPlayerEntity> survivors = new ArrayList<>();
            for (PlayerState s : match.states()) {
                ServerPlayerEntity p = match.player(s.uuid);
                if (s.isActiveSurvivor() && p != null) survivors.add(p);
            }
            if (survivors.isEmpty()) return false;
            mimic = survivors.get(player.getRandom().nextInt(survivors.size()));
        }
        ServerWorld world = player.getServerWorld();
        GlassEchoEntity echo = ModEntities.GLASS_ECHO.create(world);
        if (echo == null) return false;
        // spawn inside the caster's own hitbox: guaranteed free space, and it bolts away from there
        echo.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), 0f);
        echo.mimic(mimic, player);
        world.spawnEntity(echo);
        world.playSound(null, player.getBlockPos(), ModSounds.ECHO_SPAWN, SoundCategory.HOSTILE, 1.0f, 1.0f);
        return true;
    }

    private static boolean blackout(Match match, ServerPlayerEntity player) {
        int radius = FRConfig.get().blackoutRadius;
        ServerWorld world = player.getServerWorld();
        int count = match.blackouts().trigger(world, player.getBlockPos(), radius);
        for (PlayerState s : match.states()) {
            ServerPlayerEntity p = match.player(s.uuid);
            if (p == null || !s.isActiveSurvivor() || p.getWorld() != world) continue;
            if (p.squaredDistanceTo(player) <= radius * radius) {
                SurvivorSystems.shock(s, 8f);
                ServerNetworking.sendScreenEffect(p, FRPackets.ScreenEffect.BLACKOUT_FLASH, 1.0f);
            }
        }
        player.sendMessage(Text.translatable("message.fractured_reflections.blackout", count).formatted(Formatting.DARK_PURPLE), true);
        return true;
    }

    /** The active survivor closest to the crosshair within {@code range}, in line of sight. */
    @Nullable
    private static ServerPlayerEntity findSurvivorInView(Match match, ServerPlayerEntity player, double range) {
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        ServerPlayerEntity best = null;
        double bestScore = 0.85; // minimum cosine (~32° cone)
        for (PlayerState s : match.states()) {
            if (!s.isActiveSurvivor()) continue;
            ServerPlayerEntity p = match.player(s.uuid);
            if (p == null || p.getWorld() != player.getWorld()) continue;
            Vec3d to = p.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 1.0E-3) continue;
            double cos = to.normalize().dotProduct(look);
            if (cos > bestScore && player.canSee(p)) {
                bestScore = cos;
                best = p;
            }
        }
        return best;
    }
}
