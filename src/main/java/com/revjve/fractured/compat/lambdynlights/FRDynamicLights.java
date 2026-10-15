package com.revjve.fractured.compat.lambdynlights;

import com.revjve.fractured.client.ClientGameState;
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
        if (!FlashlightItem.isLitInHand(player)) return 0;
        return Math.round(15 * ClientGameState.flashlightIntensity(player));
    }
}
