package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

public final class WoolWeaverDailyQuest implements DailyQuestDefinition {
    private static final ItemStack[] WOOL_ITEMS = new ItemStack[] {
            new ItemStack(Items.WHITE_WOOL), new ItemStack(Items.LIGHT_GRAY_WOOL), new ItemStack(Items.GRAY_WOOL),
            new ItemStack(Items.BLACK_WOOL), new ItemStack(Items.BROWN_WOOL), new ItemStack(Items.RED_WOOL),
            new ItemStack(Items.ORANGE_WOOL), new ItemStack(Items.YELLOW_WOOL), new ItemStack(Items.LIME_WOOL),
            new ItemStack(Items.GREEN_WOOL), new ItemStack(Items.CYAN_WOOL), new ItemStack(Items.LIGHT_BLUE_WOOL),
            new ItemStack(Items.BLUE_WOOL), new ItemStack(Items.PURPLE_WOOL), new ItemStack(Items.MAGENTA_WOOL),
            new ItemStack(Items.PINK_WOOL)
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
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WOOL_PROGRESS) >= DailyQuestService.woolTarget();
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
    public void onAccepted(ServerWorld world, ServerPlayerEntity player) {
        DailyQuestService.setQuestInt(world, player.getUuid(), DailyQuestKeys.LAST_WOOL_PICKED_UP, totalWoolPickedUp(player) + 1);
        DailyQuestService.setQuestInt(world, player.getUuid(), DailyQuestKeys.EXPECTED_WOOL, 0);
        DailyQuestService.setQuestInt(world, player.getUuid(), DailyQuestKeys.EXPECTED_WOOL_EXPIRE_TICKS, 0);
    }

    @Override
    public void onServerTick(ServerWorld world, ServerPlayerEntity player) {
        if (DailyQuestService.hasCompletedToday(world, player.getUuid())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUuid())) return;

        UUID playerId = player.getUuid();
        int currentPickedUp = totalWoolPickedUp(player);
        int storedLastPickedUp = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.LAST_WOOL_PICKED_UP);
        if (storedLastPickedUp == 0) {
            DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.LAST_WOOL_PICKED_UP, currentPickedUp + 1);
            return;
        }
        int lastPickedUp = storedLastPickedUp - 1;

        int expectedWool = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.EXPECTED_WOOL);
        int delta = currentPickedUp - lastPickedUp;
        if (expectedWool > 0 && delta > 0) {
            int currentProgress = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WOOL_PROGRESS);
            int credit = Math.min(delta, Math.min(expectedWool, DailyQuestService.woolTarget() - currentProgress));
            if (credit > 0) {
                DailyQuestService.addQuestInt(world, playerId, DailyQuestKeys.WOOL_PROGRESS, credit);
                DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.EXPECTED_WOOL, expectedWool - credit);
                DailyQuestService.completeIfEligible(world, player);
                DailyQuestService.sendCurrentProgressActionbar(world, player);
            }
        }
        DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.LAST_WOOL_PICKED_UP, currentPickedUp + 1);

        int left = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.EXPECTED_WOOL_EXPIRE_TICKS) - 1;
        if (left <= 0) {
            DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.EXPECTED_WOOL, 0);
            DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.EXPECTED_WOOL_EXPIRE_TICKS, 0);
        } else {
            DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.EXPECTED_WOOL_EXPIRE_TICKS, left);
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
        DailyQuestService.addQuestInt(world, playerId, DailyQuestKeys.EXPECTED_WOOL, 3);
        DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.EXPECTED_WOOL_EXPIRE_TICKS, 40);
        DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.LAST_WOOL_PICKED_UP, totalWoolPickedUp(player) + 1);
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }

    private int totalWoolPickedUp(ServerPlayerEntity player) {
        int total = 0;
        for (ItemStack stack : WOOL_ITEMS) {
            total += DailyQuestService.getPickedUpStat(player, stack.getItem());
        }
        return total;
    }
}
