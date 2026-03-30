package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

public final class HoneyDailyQuest implements DailyQuestDefinition {
    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.HONEY;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.daily.honey.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.daily.honey.offer.1").formatted(net.minecraft.util.Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.daily.honey.offer.2").formatted(net.minecraft.util.Formatting.GRAY);
    }

    @Override
    public Text progressLine(ServerWorld world, UUID playerId) {
        return Text.translatable(
                "quest.village-quest.daily.honey.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.HONEY_PROGRESS),
                DailyQuestService.honeyTarget(),
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.COMB_PROGRESS),
                DailyQuestService.combTarget()
        ).formatted(Formatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.HONEY_PROGRESS) >= DailyQuestService.honeyTarget()
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.COMB_PROGRESS) >= DailyQuestService.combTarget();
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerWorld world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Text.translatable("quest.village-quest.daily.honey.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.honey.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.honey.completion.3").formatted(Formatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onBeeNestInteract(ServerWorld world, ServerPlayerEntity player, BlockState state, ItemStack inHand) {
        if (DailyQuestService.hasCompletedToday(world, player.getUuid())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUuid())) return;
        if (!state.isOf(Blocks.BEE_NEST) && !state.isOf(Blocks.BEEHIVE)) return;
        if (!state.contains(BeehiveBlock.HONEY_LEVEL)) return;
        Integer level = state.get(BeehiveBlock.HONEY_LEVEL);
        if (level == null || level < 5) return;

        UUID playerId = player.getUuid();
        if (inHand.isOf(Items.GLASS_BOTTLE)) {
            DailyQuestService.addQuestInt(world, playerId, DailyQuestKeys.HONEY_PROGRESS, 1);
            DailyQuestService.completeIfEligible(world, player);
            DailyQuestService.sendCurrentProgressActionbar(world, player);
        } else if (inHand.isOf(Items.SHEARS)) {
            DailyQuestService.addQuestInt(world, playerId, DailyQuestKeys.COMB_PROGRESS, 1);
            DailyQuestService.completeIfEligible(world, player);
            DailyQuestService.sendCurrentProgressActionbar(world, player);
        }
    }
}
