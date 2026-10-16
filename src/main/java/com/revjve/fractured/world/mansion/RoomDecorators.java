package com.revjve.fractured.world.mansion;

import com.revjve.fractured.FRConfig;
import com.revjve.fractured.block.BeamEmitterBlock;
import com.revjve.fractured.block.LensBlock;
import com.revjve.fractured.block.PuzzleChestBlock;
import com.revjve.fractured.block.PuzzleChestBlockEntity;
import com.revjve.fractured.block.PuzzleSymbol;
import com.revjve.fractured.block.RitualAltarBlockEntity;
import com.revjve.fractured.block.WardrobeBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import com.revjve.fractured.item.FlashlightItem;
import com.revjve.fractured.registry.ModBlocks;
import com.revjve.fractured.registry.ModItems;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LecternBlock;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

import static com.revjve.fractured.world.mansion.BuildPlan.Phase.ATTACHMENTS;
import static com.revjve.fractured.world.mansion.BuildPlan.Phase.DECOR;
import static com.revjve.fractured.world.mansion.BuildPlan.Target.MAIN;
import static com.revjve.fractured.world.mansion.BuildPlan.Target.MIRROR;
import static com.revjve.fractured.world.mansion.RoomContext.MAX;
import static com.revjve.fractured.world.mansion.RoomContext.MID;
import static com.revjve.fractured.world.mansion.RoomContext.MIN;

/** Room-module dressing, the refraction puzzle layout, puzzle chests and furnaces. */
final class RoomDecorators {
    private RoomDecorators() {}

    // --- Floors --------------------------------------------------------------------------

    static BlockState floorFor(RoomType type, int lx, int lz, Random random) {
        if (type == null) return Blocks.DARK_OAK_PLANKS.getDefaultState();
        return switch (type) {
            case FOYER -> ((lx + lz) % 2 == 0 ? Blocks.POLISHED_BLACKSTONE : Blocks.SMOOTH_QUARTZ).getDefaultState();
            case GRAND_LIBRARY -> Blocks.SPRUCE_PLANKS.getDefaultState();
            case CRYPTS -> (random.nextFloat() < 0.3f ? Blocks.CRACKED_DEEPSLATE_TILES : Blocks.DEEPSLATE_TILES).getDefaultState();
            case OBSERVATORY -> {
                if (Math.abs(lx - MID) <= 1 && Math.abs(lz - MID) <= 1) yield Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA.getDefaultState();
                if (lx == lz || lx + lz == MansionLayout.CELL) yield Blocks.POLISHED_DEEPSLATE.getDefaultState();
                yield Blocks.POLISHED_BLACKSTONE.getDefaultState();
            }
            case CONSERVATORY -> (random.nextFloat() < 0.3f ? Blocks.MOSSY_STONE_BRICKS : Blocks.MOSS_BLOCK).getDefaultState();
            case BALLROOM -> ((lx / 2 + lz / 2) % 2 == 0 ? Blocks.POLISHED_BLACKSTONE : Blocks.SMOOTH_QUARTZ).getDefaultState();
            case BEDCHAMBER -> Blocks.SPRUCE_PLANKS.getDefaultState();
            default -> Blocks.DARK_OAK_PLANKS.getDefaultState();
        };
    }

    // --- Refraction puzzle + altar ---------------------------------------------------------

    private static final int[][] FOCALS = {{4, 6}, {8, 6}};
    private static final int[][] EMITTERS = {{1, 2}, {11, 2}};
    private static final int[][] LENSES = {{1, 9}, {4, 9}, {11, 9}, {8, 9}};
    private static final Direction[] LENS_SOLUTION = {Direction.EAST, Direction.NORTH, Direction.WEST, Direction.NORTH};
    /** Beam path cells (inclusive segments) for the base orientation. */
    private static final int[][] PATH_SEGMENTS = {
            {1, 2, 1, 9}, {1, 9, 4, 9}, {4, 6, 4, 9},
            {11, 2, 11, 9}, {8, 9, 11, 9}, {8, 6, 8, 9}};

    static void placeRituals(List<RoomContext> rooms, MansionLayout layout) {
        for (int index = 0; index < rooms.size(); index++) {
            RoomContext room = rooms.get(index);
            room.ritual = true;
            Random random = room.random;
            int r = random.nextInt(4);

            room.reserveRect(3, 3, 9, 9);
            for (int[] seg : PATH_SEGMENTS) {
                for (int x = Math.min(seg[0], seg[2]); x <= Math.max(seg[0], seg[2]); x++) {
                    for (int z = Math.min(seg[1], seg[3]); z <= Math.max(seg[1], seg[3]); z++) {
                        int[] p = rot(x, z, r);
                        room.reserve(p[0], p[1]);
                    }
                }
            }

            int[] altarLocal = rot(MID, MID, r);
            room.put(DECOR, altarLocal[0], 1, altarLocal[1], ModBlocks.RITUAL_ALTAR.getDefaultState(), MAIN);
            room.put(DECOR, altarLocal[0], 1, altarLocal[1], Blocks.CRYING_OBSIDIAN.getDefaultState(), MIRROR);
            BlockPos altarPos = room.pos(altarLocal[0], 1, altarLocal[1]);

            List<BlockPos> focals = new ArrayList<>();
            for (int[] f : FOCALS) {
                int[] p = rot(f[0], f[1], r);
                room.put(DECOR, p[0], 1, p[1], ModBlocks.FOCAL_POINT.getDefaultState(), MAIN);
                room.put(DECOR, p[0], 1, p[1], Blocks.TINTED_GLASS.getDefaultState(), MIRROR);
                focals.add(room.pos(p[0], 1, p[1]));
            }

            List<BlockPos> emitters = new ArrayList<>();
            Direction emitDir = rotate(Direction.SOUTH, r);
            for (int[] e : EMITTERS) {
                int[] p = rot(e[0], e[1], r);
                room.put(DECOR, p[0], 1, p[1], ModBlocks.LUMEN_PRISM.getDefaultState().with(BeamEmitterBlock.FACING, emitDir), MAIN);
                room.put(DECOR, p[0], 1, p[1], Blocks.POLISHED_BLACKSTONE.getDefaultState(), MIRROR);
                emitters.add(room.pos(p[0], 1, p[1]));
            }

            Direction[] facings = new Direction[LENSES.length];
            for (int n = 0; n < LENSES.length; n++) facings[n] = Direction.fromHorizontal(random.nextInt(4));
            for (int pair = 0; pair < LENSES.length; pair += 2) {
                // each beam path must start unsolved
                if (facings[pair] == LENS_SOLUTION[pair] && facings[pair + 1] == LENS_SOLUTION[pair + 1]) {
                    facings[pair + random.nextInt(2)] = LENS_SOLUTION[pair].getOpposite();
                }
            }
            List<BlockPos> lenses = new ArrayList<>();
            for (int n = 0; n <= LENSES.length; n++) {
                int[] base = n < LENSES.length ? LENSES[n] : new int[]{MID, 10}; // last one is a decoy
                int[] p = rot(base[0], base[1], r);
                Direction facing = n < LENSES.length ? rotate(facings[n], r) : Direction.fromHorizontal(random.nextInt(4));
                room.reserve(p[0], p[1]);
                room.put(DECOR, p[0], 1, p[1], ModBlocks.REFRACTION_LENS.getDefaultState().with(LensBlock.FACING, facing), MAIN);
                room.put(DECOR, p[0], 1, p[1], Blocks.AMETHYST_CLUSTER.getDefaultState(), MIRROR);
                lenses.add(room.pos(p[0], 1, p[1]));
            }

            int altarIndex = index;
            layout.altars.add(new MansionLayout.AltarSite(altarIndex, altarPos, emitters, focals, lenses));
            room.plan.addFinalizer(MAIN, world -> {
                if (world.getBlockEntity(altarPos) instanceof RitualAltarBlockEntity altar) {
                    altar.configure(altarIndex, emitters, focals, lenses);
                }
            });
        }
    }

    /** Rotates a cell-local coordinate clockwise around the room centre, {@code times} quarter turns. */
    private static int[] rot(int x, int z, int times) {
        for (int n = 0; n < times; n++) {
            int nx = MansionLayout.CELL - z;
            z = x;
            x = nx;
        }
        return new int[]{x, z};
    }

    private static Direction rotate(Direction d, int times) {
        for (int n = 0; n < times; n++) d = d.rotateYClockwise();
        return d;
    }

    // --- Furnaces & puzzle chests ------------------------------------------------------------

    static void placeFunctionalFixtures(List<RoomContext> rooms, MansionLayout layout, FRConfig cfg) {
        // Mansion furnaces (pre-fuelled in the Main World) for melting cursed fragments.
        for (RoomContext room : rooms) {
            int count = room.type == RoomType.BANQUET_HALL ? 2 : room.type == RoomType.CRYPTS ? 1 : 0;
            for (int n = 0; n < count; n++) placeFurnace(room, layout);
        }
        for (RoomContext room : rooms) {
            if (layout.furnaces.size() >= 2) break;
            if (room.type == RoomType.GALLERY && !room.ritual) placeFurnace(room, layout);
        }

        // wardrobes to hide in: two in every bedchamber, now and then elsewhere
        for (RoomContext room : rooms) {
            if (room.type == RoomType.STAIRWELL || room.type == RoomType.FOYER) continue;
            int count = room.type == RoomType.BEDCHAMBER ? 2 : room.random.nextFloat() < 0.3f ? 1 : 0;
            for (int n = 0; n < count; n++) placeWardrobe(room);
        }

        List<RoomContext> candidates = new ArrayList<>();
        for (RoomContext room : rooms) {
            if (!room.ritual && room.type != RoomType.STAIRWELL && room.type != RoomType.FOYER) candidates.add(room);
        }
        if (candidates.isEmpty()) return;
        shuffle(candidates, rooms.get(0).random);

        int wanted = Math.max(1, cfg.puzzleChests);
        List<BlockPos> chestPositions = new ArrayList<>();
        List<int[]> codes = new ArrayList<>();
        for (int attempt = 0; attempt < candidates.size() * 2 && chestPositions.size() < wanted; attempt++) {
            RoomContext room = candidates.get(attempt % candidates.size());
            RoomContext.LocalSpot spot = room.takeSpot();
            if (spot == null) continue;
            int[] code = new int[PuzzleSymbol.DIALS];
            for (int n = 0; n < code.length; n++) code[n] = room.random.nextInt(PuzzleSymbol.values().length);

            room.put(DECOR, spot.lx(), 1, spot.lz(), ModBlocks.PUZZLE_CHEST.getDefaultState().with(PuzzleChestBlock.FACING, spot.facing()), MAIN);
            room.put(DECOR, spot.lx(), 1, spot.lz(), Blocks.BARREL.getDefaultState().with(BarrelBlock.FACING, spot.facing()), MIRROR);
            BlockPos chestPos = room.pos(spot.lx(), 1, spot.lz());

            RoomContext.LocalSpot signSpot = room.takeSpot();
            if (signSpot == null) signSpot = spot;
            BlockPos signPos = room.pos(signSpot.lx(), 2, signSpot.lz());
            room.put(DECOR, signSpot.lx(), 2, signSpot.lz(), Blocks.DARK_OAK_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, signSpot.facing()));
            room.plan.addFinalizer(MAIN, world -> writeHint(world, signPos, code, true));
            room.plan.addFinalizer(MIRROR, world -> writeHint(world, signPos, code, false));

            chestPositions.add(chestPos);
            codes.add(code);
            layout.puzzleChests.add(new MansionLayout.PuzzleChestSite(chestPos, code));
        }
        if (chestPositions.isEmpty()) return;

        // Distribute fragments (enough for every altar plus spares) and batteries.
        int fragments = layout.altars.size() * Math.max(1, cfg.fragmentsPerAltar) + Math.max(0, cfg.spareFragments);
        List<List<ItemStack>> loot = new ArrayList<>();
        for (int n = 0; n < chestPositions.size(); n++) loot.add(new ArrayList<>());
        for (int n = 0; n < fragments; n++) loot.get(n % loot.size()).add(new ItemStack(ModItems.CURSED_GLASS_FRAGMENT));
        for (int n = 0; n < loot.size(); n++) {
            loot.get(n).add(new ItemStack(ModItems.BATTERY, n % 2 == 0 ? 2 : 1));
            if (n % 2 == 1) loot.get(n).add(new ItemStack(ModItems.SANITY_TONIC));
            if (n == 0) loot.get(n).add(FlashlightItem.charged());
        }
        for (int n = 0; n < chestPositions.size(); n++) {
            BlockPos pos = chestPositions.get(n);
            int[] code = codes.get(n);
            ItemStack[] stacks = loot.get(n).toArray(new ItemStack[0]);
            rooms.get(0).plan.addFinalizer(MAIN, world -> {
                if (world.getBlockEntity(pos) instanceof PuzzleChestBlockEntity chest) {
                    ItemStack[] copy = new ItemStack[stacks.length];
                    for (int k = 0; k < stacks.length; k++) copy[k] = stacks[k].copy();
                    chest.configure(code, copy);
                }
            });
        }
    }

    private static void placeWardrobe(RoomContext room) {
        RoomContext.LocalSpot spot = room.takeSpot();
        if (spot == null) return;
        BlockState wardrobe = ModBlocks.WARDROBE.getDefaultState().with(WardrobeBlock.FACING, spot.facing());
        room.put(BuildPlan.Phase.DOORS, spot.lx(), 1, spot.lz(), wardrobe.with(WardrobeBlock.HALF, DoubleBlockHalf.LOWER));
        room.put(BuildPlan.Phase.DOORS, spot.lx(), 2, spot.lz(), wardrobe.with(WardrobeBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static void placeFurnace(RoomContext room, MansionLayout layout) {
        RoomContext.LocalSpot spot = room.takeSpot();
        if (spot == null) return;
        room.put(DECOR, spot.lx(), 1, spot.lz(), Blocks.FURNACE.getDefaultState().with(AbstractFurnaceBlock.FACING, spot.facing()));
        BlockPos pos = room.pos(spot.lx(), 1, spot.lz());
        layout.furnaces.add(pos);
        room.plan.addFinalizer(MAIN, world -> {
            if (world.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
                furnace.setStack(1, new ItemStack(Items.COAL_BLOCK, 2));
            }
        });
    }

    /** Main World hints are written backwards; the Mirror Dimension shows them the right way round. */
    private static void writeHint(ServerWorld world, BlockPos pos, int[] code, boolean mirrored) {
        if (!(world.getBlockEntity(pos) instanceof SignBlockEntity sign)) return;
        SignText text = new SignText().withColor(mirrored ? DyeColor.GRAY : DyeColor.LIGHT_BLUE).withGlowing(!mirrored);
        text = text.withMessage(0, Text.literal(mirrored ? "~ SSALG EHT ~" : "~ THE GLASS ~"));
        for (int n = 0; n < Math.min(3, code.length); n++) {
            PuzzleSymbol symbol = PuzzleSymbol.byIndex(code[n]);
            text = text.withMessage(n + 1, Text.literal(mirrored ? symbol.mirrored() : symbol.name()));
        }
        sign.setText(text, true);
        sign.setWaxed(true);
        sign.markDirty();
        BlockState state = world.getBlockState(pos);
        world.updateListeners(pos, state, state, 3);
    }

    private static <T> void shuffle(List<T> list, Random random) {
        for (int n = list.size() - 1; n > 0; n--) {
            int k = random.nextInt(n + 1);
            T tmp = list.get(n);
            list.set(n, list.get(k));
            list.set(k, tmp);
        }
    }

    // --- Room dressing -----------------------------------------------------------------------

    static void decorate(RoomContext room) {
        switch (room.type) {
            case GRAND_LIBRARY -> library(room);
            case CRYPTS -> crypts(room);
            case BANQUET_HALL -> banquet(room);
            case OBSERVATORY -> observatory(room);
            case CONSERVATORY -> conservatory(room);
            case FOYER -> foyer(room);
            case GALLERY -> gallery(room);
            case CHAPEL -> chapel(room);
            case BALLROOM -> ballroom(room);
            case BEDCHAMBER -> bedchamber(room);
            case STAIRWELL -> { }
        }
        lights(room);
        cobwebs(room);
    }

    private static void lights(RoomContext room) {
        boolean crypt = room.type == RoomType.CRYPTS;
        BlockState lantern = (crypt ? Blocks.SOUL_LANTERN : Blocks.LANTERN).getDefaultState().with(LanternBlock.HANGING, true);
        int[][] spots = room.type == RoomType.STAIRWELL ? new int[][]{{9, 9}} : new int[][]{{3, 9}, {9, 3}};
        for (int[] s : spots) {
            room.attach(s[0], 5, s[1], lantern);
            if (room.random.nextBoolean()) room.attach(s[0], 4, s[1], lantern); // longer drop on some
        }
        RoomContext.LocalSpot spot = room.takeSpot();
        if (spot != null) {
            room.attach(spot.lx(), 3, spot.lz(), (crypt ? Blocks.SOUL_WALL_TORCH : Blocks.WALL_TORCH).getDefaultState()
                    .with(WallTorchBlock.FACING, spot.facing()));
        }
    }

    private static void cobwebs(RoomContext room) {
        int[][] corners = {{MIN, MIN}, {MIN, MAX}, {MAX, MIN}, {MAX, MAX}};
        float chance = room.type == RoomType.CRYPTS ? 0.9f : 0.35f;
        for (int[] c : corners) {
            if (room.random.nextFloat() < chance) room.attach(c[0], 5, c[1], Blocks.COBWEB.getDefaultState());
        }
    }

    private static BlockState candle(Random random, BlockState base) {
        return base.with(CandleBlock.CANDLES, 1 + random.nextInt(4)).with(CandleBlock.LIT, true);
    }

    private static void library(RoomContext room) {
        for (int lx = MIN; lx <= MAX; lx++) {
            for (int lz = MIN; lz <= MAX; lz++) {
                boolean edge = lx == MIN || lx == MAX || lz == MIN || lz == MAX;
                if (!edge || !room.free(lx, lz) || room.random.nextFloat() < 0.15f) continue;
                room.reserve(lx, lz);
                for (int ly = 1; ly <= 3; ly++) {
                    boolean chiseled = room.random.nextFloat() < 0.12f;
                    room.put(DECOR, lx, ly, lz, (chiseled ? Blocks.CHISELED_BOOKSHELF : Blocks.BOOKSHELF).getDefaultState());
                }
            }
        }
        for (int lz : new int[]{3, 9}) {
            for (int lx = MID - 1; lx <= MID + 1; lx++) {
                if (room.furnish(lx, 1, lz, Blocks.DARK_OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP))
                        && room.random.nextFloat() < 0.5f) {
                    room.attach(lx, 2, lz, candle(room.random, Blocks.CANDLE.getDefaultState()));
                }
            }
        }
        room.furnish(3, 1, MID, Blocks.LECTERN.getDefaultState().with(LecternBlock.FACING, Direction.EAST));
        room.furnish(9, 1, MID, Blocks.LECTERN.getDefaultState().with(LecternBlock.FACING, Direction.WEST));
        for (int lx = 2; lx <= 10; lx++) {
            if (room.free(lx, MID)) room.put(DECOR, lx, 1, MID, Blocks.RED_CARPET.getDefaultState());
        }
    }

    private static void crypts(RoomContext room) {
        for (int lx : new int[]{3, MID, 9}) {
            for (int lz : new int[]{3, 8}) {
                if (!room.free(lx, lz) || !room.free(lx, lz + 1)) continue;
                room.furnish(lx, 1, lz, Blocks.POLISHED_BLACKSTONE.getDefaultState());
                room.furnish(lx, 1, lz + 1, Blocks.POLISHED_BLACKSTONE.getDefaultState());
                float roll = room.random.nextFloat();
                if (roll < 0.4f) {
                    room.attach(lx, 2, lz, Blocks.SKELETON_SKULL.getDefaultState().with(SkullBlock.ROTATION, room.random.nextInt(16)));
                } else if (roll < 0.8f) {
                    room.attach(lx, 2, lz + 1, candle(room.random, Blocks.GRAY_CANDLE.getDefaultState()));
                }
            }
        }
        for (int n = 0; n < 4; n++) {
            int lx = 2 + room.random.nextInt(9), lz = 2 + room.random.nextInt(9);
            room.attach(lx, 5, lz, Blocks.CHAIN.getDefaultState());
        }
    }

    private static void banquet(RoomContext room) {
        for (int lx = 3; lx <= 9; lx++) {
            if (!room.furnish(lx, 1, MID, Blocks.DARK_OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP))) continue;
            if (lx % 2 == 0) room.attach(lx, 2, MID, candle(room.random, Blocks.WHITE_CANDLE.getDefaultState()));
            else room.attach(lx, 2, MID, Blocks.WHITE_CARPET.getDefaultState());
            if (lx % 2 == 1) {
                room.furnish(lx, 1, MID - 1, Blocks.DARK_OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH));
                room.furnish(lx, 1, MID + 1, Blocks.DARK_OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH));
            }
        }
        RoomContext.LocalSpot hearth = room.takeSpot();
        if (hearth != null) {
            room.put(DECOR, hearth.lx(), 1, hearth.lz(), Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.LIT, true));
            room.put(DECOR, hearth.lx(), 2, hearth.lz(), Blocks.BRICKS.getDefaultState());
        }
    }

    private static void observatory(RoomContext room) {
        if (room.furnish(MID, 1, MID, Blocks.POLISHED_BLACKSTONE_WALL.getDefaultState())) {
            room.attach(MID, 2, MID, Blocks.LIGHTNING_ROD.getDefaultState());
        }
        int[][] corners = {{2, 2}, {2, 10}, {10, 2}, {10, 10}};
        for (int[] c : corners) room.furnish(c[0], 1, c[1], Blocks.END_ROD.getDefaultState());
        room.furnish(MID - 3, 1, MID, Blocks.CARTOGRAPHY_TABLE.getDefaultState());
        room.furnish(MID + 3, 1, MID, Blocks.CARTOGRAPHY_TABLE.getDefaultState());
        for (int n = 0; n < 3; n++) {
            int lx = 2 + room.random.nextInt(9), lz = 2 + room.random.nextInt(9);
            if (room.furnish(lx, 1, lz, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState())) {
                room.attach(lx, 2, lz, Blocks.AMETHYST_CLUSTER.getDefaultState());
            }
        }
    }

    private static void conservatory(RoomContext room) {
        BlockState[] pots = {Blocks.POTTED_FERN.getDefaultState(), Blocks.POTTED_DEAD_BUSH.getDefaultState(),
                Blocks.POTTED_WITHER_ROSE.getDefaultState(), Blocks.POTTED_AZALEA_BUSH.getDefaultState()};
        for (int n = 0; n < 6; n++) {
            int lx = 2 + room.random.nextInt(9), lz = 2 + room.random.nextInt(9);
            if (room.furnish(lx, 1, lz, Blocks.MOSSY_COBBLESTONE.getDefaultState())) {
                room.attach(lx, 2, lz, pots[room.random.nextInt(pots.length)]);
            }
        }
        for (int n = 0; n < 8; n++) {
            int lx = 2 + room.random.nextInt(9), lz = 2 + room.random.nextInt(9);
            room.furnish(lx, 1, lz, (room.random.nextBoolean() ? Blocks.AZALEA : Blocks.FLOWERING_AZALEA).getDefaultState());
        }
        for (int n = 0; n < 10; n++) {
            int lx = MIN + room.random.nextInt(MAX), lz = MIN + room.random.nextInt(MAX);
            if (room.free(lx, lz)) room.put(DECOR, lx, 1, lz, Blocks.MOSS_CARPET.getDefaultState());
        }
    }

    private static void foyer(RoomContext room) {
        for (int[] c : new int[][]{{3, 3}, {9, 3}, {3, 9}, {9, 9}}) {
            if (!room.free(c[0], c[1])) continue;
            room.reserve(c[0], c[1]);
            for (int ly = 1; ly <= 5; ly++) room.put(DECOR, c[0], ly, c[1], Blocks.DARK_OAK_LOG.getDefaultState());
        }
        room.attach(MID, 5, MID + 2, Blocks.CHAIN.getDefaultState());
        room.attach(MID, 4, MID + 2, Blocks.CHAIN.getDefaultState());
        room.attach(MID, 3, MID + 2, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
        for (int lz = 5; lz <= 9; lz += 2) {
            room.furnish(MIN, 1, lz, Blocks.DARK_OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.WEST));
            room.furnish(MAX, 1, lz, Blocks.DARK_OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.EAST));
        }
        for (int lz = 2; lz <= 10; lz++) {
            if (room.free(MID, lz)) room.put(DECOR, MID, 1, lz, Blocks.RED_CARPET.getDefaultState());
        }
    }

    private static void gallery(RoomContext room) {
        for (int n = MIN + 1; n <= MAX - 1; n++) {
            if (room.free(n, MID)) room.put(DECOR, n, 1, MID, Blocks.GRAY_CARPET.getDefaultState());
            if (room.free(MID, n)) room.put(DECOR, MID, 1, n, Blocks.GRAY_CARPET.getDefaultState());
        }
        for (int[] c : new int[][]{{2, 2}, {2, 10}, {10, 2}, {10, 10}, {2, MID - 2}, {10, MID + 2}}) {
            if (!room.furnish(c[0], 1, c[1], Blocks.POLISHED_BLACKSTONE.getDefaultState())) continue;
            float roll = room.random.nextFloat();
            BlockState top = roll < 0.4f ? Blocks.DECORATED_POT.getDefaultState()
                    : roll < 0.7f ? Blocks.SKELETON_SKULL.getDefaultState().with(SkullBlock.ROTATION, room.random.nextInt(16))
                    : candle(room.random, Blocks.YELLOW_CANDLE.getDefaultState());
            room.attach(c[0], 2, c[1], top);
        }
    }

    /** Pews facing a candle-lit pulpit at the north end, a blood-red aisle down the middle. */
    private static void chapel(RoomContext room) {
        for (int lz = 2; lz <= 10; lz++) {
            if (room.free(MID, lz)) room.put(DECOR, MID, 1, lz, Blocks.RED_CARPET.getDefaultState());
        }
        for (int lz = 4; lz <= 9; lz += 2) {
            for (int lx : new int[]{2, 3, 4, 8, 9, 10}) {
                room.furnish(lx, 1, lz, Blocks.DARK_OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH));
            }
        }
        room.furnish(MID, 1, 2, Blocks.LECTERN.getDefaultState().with(LecternBlock.FACING, Direction.SOUTH));
        for (int lx : new int[]{MID - 2, MID + 2}) {
            if (room.furnish(lx, 1, 2, Blocks.POLISHED_BLACKSTONE_WALL.getDefaultState())) {
                room.attach(lx, 2, 2, candle(room.random, Blocks.WHITE_CANDLE.getDefaultState()).with(CandleBlock.CANDLES, 4));
            }
        }
        room.attach(MID, 5, 3, Blocks.CHAIN.getDefaultState());
        room.attach(MID, 4, 3, Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
    }

    /** A dusty ballroom: chequered floor, columns, a great chandelier. Ballrooms get extra mirrors. */
    private static void ballroom(RoomContext room) {
        for (int[] c : new int[][]{{3, 3}, {9, 3}, {3, 9}, {9, 9}}) {
            if (!room.free(c[0], c[1])) continue;
            room.reserve(c[0], c[1]);
            for (int ly = 1; ly <= 5; ly++) room.put(DECOR, c[0], ly, c[1], Blocks.DARK_OAK_LOG.getDefaultState());
        }
        room.attach(MID, 5, MID, Blocks.CHAIN.getDefaultState());
        room.attach(MID, 4, MID, Blocks.CHAIN.getDefaultState());
        room.attach(MID, 3, MID, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
        for (int[] o : new int[][]{{-2, 0}, {2, 0}, {0, -2}, {0, 2}}) {
            room.attach(MID + o[0], 5, MID + o[1], Blocks.CHAIN.getDefaultState());
            room.attach(MID + o[0], 4, MID + o[1], Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
        }
        for (int lz = 4; lz <= 8; lz += 2) {
            room.furnish(MIN, 1, lz, Blocks.DARK_OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.WEST));
            room.furnish(MAX, 1, lz, Blocks.DARK_OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.EAST));
        }
    }

    /** A four-poster bed under a canopy, nightstands with candles. Bedchambers always hold wardrobes. */
    private static void bedchamber(RoomContext room) {
        int x0 = 4, x1 = 7, z0 = 3, z1 = 7;
        boolean clear = true;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) clear &= room.free(x, z);
        if (clear) {
            room.reserveRect(x0, z0, x1, z1);
            for (int x = x0 + 1; x <= x1 - 1; x++) {
                for (int z = z0 + 1; z <= z1; z++) {
                    room.put(DECOR, x, 1, z, Blocks.WHITE_WOOL.getDefaultState());
                    room.attach(x, 2, z, (z == z0 + 1 ? Blocks.WHITE_CARPET : Blocks.RED_CARPET).getDefaultState());
                }
            }
            for (int[] p : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
                for (int ly = 1; ly <= 3; ly++) room.put(DECOR, p[0], ly, p[1], Blocks.DARK_OAK_FENCE.getDefaultState());
            }
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) room.put(DECOR, x, 4, z, Blocks.DARK_OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP));
            }
            for (int x : new int[]{x0 - 1, x1 + 1}) {
                if (room.furnish(x, 1, z0, Blocks.BARREL.getDefaultState())) {
                    room.attach(x, 2, z0, candle(room.random, Blocks.CANDLE.getDefaultState()));
                }
            }
        }
        for (int lx = 2; lx <= 10; lx += 4) {
            if (room.free(lx, MAX)) room.put(DECOR, lx, 1, MAX, Blocks.RED_CARPET.getDefaultState());
        }
    }
}
