package com.revjve.fractured.block;

import com.revjve.fractured.registry.ModBlockEntities;
import com.revjve.fractured.registry.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class PuzzleChestBlockEntity extends BlockEntity implements NamedScreenHandlerFactory {
    private static final int LOCKOUT_TICKS = 60;

    private final SimpleInventory inventory = new SimpleInventory(27);
    private final int[] code = new int[PuzzleSymbol.DIALS];
    private long lockoutUntil;

    public PuzzleChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PUZZLE_CHEST, pos, state);
        inventory.addListener(inv -> markDirty());
    }

    public void configure(int[] code, ItemStack... loot) {
        System.arraycopy(code, 0, this.code, 0, Math.min(code.length, this.code.length));
        inventory.clear();
        for (int i = 0; i < loot.length && i < inventory.size(); i++) {
            // spread loot across the grid so it reads like a rummaged chest
            inventory.setStack((i * 7 + 3) % inventory.size(), loot[i]);
        }
        markDirty();
    }

    public int getDials() {
        return code.length;
    }

    public boolean isLockedOut(long time) {
        return time < lockoutUntil;
    }

    /** Server-side check of a submitted combination. Returns true when the chest unseals. */
    public boolean trySolve(ServerPlayerEntity player, int[] attempt) {
        if (!(world instanceof ServerWorld serverWorld)) return false;
        BlockState state = getCachedState();
        if (!state.contains(PuzzleChestBlock.SEALED) || !state.get(PuzzleChestBlock.SEALED)) return true;
        if (isLockedOut(serverWorld.getTime())) return false;
        if (Arrays.equals(code, attempt)) {
            serverWorld.setBlockState(pos, state.with(PuzzleChestBlock.SEALED, false), Block.NOTIFY_ALL);
            serverWorld.playSound(null, pos, ModSounds.PUZZLE_SOLVED, SoundCategory.BLOCKS, 1.0f, 1.0f);
            player.openHandledScreen(this);
            return true;
        }
        lockoutUntil = serverWorld.getTime() + LOCKOUT_TICKS;
        // a wrong guess is loud enough to be heard through the walls
        serverWorld.playSound(null, pos, ModSounds.PUZZLE_FAIL, SoundCategory.BLOCKS, 3.0f, 0.7f);
        player.sendMessage(Text.translatable("message.fractured_reflections.puzzle_wrong"), true);
        return false;
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("container.fractured_reflections.puzzle_chest");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, inventory);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.put("Items", inventory.toNbtList());
        nbt.putIntArray("Code", code);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        inventory.readNbtList(nbt.getList("Items", NbtElement.COMPOUND_TYPE));
        int[] stored = nbt.getIntArray("Code");
        System.arraycopy(stored, 0, code, 0, Math.min(stored.length, code.length));
    }
}
