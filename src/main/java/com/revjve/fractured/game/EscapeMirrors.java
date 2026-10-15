package com.revjve.fractured.game;

import com.revjve.fractured.block.EscapeMirrorBlock;
import com.revjve.fractured.network.FRPackets;
import com.revjve.fractured.network.ServerNetworking;
import com.revjve.fractured.registry.ModBlocks;
import com.revjve.fractured.registry.ModDimensions;
import com.revjve.fractured.registry.ModSounds;
import com.revjve.fractured.world.MirrorSync;
import com.revjve.fractured.world.mansion.MansionLayout;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Glowing Blue Cracked Mirrors. While a survivor is inside the Mirror Dimension one escape
 * mirror is kept spawned for them at a random, distant wall spot of the mansion. Jumping
 * through any escape mirror returns that survivor to the Main World and shatters the mirror.
 */
public final class EscapeMirrors {
    private static final double MIN_DISTANCE_SQ = 18 * 18;

    private final Map<UUID, BlockPos> assigned = new HashMap<>();

    public void tick(Match match) {
        ServerWorld mirror = match.mirrorWorld();
        // drop assignments for survivors who left the mirror (portal) or were eliminated
        Iterator<Map.Entry<UUID, BlockPos>> it = assigned.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BlockPos> e = it.next();
            PlayerState state = match.state(e.getKey());
            ServerPlayerEntity player = match.player(e.getKey());
            boolean stillTrapped = state != null && state.isActiveSurvivor() && player != null && player.getWorld() == mirror;
            if (!stillTrapped) {
                remove(mirror, e.getValue());
                it.remove();
            }
        }
        for (PlayerState state : match.states()) {
            if (!state.isActiveSurvivor() || assigned.containsKey(state.uuid)) continue;
            ServerPlayerEntity player = match.player(state.uuid);
            if (player == null || player.getWorld() != mirror) continue;
            BlockPos placed = spawnFor(match, player);
            if (placed != null) {
                assigned.put(state.uuid, placed);
                player.sendMessage(Text.translatable("message.fractured_reflections.escape_spawned").formatted(Formatting.AQUA), false);
            }
        }
    }

    private BlockPos spawnFor(Match match, ServerPlayerEntity player) {
        ServerWorld mirror = match.mirrorWorld();
        List<MansionLayout.WallSpot> spots = new ArrayList<>(match.layout().spareWallSpots);
        List<MansionLayout.WallSpot> far = new ArrayList<>();
        for (MansionLayout.WallSpot s : spots) {
            if (s.pos().getSquaredDistance(player.getPos()) >= MIN_DISTANCE_SQ && isClear(mirror, s.pos())) far.add(s);
        }
        List<MansionLayout.WallSpot> pool = far.isEmpty() ? spots : far;
        for (int attempt = 0; attempt < 24 && !pool.isEmpty(); attempt++) {
            MansionLayout.WallSpot spot = pool.get(mirror.random.nextInt(pool.size()));
            if (!isClear(mirror, spot.pos()) || assigned.containsValue(spot.pos())) continue;
            BlockState state = ModBlocks.ESCAPE_MIRROR.getDefaultState().with(EscapeMirrorBlock.FACING, spot.facing());
            MirrorSync.runUnsynced(() -> {
                mirror.setBlockState(spot.pos(), state, Block.NOTIFY_ALL);
                mirror.setBlockState(spot.pos().up(), state, Block.NOTIFY_ALL);
            });
            mirror.playSound(null, spot.pos(), ModSounds.MIRROR_AMBIENT, SoundCategory.BLOCKS, 2.0f, 1.4f);
            return spot.pos();
        }
        return null;
    }

    private static boolean isClear(ServerWorld world, BlockPos pos) {
        return world.getBlockState(pos).isAir() && world.getBlockState(pos.up()).isAir();
    }

    private static void remove(ServerWorld world, BlockPos pos) {
        MirrorSync.runUnsynced(() -> {
            for (BlockPos p : new BlockPos[]{pos, pos.up()}) {
                if (world.getBlockState(p).isOf(ModBlocks.ESCAPE_MIRROR)) {
                    world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        });
    }

    /** Contact with an escape mirror. The survivor must be airborne — they have to jump through. */
    public static void onContact(ServerWorld world, BlockPos pos, BlockState state, ServerPlayerEntity player) {
        if (player.isSpectator() || !ModDimensions.isMirror(world)) return;
        Match match = MatchManager.get().getMatch();
        if (match != null && match.roleOf(player) != Role.SURVIVOR) return;
        if (player.isOnGround()) {
            player.sendMessage(Text.translatable("message.fractured_reflections.escape_jump").formatted(Formatting.AQUA), true);
            return;
        }
        ServerWorld main = ModDimensions.twin(world);
        if (main == null) return;

        BlockPos bottom = world.getBlockState(pos.down()).isOf(ModBlocks.ESCAPE_MIRROR) ? pos.down() : pos;
        Direction facing = state.get(EscapeMirrorBlock.FACING);
        Vec3d exit = Vec3d.ofBottomCenter(bottom.offset(facing));

        world.spawnParticles(ParticleTypes.GLOW, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 40, 0.4, 0.8, 0.4, 0.1);
        world.playSound(null, pos, ModSounds.GLASS_SHATTER, SoundCategory.BLOCKS, 1.5f, 1.2f);
        if (match != null) match.escapes().release(world, bottom);
        else remove(world, bottom);

        player.teleport(main, exit.x, exit.y, exit.z, facing.asRotation(), player.getPitch());
        main.playSound(null, exit.x, exit.y, exit.z, ModSounds.ESCAPE, SoundCategory.PLAYERS, 1.2f, 1.0f);
        ServerNetworking.sendScreenEffect(player, FRPackets.ScreenEffect.ESCAPED, 1.0f);
        if (match != null) match.onSurvivorEscaped(player);
    }

    /** Removes an escape mirror that was used; whoever it was assigned to gets a new one next tick. */
    void release(ServerWorld world, BlockPos bottom) {
        remove(world, bottom);
        assigned.values().removeIf(p -> p.equals(bottom));
    }

    public void clear(ServerWorld mirror) {
        for (BlockPos pos : assigned.values()) remove(mirror, pos);
        assigned.clear();
    }

    public boolean hasEscapeFor(UUID uuid) {
        return assigned.containsKey(uuid);
    }
}
