package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class HoneyDailyQuest implements DailyQuestDefinition {
    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.HONEY;
    }

    @Override
    public Component title() {
        return Component.translatable("quest.village-quest.daily.honey.title");
    }

    @Override
    public Component offerParagraph1() {
        return Component.translatable("quest.village-quest.daily.honey.offer.1").withStyle(net.minecraft.ChatFormatting.GRAY);
    }

    @Override
    public Component offerParagraph2() {
        return Component.translatable("quest.village-quest.daily.honey.offer.2").withStyle(net.minecraft.ChatFormatting.GRAY);
    }

    @Override
    public Component progressLine(ServerLevel world, UUID playerId) {
        return Component.translatable(
                "quest.village-quest.daily.honey.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.HONEY_PROGRESS),
                DailyQuestService.honeyTarget(),
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.COMB_PROGRESS),
                DailyQuestService.combTarget()
        ).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.HONEY_PROGRESS) >= DailyQuestService.honeyTarget()
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.COMB_PROGRESS) >= DailyQuestService.combTarget();
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerLevel world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Component.translatable("quest.village-quest.daily.honey.completion.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.honey.completion.2").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.honey.completion.3").withStyle(ChatFormatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    public void onBeeNestInteract(ServerLevel world, ServerPlayer player, BlockState state, ItemStack inHand) {
        if (DailyQuestService.hasCompletedToday(world, player.getUUID())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUUID())) return;
        if (!state.is(Blocks.BEE_NEST) && !state.is(Blocks.BEEHIVE)) return;
        if (!state.hasProperty(BeehiveBlock.HONEY_LEVEL)) return;
        Integer level = state.getValue(BeehiveBlock.HONEY_LEVEL);
        if (level == null || level < 5) return;

        UUID playerId = player.getUUID();
        if (inHand.is(Items.GLASS_BOTTLE)) {
            DailyQuestService.addQuestInt(world, playerId, DailyQuestKeys.HONEY_PROGRESS, 1);
            DailyQuestService.completeIfEligible(world, player);
            DailyQuestService.sendCurrentProgressActionbar(world, player);
        } else if (inHand.is(Items.SHEARS)) {
            DailyQuestService.addQuestInt(world, playerId, DailyQuestKeys.COMB_PROGRESS, 1);
            DailyQuestService.completeIfEligible(world, player);
            DailyQuestService.sendCurrentProgressActionbar(world, player);
        }
    }
}
