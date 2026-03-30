package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import de.quest.util.Texts;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
    public Component title() {
        return Component.translatable("quest.village-quest.daily.wool.title");
    }

    @Override
    public Component offerParagraph1() {
        return Component.translatable("quest.village-quest.daily.wool.offer.1").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component offerParagraph2() {
        return Component.translatable("quest.village-quest.daily.wool.offer.2").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component progressLine(ServerLevel world, UUID playerId) {
        ServerPlayer player = world == null ? null : world.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) {
            Component blocked = claimBlockedMessage(world, player);
            if (blocked != null) {
                return blocked;
            }
        }

        return Component.translatable(
                "quest.village-quest.daily.wool.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SHEEP_PROGRESS),
                DailyQuestService.sheepTarget(),
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WOOL_PROGRESS),
                DailyQuestService.woolTarget()
        ).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SHEEP_PROGRESS) >= DailyQuestService.sheepTarget()
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WOOL_PROGRESS) >= DailyQuestService.woolTarget()
                && DailyQuestService.countInventoryItems(player, WOOL_ITEMS) >= DailyQuestService.woolTarget();
    }

    @Override
    public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
        return DailyQuestService.consumeInventoryItems(player, DailyQuestService.woolTarget(), WOOL_ITEMS);
    }

    @Override
    public Component claimBlockedMessage(ServerLevel world, ServerPlayer player) {
        if (player == null || world == null) {
            return null;
        }
        UUID playerId = player.getUUID();
        int woolTarget = DailyQuestService.woolTarget();
        if (DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SHEEP_PROGRESS) < DailyQuestService.sheepTarget()
                || DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WOOL_PROGRESS) < woolTarget
                || DailyQuestService.countInventoryItems(player, WOOL_ITEMS) >= woolTarget) {
            return null;
        }
        return Texts.turnInMissing(
                Component.translatable("text.village-quest.turnin.label.wool"),
                DailyQuestService.countInventoryItems(player, WOOL_ITEMS),
                woolTarget
        );
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerLevel world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Component.translatable("quest.village-quest.daily.wool.completion.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.wool.completion.2").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.wool.completion.3").withStyle(ChatFormatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onTrackedItemPickup(ServerLevel world, ServerPlayer player, ItemStack stack, int count) {
        if (DailyQuestService.hasCompletedToday(world, player.getUUID())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUUID())) return;
        if (!isWool(stack)) {
            return;
        }

        UUID playerId = player.getUUID();
        int currentProgress = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WOOL_PROGRESS);
        int credit = Math.min(count, DailyQuestService.woolTarget() - currentProgress);
        if (credit > 0) {
            DailyQuestService.addQuestInt(world, playerId, DailyQuestKeys.WOOL_PROGRESS, credit);
            DailyQuestService.completeIfEligible(world, player);
            DailyQuestService.sendCurrentProgressActionbar(world, player);
        }
    }

    @Override
    public void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack inHand) {
        if (DailyQuestService.hasCompletedToday(world, player.getUUID())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUUID())) return;
        if (!(entity instanceof Sheep sheep)) return;
        if (!inHand.is(Items.SHEARS) || !sheep.readyForShearing()) return;

        UUID playerId = player.getUUID();
        int currentSheep = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SHEEP_PROGRESS);
        if (currentSheep < DailyQuestService.sheepTarget()) {
            DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.SHEEP_PROGRESS, Math.min(DailyQuestService.sheepTarget(), currentSheep + 1));
        }
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }

    private boolean isWool(ItemStack stack) {
        for (Item woolItem : WOOL_ITEMS) {
            if (stack.is(woolItem)) {
                return true;
            }
        }
        return false;
    }
}
