package com.revjve.fractured.client.screen;

import com.revjve.fractured.block.PuzzleSymbol;
import com.revjve.fractured.client.ClientNetworking;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/** Three glyph dials. The combination is written (backwards) on a sign in the same room. */
@Environment(EnvType.CLIENT)
public class PuzzleChestScreen extends Screen {
    private final BlockPos pos;
    private final int[] dials;

    public PuzzleChestScreen(BlockPos pos, int dialCount) {
        super(Text.translatable("screen.fractured_reflections.puzzle"));
        this.pos = pos;
        this.dials = new int[Math.max(1, Math.min(dialCount, PuzzleSymbol.DIALS))];
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        int spacing = 72;
        int startX = cx - (dials.length * spacing) / 2 + 3;
        for (int n = 0; n < dials.length; n++) {
            int index = n;
            addDrawableChild(ButtonWidget.builder(symbol(index), button -> {
                dials[index] = (dials[index] + 1) % PuzzleSymbol.values().length;
                button.setMessage(symbol(index));
            }).dimensions(startX + n * spacing, cy - 10, 66, 20).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.fractured_reflections.unseal").formatted(Formatting.AQUA), button -> {
            ClientNetworking.sendPuzzleSubmit(pos, dials.clone());
            close();
        }).dimensions(cx - 50, cy + 22, 100, 20).build());
    }

    private Text symbol(int index) {
        return PuzzleSymbol.byIndex(dials[index]).displayName();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        ctx.drawCenteredTextWithShadow(textRenderer, title.copy().formatted(Formatting.DARK_AQUA), width / 2, height / 2 - 52, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.fractured_reflections.puzzle_hint").formatted(Formatting.GRAY),
                width / 2, height / 2 - 36, 0xFFFFFF);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
