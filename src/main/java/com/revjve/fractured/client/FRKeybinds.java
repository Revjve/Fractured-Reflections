package com.revjve.fractured.client;

import com.revjve.fractured.game.Ability;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class FRKeybinds {
    private static final String CATEGORY = "key.categories.fractured_reflections";

    public static final KeyBinding FLASHLIGHT = register("flashlight", GLFW.GLFW_KEY_L);
    public static final KeyBinding PORTAL = register("portal", GLFW.GLFW_KEY_R);
    public static final KeyBinding DRAG = register("drag", GLFW.GLFW_KEY_G);
    public static final KeyBinding SHAPESHIFT = register("shapeshift", GLFW.GLFW_KEY_V);
    public static final KeyBinding GLASS_ECHO = register("glass_echo", GLFW.GLFW_KEY_Z);
    public static final KeyBinding BLACKOUT = register("blackout", GLFW.GLFW_KEY_B);

    private static final KeyBinding[] ABILITY_KEYS = {PORTAL, DRAG, SHAPESHIFT, GLASS_ECHO, BLACKOUT};

    private FRKeybinds() {}

    private static KeyBinding register(String name, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.fractured_reflections." + name, InputUtil.Type.KEYSYM, key, CATEGORY));
    }

    public static void init() {
        // class-load trigger
    }

    /** The key bound to an ability, for HUD hints. */
    public static KeyBinding forAbility(Ability ability) {
        return ABILITY_KEYS[ability.ordinal()];
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null) return;
        while (FLASHLIGHT.wasPressed()) ClientNetworking.sendToggleFlashlight();
        for (Ability ability : Ability.values()) {
            KeyBinding key = forAbility(ability);
            while (key.wasPressed()) {
                if (ClientGameState.isLocalFractured()) ClientNetworking.sendAbility(ability);
            }
        }
    }
}
