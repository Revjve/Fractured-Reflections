package com.revjve.fractured.world.mansion;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.revjve.fractured.world.mansion.MansionLayout.CELL;

/** Cell-local view of one room: coordinate helpers, a reservation grid and wall-mounted spots. */
public final class RoomContext {
    public static final int MIN = 1;
    public static final int MAX = CELL - 1;
    public static final int MID = CELL / 2;

    public final MansionLayout layout;
    public final BuildPlan plan;
    public final Random random;
    public final int i;
    public final int j;
    public final int floor;
    public RoomType type;
    public boolean ritual;

    private final boolean[][] reserved = new boolean[CELL + 1][CELL + 1];
    private final List<LocalSpot> spots = new ArrayList<>();

    /** A wall-mounted local position; {@code facing} points into the room. */
    public record LocalSpot(int lx, int lz, Direction facing) {
        int frontX() { return lx + facing.getOffsetX(); }
        int frontZ() { return lz + facing.getOffsetZ(); }
    }

    RoomContext(MansionLayout layout, BuildPlan plan, Random random, int i, int j, int floor, RoomType type) {
        this.layout = layout;
        this.plan = plan;
        this.random = random;
        this.i = i;
        this.j = j;
        this.floor = floor;
        this.type = type;
        for (int w : new int[]{3, CELL - 3}) {
            spots.add(new LocalSpot(w, MIN, Direction.SOUTH));
            spots.add(new LocalSpot(w, MAX, Direction.NORTH));
            spots.add(new LocalSpot(MIN, w, Direction.EAST));
            spots.add(new LocalSpot(MAX, w, Direction.WEST));
        }
    }

    public boolean isTopFloor() {
        return floor == layout.floors - 1;
    }

    public BlockPos pos(int lx, int ly, int lz) {
        return layout.at(i, j, floor, lx, ly, lz);
    }

    public BlockPos center() {
        return pos(MID, 1, MID);
    }

    public boolean free(int lx, int lz) {
        return lx >= MIN && lx <= MAX && lz >= MIN && lz <= MAX && !reserved[lx][lz];
    }

    public void reserve(int lx, int lz) {
        if (lx >= 0 && lx <= CELL && lz >= 0 && lz <= CELL) reserved[lx][lz] = true;
    }

    public void reserveRect(int x1, int z1, int x2, int z2) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) reserve(x, z);
        }
    }

    public void put(BuildPlan.Phase phase, int lx, int ly, int lz, BlockState state) {
        plan.add(phase, pos(lx, ly, lz), state, BuildPlan.Target.BOTH);
    }

    public void put(BuildPlan.Phase phase, int lx, int ly, int lz, BlockState state, BuildPlan.Target target) {
        plan.add(phase, pos(lx, ly, lz), state, target);
    }

    /** Places a piece of furniture on a free floor cell and reserves it. */
    public boolean furnish(int lx, int ly, int lz, BlockState state) {
        if (!free(lx, lz)) return false;
        reserve(lx, lz);
        put(BuildPlan.Phase.DECOR, lx, ly, lz, state);
        return true;
    }

    /** Places something on top of already-reserved furniture (candles, skulls, pots). */
    public void attach(int lx, int ly, int lz, BlockState state) {
        put(BuildPlan.Phase.ATTACHMENTS, lx, ly, lz, state);
    }

    /** Takes a random wall spot whose cell and the cell in front of it are still free. */
    @Nullable
    public LocalSpot takeSpot() {
        List<LocalSpot> candidates = new ArrayList<>();
        for (LocalSpot s : spots) {
            if (free(s.lx, s.lz) && free(s.frontX(), s.frontZ())) candidates.add(s);
        }
        if (candidates.isEmpty()) return null;
        LocalSpot spot = candidates.get(random.nextInt(candidates.size()));
        spots.remove(spot);
        reserve(spot.lx, spot.lz);
        reserve(spot.frontX(), spot.frontZ());
        return spot;
    }

    /** Remaining usable wall spots whose cell and exit cell are both clear (after all furniture is placed). */
    public List<LocalSpot> remainingSpots() {
        List<LocalSpot> out = new ArrayList<>();
        for (LocalSpot s : spots) {
            if (free(s.lx, s.lz) && free(s.frontX(), s.frontZ())) out.add(s);
        }
        return out;
    }

    public MansionLayout.WallSpot toWorld(LocalSpot spot, int ly) {
        return new MansionLayout.WallSpot(pos(spot.lx, ly, spot.lz), spot.facing);
    }
}
