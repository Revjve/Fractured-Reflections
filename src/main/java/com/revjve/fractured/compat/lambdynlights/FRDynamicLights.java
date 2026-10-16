package com.revjve.fractured.compat.lambdynlights;

import com.revjve.fractured.client.ClientGameState;
import com.revjve.fractured.game.RoleRegistry;
import com.revjve.fractured.item.FlashlightItem;
import dev.lambdaurora.lambdynlights.api.DynamicLightHandler;
import dev.lambdaurora.lambdynlights.api.DynamicLightHandlers;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;

/**
 * LambDynamicLights entrypoint: a player holding a switched-on, charged flashlight emits light.
 * When The Fractured is close the beam flickers violently (luminance stutters between full and
 * near-dark on a jittered pattern computed client-side by {@link ClientGameState}).
 */
public final class FRDynamicLights implements DynamicLightsInitializer {
    @Override
    public void onInitializeDynamicLights() {
        DynamicLightHandlers.registerDynamicLightHandler(EntityType.PLAYER,
                DynamicLightHandler.makeHandler(FRDynamicLights::luminance, player -> false));
    }

    private static int luminance(PlayerEntity player) {
        int light = 0;
        if (FlashlightItem.isLitInHand(player)) light = Math.round(15 * ClientGameState.flashlightIntensity(player));
        // the undisguised Fractured's beating core casts a dim, pulsing glow — a tell in dark corridors
        if (RoleRegistry.isFracturedClient(player.getUuid()) && !ClientGameState.disguises.containsKey(player.getUuid())) {
            float beat = (float) Math.pow(Math.sin(player.age * 0.21) * 0.5 + 0.5, 4);
            light = Math.max(light, 3 + Math.round(beat * 3));
        }
        return light;
    }
}
