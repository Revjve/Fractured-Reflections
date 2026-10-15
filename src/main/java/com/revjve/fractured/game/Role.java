package com.revjve.fractured.game;

public enum Role {
    NONE,
    SURVIVOR,
    FRACTURED,
    /** An eliminated survivor watching the rest of the match. */
    SPECTATOR;

    public static Role byOrdinal(int ordinal) {
        Role[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
    }
}
