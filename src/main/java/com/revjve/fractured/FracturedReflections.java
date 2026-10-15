package com.revjve.fractured;

import com.revjve.fractured.game.MatchCommands;
import com.revjve.fractured.game.MatchManager;
import com.revjve.fractured.game.ProximityChat;
import com.revjve.fractured.network.ServerNetworking;
import com.revjve.fractured.registry.ModBlockEntities;
import com.revjve.fractured.registry.ModBlocks;
import com.revjve.fractured.registry.ModEntities;
import com.revjve.fractured.registry.ModItemGroups;
import com.revjve.fractured.registry.ModItems;
import com.revjve.fractured.registry.ModParticles;
import com.revjve.fractured.registry.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FracturedReflections implements ModInitializer {
    public static final String MOD_ID = "fractured_reflections";
    public static final Logger LOGGER = LoggerFactory.getLogger("Fractured Reflections");

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        FRConfig.load();

        ModSounds.register();
        ModParticles.register();
        ModBlocks.register();
        ModItems.register();
        ModBlockEntities.register();
        ModEntities.register();
        ModItemGroups.register();

        ServerNetworking.registerReceivers();
        CommandRegistrationCallback.EVENT.register(MatchCommands::register);

        ServerTickEvents.END_SERVER_TICK.register(server -> MatchManager.get().tick(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> MatchManager.get().shutdown(server));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> MatchManager.get().onJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MatchManager.get().onDisconnect(handler.player));

        ServerLivingEntityEvents.ALLOW_DAMAGE.register(MatchManager.get()::allowDamage);
        ServerLivingEntityEvents.ALLOW_DEATH.register(MatchManager.get()::allowDeath);
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(ProximityChat::onChat);

        LOGGER.info("The glass remembers.");
    }
}
