package com.revjve.fractured.block;

import net.minecraft.text.Text;

/** Dial glyphs for puzzle chests. Hints are painted on nearby signs in mirror-writing. */
public enum PuzzleSymbol {
    MOON, EYE, ROSE, KEY, SKULL, STAR;

    public static final int DIALS = 3;

    public Text displayName() {
        return Text.translatable("puzzle.fractured_reflections.symbol." + name().toLowerCase());
    }

    /** The glyph's name spelled backwards, as it would read in a mirror. */
    public String mirrored() {
        return new StringBuilder(name()).reverse().toString();
    }

    public static PuzzleSymbol byIndex(int index) {
        PuzzleSymbol[] values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
