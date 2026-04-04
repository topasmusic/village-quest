package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class WoolWeaverDailyQuest implements DailyQuestDefinition {
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
        return Text.translatable(
                "quest.village-quest.daily.wool.progress_shear_only",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SHEEP_PROGRESS),
                DailyQuestService.sheepTarget()
        ).formatted(Formatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SHEEP_PROGRESS) >= DailyQuestService.sheepTarget();
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
}
