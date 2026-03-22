package de.quest.quest.daily;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public interface DailyQuestDefinition {
    DailyQuestService.DailyQuestType type();

    Text title();

    Text offerParagraph1();

    Text offerParagraph2();

    Text progressLine(ServerWorld world, UUID playerId);

    boolean isComplete(ServerWorld world, ServerPlayerEntity player);

    DailyQuestCompletion buildCompletion(ServerWorld world);

    default void onAccepted(ServerWorld world, ServerPlayerEntity player) {}

    default void onServerTick(ServerWorld world, ServerPlayerEntity player) {}

    default void onBlockBreak(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {}

    default void onBeeNestInteract(ServerWorld world, ServerPlayerEntity player, BlockState state, ItemStack inHand) {}

    default void onEntityUse(ServerWorld world, ServerPlayerEntity player, Entity entity, ItemStack inHand) {}

    default void onFurnaceOutput(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {}

    default void onVillagerTrade(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {}

    default void onAnimalLove(ServerWorld world, ServerPlayerEntity player, AnimalEntity animal) {}

    default void onMonsterKill(ServerWorld world, ServerPlayerEntity player, Entity killedEntity) {}
}
