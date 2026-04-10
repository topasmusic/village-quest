package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class WoolWeaverDailyQuest implements DailyQuestDefinition {
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
        return Component.translatable(
                "quest.village-quest.daily.wool.progress_shear_only",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SHEEP_PROGRESS),
                DailyQuestService.sheepTarget()
        ).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SHEEP_PROGRESS) >= DailyQuestService.sheepTarget();
    }

    @Override
    public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
        return true;
    }

    @Override
    public Component claimBlockedMessage(ServerLevel world, ServerPlayer player) {
        return null;
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
}
