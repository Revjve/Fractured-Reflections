package com.revjve.fractured.client;

import com.revjve.fractured.game.RoleRegistry;
import com.revjve.fractured.registry.ModDimensions;
import com.revjve.fractured.registry.ModParticles;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * Client-side particle work around The Fractured: smouldering embers shed from its core, a trail
 * of glass splinters and sparks when it sprints or flies, and a storm of shards blasting out of
 * the mirror behind it while it crawls through.
 */
@Environment(EnvType.CLIENT)
public final class FracturedEffects {
    private static final Random RANDOM = Random.create();

    private FracturedEffects() {}

    public static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world == null || client.isPaused() || !ClientGameState.inMatch()) return;
        boolean mirror = world.getRegistryKey() == ModDimensions.MIRROR;
        BlockStateParticleEffect splinter = new BlockStateParticleEffect(ParticleTypes.BLOCK,
                (mirror ? Blocks.RED_STAINED_GLASS : Blocks.GLASS).getDefaultState());

        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof PlayerEntity player) || !RoleRegistry.isFracturedClient(player.getUuid())) continue;
            if (ClientGameState.disguises.containsKey(player.getUuid()) || player == client.player && client.options.getPerspective().isFirstPerson()) continue;

            Vec3d core = player.getPos().add(0, 1.3, 0);
            if (RANDOM.nextInt(3) == 0) {
                world.addParticle(ModParticles.GLASS_MOTE, core.x + gauss(0.25), core.y + gauss(0.3), core.z + gauss(0.25),
                        gauss(0.01), 0.01 + RANDOM.nextDouble() * 0.01, gauss(0.01));
            }

            Vec3d velocity = player.getVelocity();
            double speed = velocity.horizontalLength();
            if (speed > 0.16 || (player.getAbilities().flying && speed > 0.05)) {
                for (int n = 0; n < 2; n++) {
                    world.addParticle(splinter, player.getX() + gauss(0.3), player.getY() + 0.2 + RANDOM.nextDouble() * 1.6, player.getZ() + gauss(0.3),
                            -velocity.x * 0.5, 0.05, -velocity.z * 0.5);
                }
                world.addParticle(mirror ? ParticleTypes.CRIMSON_SPORE : ParticleTypes.END_ROD,
                        core.x + gauss(0.2), core.y + gauss(0.4), core.z + gauss(0.2), -velocity.x * 0.2, 0.0, -velocity.z * 0.2);
            }

            ClientGameState.Crawl crawl = ClientGameState.crawls.get(player.getId());
            if (crawl != null) {
                int age = ClientGameState.clientTicks - crawl.startedAt();
                if (age < 12) burstFromMirror(world, player, crawl.facing(), splinter, age == 0 ? 40 : 6);
            }
        }
    }

    private static void burstFromMirror(ClientWorld world, PlayerEntity player, Direction facing, BlockStateParticleEffect splinter, int count) {
        // the mirror is behind the emerging body; shards erupt out of its plane into the room
        Vec3d plane = player.getPos().subtract(facing.getOffsetX() * 0.8, 0, facing.getOffsetZ() * 0.8);
        for (int n = 0; n < count; n++) {
            double side = gauss(0.5);
            double x = plane.x + (facing.getAxis() == Direction.Axis.Z ? side : 0);
            double z = plane.z + (facing.getAxis() == Direction.Axis.X ? side : 0);
            double y = plane.y + RANDOM.nextDouble() * 2.0;
            double push = 0.15 + RANDOM.nextDouble() * 0.35;
            world.addParticle(splinter, x, y, z, facing.getOffsetX() * push + gauss(0.08), gauss(0.1) + 0.05, facing.getOffsetZ() * push + gauss(0.08));
            if (n % 4 == 0) world.addParticle(ModParticles.GLASS_MOTE, x, y, z, facing.getOffsetX() * push * 0.3, 0.02, facing.getOffsetZ() * push * 0.3);
        }
    }

    private static double gauss(double scale) {
        return RANDOM.nextGaussian() * scale;
    }
}
