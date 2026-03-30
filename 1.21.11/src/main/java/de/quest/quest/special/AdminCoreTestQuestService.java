package de.quest.quest.special;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.util.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public final class AdminCoreTestQuestService {
    private static final String ACTIVE_FLAG = "admin_core_test.active";
    private static final String COMPLETE_FLAG = "admin_core_test.complete";
    private static final String BLOCK_BREAK_FLAG = "admin_core_test.block_break";
    private static final String SHEAR_FLAG = "admin_core_test.shear";
    private static final String WOOL_FLAG = "admin_core_test.wool";
    private static final String BREED_FLAG = "admin_core_test.breed";
    private static final String RAW_IRON_FLAG = "admin_core_test.raw_iron";
    private static final String SMELT_FLAG = "admin_core_test.smelt";
    private static final String REDSTONE_FLAG = "admin_core_test.redstone";
    private static final String HONEY_FLAG = "admin_core_test.honey";
    private static final String TRADE_FLAG = "admin_core_test.trade";
    private static final String BELL_FLAG = "admin_core_test.bell";
    private static final String PILGRIM_FLAG = "admin_core_test.pilgrim";
    private static final String ANVIL_FLAG = "admin_core_test.anvil";
    private static final String KILL_FLAG = "admin_core_test.kill";

    private static final Item[] WOOL_ITEMS = new Item[] {
            Items.WHITE_WOOL, Items.LIGHT_GRAY_WOOL, Items.GRAY_WOOL,
            Items.BLACK_WOOL, Items.BROWN_WOOL, Items.RED_WOOL,
            Items.ORANGE_WOOL, Items.YELLOW_WOOL, Items.LIME_WOOL,
            Items.GREEN_WOOL, Items.CYAN_WOOL, Items.LIGHT_BLUE_WOOL,
            Items.BLUE_WOOL, Items.PURPLE_WOOL, Items.MAGENTA_WOOL,
            Items.PINK_WOOL
    };

    private AdminCoreTestQuestService() {}

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

    public static boolean isActive(ServerWorld world, UUID playerId) {
        return world != null && playerId != null && data(world, playerId).hasMilestoneFlag(ACTIVE_FLAG);
    }

    public static SpecialQuestStatus openStatus(ServerWorld world, UUID playerId) {
        if (!isActive(world, playerId)) {
            return null;
        }
        return new SpecialQuestStatus(title(), progressLines(data(world, playerId)));
    }

    public static boolean adminStart(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }

        PlayerQuestData data = data(world, player.getUuid());
        clearFlags(data);
        data.setMilestoneFlag(ACTIVE_FLAG, true);
        markDirty(world);

        player.sendMessage(Texts.acceptedTitle(title(), Formatting.GREEN), false);
        player.sendMessage(Text.translatable("message.village-quest.special.admin_core_test.started").formatted(Formatting.GRAY), false);
        QuestTrackerService.enableForAcceptedQuest(world, player);
        refreshQuestUi(world, player);
        return true;
    }

    public static boolean adminReset(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }

        PlayerQuestData data = data(world, player.getUuid());
        boolean hadState = hasAnyFlag(data);
        clearFlags(data);
        markDirty(world);
        refreshQuestUi(world, player);
        return hadState;
    }

    public static void onEntityUse(ServerWorld world, ServerPlayerEntity player, Entity entity, ItemStack inHand) {
        if (!(entity instanceof SheepEntity sheep) || inHand == null || !inHand.isOf(Items.SHEARS) || !sheep.isShearable()) {
            return;
        }
        completeObjective(world, player, SHEAR_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.shear", true));
    }

    public static void onUseBlock(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
        if (world == null || player == null || pos == null) {
            return;
        }
        if (world.getBlockState(pos).isOf(Blocks.BELL)) {
            completeObjective(world, player, BELL_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.bell", true));
        }
    }

    public static void onBlockBreak(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {
        if (state != null && state.isIn(BlockTags.LOGS)) {
            completeObjective(world, player, BLOCK_BREAK_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.block_break", true));
        }
    }

    public static void onTrackedItemPickup(ServerWorld world, ServerPlayerEntity player, ItemStack stack, int count) {
        if (stack == null || stack.isEmpty() || count <= 0) {
            return;
        }
        if (isWool(stack)) {
            completeObjective(world, player, WOOL_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.wool", true));
        }
        if (stack.isOf(Items.RAW_IRON)) {
            completeObjective(world, player, RAW_IRON_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.raw_iron", true));
        }
        if (stack.isOf(Items.REDSTONE)) {
            completeObjective(world, player, REDSTONE_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.redstone", true));
        }
    }

    public static void onAnimalLove(ServerWorld world, ServerPlayerEntity player, AnimalEntity animal) {
        if (animal != null) {
            completeObjective(world, player, BREED_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.breed", true));
        }
    }

    public static void onFurnaceOutput(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
        if (stack != null && stack.isOf(Items.IRON_INGOT)) {
            completeObjective(world, player, SMELT_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.smelt", true));
        }
    }

    public static void onBeeNestInteract(ServerWorld world, ServerPlayerEntity player, BlockState state, ItemStack inHand) {
        if (world == null || player == null || state == null || inHand == null) {
            return;
        }
        if ((!state.isOf(Blocks.BEE_NEST) && !state.isOf(Blocks.BEEHIVE)) || !state.contains(BeehiveBlock.HONEY_LEVEL)) {
            return;
        }
        Integer honeyLevel = state.get(BeehiveBlock.HONEY_LEVEL);
        if (honeyLevel == null || honeyLevel < 5) {
            return;
        }
        if (inHand.isOf(Items.GLASS_BOTTLE) || inHand.isOf(Items.SHEARS)) {
            completeObjective(world, player, HONEY_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.honey", true));
        }
    }

    public static void onVillagerTrade(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            completeObjective(world, player, TRADE_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.trade", true));
        }
    }

    public static void onPilgrimPurchase(ServerWorld world, ServerPlayerEntity player, String offerId) {
        if (offerId != null && !offerId.isBlank()) {
            completeObjective(world, player, PILGRIM_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.pilgrim", true));
        }
    }

    public static void onAnvilOutput(ServerWorld world, ServerPlayerEntity player, ItemStack leftInput, ItemStack rightInput, ItemStack output) {
        if (output != null && !output.isEmpty()) {
            completeObjective(world, player, ANVIL_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.anvil", true));
        }
    }

    public static void onMonsterKill(ServerWorld world, ServerPlayerEntity player, Entity killedEntity) {
        if (killedEntity instanceof HostileEntity) {
            completeObjective(world, player, KILL_FLAG, progressLine("quest.village-quest.special.admin_core_test.progress.kill", true));
        }
    }

    private static void completeObjective(ServerWorld world, ServerPlayerEntity player, String flagKey, Text actionbarLine) {
        if (world == null || player == null) {
            return;
        }

        PlayerQuestData data = data(world, player.getUuid());
        if (!data.hasMilestoneFlag(ACTIVE_FLAG) || data.hasMilestoneFlag(COMPLETE_FLAG) || data.hasMilestoneFlag(flagKey)) {
            return;
        }

        data.setMilestoneFlag(flagKey, true);
        boolean completedQuest = isComplete(data);
        if (completedQuest) {
            data.setMilestoneFlag(COMPLETE_FLAG, true);
        }
        markDirty(world);

        player.sendMessage(Text.translatable("message.village-quest.quest.progress.step_complete", actionbarLine.copy()).formatted(Formatting.GREEN), false);
        player.sendMessage(actionbarLine.copy().formatted(Formatting.GREEN), true);

        if (completedQuest) {
            player.sendMessage(Texts.completedTitle(title(), Formatting.GREEN), false);
            player.sendMessage(Text.translatable("message.village-quest.special.admin_core_test.completed").formatted(Formatting.GRAY), false);
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.1f);
        } else {
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.18f, 1.35f);
        }

        refreshQuestUi(world, player);
    }

    private static boolean isComplete(PlayerQuestData data) {
        return data.hasMilestoneFlag(BLOCK_BREAK_FLAG)
                && data.hasMilestoneFlag(SHEAR_FLAG)
                && data.hasMilestoneFlag(WOOL_FLAG)
                && data.hasMilestoneFlag(BREED_FLAG)
                && data.hasMilestoneFlag(RAW_IRON_FLAG)
                && data.hasMilestoneFlag(SMELT_FLAG)
                && data.hasMilestoneFlag(REDSTONE_FLAG)
                && data.hasMilestoneFlag(HONEY_FLAG)
                && data.hasMilestoneFlag(TRADE_FLAG)
                && data.hasMilestoneFlag(BELL_FLAG)
                && data.hasMilestoneFlag(PILGRIM_FLAG)
                && data.hasMilestoneFlag(ANVIL_FLAG)
                && data.hasMilestoneFlag(KILL_FLAG);
    }

    private static List<Text> progressLines(PlayerQuestData data) {
        List<Text> lines = new ArrayList<>();
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.block_break", data.hasMilestoneFlag(BLOCK_BREAK_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.shear", data.hasMilestoneFlag(SHEAR_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.wool", data.hasMilestoneFlag(WOOL_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.breed", data.hasMilestoneFlag(BREED_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.raw_iron", data.hasMilestoneFlag(RAW_IRON_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.smelt", data.hasMilestoneFlag(SMELT_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.redstone", data.hasMilestoneFlag(REDSTONE_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.honey", data.hasMilestoneFlag(HONEY_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.trade", data.hasMilestoneFlag(TRADE_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.bell", data.hasMilestoneFlag(BELL_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.pilgrim", data.hasMilestoneFlag(PILGRIM_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.anvil", data.hasMilestoneFlag(ANVIL_FLAG)));
        lines.add(progressLine("quest.village-quest.special.admin_core_test.progress.kill", data.hasMilestoneFlag(KILL_FLAG)));
        if (data.hasMilestoneFlag(COMPLETE_FLAG)) {
            lines.add(Text.translatable("quest.village-quest.special.admin_core_test.progress.complete").formatted(Formatting.GOLD));
        }
        return lines;
    }

    private static Text progressLine(String key, boolean done) {
        return Text.translatable(key, done ? 1 : 0, 1).formatted(done ? Formatting.GREEN : Formatting.GRAY);
    }

    private static boolean isWool(ItemStack stack) {
        for (Item woolItem : WOOL_ITEMS) {
            if (stack.isOf(woolItem)) {
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
                || data.hasMilestoneFlag(SMELT_FLAG)
                || data.hasMilestoneFlag(REDSTONE_FLAG)
                || data.hasMilestoneFlag(HONEY_FLAG)
                || data.hasMilestoneFlag(TRADE_FLAG)
                || data.hasMilestoneFlag(BELL_FLAG)
                || data.hasMilestoneFlag(PILGRIM_FLAG)
                || data.hasMilestoneFlag(ANVIL_FLAG)
                || data.hasMilestoneFlag(KILL_FLAG);
    }

    private static void clearFlags(PlayerQuestData data) {
        data.setMilestoneFlag(ACTIVE_FLAG, false);
        data.setMilestoneFlag(COMPLETE_FLAG, false);
        data.setMilestoneFlag(BLOCK_BREAK_FLAG, false);
        data.setMilestoneFlag(SHEAR_FLAG, false);
        data.setMilestoneFlag(WOOL_FLAG, false);
        data.setMilestoneFlag(BREED_FLAG, false);
        data.setMilestoneFlag(RAW_IRON_FLAG, false);
        data.setMilestoneFlag(SMELT_FLAG, false);
        data.setMilestoneFlag(REDSTONE_FLAG, false);
        data.setMilestoneFlag(HONEY_FLAG, false);
        data.setMilestoneFlag(TRADE_FLAG, false);
        data.setMilestoneFlag(BELL_FLAG, false);
        data.setMilestoneFlag(PILGRIM_FLAG, false);
        data.setMilestoneFlag(ANVIL_FLAG, false);
        data.setMilestoneFlag(KILL_FLAG, false);
    }

    private static Text title() {
        return Text.translatable("quest.village-quest.special.admin_core_test.title");
    }
}
