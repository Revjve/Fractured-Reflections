package com.revjve.fractured.world.mansion;

/** Room modules the mansion is assembled from. */
public enum RoomType {
    FOYER(false),
    GALLERY(false),
    GRAND_LIBRARY(false),
    CRYPTS(true),
    BANQUET_HALL(false),
    OBSERVATORY(false),
    CONSERVATORY(false),
    CHAPEL(false),
    BALLROOM(false),
    BEDCHAMBER(false),
    STAIRWELL(false);

    /** Underground-feeling rooms get no exterior windows. */
    public final boolean windowless;

    RoomType(boolean windowless) {
        this.windowless = windowless;
    }

    public boolean canHostRitual() {
        return this != FOYER && this != STAIRWELL;
    }
}
