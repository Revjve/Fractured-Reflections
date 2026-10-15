package com.revjve.fractured.client.hud;

import com.revjve.fractured.client.ClientGameState;
import com.revjve.fractured.client.FRKeybinds;
import com.revjve.fractured.game.Ability;
import com.revjve.fractured.game.MatchPhase;
import com.revjve.fractured.game.Role;
import com.revjve.fractured.item.FlashlightItem;
import com.revjve.fractured.registry.ModItems;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom HUD. Survivors: Stamina bar (bottom right) with the Sanity meter above it and the
 * flashlight charge; dynamic Task List (top right) listing the next ritual step of every altar,
 * the forging chain derived from the inventory, and the escape objective while trapped.
 * The Fractured: ability cooldowns with their keys, disguise timer, and pings toward revealed
 * cleansing altars. Everyone: phase and match timer (top centre).
 */
@Environment(EnvType.CLIENT)
public final class FRHud {
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI"};
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private FRHud() {}

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ClientGameState.inMatch() || client.player == null || client.options.hudHidden) return;
        TextRenderer font = client.textRenderer;
        int width = ctx.getScaledWindowWidth();
        int height = ctx.getScaledWindowHeight();

        drawTimer(ctx, font, width);
        Role role = ClientGameState.localRole();
        if (ClientGameState.phase == MatchPhase.GENERATING) return;
        if (role == Role.SURVIVOR) {
            drawSurvivorMeters(ctx, font, client.player, width, height);
            drawTaskList(ctx, font, client.player, width);
        } else if (role == Role.FRACTURED) {
            drawAbilities(ctx, font, width, height);
            drawReveals(ctx, font, client.player, width);
        } else if (role == Role.SPECTATOR) {
            Text t = Text.translatable("hud.fractured_reflections.spectating").formatted(Formatting.GRAY, Formatting.ITALIC);
            ctx.drawCenteredTextWithShadow(font, t, width / 2, height - 60, 0xFFFFFF);
            drawTaskList(ctx, font, client.player, width);
        }
    }

    private static void drawTimer(DrawContext ctx, TextRenderer font, int width) {
        int seconds = Math.max(0, ClientGameState.phaseTicks / 20);
        String clock = String.format("%d:%02d", seconds / 60, seconds % 60);
        Text label = Text.translatable("hud.fractured_reflections.phase." + ClientGameState.phase.name().toLowerCase());
        MutableText line = Text.empty().append(label).append(ClientGameState.phase == MatchPhase.GENERATING ? Text.empty() : Text.literal("  " + clock));
        ctx.drawCenteredTextWithShadow(font, line.formatted(Formatting.DARK_AQUA), width / 2, 6, 0xFFFFFF);
    }

    // --- Survivors -----------------------------------------------------------------------------

    private static void drawSurvivorMeters(DrawContext ctx, TextRenderer font, ClientPlayerEntity player, int width, int height) {
        int barW = 92, barH = 5;
        int x = width - barW - 8;
        int y = height - 16;

        float stamina = MathHelper.clamp(ClientGameState.stamina / Math.max(1f, ClientGameState.staminaMax), 0f, 1f);
        int staminaColor = ClientGameState.exhausted ? 0xFFB33A3A : 0xFFE8C15A;
        bar(ctx, x, y, barW, barH, stamina, staminaColor);
        ctx.drawTextWithShadow(font, Text.translatable("hud.fractured_reflections.stamina"), x, y - 10, ClientGameState.exhausted ? 0xFF6060 : 0xD8D8D8);

        float sanity = MathHelper.clamp(ClientGameState.sanity / Math.max(1f, ClientGameState.sanityMax), 0f, 1f);
        int jitter = sanity < 0.3f ? Math.round(MathHelper.sin(ClientGameState.clientTicks * 1.3f) * (0.3f - sanity) * 6f) : 0;
        int sy = y - 26;
        int sanityColor = sanity < 0.3f ? 0xFF7A2BB5 : 0xFF9C6BE0;
        bar(ctx, x + jitter, sy, barW, barH, sanity, sanityColor);
        ctx.drawTextWithShadow(font, Text.translatable("hud.fractured_reflections.sanity"), x + jitter, sy - 10, sanity < 0.3f ? 0xC080FF : 0xD8D8D8);
        if (ClientGameState.trapped) {
            ctx.drawTextWithShadow(font, Text.literal("x2").formatted(Formatting.AQUA), x + barW - 12, sy - 10, 0xFFFFFF);
        }

        ItemStack light = findFlashlight(player.getInventory());
        if (!light.isEmpty()) {
            int percent = Math.round(100f * FlashlightItem.getCharge(light) / FlashlightItem.maxCharge());
            Text t = Text.translatable("hud.fractured_reflections.flashlight", percent);
            int color = FlashlightItem.isOn(light) ? 0xFFF2A0 : 0x909090;
            ctx.drawTextWithShadow(font, t, x, sy - 22, color);
        }
    }

    private static void bar(DrawContext ctx, int x, int y, int w, int h, float fill, int color) {
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xAA000000);
        ctx.fill(x, y, x + Math.round(w * fill), y + h, color);
    }

    private static ItemStack findFlashlight(PlayerInventory inventory) {
        for (int n = 0; n < inventory.size(); n++) {
            ItemStack s = inventory.getStack(n);
            if (s.isOf(ModItems.FLASHLIGHT)) return s;
        }
        return ItemStack.EMPTY;
    }

    private static int count(PlayerInventory inventory, Item item) {
        int total = 0;
        for (int n = 0; n < inventory.size(); n++) {
            ItemStack s = inventory.getStack(n);
            if (s.isOf(item)) total += s.getCount();
        }
        return total;
    }

    private static void drawTaskList(DrawContext ctx, TextRenderer font, ClientPlayerEntity player, int width) {
        List<Text> lines = new ArrayList<>();
        if (ClientGameState.trapped) {
            boolean blink = (ClientGameState.clientTicks / 10) % 2 == 0;
            lines.add(Text.translatable("task.fractured_reflections.escape").formatted(blink ? Formatting.AQUA : Formatting.DARK_AQUA, Formatting.BOLD));
        }
        int remaining = 0;
        boolean needsForging = false;
        for (int n = 0; n < ClientGameState.altars.size(); n++) {
            ClientGameState.AltarTask a = ClientGameState.altars.get(n);
            String numeral = ROMAN[Math.min(n, ROMAN.length - 1)];
            if (a.completed()) {
                lines.add(Text.translatable("task.fractured_reflections.altar_done", numeral).formatted(Formatting.DARK_GRAY, Formatting.STRIKETHROUGH));
                continue;
            }
            remaining++;
            if (!a.refracted()) {
                lines.add(Text.translatable("task.fractured_reflections.refraction", numeral).formatted(Formatting.WHITE));
            } else if (!a.forged()) {
                needsForging = true;
                lines.add(Text.translatable("task.fractured_reflections.forging", numeral).formatted(Formatting.LIGHT_PURPLE));
            } else if (!a.cleansing()) {
                lines.add(Text.translatable("task.fractured_reflections.begin_cleansing", numeral).formatted(Formatting.GOLD));
            } else {
                int percent = Math.round(a.progress() * 100);
                lines.add(Text.translatable("task.fractured_reflections.cleansing", numeral, percent).formatted(Formatting.YELLOW));
            }
        }
        if (needsForging || remaining > 0) {
            PlayerInventory inv = player.getInventory();
            int fragments = count(inv, ModItems.CURSED_GLASS_FRAGMENT);
            int molten = count(inv, ModItems.MOLTEN_MIRROR_GLASS);
            int pure = count(inv, ModItems.PURE_MIRROR);
            Formatting sub = Formatting.GRAY;
            if (pure > 0) {
                lines.add(Text.translatable("task.fractured_reflections.sub_seat", pure).formatted(Formatting.LIGHT_PURPLE));
            } else if (molten >= 3) {
                lines.add(Text.translatable("task.fractured_reflections.sub_craft", molten).formatted(sub));
            } else if (fragments > 0 || molten > 0) {
                lines.add(Text.translatable("task.fractured_reflections.sub_smelt", fragments, molten).formatted(sub));
            } else if (needsForging) {
                lines.add(Text.translatable("task.fractured_reflections.sub_find", ClientGameState.fragmentsPerAltar).formatted(sub));
            }
        }
        if (lines.isEmpty()) return;

        Text title = Text.translatable("hud.fractured_reflections.tasks", remaining).formatted(Formatting.DARK_AQUA, Formatting.BOLD);
        int maxW = font.getWidth(title);
        for (Text t : lines) maxW = Math.max(maxW, font.getWidth(t));
        int x = width - maxW - 8;
        int y = 8;
        ctx.fill(x - 4, y - 4, width - 4, y + 12 + lines.size() * 10 + 2, 0x66000000);
        ctx.drawTextWithShadow(font, title, x, y, 0xFFFFFF);
        for (int n = 0; n < lines.size(); n++) ctx.drawTextWithShadow(font, lines.get(n), x, y + 12 + n * 10, 0xFFFFFF);
    }

    // --- The Fractured ---------------------------------------------------------------------------

    private static void drawAbilities(DrawContext ctx, TextRenderer font, int width, int height) {
        Ability[] abilities = Ability.values();
        int rowH = 12, w = 132;
        int x = width - w - 8;
        int y = height - 10 - abilities.length * rowH;
        ctx.fill(x - 4, y - 16, width - 4, height - 6, 0x66000000);
        ctx.drawTextWithShadow(font, Text.translatable("hud.fractured_reflections.abilities").formatted(Formatting.DARK_AQUA, Formatting.BOLD), x, y - 12, 0xFFFFFF);
        for (Ability ability : abilities) {
            int cd = ClientGameState.cooldowns[ability.ordinal()];
            int max = Math.max(1, ClientGameState.cooldownMax[ability.ordinal()]);
            Text key = FRKeybinds.forAbility(ability).getBoundKeyLocalizedText();
            MutableText label = Text.literal("[").append(key).append("] ").append(Text.translatable(ability.translationKey()));
            boolean ready = cd <= 0;
            ctx.drawTextWithShadow(font, label, x, y, ready ? 0xBFEFFF : 0x707070);
            if (!ready) {
                String secs = ((cd + 19) / 20) + "s";
                ctx.drawTextWithShadow(font, secs, width - 8 - font.getWidth(secs), y, 0xA0A0A0);
                ctx.fill(x, y + 9, x + Math.round((w - 10) * (1f - cd / (float) max)), y + 10, 0xFF3FA7C9);
            }
            y += rowH;
        }
        if (ClientGameState.disguiseTicks > 0) {
            Text t = Text.translatable("hud.fractured_reflections.disguised", ClientGameState.disguiseName, ClientGameState.disguiseTicks / 20)
                    .formatted(Formatting.LIGHT_PURPLE);
            ctx.drawCenteredTextWithShadow(font, t, width / 2, height - 72, 0xFFFFFF);
        }
        if (ClientGameState.dragWindup > 0) {
            ctx.drawCenteredTextWithShadow(font, Text.translatable("hud.fractured_reflections.dragging").formatted(Formatting.AQUA), width / 2, height - 84, 0xFFFFFF);
        }
    }

    private static void drawReveals(DrawContext ctx, TextRenderer font, ClientPlayerEntity player, int width) {
        int y = 20;
        for (ClientGameState.Reveal reveal : ClientGameState.reveals) {
            BlockPos pos = reveal.pos();
            double dx = pos.getX() + 0.5 - player.getX();
            double dz = pos.getZ() + 0.5 - player.getZ();
            float targetYaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90f;
            float rel = MathHelper.wrapDegrees(targetYaw - player.getYaw());
            int index = Math.floorMod(Math.round(rel / 45f), 8);
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            boolean blink = (ClientGameState.clientTicks / 6) % 2 == 0;
            Text t = Text.translatable("hud.fractured_reflections.reveal", ARROWS[index], dist).formatted(blink ? Formatting.GOLD : Formatting.YELLOW);
            ctx.drawCenteredTextWithShadow(font, t, width / 2, y, 0xFFFFFF);
            y += 10;
        }
    }
}
