package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestCompletion;
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
    public void onServerTick(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();

        int curHoney = DailyQuestService.countInventoryItem(player, Items.HONEY_BOTTLE);
        int lastHoney = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.LAST_HONEY_COUNT);
        if (lastHoney == 0) {
            lastHoney = curHoney;
        }
        int expectedHoney = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.EXPECTED_HONEY);
        if (expectedHoney > 0) {
            int delta = curHoney - lastHoney;
            if (delta > 0 && DailyQuestService.isAcceptedToday(world, playerId)) {
                int credit = Math.min(delta, expectedHoney);
                DailyQuestService.addQuestInt(world, playerId, DailyQuestKeys.HONEY_PROGRESS, credit);
                DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.EXPECTED_HONEY, expectedHoney - credit);
                DailyQuestService.completeIfEligible(world, player);
                DailyQuestService.sendCurrentProgressActionbar(world, player);
            }
        }
        DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.LAST_HONEY_COUNT, curHoney);

        int curComb = DailyQuestService.countInventoryItem(player, Items.HONEYCOMB);
        int lastComb = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.LAST_COMB_COUNT);
        if (lastComb == 0) {
            lastComb = curComb;
        }
        int expectedComb = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.EXPECTED_COMB);
        if (expectedComb > 0) {
            int delta = curComb - lastComb;
            if (delta > 0 && DailyQuestService.isAcceptedToday(world, playerId)) {
                int credit = Math.min(delta, expectedComb);
                DailyQuestService.addQuestInt(world, playerId, DailyQuestKeys.COMB_PROGRESS, credit);
                DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.EXPECTED_COMB, expectedComb - credit);
                DailyQuestService.completeIfEligible(world, player);
                DailyQuestService.sendCurrentProgressActionbar(world, player);
            }
        }
        DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.LAST_COMB_COUNT, curComb);

        int left = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.EXPECTED_EXPIRE_TICKS) - 1;
        if (left <= 0) {
            DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.EXPECTED_HONEY, 0);
            DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.EXPECTED_COMB, 0);
            DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.EXPECTED_EXPIRE_TICKS, 0);
        } else {
            DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.EXPECTED_EXPIRE_TICKS, left);
        }
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
            DailyQuestService.addQuestInt(world, playerId, DailyQuestKeys.EXPECTED_HONEY, 1);
            if (DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.LAST_HONEY_COUNT) == 0) {
                DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.LAST_HONEY_COUNT,
                        DailyQuestService.countInventoryItem(player, Items.HONEY_BOTTLE));
            }
            DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.EXPECTED_EXPIRE_TICKS, 200);
        } else if (inHand.isOf(Items.SHEARS)) {
            DailyQuestService.addQuestInt(world, playerId, DailyQuestKeys.EXPECTED_COMB, 1);
            if (DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.LAST_COMB_COUNT) == 0) {
                DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.LAST_COMB_COUNT,
                        DailyQuestService.countInventoryItem(player, Items.HONEYCOMB));
            }
            DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.EXPECTED_EXPIRE_TICKS, 200);
        }
    }
}
