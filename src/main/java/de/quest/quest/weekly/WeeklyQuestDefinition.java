package de.quest.quest.weekly;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;

public interface WeeklyQuestDefinition {
    WeeklyQuestService.WeeklyQuestType type();

    Text title();

    Text offerParagraph1();

    Text offerParagraph2();

    List<Text> progressLines(ServerWorld world, UUID playerId);

    boolean isComplete(ServerWorld world, ServerPlayerEntity player);

    WeeklyQuestCompletion buildCompletion();

    default void onAccepted(ServerWorld world, ServerPlayerEntity player) {}

    default void onServerTick(ServerWorld world, ServerPlayerEntity player) {}

    default void onBlockBreak(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {}

    default void onEntityUse(ServerWorld world, ServerPlayerEntity player, Entity entity, ItemStack inHand) {}

    default void onFurnaceOutput(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {}

    default void onVillagerTrade(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {}

    default void onAnimalLove(ServerWorld world, ServerPlayerEntity player, AnimalEntity animal) {}

    default void onPilgrimPurchase(ServerWorld world, ServerPlayerEntity player, String offerId) {}

    default void onMonsterKill(ServerWorld world, ServerPlayerEntity player, Entity killedEntity) {}
}
