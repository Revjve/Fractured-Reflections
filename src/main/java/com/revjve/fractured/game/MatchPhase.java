package com.revjve.fractured.game;

public enum MatchPhase {
    IDLE,
    /** Mansion is being built into both worlds over several ticks. */
    GENERATING,
    /** Players are placed; short countdown before the hunt begins. */
    PREPARING,
    HUNT,
    /** Result screen; everyone is returned to the overworld afterwards. */
    ENDED;

    public static MatchPhase byOrdinal(int ordinal) {
        MatchPhase[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : IDLE;
    }
}
