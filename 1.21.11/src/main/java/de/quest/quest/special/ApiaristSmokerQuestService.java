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
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.UUID;

public final class ApiaristSmokerQuestService {
    public static final int REQUIRED_FARMING_REPUTATION = 200;
    private static final int HONEY_TARGET = 8;
    private static final int COMB_TARGET = 4;
    private static final int MAX_DAILY_USES = 10;

    private ApiaristSmokerQuestService() {}

    private static long currentDay() {
        return TimeUtil.currentDay();
    }

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

    public static boolean handleQuestMasterInteraction(ServerWorld world, ServerPlayerEntity player, boolean skipOffer) {
        if (world == null || player == null) {
            return false;
        }

        PlayerQuestData data = data(world, player.getUuid());
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

    public static boolean acceptQuest(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null || ModItems.APIARISTS_SMOKER == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
        data.setPendingSpecialOfferKind(null);
        if (!shouldOfferQuest(world, player, data)) {
            markDirty(world);
            return false;
        }

        data.resetApiaristSmokerQuest();
        data.setApiaristSmokerQuestStage(RelicQuestStage.ACTIVE);
        markDirty(world);
        player.sendMessage(Texts.acceptedTitle(title(), Formatting.GREEN), false);
        QuestTrackerService.enableForAcceptedQuest(world, player);
        showProgress(player, data);
        refreshQuestUi(world, player);
        return true;
    }

    public static boolean isActive(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        RelicQuestStage stage = data(world, playerId).getApiaristSmokerQuestStage();
        return stage == RelicQuestStage.ACTIVE || stage == RelicQuestStage.READY;
    }

    public static boolean isCompleted(ServerWorld world, UUID playerId) {
        return world != null && playerId != null && data(world, playerId).getApiaristSmokerQuestStage() == RelicQuestStage.COMPLETED;
    }

    public static boolean isDiscovered(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        PlayerQuestData data = data(world, playerId);
        return RelicQuestProgressionService.hasStoryRequirement(world, playerId, SpecialQuestKind.APIARIST_SMOKER)
                || data.getPendingSpecialOfferKind() == SpecialQuestKind.APIARIST_SMOKER
                || data.getApiaristSmokerQuestStage() != RelicQuestStage.NONE;
    }

    public static SpecialQuestStatus openStatus(ServerWorld world, UUID playerId) {
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

    public static boolean claimFromQuestMaster(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
        if (data.getApiaristSmokerQuestStage() != RelicQuestStage.READY) {
            return false;
        }
        completeQuest(world, player, data);
        return true;
    }

    public static void onBeeNestInteract(ServerWorld world, ServerPlayerEntity player, BlockState state, ItemStack inHand) {
        if (world == null || player == null || state == null || inHand == null) {
            return;
        }
        PlayerQuestData data = data(world, player.getUuid());
        if (data.getApiaristSmokerQuestStage() != RelicQuestStage.ACTIVE) {
            return;
        }
        if ((!state.isOf(Blocks.BEE_NEST) && !state.isOf(Blocks.BEEHIVE)) || !state.contains(BeehiveBlock.HONEY_LEVEL)) {
            return;
        }

        Integer honeyLevel = state.get(BeehiveBlock.HONEY_LEVEL);
        if (honeyLevel == null || honeyLevel < 5) {
            return;
        }

        int beforeHoney = data.getApiaristSmokerHoneyProgress();
        int beforeComb = data.getApiaristSmokerCombProgress();
        if (inHand.isOf(Items.GLASS_BOTTLE)) {
            data.setApiaristSmokerHoneyProgress(Math.min(HONEY_TARGET, beforeHoney + 1));
        } else if (inHand.isOf(Items.SHEARS)) {
            data.setApiaristSmokerCombProgress(Math.min(COMB_TARGET, beforeComb + 1));
        } else {
            return;
        }
        updateProgress(world, player, data, beforeHoney, beforeComb);
    }

    public static ActionResult useSmoker(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {
        if (world == null || player == null || pos == null || state == null) {
            return ActionResult.PASS;
        }
        if ((!state.isOf(Blocks.BEE_NEST) && !state.isOf(Blocks.BEEHIVE)) || !state.contains(BeehiveBlock.HONEY_LEVEL)) {
            return ActionResult.PASS;
        }

        PlayerQuestData data = data(world, player.getUuid());
        long day = currentDay();
        if (data.getApiaristSmokerLastUseDay() != day) {
            data.setApiaristSmokerLastUseDay(day);
            data.setApiaristSmokerUsesToday(0);
        }
        if (data.getApiaristSmokerUsesToday() >= MAX_DAILY_USES) {
            player.sendMessage(Text.translatable("message.village-quest.special.apiarist_smoker.used_today").formatted(Formatting.RED), false);
            return ActionResult.SUCCESS;
        }

        Integer honeyLevel = state.get(BeehiveBlock.HONEY_LEVEL);
        boolean filledHive = honeyLevel != null && honeyLevel < 5;
        if (filledHive) {
            world.setBlockState(pos, state.with(BeehiveBlock.HONEY_LEVEL, 5), 3);
        }

        calmNearbyBees(world, pos);
        data.setApiaristSmokerLastUseDay(day);
        data.setApiaristSmokerUsesToday(data.getApiaristSmokerUsesToday() + 1);
        markDirty(world);

        world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 18, 0.2, 0.25, 0.2, 0.01);
        world.playSound(null, pos, SoundEvents.BLOCK_BEEHIVE_WORK, SoundCategory.PLAYERS, 0.9f, filledHive ? 0.8f : 0.6f);
        player.sendMessage(Text.translatable(
                filledHive
                        ? "message.village-quest.special.apiarist_smoker.filled"
                        : "message.village-quest.special.apiarist_smoker.calmed",
                Math.max(0, MAX_DAILY_USES - data.getApiaristSmokerUsesToday()),
                MAX_DAILY_USES
        ).formatted(Formatting.GREEN), true);
        return ActionResult.SUCCESS;
    }

    private static void calmNearbyBees(ServerWorld world, BlockPos pos) {
        Box area = new Box(pos).expand(12.0);
        for (BeeEntity bee : world.getEntitiesByClass(BeeEntity.class, area, bee -> bee.isAlive() && !bee.isRemoved())) {
            bee.stopAnger();
            bee.setTarget(null);
        }
    }

    private static boolean shouldOfferQuest(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data) {
        return data.getApiaristSmokerQuestStage() == RelicQuestStage.NONE
                && !data.isPendingSpecialOffer()
                && ModItems.APIARISTS_SMOKER != null
                && DailyQuestService.openQuestStatus(world, player.getUuid()) == null
                && RelicQuestProgressionService.isUnlocked(world, player.getUuid(), SpecialQuestKind.APIARIST_SMOKER);
    }

    private static void showOffer(ServerWorld world, ServerPlayerEntity player) {
        PlayerQuestData data = data(world, player.getUuid());
        data.setPendingSpecialOfferKind(SpecialQuestKind.APIARIST_SMOKER);
        markDirty(world);

        Text divider = Text.literal("------------------------------").formatted(Formatting.GRAY);
        Text accept = Text.translatable("text.village-quest.special.apiarist_smoker.offer.accept").styled(style -> style
                .withColor(Formatting.GRAY)
                .withClickEvent(new ClickEvent.RunCommand("/dailyquest accept")));

        MutableText body = Text.empty()
                .append(divider.copy()).append(Text.literal("\n"))
                .append(Texts.dailyTitle(title(), Formatting.GREEN)).append(Text.literal("\n\n"))
                .append(Text.translatable("quest.village-quest.special.apiarist_smoker.offer.1")).append(Text.literal("\n\n"))
                .append(Text.translatable("quest.village-quest.special.apiarist_smoker.offer.2")).append(Text.literal("\n\n\n"))
                .append(accept).append(Text.literal("\n"))
                .append(divider.copy());
        player.sendMessage(body, false);
    }

    private static void showProgress(ServerPlayerEntity player, PlayerQuestData data) {
        player.sendMessage(Texts.dailyTitle(title(), Formatting.GREEN), false);
        for (Text line : progressLines(data)) {
            player.sendMessage(line, false);
        }
    }

    private static List<Text> progressLines(PlayerQuestData data) {
        return List.of(
                Text.translatable("quest.village-quest.special.apiarist_smoker.progress.honey", data.getApiaristSmokerHoneyProgress(), HONEY_TARGET).formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.special.apiarist_smoker.progress.comb", data.getApiaristSmokerCombProgress(), COMB_TARGET).formatted(Formatting.GRAY)
        );
    }

    private static boolean isComplete(PlayerQuestData data) {
        return data.getApiaristSmokerHoneyProgress() >= HONEY_TARGET
                && data.getApiaristSmokerCombProgress() >= COMB_TARGET;
    }

    private static void updateProgress(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data, int beforeHoney, int beforeComb) {
        Text actionbar = null;
        boolean completedStep = false;
        if (beforeHoney != data.getApiaristSmokerHoneyProgress()) {
            actionbar = Text.translatable("quest.village-quest.special.apiarist_smoker.progress.honey", data.getApiaristSmokerHoneyProgress(), HONEY_TARGET).formatted(Formatting.GREEN);
            if (beforeHoney < HONEY_TARGET && data.getApiaristSmokerHoneyProgress() >= HONEY_TARGET) {
                player.sendMessage(Text.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).formatted(Formatting.GREEN), false);
                completedStep = true;
            }
        }
        if (beforeComb != data.getApiaristSmokerCombProgress()) {
            actionbar = Text.translatable("quest.village-quest.special.apiarist_smoker.progress.comb", data.getApiaristSmokerCombProgress(), COMB_TARGET).formatted(Formatting.GREEN);
            if (beforeComb < COMB_TARGET && data.getApiaristSmokerCombProgress() >= COMB_TARGET) {
                player.sendMessage(Text.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).formatted(Formatting.GREEN), false);
                completedStep = true;
            }
        }

        if (data.getApiaristSmokerQuestStage() == RelicQuestStage.ACTIVE && isComplete(data)) {
            data.setApiaristSmokerQuestStage(RelicQuestStage.READY);
            player.sendMessage(Text.translatable("message.village-quest.special.apiarist_smoker.ready").formatted(Formatting.GREEN), false);
            completedStep = true;
        }

        markDirty(world);
        if (actionbar != null) {
            player.sendMessage(actionbar, true);
        }
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, completedStep ? 0.28f : 0.18f, completedStep ? 1.7f : 1.35f);
        refreshQuestUi(world, player);
    }

    private static void completeQuest(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data) {
        giveOrDrop(player, new ItemStack(ModItems.APIARISTS_SMOKER));
        data.setPendingSpecialOfferKind(null);
        data.setApiaristSmokerQuestStage(RelicQuestStage.COMPLETED);
        data.setApiaristSmokerHoneyProgress(HONEY_TARGET);
        data.setApiaristSmokerCombProgress(COMB_TARGET);
        markDirty(world);

        Text divider = Text.literal("------------------------------").formatted(Formatting.GRAY);
        MutableText body = Text.empty()
                .append(divider.copy()).append(Text.literal("\n"))
                .append(Texts.completedTitle(title(), Formatting.GREEN)).append(Text.literal("\n\n"))
                .append(Text.translatable("quest.village-quest.special.apiarist_smoker.completed.1")).append(Text.literal("\n\n"))
                .append(Text.translatable("quest.village-quest.special.apiarist_smoker.completed.2")).append(Text.literal("\n"))
                .append(divider.copy());
        player.sendMessage(body, false);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.1f);
        refreshQuestUi(world, player);
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
        return Text.translatable("quest.village-quest.special.apiarist_smoker.title");
    }
}
