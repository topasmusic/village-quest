package de.quest.quest.special;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.quest.daily.DailyQuestService;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.registry.ModItems;
import de.quest.util.Texts;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.ItemStack;

public final class ShepherdFluteQuestService {
    public static final int REQUIRED_ANIMAL_REPUTATION = 200;
    private static final int BREED_TARGET = 20;
    private static final String TAME_WOLF_FLAG = "special.shepherd_flute.tamed_wolf";
    private static final String TAME_CAT_FLAG = "special.shepherd_flute.tamed_cat";
    private static final String TAME_PARROT_FLAG = "special.shepherd_flute.tamed_parrot";

    private ShepherdFluteQuestService() {}

    private static PlayerQuestData data(ServerLevel world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    private static void markDirty(ServerLevel world) {
        if (world != null) {
            QuestState.get(world.getServer()).setDirty();
        }
    }

    private static void refreshQuestUi(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return;
        }
        QuestBookHelper.refreshQuestBook(world, player);
        QuestTrackerService.refresh(world, player);
        QuestMasterUiService.refreshIfOpen(world, player);
    }

    public static void onServerTick(MinecraftServer server) {
        // Progress is driven by breeding and taming hooks.
    }

    public static boolean handleQuestMasterInteraction(ServerLevel world, ServerPlayer player, boolean skipOffer) {
        if (world == null || player == null) {
            return false;
        }

        PlayerQuestData data = data(world, player.getUUID());
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

    public static boolean acceptQuest(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null || ModItems.SHEPHERD_FLUTE == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        data.setPendingSpecialOfferKind(null);
        if (!shouldOfferQuest(world, player, data)) {
            markDirty(world);
            return false;
        }

        data.resetShepherdFluteQuest();
        data.setShepherdFluteQuestStage(RelicQuestStage.ACTIVE);
        clearTamingFlags(data);
        markDirty(world);
        player.sendSystemMessage(Texts.acceptedTitle(title(), ChatFormatting.AQUA), false);
        QuestTrackerService.enableForAcceptedQuest(world, player);
        showProgress(player, data);
        refreshQuestUi(world, player);
        return true;
    }

    public static boolean isActive(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        RelicQuestStage stage = data(world, playerId).getShepherdFluteQuestStage();
        return stage == RelicQuestStage.ACTIVE || stage == RelicQuestStage.READY;
    }

    public static boolean isCompleted(ServerLevel world, UUID playerId) {
        return world != null && playerId != null && data(world, playerId).getShepherdFluteQuestStage() == RelicQuestStage.COMPLETED;
    }

    public static boolean isDiscovered(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        PlayerQuestData data = data(world, playerId);
        return RelicQuestProgressionService.hasStoryRequirement(world, playerId, SpecialQuestKind.SHEPHERD_FLUTE)
                || data.getPendingSpecialOfferKind() == SpecialQuestKind.SHEPHERD_FLUTE
                || data.getShepherdFluteQuestStage() != RelicQuestStage.NONE;
    }

    public static SpecialQuestStatus openStatus(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return null;
        }
        PlayerQuestData data = data(world, playerId);
        RelicQuestStage stage = data.getShepherdFluteQuestStage();
        if (stage != RelicQuestStage.ACTIVE && stage != RelicQuestStage.READY) {
            return null;
        }
        return new SpecialQuestStatus(title(), progressLines(data));
    }

    public static boolean claimFromQuestMaster(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        if (data.getShepherdFluteQuestStage() != RelicQuestStage.READY) {
            return false;
        }
        return completeQuest(world, player, data);
    }

    public static void onAnimalLove(ServerLevel world, ServerPlayer player, Animal animal) {
        if (world == null || player == null || animal == null) {
            return;
        }
        PlayerQuestData data = data(world, player.getUUID());
        if (data.getShepherdFluteQuestStage() != RelicQuestStage.ACTIVE) {
            return;
        }

        int beforeBreed = data.getShepherdFluteBreedProgress();
        boolean beforeWolf = data.hasMilestoneFlag(TAME_WOLF_FLAG);
        boolean beforeCat = data.hasMilestoneFlag(TAME_CAT_FLAG);
        boolean beforeParrot = data.hasMilestoneFlag(TAME_PARROT_FLAG);
        data.setShepherdFluteBreedProgress(Math.min(BREED_TARGET, beforeBreed + 1));
        updateProgress(world, player, data, beforeBreed, beforeWolf, beforeCat, beforeParrot);
    }

    public static void onAnimalTamed(ServerLevel world, ServerPlayer player, TamableAnimal animal) {
        if (world == null || player == null || animal == null) {
            return;
        }
        PlayerQuestData data = data(world, player.getUUID());
        if (data.getShepherdFluteQuestStage() != RelicQuestStage.ACTIVE) {
            return;
        }

        int beforeBreed = data.getShepherdFluteBreedProgress();
        boolean beforeWolf = data.hasMilestoneFlag(TAME_WOLF_FLAG);
        boolean beforeCat = data.hasMilestoneFlag(TAME_CAT_FLAG);
        boolean beforeParrot = data.hasMilestoneFlag(TAME_PARROT_FLAG);

        if (animal instanceof Wolf) {
            data.setMilestoneFlag(TAME_WOLF_FLAG, true);
        } else if (animal instanceof Cat) {
            data.setMilestoneFlag(TAME_CAT_FLAG, true);
        } else if (animal instanceof Parrot) {
            data.setMilestoneFlag(TAME_PARROT_FLAG, true);
        } else {
            return;
        }

        updateProgress(world, player, data, beforeBreed, beforeWolf, beforeCat, beforeParrot);
    }

    public static InteractionResult onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack inHand) {
        return InteractionResult.PASS;
    }

    public static void onTrackedItemPickup(ServerLevel world, ServerPlayer player, ItemStack stack, int count) {
        // No tracked-drop objective for this quest anymore.
    }

    public static boolean useFlute(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }

        List<Animal> animals = world.getEntitiesOfClass(
                Animal.class,
                player.getBoundingBox().inflate(24.0),
                animal -> animal.isAlive() && !animal.isRemoved() && animal.distanceToSqr(player) <= (24.0 * 24.0)
        );
        if (animals.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.village-quest.special.flute.no_animals").withStyle(ChatFormatting.GRAY), true);
            return false;
        }

        for (Animal animal : animals) {
            animal.getNavigation().moveTo(player, 1.15);
            animal.getLookControl().setLookAt(player.getX(), player.getEyeY(), player.getZ());
        }

        world.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 0.9f, 1.05f);
        player.sendSystemMessage(Component.translatable("message.village-quest.special.flute.used", animals.size()).withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    private static boolean shouldOfferQuest(ServerLevel world, ServerPlayer player, PlayerQuestData data) {
        return data.getShepherdFluteQuestStage() == RelicQuestStage.NONE
                && !data.isPendingSpecialOffer()
                && ModItems.SHEPHERD_FLUTE != null
                && DailyQuestService.openQuestStatus(world, player.getUUID()) == null
                && RelicQuestProgressionService.isUnlocked(world, player.getUUID(), SpecialQuestKind.SHEPHERD_FLUTE);
    }

    private static void showOffer(ServerLevel world, ServerPlayer player) {
        PlayerQuestData data = data(world, player.getUUID());
        data.setPendingSpecialOfferKind(SpecialQuestKind.SHEPHERD_FLUTE);
        markDirty(world);

        Component divider = Component.literal("------------------------------").withStyle(ChatFormatting.GRAY);
        Component accept = Component.translatable("text.village-quest.special.flute.offer.accept").withStyle(style -> style
                .withColor(ChatFormatting.GRAY)
                .withClickEvent(new ClickEvent.RunCommand("/dailyquest accept")));

        MutableComponent body = Component.empty()
                .append(divider.copy()).append(Component.literal("\n"))
                .append(Texts.dailyTitle(title(), ChatFormatting.AQUA)).append(Component.literal("\n\n"))
                .append(Component.translatable("quest.village-quest.special.flute.offer.1")).append(Component.literal("\n\n"))
                .append(Component.translatable("quest.village-quest.special.flute.offer.2")).append(Component.literal("\n\n\n"))
                .append(accept).append(Component.literal("\n"))
                .append(divider.copy());
        player.sendSystemMessage(body, false);
    }

    private static void showProgress(ServerPlayer player, PlayerQuestData data) {
        player.sendSystemMessage(Texts.dailyTitle(title(), ChatFormatting.AQUA), false);
        for (Component line : progressLines(data)) {
            player.sendSystemMessage(line, false);
        }
    }

    private static List<Component> progressLines(PlayerQuestData data) {
        return List.of(
                Component.translatable("quest.village-quest.special.flute.progress.breed", data.getShepherdFluteBreedProgress(), BREED_TARGET).withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.special.flute.progress.wolf", data.hasMilestoneFlag(TAME_WOLF_FLAG) ? 1 : 0, 1).withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.special.flute.progress.cat", data.hasMilestoneFlag(TAME_CAT_FLAG) ? 1 : 0, 1).withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.special.flute.progress.parrot", data.hasMilestoneFlag(TAME_PARROT_FLAG) ? 1 : 0, 1).withStyle(ChatFormatting.GRAY)
        );
    }

    private static boolean isComplete(PlayerQuestData data) {
        return data.getShepherdFluteBreedProgress() >= BREED_TARGET
                && data.hasMilestoneFlag(TAME_WOLF_FLAG)
                && data.hasMilestoneFlag(TAME_CAT_FLAG)
                && data.hasMilestoneFlag(TAME_PARROT_FLAG);
    }

    private static void updateProgress(ServerLevel world,
                                       ServerPlayer player,
                                       PlayerQuestData data,
                                       int beforeBreed,
                                       boolean beforeWolf,
                                       boolean beforeCat,
                                       boolean beforeParrot) {
        Component actionbar = null;
        boolean completedStep = false;

        if (beforeBreed != data.getShepherdFluteBreedProgress()) {
            actionbar = Component.translatable("quest.village-quest.special.flute.progress.breed", data.getShepherdFluteBreedProgress(), BREED_TARGET).withStyle(ChatFormatting.AQUA);
            if (beforeBreed < BREED_TARGET && data.getShepherdFluteBreedProgress() >= BREED_TARGET) {
                player.sendSystemMessage(Component.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).withStyle(ChatFormatting.AQUA), false);
                completedStep = true;
            }
        }
        if (beforeWolf != data.hasMilestoneFlag(TAME_WOLF_FLAG)) {
            actionbar = Component.translatable("quest.village-quest.special.flute.progress.wolf", data.hasMilestoneFlag(TAME_WOLF_FLAG) ? 1 : 0, 1).withStyle(ChatFormatting.AQUA);
            player.sendSystemMessage(Component.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).withStyle(ChatFormatting.AQUA), false);
            completedStep = true;
        }
        if (beforeCat != data.hasMilestoneFlag(TAME_CAT_FLAG)) {
            actionbar = Component.translatable("quest.village-quest.special.flute.progress.cat", data.hasMilestoneFlag(TAME_CAT_FLAG) ? 1 : 0, 1).withStyle(ChatFormatting.AQUA);
            player.sendSystemMessage(Component.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).withStyle(ChatFormatting.AQUA), false);
            completedStep = true;
        }
        if (beforeParrot != data.hasMilestoneFlag(TAME_PARROT_FLAG)) {
            actionbar = Component.translatable("quest.village-quest.special.flute.progress.parrot", data.hasMilestoneFlag(TAME_PARROT_FLAG) ? 1 : 0, 1).withStyle(ChatFormatting.AQUA);
            player.sendSystemMessage(Component.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).withStyle(ChatFormatting.AQUA), false);
            completedStep = true;
        }

        if (data.getShepherdFluteQuestStage() == RelicQuestStage.ACTIVE && isComplete(data)) {
            data.setShepherdFluteQuestStage(RelicQuestStage.READY);
            player.sendSystemMessage(Component.translatable("message.village-quest.special.flute.ready").withStyle(ChatFormatting.AQUA), false);
            completedStep = true;
        }

        markDirty(world);
        if (actionbar != null) {
            player.sendSystemMessage(actionbar, true);
        }
        world.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, completedStep ? 0.28f : 0.18f, completedStep ? 1.7f : 1.35f);
        refreshQuestUi(world, player);
    }

    private static boolean completeQuest(ServerLevel world, ServerPlayer player, PlayerQuestData data) {
        giveOrDrop(player, new ItemStack(ModItems.SHEPHERD_FLUTE));
        data.setPendingSpecialOfferKind(null);
        data.setShepherdFluteQuestStage(RelicQuestStage.COMPLETED);
        data.setShepherdFluteBreedProgress(BREED_TARGET);
        data.setMilestoneFlag(TAME_WOLF_FLAG, true);
        data.setMilestoneFlag(TAME_CAT_FLAG, true);
        data.setMilestoneFlag(TAME_PARROT_FLAG, true);
        markDirty(world);

        Component divider = Component.literal("------------------------------").withStyle(ChatFormatting.GRAY);
        MutableComponent body = Component.empty()
                .append(divider.copy()).append(Component.literal("\n"))
                .append(Texts.completedTitle(title(), ChatFormatting.AQUA)).append(Component.literal("\n\n"))
                .append(Component.translatable("quest.village-quest.special.flute.completed.1")).append(Component.literal("\n\n"))
                .append(Component.translatable("quest.village-quest.special.flute.completed.2")).append(Component.literal("\n"))
                .append(divider.copy());
        player.sendSystemMessage(body, false);
        world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.15f);
        refreshQuestUi(world, player);
        return true;
    }

    private static void clearTamingFlags(PlayerQuestData data) {
        data.setMilestoneFlag(TAME_WOLF_FLAG, false);
        data.setMilestoneFlag(TAME_CAT_FLAG, false);
        data.setMilestoneFlag(TAME_PARROT_FLAG, false);
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        ItemStack remainder = stack.copy();
        boolean inserted = player.getInventory().add(remainder);
        if (!inserted || !remainder.isEmpty()) {
            if (!remainder.isEmpty()) {
                player.drop(remainder, false);
            }
            player.sendSystemMessage(Component.translatable("message.village-quest.daily.inventory_full.prefix").withStyle(ChatFormatting.GRAY)
                    .append(stack.getDisplayName())
                    .append(Component.translatable("message.village-quest.daily.inventory_full.suffix").withStyle(ChatFormatting.GRAY)), false);
        } else {
            player.inventoryMenu.broadcastChanges();
        }
    }

    private static Component title() {
        return Component.translatable("quest.village-quest.special.flute.title");
    }
}
