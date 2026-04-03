package de.quest.quest.special;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.quest.daily.DailyQuestService;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.registry.ModItems;
import de.quest.util.Texts;
import de.quest.util.TimeUtil;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class ApiaristSmokerQuestService {
    public static final int REQUIRED_FARMING_REPUTATION = 200;
    private static final int HONEY_TARGET = 8;
    private static final int COMB_TARGET = 4;
    private static final int MAX_DAILY_USES = 10;

    private ApiaristSmokerQuestService() {}

    private static long currentDay() {
        return TimeUtil.currentDay();
    }

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

    public static boolean handleQuestMasterInteraction(ServerLevel world, ServerPlayer player, boolean skipOffer) {
        if (world == null || player == null) {
            return false;
        }

        PlayerQuestData data = data(world, player.getUUID());
        return switch (data.getApiaristSmokerQuestStage()) {
            case ACTIVE -> {
                showProgress(player, data);
                yield true;
            }
            case READY -> {
                completeQuest(world, player, data);
                yield true;
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
        if (world == null || player == null || ModItems.APIARISTS_SMOKER == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        data.setPendingSpecialOfferKind(null);
        if (!shouldOfferQuest(world, player, data)) {
            markDirty(world);
            return false;
        }

        data.resetApiaristSmokerQuest();
        data.setApiaristSmokerQuestStage(RelicQuestStage.ACTIVE);
        markDirty(world);
        player.sendSystemMessage(Texts.acceptedTitle(title(), ChatFormatting.GREEN), false);
        QuestTrackerService.enableForAcceptedQuest(world, player);
        showProgress(player, data);
        refreshQuestUi(world, player);
        return true;
    }

    public static boolean isActive(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        RelicQuestStage stage = data(world, playerId).getApiaristSmokerQuestStage();
        return stage == RelicQuestStage.ACTIVE || stage == RelicQuestStage.READY;
    }

    public static boolean isCompleted(ServerLevel world, UUID playerId) {
        return world != null && playerId != null && data(world, playerId).getApiaristSmokerQuestStage() == RelicQuestStage.COMPLETED;
    }

    public static boolean isDiscovered(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        PlayerQuestData data = data(world, playerId);
        return RelicQuestProgressionService.hasStoryRequirement(world, playerId, SpecialQuestKind.APIARIST_SMOKER)
                || data.getPendingSpecialOfferKind() == SpecialQuestKind.APIARIST_SMOKER
                || data.getApiaristSmokerQuestStage() != RelicQuestStage.NONE;
    }

    public static SpecialQuestStatus openStatus(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return null;
        }
        PlayerQuestData data = data(world, playerId);
        RelicQuestStage stage = data.getApiaristSmokerQuestStage();
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
        if (data.getApiaristSmokerQuestStage() != RelicQuestStage.READY) {
            return false;
        }
        completeQuest(world, player, data);
        return true;
    }

    public static void onBeeNestInteract(ServerLevel world, ServerPlayer player, BlockState state, ItemStack inHand) {
        if (world == null || player == null || state == null || inHand == null) {
            return;
        }
        PlayerQuestData data = data(world, player.getUUID());
        if (data.getApiaristSmokerQuestStage() != RelicQuestStage.ACTIVE) {
            return;
        }
        if ((!state.is(Blocks.BEE_NEST) && !state.is(Blocks.BEEHIVE)) || !state.hasProperty(BeehiveBlock.HONEY_LEVEL)) {
            return;
        }

        Integer honeyLevel = state.getValue(BeehiveBlock.HONEY_LEVEL);
        if (honeyLevel == null || honeyLevel < 5) {
            return;
        }

        int beforeHoney = data.getApiaristSmokerHoneyProgress();
        int beforeComb = data.getApiaristSmokerCombProgress();
        if (inHand.is(Items.GLASS_BOTTLE)) {
            data.setApiaristSmokerHoneyProgress(Math.min(HONEY_TARGET, beforeHoney + 1));
        } else if (inHand.is(Items.SHEARS)) {
            data.setApiaristSmokerCombProgress(Math.min(COMB_TARGET, beforeComb + 1));
        } else {
            return;
        }
        updateProgress(world, player, data, beforeHoney, beforeComb);
    }

    public static InteractionResult useSmoker(ServerLevel world, ServerPlayer player, BlockPos pos, BlockState state) {
        if (world == null || player == null || pos == null || state == null) {
            return InteractionResult.PASS;
        }
        if ((!state.is(Blocks.BEE_NEST) && !state.is(Blocks.BEEHIVE)) || !state.hasProperty(BeehiveBlock.HONEY_LEVEL)) {
            return InteractionResult.PASS;
        }

        PlayerQuestData data = data(world, player.getUUID());
        long day = currentDay();
        if (data.getApiaristSmokerLastUseDay() != day) {
            data.setApiaristSmokerLastUseDay(day);
            data.setApiaristSmokerUsesToday(0);
        }
        if (data.getApiaristSmokerUsesToday() >= MAX_DAILY_USES) {
            player.sendSystemMessage(Component.translatable("message.village-quest.special.apiarist_smoker.used_today").withStyle(ChatFormatting.RED), false);
            return InteractionResult.SUCCESS;
        }

        Integer honeyLevel = state.getValue(BeehiveBlock.HONEY_LEVEL);
        boolean filledHive = honeyLevel != null && honeyLevel < 5;
        if (filledHive) {
            world.setBlock(pos, state.setValue(BeehiveBlock.HONEY_LEVEL, 5), 3);
        }

        calmNearbyBees(world, pos);
        data.setApiaristSmokerLastUseDay(day);
        data.setApiaristSmokerUsesToday(data.getApiaristSmokerUsesToday() + 1);
        markDirty(world);

        world.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 18, 0.2, 0.25, 0.2, 0.01);
        world.playSound(null, pos, SoundEvents.BEEHIVE_WORK, SoundSource.PLAYERS, 0.9f, filledHive ? 0.8f : 0.6f);
        player.sendSystemMessage(Component.translatable(
                filledHive
                        ? "message.village-quest.special.apiarist_smoker.filled"
                        : "message.village-quest.special.apiarist_smoker.calmed",
                Math.max(0, MAX_DAILY_USES - data.getApiaristSmokerUsesToday()),
                MAX_DAILY_USES
        ).withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.SUCCESS;
    }

    private static void calmNearbyBees(ServerLevel world, BlockPos pos) {
        AABB area = new AABB(pos).inflate(12.0);
        for (Bee bee : world.getEntitiesOfClass(Bee.class, area, bee -> bee.isAlive() && !bee.isRemoved())) {
            bee.stopBeingAngry();
            bee.setTarget(null);
        }
    }

    private static boolean shouldOfferQuest(ServerLevel world, ServerPlayer player, PlayerQuestData data) {
        return data.getApiaristSmokerQuestStage() == RelicQuestStage.NONE
                && !data.isPendingSpecialOffer()
                && ModItems.APIARISTS_SMOKER != null
                && DailyQuestService.openQuestStatus(world, player.getUUID()) == null
                && RelicQuestProgressionService.isUnlocked(world, player.getUUID(), SpecialQuestKind.APIARIST_SMOKER);
    }

    private static void showOffer(ServerLevel world, ServerPlayer player) {
        PlayerQuestData data = data(world, player.getUUID());
        data.setPendingSpecialOfferKind(SpecialQuestKind.APIARIST_SMOKER);
        markDirty(world);

        Component divider = Component.literal("------------------------------").withStyle(ChatFormatting.GRAY);
        Component accept = Component.translatable("text.village-quest.special.apiarist_smoker.offer.accept").withStyle(style -> style
                .withColor(ChatFormatting.GRAY)
                .withClickEvent(new ClickEvent.RunCommand("/dailyquest accept")));

        MutableComponent body = Component.empty()
                .append(divider.copy()).append(Component.literal("\n"))
                .append(Texts.dailyTitle(title(), ChatFormatting.GREEN)).append(Component.literal("\n\n"))
                .append(Component.translatable("quest.village-quest.special.apiarist_smoker.offer.1")).append(Component.literal("\n\n"))
                .append(Component.translatable("quest.village-quest.special.apiarist_smoker.offer.2")).append(Component.literal("\n\n\n"))
                .append(accept).append(Component.literal("\n"))
                .append(divider.copy());
        player.sendSystemMessage(body, false);
    }

    private static void showProgress(ServerPlayer player, PlayerQuestData data) {
        player.sendSystemMessage(Texts.dailyTitle(title(), ChatFormatting.GREEN), false);
        for (Component line : progressLines(data)) {
            player.sendSystemMessage(line, false);
        }
    }

    private static List<Component> progressLines(PlayerQuestData data) {
        return List.of(
                Component.translatable("quest.village-quest.special.apiarist_smoker.progress.honey", data.getApiaristSmokerHoneyProgress(), HONEY_TARGET).withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.special.apiarist_smoker.progress.comb", data.getApiaristSmokerCombProgress(), COMB_TARGET).withStyle(ChatFormatting.GRAY)
        );
    }

    private static boolean isComplete(PlayerQuestData data) {
        return data.getApiaristSmokerHoneyProgress() >= HONEY_TARGET
                && data.getApiaristSmokerCombProgress() >= COMB_TARGET;
    }

    private static void updateProgress(ServerLevel world, ServerPlayer player, PlayerQuestData data, int beforeHoney, int beforeComb) {
        Component actionbar = null;
        boolean completedStep = false;
        if (beforeHoney != data.getApiaristSmokerHoneyProgress()) {
            actionbar = Component.translatable("quest.village-quest.special.apiarist_smoker.progress.honey", data.getApiaristSmokerHoneyProgress(), HONEY_TARGET).withStyle(ChatFormatting.GREEN);
            if (beforeHoney < HONEY_TARGET && data.getApiaristSmokerHoneyProgress() >= HONEY_TARGET) {
                player.sendSystemMessage(Component.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).withStyle(ChatFormatting.GREEN), false);
                completedStep = true;
            }
        }
        if (beforeComb != data.getApiaristSmokerCombProgress()) {
            actionbar = Component.translatable("quest.village-quest.special.apiarist_smoker.progress.comb", data.getApiaristSmokerCombProgress(), COMB_TARGET).withStyle(ChatFormatting.GREEN);
            if (beforeComb < COMB_TARGET && data.getApiaristSmokerCombProgress() >= COMB_TARGET) {
                player.sendSystemMessage(Component.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).withStyle(ChatFormatting.GREEN), false);
                completedStep = true;
            }
        }

        if (data.getApiaristSmokerQuestStage() == RelicQuestStage.ACTIVE && isComplete(data)) {
            data.setApiaristSmokerQuestStage(RelicQuestStage.READY);
            player.sendSystemMessage(Component.translatable("message.village-quest.special.apiarist_smoker.ready").withStyle(ChatFormatting.GREEN), false);
            completedStep = true;
        }

        markDirty(world);
        if (actionbar != null) {
            player.sendSystemMessage(actionbar, true);
        }
        world.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, completedStep ? 0.28f : 0.18f, completedStep ? 1.7f : 1.35f);
        refreshQuestUi(world, player);
    }

    private static void completeQuest(ServerLevel world, ServerPlayer player, PlayerQuestData data) {
        giveOrDrop(player, new ItemStack(ModItems.APIARISTS_SMOKER));
        data.setPendingSpecialOfferKind(null);
        data.setApiaristSmokerQuestStage(RelicQuestStage.COMPLETED);
        data.setApiaristSmokerHoneyProgress(HONEY_TARGET);
        data.setApiaristSmokerCombProgress(COMB_TARGET);
        markDirty(world);

        Component divider = Component.literal("------------------------------").withStyle(ChatFormatting.GRAY);
        MutableComponent body = Component.empty()
                .append(divider.copy()).append(Component.literal("\n"))
                .append(Texts.completedTitle(title(), ChatFormatting.GREEN)).append(Component.literal("\n\n"))
                .append(Component.translatable("quest.village-quest.special.apiarist_smoker.completed.1")).append(Component.literal("\n\n"))
                .append(Component.translatable("quest.village-quest.special.apiarist_smoker.completed.2")).append(Component.literal("\n"))
                .append(divider.copy());
        player.sendSystemMessage(body, false);
        world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.1f);
        refreshQuestUi(world, player);
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
        return Component.translatable("quest.village-quest.special.apiarist_smoker.title");
    }
}
