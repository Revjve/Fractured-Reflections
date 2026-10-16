package com.revjve.fractured.world.mansion;

import com.revjve.fractured.FRConfig;
import com.revjve.fractured.block.MirrorBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import com.revjve.fractured.registry.ModBlocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import static com.revjve.fractured.world.mansion.MansionLayout.CELL;
import static com.revjve.fractured.world.mansion.MansionLayout.FLOOR_H;
import static com.revjve.fractured.world.mansion.RoomContext.MAX;
import static com.revjve.fractured.world.mansion.RoomContext.MID;
import static com.revjve.fractured.world.mansion.RoomContext.MIN;

/**
 * Procedural gothic mansion: a grid of 11x11 rooms per floor, connected by a randomised
 * spanning-tree maze (plus extra loops for chases), stacked floors joined by stairwells, and
 * room modules (library, crypts, banquet hall, observatory, conservatory...) dressed by
 * {@link RoomDecorators}. Produces a {@link BuildPlan} for both worlds plus the layout data the
 * match logic needs (altars, chests, spawn points, wall spots).
 */
public final class MansionGenerator {
    private static final int NONE = 0;
    private static final int DOOR = 1;
    private static final int ARCH = 2;

    private final FRConfig cfg;
    private final Random random;
    private final MansionLayout layout;
    private final BuildPlan plan = new BuildPlan();
    private final RoomContext[][][] rooms;
    /** eastOpen[f][i][j]: opening between cell (i,j) and (i+1,j). */
    private final int[][][] eastOpen;
    /** southOpen[f][i][j]: opening between cell (i,j) and (i,j+1). */
    private final int[][][] southOpen;
    /** Stair flights as {floor, i, j}: the flight rises from (i,j) on floor f to floor f+1. */
    private final List<int[]> flights = new ArrayList<>();

    public record Result(MansionLayout layout, BuildPlan plan) {}

    private MansionGenerator(long seed, BlockPos origin, FRConfig cfg) {
        this.cfg = cfg;
        this.random = Random.create(seed);
        int w = Math.max(3, cfg.mansionWidth);
        int d = Math.max(3, cfg.mansionDepth);
        int f = Math.max(1, Math.min(4, cfg.mansionFloors));
        this.layout = new MansionLayout(origin, w, d, f);
        this.rooms = new RoomContext[f][w][d];
        this.eastOpen = new int[f][w][d];
        this.southOpen = new int[f][w][d];
    }

    public static Result generate(long seed, BlockPos origin) {
        MansionGenerator gen = new MansionGenerator(seed, origin, FRConfig.get());
        gen.run();
        return new Result(gen.layout, gen.plan);
    }

    private void run() {
        assignRooms();
        carveMaze();
        for (int f = 0; f < layout.floors; f++) {
            for (int i = 0; i < layout.width; i++) {
                for (int j = 0; j < layout.depth; j++) {
                    rooms[f][i][j] = new RoomContext(layout, plan, random, i, j, f, layout.rooms[f][i][j]);
                    layout.roomCenters.add(rooms[f][i][j].center());
                }
            }
        }
        reserveOpenings();
        buildShell();
        buildStairwells();
        RoomDecorators.placeRituals(pickRitualRooms(), layout);
        placeSpawns();
        placeMirrors();
        RoomDecorators.placeFunctionalFixtures(allRooms(), layout, cfg);
        for (RoomContext room : allRooms()) RoomDecorators.decorate(room);
        for (RoomContext room : allRooms()) {
            for (RoomContext.LocalSpot spot : room.remainingSpots()) layout.spareWallSpots.add(room.toWorld(spot, 1));
        }
    }

    private List<RoomContext> allRooms() {
        List<RoomContext> all = new ArrayList<>();
        for (RoomContext[][] floor : rooms) for (RoomContext[] row : floor) Collections.addAll(all, row);
        return all;
    }

    // --- Room assignment -----------------------------------------------------------------

    private void assignRooms() {
        int w = layout.width, d = layout.depth, top = layout.floors - 1;
        RoomType[][][] r = layout.rooms;
        r[0][w / 2][0] = RoomType.FOYER;

        for (int f = 0; f < top; f++) {
            for (int tries = 0; tries < 100; tries++) {
                int i = random.nextInt(w), j = random.nextInt(d);
                if (r[f][i][j] == null && r[f + 1][i][j] == null) {
                    r[f][i][j] = RoomType.STAIRWELL;
                    r[f + 1][i][j] = RoomType.STAIRWELL;
                    flights.add(new int[]{f, i, j});
                    break;
                }
            }
        }

        int mid = Math.min(1, top);
        require(0, RoomType.CRYPTS, 2);
        require(0, RoomType.BANQUET_HALL, 1);
        require(mid, RoomType.GRAND_LIBRARY, 2);
        require(mid, RoomType.CONSERVATORY, 1);
        require(top, RoomType.OBSERVATORY, 2);
        require(0, RoomType.BALLROOM, 1);
        require(mid, RoomType.CHAPEL, 1);
        require(Math.max(mid, top), RoomType.BEDCHAMBER, 1);

        for (int f = 0; f <= top; f++) {
            RoomType[] pool = f == 0
                    ? new RoomType[]{RoomType.CRYPTS, RoomType.BANQUET_HALL, RoomType.GALLERY, RoomType.GALLERY, RoomType.BALLROOM}
                    : f == top
                    ? new RoomType[]{RoomType.OBSERVATORY, RoomType.CONSERVATORY, RoomType.GRAND_LIBRARY, RoomType.GALLERY, RoomType.BEDCHAMBER, RoomType.CHAPEL}
                    : new RoomType[]{RoomType.GRAND_LIBRARY, RoomType.CONSERVATORY, RoomType.GALLERY, RoomType.BANQUET_HALL, RoomType.BEDCHAMBER, RoomType.CHAPEL};
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < d; j++) {
                    if (r[f][i][j] == null) r[f][i][j] = pool[random.nextInt(pool.length)];
                }
            }
        }
    }

    private void require(int f, RoomType type, int count) {
        for (int n = 0; n < count; n++) {
            for (int tries = 0; tries < 60; tries++) {
                int i = random.nextInt(layout.width), j = random.nextInt(layout.depth);
                if (layout.rooms[f][i][j] == null) {
                    layout.rooms[f][i][j] = type;
                    break;
                }
            }
        }
    }

    // --- Maze ----------------------------------------------------------------------------

    private void carveMaze() {
        int w = layout.width, d = layout.depth;
        for (int f = 0; f < layout.floors; f++) {
            boolean[][] visited = new boolean[w][d];
            Deque<int[]> stack = new ArrayDeque<>();
            stack.push(new int[]{random.nextInt(w), random.nextInt(d)});
            visited[stack.peek()[0]][stack.peek()[1]] = true;
            while (!stack.isEmpty()) {
                int[] cur = stack.peek();
                List<int[]> next = new ArrayList<>();
                for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int ni = cur[0] + dir[0], nj = cur[1] + dir[1];
                    if (ni >= 0 && nj >= 0 && ni < w && nj < d && !visited[ni][nj]) next.add(new int[]{ni, nj});
                }
                if (next.isEmpty()) {
                    stack.pop();
                    continue;
                }
                int[] n = next.get(random.nextInt(next.size()));
                open(f, cur[0], cur[1], n[0], n[1], random.nextInt(5) < 3 ? DOOR : ARCH);
                visited[n[0]][n[1]] = true;
                stack.push(n);
            }
            // extra loops so survivors can circle and break line of sight
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < d; j++) {
                    if (i + 1 < w && eastOpen[f][i][j] == NONE && random.nextFloat() < 0.3f) eastOpen[f][i][j] = random.nextBoolean() ? DOOR : ARCH;
                    if (j + 1 < d && southOpen[f][i][j] == NONE && random.nextFloat() < 0.3f) southOpen[f][i][j] = random.nextBoolean() ? DOOR : ARCH;
                }
            }
        }
    }

    private void open(int f, int i1, int j1, int i2, int j2, int kind) {
        if (i1 != i2) eastOpen[f][Math.min(i1, i2)][j1] = kind;
        else southOpen[f][i1][Math.min(j1, j2)] = kind;
    }

    private void reserveOpenings() {
        for (int f = 0; f < layout.floors; f++) {
            for (int i = 0; i < layout.width; i++) {
                for (int j = 0; j < layout.depth; j++) {
                    RoomContext room = rooms[f][i][j];
                    if (i + 1 < layout.width && eastOpen[f][i][j] != NONE) {
                        room.reserveRect(MAX, MID - 1, MAX, MID + 1);
                        rooms[f][i + 1][j].reserveRect(MIN, MID - 1, MIN, MID + 1);
                    }
                    if (j + 1 < layout.depth && southOpen[f][i][j] != NONE) {
                        room.reserveRect(MID - 1, MAX, MID + 1, MAX);
                        rooms[f][i][j + 1].reserveRect(MID - 1, MIN, MID + 1, MIN);
                    }
                }
            }
        }
        RoomContext foyer = rooms[0][layout.width / 2][0];
        foyer.reserveRect(MID - 1, MIN, MID + 1, MIN + 1);
        foyer.reserveRect(4, 4, 7, 4); // survivor spawn line
    }

    // --- Shell ---------------------------------------------------------------------------

    private void buildShell() {
        BlockState innerWall = Blocks.DEEPSLATE_BRICKS.getDefaultState();
        BlockState outerWall = Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
        BlockState pillar = Blocks.DARK_OAK_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Y);
        BlockState trim = Blocks.POLISHED_DEEPSLATE.getDefaultState();
        int w = layout.width, d = layout.depth;
        int sizeX = w * CELL, sizeZ = d * CELL;

        for (int f = 0; f <= layout.floors; f++) {
            boolean roof = f == layout.floors;
            int yb = f * FLOOR_H;
            // floor / ceiling slab
            for (int x = 0; x <= sizeX; x++) {
                for (int z = 0; z <= sizeZ; z++) {
                    BlockPos p = layout.origin.add(x, yb, z);
                    boolean onGrid = x % CELL == 0 || z % CELL == 0;
                    BlockState s;
                    if (roof) {
                        RoomType below = layout.rooms[f - 1][Math.min(x / CELL, w - 1)][Math.min(z / CELL, d - 1)];
                        s = !onGrid && below == RoomType.OBSERVATORY ? Blocks.GLASS.getDefaultState() : Blocks.POLISHED_BLACKSTONE.getDefaultState();
                    } else if (onGrid) {
                        s = trim;
                    } else {
                        s = RoomDecorators.floorFor(layout.rooms[f][x / CELL][z / CELL], x % CELL, z % CELL, random);
                    }
                    plan.add(BuildPlan.Phase.STRUCTURE, p, s);
                }
            }
            if (roof) break;

            // walls along X lines (planes of constant x)
            for (int gi = 0; gi <= w; gi++) {
                for (int z = 0; z <= sizeZ; z++) {
                    int j = Math.min(z / CELL, d - 1), lz = z - j * CELL;
                    boolean exterior = gi == 0 || gi == w;
                    int kind = exterior || z % CELL == 0 ? NONE : eastOpen[f][gi - 1][j];
                    for (int ly = 1; ly < FLOOR_H; ly++) {
                        BlockPos p = layout.origin.add(gi * CELL, yb + ly, z);
                        BlockState s = z % CELL == 0 ? pillar : exterior ? outerWall : innerWall;
                        if (!openingCarves(kind, lz, ly)) {
                            if (exterior && z % CELL != 0 && isWindow(f, gi == 0 ? 0 : w - 1, j, lz, ly)) s = Blocks.TINTED_GLASS.getDefaultState();
                            plan.add(BuildPlan.Phase.STRUCTURE, p, s);
                        }
                    }
                    if (kind == DOOR && lz == MID) placeDoor(layout.origin.add(gi * CELL, yb + 1, z), Direction.EAST);
                }
            }
            // walls along Z lines
            for (int gj = 0; gj <= d; gj++) {
                for (int x = 0; x <= sizeX; x++) {
                    if (x % CELL == 0) continue; // pillars already placed by the X pass
                    int i = Math.min(x / CELL, w - 1), lx = x - i * CELL;
                    boolean exterior = gj == 0 || gj == d;
                    int kind = exterior ? NONE : southOpen[f][i][gj - 1];
                    for (int ly = 1; ly < FLOOR_H; ly++) {
                        BlockPos p = layout.origin.add(x, yb + ly, gj * CELL);
                        BlockState s = exterior ? outerWall : innerWall;
                        if (!openingCarves(kind, lx, ly)) {
                            boolean frontOfFoyer = f == 0 && gj == 0 && i == w / 2;
                            if (frontOfFoyer && lx == MID && ly <= 2) continue; // entrance door
                            if (exterior && !frontOfFoyer && isWindow(f, i, gj == 0 ? 0 : d - 1, lx, ly)) s = Blocks.TINTED_GLASS.getDefaultState();
                            plan.add(BuildPlan.Phase.STRUCTURE, p, s);
                        }
                    }
                    if (kind == DOOR && lx == MID) placeDoor(layout.origin.add(x, yb + 1, gj * CELL), Direction.SOUTH);
                }
            }
        }

        // the front entrance is sealed: an iron door with no way to open it
        BlockPos entrance = layout.origin.add((w / 2) * CELL + MID, 1, 0);
        BlockState iron = Blocks.IRON_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH);
        plan.add(BuildPlan.Phase.DOORS, entrance, iron.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        plan.add(BuildPlan.Phase.DOORS, entrance.up(), iron.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));

        // parapet
        int roofY = layout.floors * FLOOR_H + 1;
        for (int x = 0; x <= sizeX; x++) {
            for (int z = 0; z <= sizeZ; z++) {
                boolean edge = x == 0 || z == 0 || x == sizeX || z == sizeZ;
                if (!edge) continue;
                boolean corner = x % CELL == 0 && z % CELL == 0;
                plan.add(BuildPlan.Phase.DECOR, layout.origin.add(x, roofY, z),
                        corner ? Blocks.DEEPSLATE_TILE_WALL.getDefaultState() : Blocks.BLACKSTONE_WALL.getDefaultState());
            }
        }
    }

    private static boolean openingCarves(int kind, int along, int ly) {
        if (kind == DOOR) return along == MID && ly <= 2;
        if (kind == ARCH) return Math.abs(along - MID) <= 1 && ly <= 3;
        return false;
    }

    private boolean isWindow(int f, int i, int j, int along, int ly) {
        RoomType type = layout.rooms[f][i][j];
        return type != null && !type.windowless && type != RoomType.STAIRWELL
                && Math.abs(along - MID) <= 1 && (ly == 2 || ly == 3);
    }

    private void placeDoor(BlockPos lower, Direction facing) {
        BlockState door = Blocks.DARK_OAK_DOOR.getDefaultState()
                .with(DoorBlock.FACING, random.nextBoolean() ? facing : facing.getOpposite())
                .with(DoorBlock.HINGE, random.nextBoolean() ? DoorHinge.LEFT : DoorHinge.RIGHT)
                .with(DoorBlock.OPEN, random.nextFloat() < 0.35f);
        plan.add(BuildPlan.Phase.DOORS, lower, door.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        plan.add(BuildPlan.Phase.DOORS, lower.up(), door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    // --- Stairwells ----------------------------------------------------------------------

    private void buildStairwells() {
        for (int[] flight : flights) {
            buildStairs(rooms[flight[0]][flight[1]][flight[2]], rooms[flight[0] + 1][flight[1]][flight[2]]);
        }
    }

    private void buildStairs(RoomContext lower, RoomContext upper) {
        BlockState step = Blocks.DARK_OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.EAST);
        for (int k = 0; k <= 5; k++) {
            for (int lz = 2; lz <= 3; lz++) {
                lower.put(BuildPlan.Phase.DECOR, 2 + k, 1 + k, lz, step);
                if (k <= 4) lower.put(BuildPlan.Phase.DECOR, 2 + k, FLOOR_H, lz, Blocks.AIR.getDefaultState());
            }
        }
        lower.reserveRect(MIN, MIN, 9, 4);
        upper.reserveRect(MIN, MIN, 9, 5);
        BlockState rail = Blocks.DARK_OAK_FENCE.getDefaultState();
        for (int lx = 1; lx <= 6; lx++) upper.put(BuildPlan.Phase.DECOR, lx, 1, 4, rail);
        for (int lz = 2; lz <= 3; lz++) upper.put(BuildPlan.Phase.DECOR, 1, 1, lz, rail);
    }

    // --- Rituals, mirrors, spawns ----------------------------------------------------------

    private List<RoomContext> pickRitualRooms() {
        List<RoomContext> picked = new ArrayList<>();
        for (int round = 0; round < 3 && picked.size() < 3; round++) {
            for (int f = layout.floors - 1; f >= 0 && picked.size() < 3; f--) {
                List<RoomContext> candidates = new ArrayList<>();
                for (RoomContext[] row : rooms[f]) {
                    for (RoomContext room : row) {
                        if (room.type.canHostRitual() && !room.ritual && !picked.contains(room)) candidates.add(room);
                    }
                }
                if (!candidates.isEmpty()) picked.add(candidates.get(random.nextInt(candidates.size())));
            }
        }
        return picked;
    }

    private void placeMirrors() {
        for (RoomContext room : allRooms()) {
            if (room.type == RoomType.STAIRWELL) continue;
            int count = room.type == RoomType.BALLROOM ? 3 : 1 + (random.nextFloat() < 0.45f ? 1 : 0);
            for (int n = 0; n < count; n++) {
                RoomContext.LocalSpot spot = room.takeSpot();
                if (spot == null) break;
                BlockState mirror = ModBlocks.MIRROR.getDefaultState().with(MirrorBlock.FACING, spot.facing());
                room.put(BuildPlan.Phase.DECOR, spot.lx(), 1, spot.lz(), mirror);
                room.put(BuildPlan.Phase.DECOR, spot.lx(), 2, spot.lz(), mirror);
                layout.mirrors.add(room.toWorld(spot, 1));
                layout.mirrors.add(room.toWorld(spot, 2));
            }
        }
    }

    private void placeSpawns() {
        RoomContext foyer = rooms[0][layout.width / 2][0];
        for (int n = 0; n < 4; n++) layout.survivorSpawns.add(foyer.pos(4 + n, 1, 4));
        // The Fractured starts in the Mirror Dimension, in the crypt farthest from the foyer.
        RoomContext lair = null;
        double best = -1;
        for (RoomContext[] row : rooms[0]) {
            for (RoomContext room : row) {
                if (room.ritual || room.type == RoomType.FOYER || room.type == RoomType.STAIRWELL) continue;
                double dist = room.center().getSquaredDistance(foyer.center()) + (room.type == RoomType.CRYPTS ? 1.0e6 : 0);
                if (dist > best) {
                    best = dist;
                    lair = room;
                }
            }
        }
        if (lair == null) lair = foyer;
        lair.reserveRect(MID - 1, MID - 1, MID + 1, MID + 1);
        layout.fracturedSpawn = lair.pos(MID, 1, MID);
    }
}
