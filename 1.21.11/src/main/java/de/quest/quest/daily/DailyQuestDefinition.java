package de.quest.quest.daily;

import de.quest.quest.QuestCompletionMode;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public interface DailyQuestDefinition {
    DailyQuestService.DailyQuestType type();

    Component title();

    Component offerParagraph1();

    Component offerParagraph2();

    default java.util.List<Component> descriptionLines(ServerLevel world, UUID playerId) {
        return de.quest.quest.PeacefulRepeatableSupplies.daily(world,type())
                ? de.quest.quest.PeacefulRepeatableSupplies.dailyDescription(type())
                : java.util.List.of(offerParagraph1(),offerParagraph2());
    }

    Component progressLine(ServerLevel world, UUID playerId);

    boolean isComplete(ServerLevel world, ServerPlayer player);

    DailyQuestCompletion buildCompletion(ServerLevel world);

    default DailyQuestCompletion completionFor(ServerLevel world) {
        var completion = buildCompletion(world);
        return de.quest.quest.PeacefulRepeatableSupplies.daily(world,type())
                ? de.quest.quest.PeacefulRepeatableSupplies.completion(completion) : completion;
    }

    default QuestCompletionMode completionMode() { return QuestCompletionMode.AUTOMATIC; }

    default QuestCompletionMode completionMode(ServerLevel world) {
        return de.quest.quest.PeacefulRepeatableSupplies.daily(world,type())
                ? QuestCompletionMode.QUESTMASTER_TURN_IN : completionMode();
    }

    default boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
        return !de.quest.quest.PeacefulRepeatableSupplies.daily(world,type())
                || de.quest.quest.PeacefulRepeatableSupplies.consumeDaily(world,player,type());
    }

    default Component claimBlockedMessage(ServerLevel world, ServerPlayer player) {
        return de.quest.quest.PeacefulRepeatableSupplies.daily(world,type())
                && !de.quest.quest.PeacefulRepeatableSupplies.dailyComplete(world,player,type())
                ? de.quest.quest.PeacefulRepeatableSupplies.blocked() : null;
    }

    default Component claimBlockedMessage(ServerLevel world, ServerPlayer player, boolean allRequirements) {
        return claimBlockedMessage(world, player);
    }

    default void onAccepted(ServerLevel world, ServerPlayer player) {}

    default void onServerTick(ServerLevel world, ServerPlayer player) {}

    default void onBlockBreak(ServerLevel world, ServerPlayer player, BlockPos pos, BlockState state) {}

    default void onBeeNestInteract(ServerLevel world, ServerPlayer player, BlockState state, ItemStack inHand) {}

    default void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack inHand) {}

    default void onSheepSheared(ServerLevel world, ServerPlayer player, Sheep sheep) {}

    default void onTrackedItemPickup(ServerLevel world, ServerPlayer player, ItemStack stack, int count) {}

    default void onFurnaceOutput(ServerLevel world, ServerPlayer player, ItemStack stack) {}

    default void onVillagerTrade(ServerLevel world, ServerPlayer player, ItemStack stack) {}

    default void onAnimalLove(ServerLevel world, ServerPlayer player, Animal animal) {}

    default void onMonsterKill(ServerLevel world, ServerPlayer player, Entity killedEntity) {}
}
