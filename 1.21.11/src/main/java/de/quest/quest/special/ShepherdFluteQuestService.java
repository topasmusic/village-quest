package de.quest.quest.special;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.quest.daily.DailyQuestService;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.registry.ModItems;
import de.quest.util.Texts;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.UUID;

public final class ShepherdFluteQuestService {
    public static final int REQUIRED_ANIMAL_REPUTATION = 200;
    private static final int BREED_TARGET = 4;
    private static final int SHEAR_TARGET = 3;
    private static final int WOOL_TARGET = 8;
    private static final Item[] WOOL_ITEMS = new Item[] {
            Items.WHITE_WOOL, Items.LIGHT_GRAY_WOOL, Items.GRAY_WOOL, Items.BLACK_WOOL,
            Items.BROWN_WOOL, Items.RED_WOOL, Items.ORANGE_WOOL, Items.YELLOW_WOOL,
            Items.LIME_WOOL, Items.GREEN_WOOL, Items.CYAN_WOOL, Items.LIGHT_BLUE_WOOL,
            Items.BLUE_WOOL, Items.PURPLE_WOOL, Items.MAGENTA_WOOL, Items.PINK_WOOL
    };

    private ShepherdFluteQuestService() {}

    private static PlayerQuestData data(ServerWorld world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    private static void markDirty(ServerWorld world) {
        if (world != null) {
            QuestState.get(world.getServer()).markDirty();
        }
    }

    private static void refreshQuestUi(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return;
        }
        QuestBookHelper.refreshQuestBook(world, player);
        QuestTrackerService.refresh(world, player);
        QuestMasterUiService.refreshIfOpen(world, player);
    }

    public static void onServerTick(MinecraftServer server) {
        // Wool progress is granted on tracked pickups.
    }

    public static boolean handleQuestMasterInteraction(ServerWorld world, ServerPlayerEntity player, boolean skipOffer) {
        if (world == null || player == null) {
            return false;
        }

        PlayerQuestData data = data(world, player.getUuid());
        return switch (data.getShepherdFluteQuestStage()) {
            case ACTIVE -> {
                showProgress(player, data);
                yield true;
            }
            case READY -> {
                yield completeQuest(world, player, data);
            }
            case COMPLETED -> false;
            case NONE -> {
                if (!skipOffer && shouldOfferQuest(world, player, data)) {
                    showOffer(world, player);
                    yield true;
                }
                yield false;
            }
        };
    }

    public static boolean acceptQuest(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null || ModItems.SHEPHERD_FLUTE == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
        data.setPendingSpecialOfferKind(null);
        if (!shouldOfferQuest(world, player, data)) {
            markDirty(world);
            return false;
        }

        data.resetShepherdFluteQuest();
        data.setShepherdFluteQuestStage(RelicQuestStage.ACTIVE);
        data.setShepherdFluteWoolBaseline(0);
        markDirty(world);
        player.sendMessage(Texts.acceptedTitle(title(), Formatting.AQUA), false);
        QuestTrackerService.enableForAcceptedQuest(world, player);
        showProgress(player, data);
        refreshQuestUi(world, player);
        return true;
    }

    public static boolean isActive(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        RelicQuestStage stage = data(world, playerId).getShepherdFluteQuestStage();
        return stage == RelicQuestStage.ACTIVE || stage == RelicQuestStage.READY;
    }

    public static boolean isCompleted(ServerWorld world, UUID playerId) {
        return world != null && playerId != null && data(world, playerId).getShepherdFluteQuestStage() == RelicQuestStage.COMPLETED;
    }

    public static boolean isDiscovered(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        PlayerQuestData data = data(world, playerId);
        return RelicQuestProgressionService.hasStoryRequirement(world, playerId, SpecialQuestKind.SHEPHERD_FLUTE)
                || data.getPendingSpecialOfferKind() == SpecialQuestKind.SHEPHERD_FLUTE
                || data.getShepherdFluteQuestStage() != RelicQuestStage.NONE;
    }

    public static SpecialQuestStatus openStatus(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return null;
        }
        PlayerQuestData data = data(world, playerId);
        RelicQuestStage stage = data.getShepherdFluteQuestStage();
        if (stage != RelicQuestStage.ACTIVE && stage != RelicQuestStage.READY) {
            return null;
        }
        return new SpecialQuestStatus(title(), progressLines(data, world.getServer().getPlayerManager().getPlayer(playerId)));
    }

    public static boolean claimFromQuestMaster(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
        if (data.getShepherdFluteQuestStage() != RelicQuestStage.READY) {
            return false;
        }
        return completeQuest(world, player, data);
    }

    public static void onAnimalLove(ServerWorld world, ServerPlayerEntity player, AnimalEntity animal) {
        if (world == null || player == null || animal == null) {
            return;
        }
        PlayerQuestData data = data(world, player.getUuid());
        if (data.getShepherdFluteQuestStage() != RelicQuestStage.ACTIVE) {
            return;
        }

        int beforeBreed = data.getShepherdFluteBreedProgress();
        int beforeShear = data.getShepherdFluteShearProgress();
        int beforeWool = data.getShepherdFluteWoolProgress();
        data.setShepherdFluteBreedProgress(Math.min(BREED_TARGET, beforeBreed + 1));
        updateProgress(world, player, data, beforeBreed, beforeShear, beforeWool);
    }

    public static ActionResult onEntityUse(ServerWorld world, ServerPlayerEntity player, Entity entity, ItemStack inHand) {
        if (world == null || player == null || !(entity instanceof SheepEntity sheep) || inHand == null) {
            return ActionResult.PASS;
        }
        PlayerQuestData data = data(world, player.getUuid());
        if (data.getShepherdFluteQuestStage() != RelicQuestStage.ACTIVE) {
            return ActionResult.PASS;
        }
        if (!inHand.isOf(Items.SHEARS) || !sheep.isShearable()) {
            return ActionResult.PASS;
        }

        int beforeBreed = data.getShepherdFluteBreedProgress();
        int beforeShear = data.getShepherdFluteShearProgress();
        int beforeWool = data.getShepherdFluteWoolProgress();
        data.setShepherdFluteShearProgress(Math.min(SHEAR_TARGET, beforeShear + 1));
        updateProgress(world, player, data, beforeBreed, beforeShear, beforeWool);
        return ActionResult.PASS;
    }

    public static void onTrackedItemPickup(ServerWorld world, ServerPlayerEntity player, ItemStack stack, int count) {
        if (world == null || player == null || stack == null || count <= 0) {
            return;
        }

        PlayerQuestData data = data(world, player.getUuid());
        if (data.getShepherdFluteQuestStage() != RelicQuestStage.ACTIVE || !isWool(stack.getItem())) {
            return;
        }

        int beforeBreed = data.getShepherdFluteBreedProgress();
        int beforeShear = data.getShepherdFluteShearProgress();
        int beforeWool = data.getShepherdFluteWoolProgress();
        data.setShepherdFluteWoolProgress(Math.min(WOOL_TARGET, beforeWool + count));
        updateProgress(world, player, data, beforeBreed, beforeShear, beforeWool);
    }

    public static boolean useFlute(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }

        List<AnimalEntity> animals = world.getEntitiesByClass(
                AnimalEntity.class,
                player.getBoundingBox().expand(24.0),
                animal -> animal.isAlive() && !animal.isRemoved() && animal.squaredDistanceTo(player) <= (24.0 * 24.0)
        );
        if (animals.isEmpty()) {
            player.sendMessage(Text.translatable("message.village-quest.special.flute.no_animals").formatted(Formatting.GRAY), true);
            return false;
        }

        for (AnimalEntity animal : animals) {
            animal.getNavigation().startMovingTo(player, 1.15);
            animal.getLookControl().lookAt(player.getX(), player.getEyeY(), player.getZ());
        }

        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), SoundCategory.PLAYERS, 0.9f, 1.05f);
        player.sendMessage(Text.translatable("message.village-quest.special.flute.used", animals.size()).formatted(Formatting.AQUA), true);
        return true;
    }

    private static boolean shouldOfferQuest(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data) {
        return data.getShepherdFluteQuestStage() == RelicQuestStage.NONE
                && !data.isPendingSpecialOffer()
                && ModItems.SHEPHERD_FLUTE != null
                && DailyQuestService.openQuestStatus(world, player.getUuid()) == null
                && RelicQuestProgressionService.isUnlocked(world, player.getUuid(), SpecialQuestKind.SHEPHERD_FLUTE);
    }

    private static void showOffer(ServerWorld world, ServerPlayerEntity player) {
        PlayerQuestData data = data(world, player.getUuid());
        data.setPendingSpecialOfferKind(SpecialQuestKind.SHEPHERD_FLUTE);
        markDirty(world);

        Text divider = Text.literal("------------------------------").formatted(Formatting.GRAY);
        Text accept = Text.translatable("text.village-quest.special.flute.offer.accept").styled(style -> style
                .withColor(Formatting.GRAY)
                .withClickEvent(new ClickEvent.RunCommand("/dailyquest accept")));

        MutableText body = Text.empty()
                .append(divider.copy()).append(Text.literal("\n"))
                .append(Texts.dailyTitle(title(), Formatting.AQUA)).append(Text.literal("\n\n"))
                .append(Text.translatable("quest.village-quest.special.flute.offer.1")).append(Text.literal("\n\n"))
                .append(Text.translatable("quest.village-quest.special.flute.offer.2")).append(Text.literal("\n\n\n"))
                .append(accept).append(Text.literal("\n"))
                .append(divider.copy());
        player.sendMessage(body, false);
    }

    private static void showProgress(ServerPlayerEntity player, PlayerQuestData data) {
        player.sendMessage(Texts.dailyTitle(title(), Formatting.AQUA), false);
        for (Text line : progressLines(data, player)) {
            player.sendMessage(line, false);
        }
    }

    private static List<Text> progressLines(PlayerQuestData data, ServerPlayerEntity player) {
        Text line1 = Text.translatable("quest.village-quest.special.flute.progress.breed", data.getShepherdFluteBreedProgress(), BREED_TARGET).formatted(Formatting.GRAY);
        Text line2 = Text.translatable("quest.village-quest.special.flute.progress.shear", data.getShepherdFluteShearProgress(), SHEAR_TARGET).formatted(Formatting.GRAY);
        Text line3 = Text.translatable("quest.village-quest.special.flute.progress.wool", data.getShepherdFluteWoolProgress(), WOOL_TARGET).formatted(Formatting.GRAY);
        Text blocked = missingWoolTurnInLine(data, player);
        return blocked == null ? List.of(line1, line2, line3) : List.of(line1, line2, line3, blocked);
    }

    private static boolean isComplete(PlayerQuestData data) {
        return data.getShepherdFluteBreedProgress() >= BREED_TARGET
                && data.getShepherdFluteShearProgress() >= SHEAR_TARGET
                && data.getShepherdFluteWoolProgress() >= WOOL_TARGET;
    }

    private static void updateProgress(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data, int beforeBreed, int beforeShear, int beforeWool) {
        Text actionbar = null;
        boolean completedStep = false;
        if (beforeBreed != data.getShepherdFluteBreedProgress()) {
            actionbar = Text.translatable("quest.village-quest.special.flute.progress.breed", data.getShepherdFluteBreedProgress(), BREED_TARGET).formatted(Formatting.AQUA);
            if (beforeBreed < BREED_TARGET && data.getShepherdFluteBreedProgress() >= BREED_TARGET) {
                player.sendMessage(Text.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).formatted(Formatting.AQUA), false);
                completedStep = true;
            }
        }
        if (beforeShear != data.getShepherdFluteShearProgress()) {
            actionbar = Text.translatable("quest.village-quest.special.flute.progress.shear", data.getShepherdFluteShearProgress(), SHEAR_TARGET).formatted(Formatting.AQUA);
            if (beforeShear < SHEAR_TARGET && data.getShepherdFluteShearProgress() >= SHEAR_TARGET) {
                player.sendMessage(Text.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).formatted(Formatting.AQUA), false);
                completedStep = true;
            }
        }
        if (beforeWool != data.getShepherdFluteWoolProgress()) {
            actionbar = Text.translatable("quest.village-quest.special.flute.progress.wool", data.getShepherdFluteWoolProgress(), WOOL_TARGET).formatted(Formatting.AQUA);
            if (beforeWool < WOOL_TARGET && data.getShepherdFluteWoolProgress() >= WOOL_TARGET) {
                player.sendMessage(Text.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).formatted(Formatting.AQUA), false);
                completedStep = true;
            }
        }

        if (data.getShepherdFluteQuestStage() == RelicQuestStage.ACTIVE && isComplete(data)) {
            data.setShepherdFluteQuestStage(RelicQuestStage.READY);
            player.sendMessage(Text.translatable("message.village-quest.special.flute.ready").formatted(Formatting.AQUA), false);
            completedStep = true;
        }

        markDirty(world);
        if (actionbar != null) {
            player.sendMessage(actionbar, true);
        }
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, completedStep ? 0.28f : 0.18f, completedStep ? 1.7f : 1.35f);
        refreshQuestUi(world, player);
    }

    private static boolean completeQuest(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data) {
        if (countInventoryWool(player) < WOOL_TARGET || !consumeInventoryWool(player, WOOL_TARGET)) {
            player.sendMessage(Text.translatable("message.village-quest.special.flute.wool_missing").formatted(Formatting.RED), false);
            refreshQuestUi(world, player);
            return false;
        }

        giveOrDrop(player, new ItemStack(ModItems.SHEPHERD_FLUTE));
        data.setPendingSpecialOfferKind(null);
        data.setShepherdFluteQuestStage(RelicQuestStage.COMPLETED);
        data.setShepherdFluteBreedProgress(BREED_TARGET);
        data.setShepherdFluteShearProgress(SHEAR_TARGET);
        data.setShepherdFluteWoolProgress(WOOL_TARGET);
        markDirty(world);

        Text divider = Text.literal("------------------------------").formatted(Formatting.GRAY);
        MutableText body = Text.empty()
                .append(divider.copy()).append(Text.literal("\n"))
                .append(Texts.completedTitle(title(), Formatting.AQUA)).append(Text.literal("\n\n"))
                .append(Text.translatable("quest.village-quest.special.flute.completed.1")).append(Text.literal("\n\n"))
                .append(Text.translatable("quest.village-quest.special.flute.completed.2")).append(Text.literal("\n"))
                .append(divider.copy());
        player.sendMessage(body, false);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.15f);
        refreshQuestUi(world, player);
        return true;
    }

    private static boolean isWool(Item item) {
        for (Item woolItem : WOOL_ITEMS) {
            if (woolItem == item) {
                return true;
            }
        }
        return false;
    }

    private static int countInventoryWool(ServerPlayerEntity player) {
        return DailyQuestService.countInventoryItems(player, WOOL_ITEMS);
    }

    private static Text missingWoolTurnInLine(PlayerQuestData data, ServerPlayerEntity player) {
        if (player == null
                || data.getShepherdFluteBreedProgress() < BREED_TARGET
                || data.getShepherdFluteShearProgress() < SHEAR_TARGET
                || data.getShepherdFluteWoolProgress() < WOOL_TARGET
                || countInventoryWool(player) >= WOOL_TARGET) {
            return null;
        }
        return Texts.turnInMissing(
                Text.translatable("text.village-quest.turnin.label.wool"),
                countInventoryWool(player),
                WOOL_TARGET
        );
    }

    private static boolean consumeInventoryWool(ServerPlayerEntity player, int amount) {
        return DailyQuestService.consumeInventoryItems(player, amount, WOOL_ITEMS);
    }

    private static void giveOrDrop(ServerPlayerEntity player, ItemStack stack) {
        ItemStack remainder = stack.copy();
        boolean inserted = player.getInventory().insertStack(remainder);
        if (!inserted || !remainder.isEmpty()) {
            if (!remainder.isEmpty()) {
                player.dropItem(remainder, false);
            }
            player.sendMessage(Text.translatable("message.village-quest.daily.inventory_full.prefix").formatted(Formatting.GRAY)
                    .append(stack.toHoverableText())
                    .append(Text.translatable("message.village-quest.daily.inventory_full.suffix").formatted(Formatting.GRAY)), false);
        } else {
            player.playerScreenHandler.sendContentUpdates();
        }
    }

    private static Text title() {
        return Text.translatable("quest.village-quest.special.flute.title");
    }
}
