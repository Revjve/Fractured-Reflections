package com.revjve.fractured.client;

import com.revjve.fractured.game.MatchPhase;
import com.revjve.fractured.registry.ModSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * Client-side horror director. Low sanity produces auditory hallucinations that only the local
 * player hears — fake footsteps creeping up behind them, distorted whispers, sudden stings —
 * while The Fractured's proximity drives a heartbeat whose tempo rises as it closes in.
 * The visual side (FOV warping, chromatic split) lives in FRPostEffects and the FOV mixin.
 */
@Environment(EnvType.CLIENT)
public final class HallucinationController {
    private static final Random RANDOM = Random.create();
    private static int lastBeat;
    private static int footstepsLeft;
    private static int nextStepAt;
    private static double stepDistance;

    private HallucinationController() {}

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.isPaused()) return;
        if (!ClientGameState.isLocalSurvivor() || ClientGameState.phase != MatchPhase.HUNT) {
            footstepsLeft = 0;
            return;
        }
        int now = ClientGameState.clientTicks;
        float sanity = ClientGameState.sanity / Math.max(1f, ClientGameState.sanityMax);

        // Heartbeat: proximity of the killer, or panic at very low sanity.
        float proximity = ClientGameState.fracturedProximity;
        int interval = proximity > 0 ? Math.round(MathHelper.lerp(proximity, 34f, 9f)) : sanity < 0.25f ? 30 : -1;
        if (interval > 0 && now - lastBeat >= interval) {
            lastBeat = now;
            float volume = proximity > 0 ? 0.35f + 0.65f * proximity : 0.3f;
            client.getSoundManager().play(PositionedSoundInstance.master(ModSounds.HEARTBEAT, 1.0f, volume));
        }

        if (sanity >= 0.6f) {
            footstepsLeft = 0;
            return;
        }
        float insanity = (0.6f - sanity) / 0.6f;

        // Footsteps that follow the player and stop when they turn around.
        if (footstepsLeft > 0) {
            if (now >= nextStepAt) {
                stepDistance = Math.max(1.5, stepDistance - 0.5);
                playBehind(client, player, ModSounds.FAKE_FOOTSTEP, stepDistance, 0.7f, 0.85f + RANDOM.nextFloat() * 0.2f);
                footstepsLeft--;
                nextStepAt = now + 6 + RANDOM.nextInt(4);
            }
        } else if (RANDOM.nextFloat() < 0.002f + insanity * 0.008f) {
            footstepsLeft = 4 + RANDOM.nextInt(5);
            stepDistance = 4 + RANDOM.nextDouble() * 3;
            nextStepAt = now;
        }

        if (sanity < 0.45f && RANDOM.nextFloat() < 0.0015f + insanity * 0.005f) {
            Vec3d at = player.getPos().add((RANDOM.nextDouble() - 0.5) * 6, 1.2, (RANDOM.nextDouble() - 0.5) * 6);
            client.world.playSound(at.x, at.y, at.z, ModSounds.WHISPER, SoundCategory.AMBIENT, 0.6f + insanity * 0.4f, 0.5f + RANDOM.nextFloat() * 0.3f, false);
        }

        if (sanity < 0.2f && RANDOM.nextFloat() < 0.0012f) {
            client.getSoundManager().play(PositionedSoundInstance.master(ModSounds.STING, 0.9f + RANDOM.nextFloat() * 0.2f, 0.7f));
            ClientGameState.screenPulse = Math.max(ClientGameState.screenPulse, 0.35f);
        }
    }

    private static void playBehind(MinecraftClient client, ClientPlayerEntity player, SoundEvent sound, double distance, float volume, float pitch) {
        Vec3d back = player.getRotationVector().multiply(1, 0, 1);
        back = back.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : back.normalize();
        Vec3d at = player.getPos().subtract(back.multiply(distance));
        client.world.playSound(at.x, at.y, at.z, sound, SoundCategory.PLAYERS, volume, pitch, false);
    }
}
