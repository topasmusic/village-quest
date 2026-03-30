package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import de.quest.util.Texts;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class WoolWeaverDailyQuest implements DailyQuestDefinition {
    private static final Item[] WOOL_ITEMS = new Item[] {
            Items.WHITE_WOOL, Items.LIGHT_GRAY_WOOL, Items.GRAY_WOOL,
            Items.BLACK_WOOL, Items.BROWN_WOOL, Items.RED_WOOL,
            Items.ORANGE_WOOL, Items.YELLOW_WOOL, Items.LIME_WOOL,
            Items.GREEN_WOOL, Items.CYAN_WOOL, Items.LIGHT_BLUE_WOOL,
            Items.BLUE_WOOL, Items.PURPLE_WOOL, Items.MAGENTA_WOOL,
            Items.PINK_WOOL
    };

    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.WOOL_WEAVING;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.daily.wool.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.daily.wool.offer.1").formatted(Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.daily.wool.offer.2").formatted(Formatting.GRAY);
    }

    @Override
    public Text progressLine(ServerWorld world, UUID playerId) {
        ServerPlayerEntity player = world == null ? null : world.getServer().getPlayerManager().getPlayer(playerId);
        if (player != null) {
            Text blocked = claimBlockedMessage(world, player);
            if (blocked != null) {
                return blocked;
            }
        }

        return Text.translatable(
                "quest.village-quest.daily.wool.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SHEEP_PROGRESS),
                DailyQuestService.sheepTarget(),
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WOOL_PROGRESS),
                DailyQuestService.woolTarget()
        ).formatted(Formatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SHEEP_PROGRESS) >= DailyQuestService.sheepTarget()
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WOOL_PROGRESS) >= DailyQuestService.woolTarget()
                && DailyQuestService.countInventoryItems(player, WOOL_ITEMS) >= DailyQuestService.woolTarget();
    }

    @Override
    public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
        return DailyQuestService.consumeInventoryItems(player, DailyQuestService.woolTarget(), WOOL_ITEMS);
    }

    @Override
    public Text claimBlockedMessage(ServerWorld world, ServerPlayerEntity player) {
        if (player == null || world == null) {
            return null;
        }
        UUID playerId = player.getUuid();
        int woolTarget = DailyQuestService.woolTarget();
        if (DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SHEEP_PROGRESS) < DailyQuestService.sheepTarget()
                || DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WOOL_PROGRESS) < woolTarget
                || DailyQuestService.countInventoryItems(player, WOOL_ITEMS) >= woolTarget) {
            return null;
        }
        return Texts.turnInMissing(
                Text.translatable("text.village-quest.turnin.label.wool"),
                DailyQuestService.countInventoryItems(player, WOOL_ITEMS),
                woolTarget
        );
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerWorld world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Text.translatable("quest.village-quest.daily.wool.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.wool.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.wool.completion.3").formatted(Formatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onTrackedItemPickup(ServerWorld world, ServerPlayerEntity player, ItemStack stack, int count) {
        if (DailyQuestService.hasCompletedToday(world, player.getUuid())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUuid())) return;
        if (!isWool(stack)) {
            return;
        }

        UUID playerId = player.getUuid();
        int currentProgress = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WOOL_PROGRESS);
        int credit = Math.min(count, DailyQuestService.woolTarget() - currentProgress);
        if (credit > 0) {
            DailyQuestService.addQuestInt(world, playerId, DailyQuestKeys.WOOL_PROGRESS, credit);
            DailyQuestService.completeIfEligible(world, player);
            DailyQuestService.sendCurrentProgressActionbar(world, player);
        }
    }

    @Override
    public void onEntityUse(ServerWorld world, ServerPlayerEntity player, Entity entity, ItemStack inHand) {
        if (DailyQuestService.hasCompletedToday(world, player.getUuid())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUuid())) return;
        if (!(entity instanceof SheepEntity sheep)) return;
        if (!inHand.isOf(Items.SHEARS) || !sheep.isShearable()) return;

        UUID playerId = player.getUuid();
        int currentSheep = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SHEEP_PROGRESS);
        if (currentSheep < DailyQuestService.sheepTarget()) {
            DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.SHEEP_PROGRESS, Math.min(DailyQuestService.sheepTarget(), currentSheep + 1));
        }
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }

    private boolean isWool(ItemStack stack) {
        for (Item woolItem : WOOL_ITEMS) {
            if (stack.isOf(woolItem)) {
                return true;
            }
        }
        return false;
    }
}
