package com.revjve.fractured.client;

import com.revjve.fractured.client.sound.MuffleFilter;
import com.revjve.fractured.registry.ModParticles;
import com.revjve.fractured.registry.ModSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.random.Random;

/**
 * Mirror Dimension ambience: floating glass motes drifting around the camera and a slow
 * shimmering drone. Colour grading and muffling are handled by FRPostEffects and SourceMixin.
 */
@Environment(EnvType.CLIENT)
public final class MirrorAtmosphere {
    private static final Random RANDOM = Random.create();
    private static int nextDrone;

    private MirrorAtmosphere() {}

    public static void tick(MinecraftClient client) {
        MuffleFilter.active = ClientGameState.inMirrorDimension();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.isPaused() || !MuffleFilter.active) return;

        int motes = client.options.getParticles().getValue().getId() == 0 ? 3 : 1;
        for (int n = 0; n < motes; n++) {
            double x = player.getX() + (RANDOM.nextDouble() - 0.5) * 16;
            double y = player.getEyeY() + (RANDOM.nextDouble() - 0.5) * 6;
            double z = player.getZ() + (RANDOM.nextDouble() - 0.5) * 16;
            client.world.addParticle(ModParticles.GLASS_MOTE, x, y, z,
                    (RANDOM.nextDouble() - 0.5) * 0.01, -0.002 - RANDOM.nextDouble() * 0.004, (RANDOM.nextDouble() - 0.5) * 0.01);
        }

        if (ClientGameState.clientTicks >= nextDrone) {
            nextDrone = ClientGameState.clientTicks + 120 + RANDOM.nextInt(200);
            client.world.playSound(player.getX(), player.getY(), player.getZ(), ModSounds.MIRROR_AMBIENT, SoundCategory.AMBIENT,
                    0.5f, 0.5f + RANDOM.nextFloat() * 0.3f, false);
        }
    }
}
