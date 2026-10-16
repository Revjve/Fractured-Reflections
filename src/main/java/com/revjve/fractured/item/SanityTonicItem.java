package com.revjve.fractured.item;

import com.revjve.fractured.FRConfig;
import com.revjve.fractured.game.Match;
import com.revjve.fractured.game.MatchManager;
import com.revjve.fractured.game.PlayerState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/** A bitter laudanum tonic: drink to claw back some sanity. */
public class SanityTonicItem extends Item {
    public SanityTonicItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return ItemUsage.consumeHeldItem(world, user, hand);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.DRINK;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 40;
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            Match match = MatchManager.get().getMatch();
            PlayerState state = match == null ? null : match.state(player.getUuid());
            if (state != null) {
                state.sanity = Math.min(FRConfig.get().sanityMax, state.sanity + FRConfig.get().tonicSanity);
            }
            ((ServerWorld) world).spawnParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getEyeY(), player.getZ(), 8, 0.3, 0.3, 0.3, 0);
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 1f, 0.8f);
            player.sendMessage(Text.translatable("message.fractured_reflections.tonic").formatted(Formatting.GREEN), true);
            if (!player.getAbilities().creativeMode) stack.decrement(1);
        }
        return stack;
    }
}
