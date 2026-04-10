package de.quest.quest.special;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.quest.daily.DailyQuestService;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.util.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class AdminCoreTestQuestService {
    private static final String ACTIVE_FLAG = "admin_core_test.active";
    private static final String COMPLETE_FLAG = "admin_core_test.complete";
    private static final String BLOCK_BREAK_FLAG = "admin_core_test.block_break";
    private static final String SHEAR_FLAG = "admin_core_test.shear";
    private static final String WOOL_FLAG = "admin_core_test.wool";
    private static final String BREED_FLAG = "admin_core_test.breed";
    private static final String RAW_IRON_FLAG = "admin_core_test.raw_iron";
    private static final String RAW_GOLD_FLAG = "admin_core_test.raw_gold";
    private static final String SMELT_FLAG = "admin_core_test.smelt";
    private static final String REDSTONE_FLAG = "admin_core_test.redstone";
    private static final String LAPIS_FLAG = "admin_core_test.lapis";
    private static final String HONEY_FLAG = "admin_core_test.honey";
    private static final String HONEY_BLOCK_FLAG = "admin_core_test.honey_block";
    private static final String TRADE_FLAG = "admin_core_test.trade";
    private static final String VILLAGER_PURCHASE_FLAG = "admin_core_test.villager_purchase";
    private static final String BELL_FLAG = "admin_core_test.bell";
    private static final String PILGRIM_FLAG = "admin_core_test.pilgrim";
    private static final String ANVIL_FLAG = "admin_core_test.anvil";
    private static final String KILL_FLAG = "admin_core_test.kill";
    private static final String TAME_WOLF_FLAG = "admin_core_test.tame_wolf";
    private static final String TAME_CAT_FLAG = "admin_core_test.tame_cat";
    private static final String TAME_PARROT_FLAG = "admin_core_test.tame_parrot";
    private static final String HONEY_BLOCK_BASELINE_KEY = "admin_core_test.honey_block_baseline";

    private static final Item[] WOOL_ITEMS = new Item[] {
            Items.WHITE_WOOL, Items.LIGHT_GRAY_WOOL, Items.GRAY_WOOL,
            Items.BLACK_WOOL, Items.BROWN_WOOL, Items.RED_WOOL,
            Items.ORANGE_WOOL, Items.YELLOW_WOOL, Items.LIME_WOOL,
            Items.GREEN_WOOL, Items.CYAN_WOOL, Items.LIGHT_BLUE_WOOL,
            Items.BLUE_WOOL, Items.PURPLE_WOOL, Items.MAGENTA_WOOL,
            Items.PINK_WOOL
    };

    private AdminCoreTestQuestService() {}

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
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel world) || !isActive(world, player.getUUID())) {
                continue;
            }
            PlayerQuestData data = data(world, player.getUUID());
            int baseline = data.getPilgrimInt(HONEY_BLOCK_BASELINE_KEY);
            if (DailyQuestService.getCraftedStat(player, Items.HONEY_BLOCK) > baseline) {
                completeObjective(world, player, HONEY_BLOCK_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.honey_block", true));
            }
        }
    }

    public static boolean isActive(ServerLevel world, UUID playerId) {
        return world != null && playerId != null && data(world, playerId).hasMilestoneFlag(ACTIVE_FLAG);
    }

    public static SpecialQuestStatus openStatus(ServerLevel world, UUID playerId) {
        if (!isActive(world, playerId)) {
            return null;
        }
        return new SpecialQuestStatus(title(), progressLines(data(world, playerId)));
    }

    public static boolean adminStart(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }

        PlayerQuestData data = data(world, player.getUUID());
        clearFlags(data);
        data.setPilgrimInt(HONEY_BLOCK_BASELINE_KEY, DailyQuestService.getCraftedStat(player, Items.HONEY_BLOCK));
        data.setMilestoneFlag(ACTIVE_FLAG, true);
        markDirty(world);

        player.sendSystemMessage(Texts.acceptedTitle(title(), ChatFormatting.GREEN), false);
        player.sendSystemMessage(Component.translatable("message.village-quest.special.admin_core_test.started").withStyle(ChatFormatting.GRAY), false);
        QuestTrackerService.enableForAcceptedQuest(world, player);
        refreshQuestUi(world, player);
        return true;
    }

    public static boolean adminReset(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }

        PlayerQuestData data = data(world, player.getUUID());
        boolean hadState = hasAnyFlag(data);
        clearFlags(data);
        markDirty(world);
        refreshQuestUi(world, player);
        return hadState;
    }

    public static void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack inHand) {
        if (!(entity instanceof Sheep sheep) || inHand == null || !inHand.is(Items.SHEARS) || !sheep.readyForShearing()) {
            return;
        }
        completeObjective(world, player, SHEAR_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.shear", true));
    }

    public static void onUseBlock(ServerLevel world, ServerPlayer player, BlockPos pos) {
        if (world == null || player == null || pos == null) {
            return;
        }
        if (world.getBlockState(pos).is(Blocks.BELL)) {
            completeObjective(world, player, BELL_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.bell", true));
        }
    }

    public static void onBlockBreak(ServerLevel world, ServerPlayer player, BlockPos pos, BlockState state) {
        if (state != null && state.is(BlockTags.LOGS)) {
            completeObjective(world, player, BLOCK_BREAK_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.block_break", true));
        }
    }

    public static void onTrackedItemPickup(ServerLevel world, ServerPlayer player, ItemStack stack, int count) {
        if (stack == null || stack.isEmpty() || count <= 0) {
            return;
        }
        if (isWool(stack)) {
            completeObjective(world, player, WOOL_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.wool", true));
        }
        if (stack.is(Items.RAW_IRON)) {
            completeObjective(world, player, RAW_IRON_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.raw_iron", true));
        }
        if (stack.is(Items.RAW_GOLD)) {
            completeObjective(world, player, RAW_GOLD_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.raw_gold", true));
        }
        if (stack.is(Items.REDSTONE)) {
            completeObjective(world, player, REDSTONE_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.redstone", true));
        }
        if (stack.is(Items.LAPIS_LAZULI)) {
            completeObjective(world, player, LAPIS_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.lapis", true));
        }
    }

    public static void onAnimalLove(ServerLevel world, ServerPlayer player, Animal animal) {
        if (animal != null) {
            completeObjective(world, player, BREED_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.breed", true));
        }
    }

    public static void onAnimalTamed(ServerLevel world, ServerPlayer player, TamableAnimal animal) {
        if (animal instanceof Wolf) {
            completeObjective(world, player, TAME_WOLF_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.tame_wolf", true));
        } else if (animal instanceof Cat) {
            completeObjective(world, player, TAME_CAT_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.tame_cat", true));
        } else if (animal instanceof Parrot) {
            completeObjective(world, player, TAME_PARROT_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.tame_parrot", true));
        }
    }

    public static void onFurnaceOutput(ServerLevel world, ServerPlayer player, ItemStack stack) {
        if (stack != null && stack.is(Items.IRON_INGOT)) {
            completeObjective(world, player, SMELT_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.smelt", true));
        }
    }

    public static void onBeeNestInteract(ServerLevel world, ServerPlayer player, BlockState state, ItemStack inHand) {
        if (world == null || player == null || state == null || inHand == null) {
            return;
        }
        if ((!state.is(Blocks.BEE_NEST) && !state.is(Blocks.BEEHIVE)) || !state.hasProperty(BeehiveBlock.HONEY_LEVEL)) {
            return;
        }
        Integer honeyLevel = state.getValue(BeehiveBlock.HONEY_LEVEL);
        if (honeyLevel == null || honeyLevel < 5) {
            return;
        }
        if (inHand.is(Items.GLASS_BOTTLE) || inHand.is(Items.SHEARS)) {
            completeObjective(world, player, HONEY_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.honey", true));
        }
    }

    public static void onVillagerTrade(ServerLevel world, ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        completeObjective(world, player, TRADE_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.trade", true));
        if (!stack.is(Items.EMERALD)) {
            completeObjective(world, player, VILLAGER_PURCHASE_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.villager_purchase", true));
        }
    }

    public static void onPilgrimPurchase(ServerLevel world, ServerPlayer player, String offerId) {
        if (offerId != null && !offerId.isBlank()) {
            completeObjective(world, player, PILGRIM_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.pilgrim", true));
        }
    }

    public static void onAnvilOutput(ServerLevel world, ServerPlayer player, ItemStack leftInput, ItemStack rightInput, ItemStack output) {
        if (output != null && !output.isEmpty()) {
            completeObjective(world, player, ANVIL_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.anvil", true));
        }
    }

    public static void onMonsterKill(ServerLevel world, ServerPlayer player, Entity killedEntity) {
        if (killedEntity instanceof Monster) {
            completeObjective(world, player, KILL_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.kill", true));
        }
    }

    private static void completeObjective(ServerLevel world, ServerPlayer player, String flagKey, Component actionbarLine) {
        if (world == null || player == null) {
            return;
        }

        PlayerQuestData data = data(world, player.getUUID());
        if (!data.hasMilestoneFlag(ACTIVE_FLAG) || data.hasMilestoneFlag(COMPLETE_FLAG) || data.hasMilestoneFlag(flagKey)) {
            return;
        }

        data.setMilestoneFlag(flagKey, true);
        boolean completedQuest = isComplete(data);
        if (completedQuest) {
            data.setMilestoneFlag(COMPLETE_FLAG, true);
        }
        markDirty(world);

        player.sendSystemMessage(Component.translatable("message.village-quest.quest.progress.step_complete", actionbarLine.copy()).withStyle(ChatFormatting.GREEN), false);
        player.sendSystemMessage(actionbarLine.copy().withStyle(ChatFormatting.GREEN), true);

        BlockPos pos = player.blockPosition();
        if (completedQuest) {
            player.sendSystemMessage(Texts.completedTitle(title(), ChatFormatting.GREEN), false);
            player.sendSystemMessage(Component.translatable("message.village-quest.special.admin_core_test.completed").withStyle(ChatFormatting.GRAY), false);
            world.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.1f);
        } else {
            world.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.18f, 1.35f);
        }

        refreshQuestUi(world, player);
    }

    private static boolean isComplete(PlayerQuestData data) {
        return data.hasMilestoneFlag(BLOCK_BREAK_FLAG)
                && data.hasMilestoneFlag(SHEAR_FLAG)
                && data.hasMilestoneFlag(WOOL_FLAG)
                && data.hasMilestoneFlag(BREED_FLAG)
                && data.hasMilestoneFlag(RAW_IRON_FLAG)
                && data.hasMilestoneFlag(RAW_GOLD_FLAG)
                && data.hasMilestoneFlag(SMELT_FLAG)
                && data.hasMilestoneFlag(REDSTONE_FLAG)
                && data.hasMilestoneFlag(LAPIS_FLAG)
                && data.hasMilestoneFlag(HONEY_FLAG)
                && data.hasMilestoneFlag(HONEY_BLOCK_FLAG)
                && data.hasMilestoneFlag(TRADE_FLAG)
                && data.hasMilestoneFlag(VILLAGER_PURCHASE_FLAG)
                && data.hasMilestoneFlag(BELL_FLAG)
                && data.hasMilestoneFlag(PILGRIM_FLAG)
                && data.hasMilestoneFlag(ANVIL_FLAG)
                && data.hasMilestoneFlag(KILL_FLAG)
                && data.hasMilestoneFlag(TAME_WOLF_FLAG)
                && data.hasMilestoneFlag(TAME_CAT_FLAG)
                && data.hasMilestoneFlag(TAME_PARROT_FLAG);
    }

    private static List<Component> progressLines(PlayerQuestData data) {
        List<Component> lines = new ArrayList<>();
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.block_break", data.hasMilestoneFlag(BLOCK_BREAK_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.shear", data.hasMilestoneFlag(SHEAR_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.wool", data.hasMilestoneFlag(WOOL_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.breed", data.hasMilestoneFlag(BREED_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.raw_iron", data.hasMilestoneFlag(RAW_IRON_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.raw_gold", data.hasMilestoneFlag(RAW_GOLD_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.smelt", data.hasMilestoneFlag(SMELT_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.redstone", data.hasMilestoneFlag(REDSTONE_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.lapis", data.hasMilestoneFlag(LAPIS_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.honey", data.hasMilestoneFlag(HONEY_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.honey_block", data.hasMilestoneFlag(HONEY_BLOCK_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.trade", data.hasMilestoneFlag(TRADE_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.villager_purchase", data.hasMilestoneFlag(VILLAGER_PURCHASE_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.bell", data.hasMilestoneFlag(BELL_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.pilgrim", data.hasMilestoneFlag(PILGRIM_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.anvil", data.hasMilestoneFlag(ANVIL_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.kill", data.hasMilestoneFlag(KILL_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.tame_wolf", data.hasMilestoneFlag(TAME_WOLF_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.tame_cat", data.hasMilestoneFlag(TAME_CAT_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.tame_parrot", data.hasMilestoneFlag(TAME_PARROT_FLAG)));
        if (data.hasMilestoneFlag(COMPLETE_FLAG)) {
            lines.add(Component.translatable("quest.village-quest.special.admin_core_test.progress.complete").withStyle(ChatFormatting.GOLD));
        }
        return lines;
    }

    private static Component progressLine(String key, boolean done) {
        return Component.translatable(key, done ? 1 : 0, 1).withStyle(done ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private static boolean isWool(ItemStack stack) {
        for (Item woolItem : WOOL_ITEMS) {
            if (stack.is(woolItem)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnyFlag(PlayerQuestData data) {
        return data.hasMilestoneFlag(ACTIVE_FLAG)
                || data.hasMilestoneFlag(COMPLETE_FLAG)
                || data.hasMilestoneFlag(BLOCK_BREAK_FLAG)
                || data.hasMilestoneFlag(SHEAR_FLAG)
                || data.hasMilestoneFlag(WOOL_FLAG)
                || data.hasMilestoneFlag(BREED_FLAG)
                || data.hasMilestoneFlag(RAW_IRON_FLAG)
                || data.hasMilestoneFlag(RAW_GOLD_FLAG)
                || data.hasMilestoneFlag(SMELT_FLAG)
                || data.hasMilestoneFlag(REDSTONE_FLAG)
                || data.hasMilestoneFlag(LAPIS_FLAG)
                || data.hasMilestoneFlag(HONEY_FLAG)
                || data.hasMilestoneFlag(HONEY_BLOCK_FLAG)
                || data.hasMilestoneFlag(TRADE_FLAG)
                || data.hasMilestoneFlag(VILLAGER_PURCHASE_FLAG)
                || data.hasMilestoneFlag(BELL_FLAG)
                || data.hasMilestoneFlag(PILGRIM_FLAG)
                || data.hasMilestoneFlag(ANVIL_FLAG)
                || data.hasMilestoneFlag(KILL_FLAG)
                || data.hasMilestoneFlag(TAME_WOLF_FLAG)
                || data.hasMilestoneFlag(TAME_CAT_FLAG)
                || data.hasMilestoneFlag(TAME_PARROT_FLAG)
                || data.getPilgrimInt(HONEY_BLOCK_BASELINE_KEY) > 0;
    }

    private static void clearFlags(PlayerQuestData data) {
        data.setMilestoneFlag(ACTIVE_FLAG, false);
        data.setMilestoneFlag(COMPLETE_FLAG, false);
        data.setMilestoneFlag(BLOCK_BREAK_FLAG, false);
        data.setMilestoneFlag(SHEAR_FLAG, false);
        data.setMilestoneFlag(WOOL_FLAG, false);
        data.setMilestoneFlag(BREED_FLAG, false);
        data.setMilestoneFlag(RAW_IRON_FLAG, false);
        data.setMilestoneFlag(RAW_GOLD_FLAG, false);
        data.setMilestoneFlag(SMELT_FLAG, false);
        data.setMilestoneFlag(REDSTONE_FLAG, false);
        data.setMilestoneFlag(LAPIS_FLAG, false);
        data.setMilestoneFlag(HONEY_FLAG, false);
        data.setMilestoneFlag(HONEY_BLOCK_FLAG, false);
        data.setMilestoneFlag(TRADE_FLAG, false);
        data.setMilestoneFlag(VILLAGER_PURCHASE_FLAG, false);
        data.setMilestoneFlag(BELL_FLAG, false);
        data.setMilestoneFlag(PILGRIM_FLAG, false);
        data.setMilestoneFlag(ANVIL_FLAG, false);
        data.setMilestoneFlag(KILL_FLAG, false);
        data.setMilestoneFlag(TAME_WOLF_FLAG, false);
        data.setMilestoneFlag(TAME_CAT_FLAG, false);
        data.setMilestoneFlag(TAME_PARROT_FLAG, false);
        data.setPilgrimInt(HONEY_BLOCK_BASELINE_KEY, 0);
    }

    private static Component title() {
        return Component.translatable("quest.village-quest.special.admin_core_test.title");
    }
}
