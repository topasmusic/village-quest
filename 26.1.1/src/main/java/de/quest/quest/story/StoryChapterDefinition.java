package de.quest.quest.story;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public interface StoryChapterDefinition {
    Component title();

    Component offerParagraph1();

    Component offerParagraph2();

    List<Component> progressLines(ServerLevel world, UUID playerId);

    boolean isComplete(ServerLevel world, ServerPlayer player);

    StoryChapterCompletion buildCompletion();

    default boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) { return true; }

    default Component claimBlockedMessage(ServerLevel world, ServerPlayer player) { return null; }

    default void onAccepted(ServerLevel world, ServerPlayer player) {}

    default void onServerTick(ServerLevel world, ServerPlayer player) {}

    default void onUseBlock(ServerLevel world, ServerPlayer player, BlockPos pos, BlockState state, ItemStack inHand) {}

    default void onBlockBreak(ServerLevel world, ServerPlayer player, BlockPos pos, BlockState state) {}

    default void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack inHand) {}

    default void onTrackedItemPickup(ServerLevel world, ServerPlayer player, ItemStack stack, int count) {}

    default void onFurnaceOutput(ServerLevel world, ServerPlayer player, ItemStack stack) {}

    default void onAnvilOutput(ServerLevel world, ServerPlayer player, ItemStack leftInput, ItemStack rightInput, ItemStack output) {}

    default void onVillagerTrade(ServerLevel world, ServerPlayer player, ItemStack stack) {}

    default void onAnimalLove(ServerLevel world, ServerPlayer player, Animal animal) {}

    default void onPilgrimPurchase(ServerLevel world, ServerPlayer player, String offerId) {}

    default void onMonsterKill(ServerLevel world, ServerPlayer player, Entity killedEntity) {}

    default void onBeeNestInteract(ServerLevel world, ServerPlayer player, BlockState state, ItemStack inHand) {}
}
