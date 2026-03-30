package de.quest.content.story;

import de.quest.economy.CurrencyService;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.story.StoryArcDefinition;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryChapterCompletion;
import de.quest.quest.story.StoryChapterDefinition;
import de.quest.quest.story.StoryQuestKeys;
import de.quest.quest.story.StoryQuestService;
import de.quest.quest.story.VillageProjectType;
import de.quest.reputation.ReputationService;
import de.quest.util.Texts;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class SilentForgeStoryArc implements StoryArcDefinition {
    private static final int COLD_HEARTH_IRON_TARGET = 18;
    private static final int COLD_HEARTH_COAL_TARGET = 12;
    private static final int COLD_HEARTH_DIAMOND_TARGET = 5;
    private static final int BELLOWS_IRON_TARGET = 24;

    private final List<StoryChapterDefinition> chapters = List.of(
            new ColdHearthChapter(),
            new BellowsAgainChapter(),
            new ToolsForTheHallChapter(),
            new MastersEdgeChapter()
    );

    @Override
    public StoryArcType type() {
        return StoryArcType.SILENT_FORGE;
    }

    @Override
    public Component title() {
        return Component.translatable("quest.village-quest.story.silent_forge.title");
    }

    @Override
    public int chapterCount() {
        return chapters.size();
    }

    @Override
    public StoryChapterDefinition chapter(int chapterIndex) {
        if (chapterIndex < 0 || chapterIndex >= chapters.size()) {
            return null;
        }
        return chapters.get(chapterIndex);
    }

    @Override
    public boolean isUnlocked(ServerLevel world, UUID playerId) {
        return StoryQuestService.isCompleted(world, playerId, StoryArcType.FAILING_HARVEST);
    }

    private abstract static class SilentForgeChapter implements StoryChapterDefinition {
        protected void addProgress(ServerLevel world, ServerPlayer player, String key, int amount, int target) {
            StoryQuestService.addQuestIntClamped(world, player.getUUID(), key, amount, target);
            StoryQuestService.completeIfEligible(world, player);
        }

        protected int progress(ServerLevel world, UUID playerId, String key) {
            return StoryQuestService.getQuestInt(world, playerId, key);
        }

        protected boolean hasItem(ServerPlayer player, Item item, int amount) {
            return DailyQuestService.countInventoryItem(player, item) >= amount;
        }

        protected boolean consumeItem(ServerPlayer player, Item item, int amount) {
            return DailyQuestService.consumeInventoryItem(player, item, amount);
        }

        protected boolean hasItems(ServerPlayer player, int amount, Item... items) {
            return DailyQuestService.countInventoryItems(player, items) >= amount;
        }

        protected boolean consumeItems(ServerPlayer player, int amount, Item... items) {
            return DailyQuestService.consumeInventoryItems(player, amount, items);
        }

        protected void updateCraftProgress(ServerLevel world,
                                           ServerPlayer player,
                                           String baselineKey,
                                           String progressKey,
                                           Item item) {
            int baseline = StoryQuestService.getQuestInt(world, player.getUUID(), baselineKey);
            if (baseline == 0) {
                StoryQuestService.setQuestInt(world, player.getUUID(), baselineKey, DailyQuestService.getCraftedStat(player, item) + 1);
                return;
            }

            int crafted = DailyQuestService.getCraftedStat(player, item);
            int delta = crafted - (baseline - 1);
            int progress = delta > 0 ? 1 : 0;
            if (StoryQuestService.getQuestInt(world, player.getUUID(), progressKey) != progress) {
                StoryQuestService.setQuestInt(world, player.getUUID(), progressKey, progress);
            }
        }

        protected Holder<Enchantment> sharpness(ServerLevel world) {
            return world.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.SHARPNESS);
        }

        protected boolean hasSharpness(ServerLevel world, ItemStack stack) {
            return world != null
                    && stack != null
                    && !stack.isEmpty()
                    && EnchantmentHelper.getItemEnchantmentLevel(sharpness(world), stack) > 0;
        }

        protected int findSharpDiamondSwordSlot(ServerPlayer player, ServerLevel world) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.is(Items.DIAMOND_SWORD) && hasSharpness(world, stack)) {
                    return i;
                }
            }
            return -1;
        }
    }

    private static final class ColdHearthChapter extends SilentForgeChapter {
        @Override
        public Component title() {
            return Component.translatable("quest.village-quest.story.silent_forge.chapter_1.title");
        }

        @Override
        public Component offerParagraph1() {
            return Component.translatable("quest.village-quest.story.silent_forge.chapter_1.offer.1").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public Component offerParagraph2() {
            return Component.translatable("quest.village-quest.story.silent_forge.chapter_1.offer.2").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            Component line1 = Component.translatable(
                    "quest.village-quest.story.silent_forge.chapter_1.progress.1",
                    progress(world, playerId, StoryQuestKeys.SILENT_FORGE_IRON_ORE),
                    COLD_HEARTH_IRON_TARGET,
                    progress(world, playerId, StoryQuestKeys.SILENT_FORGE_COAL_ORE),
                    COLD_HEARTH_COAL_TARGET
            ).withStyle(ChatFormatting.GRAY);
            Component line2 = Component.translatable(
                    "quest.village-quest.story.silent_forge.chapter_1.progress.2",
                    progress(world, playerId, StoryQuestKeys.SILENT_FORGE_DIAMOND_ORE),
                    COLD_HEARTH_DIAMOND_TARGET
            ).withStyle(ChatFormatting.GRAY);
            ServerPlayer player = world == null ? null : world.getServer().getPlayerList().getPlayer(playerId);
            Component blocked = player == null ? null : claimBlockedMessage(world, player);
            return blocked == null ? List.of(line1, line2) : List.of(line1, line2, blocked);
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            UUID playerId = player.getUUID();
            return progress(world, playerId, StoryQuestKeys.SILENT_FORGE_IRON_ORE) >= COLD_HEARTH_IRON_TARGET
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_COAL_ORE) >= COLD_HEARTH_COAL_TARGET
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_DIAMOND_ORE) >= COLD_HEARTH_DIAMOND_TARGET
                    && hasItem(player, Items.RAW_IRON, COLD_HEARTH_IRON_TARGET)
                    && hasItem(player, Items.COAL, COLD_HEARTH_COAL_TARGET)
                    && hasItem(player, Items.DIAMOND, COLD_HEARTH_DIAMOND_TARGET);
        }

        @Override
        public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
            if (!hasItem(player, Items.RAW_IRON, COLD_HEARTH_IRON_TARGET)
                    || !hasItem(player, Items.COAL, COLD_HEARTH_COAL_TARGET)
                    || !hasItem(player, Items.DIAMOND, COLD_HEARTH_DIAMOND_TARGET)) {
                return false;
            }
            return consumeItem(player, Items.RAW_IRON, COLD_HEARTH_IRON_TARGET)
                    && consumeItem(player, Items.COAL, COLD_HEARTH_COAL_TARGET)
                    && consumeItem(player, Items.DIAMOND, COLD_HEARTH_DIAMOND_TARGET);
        }

        @Override
        public Component claimBlockedMessage(ServerLevel world, ServerPlayer player) {
            if (player == null || world == null) {
                return null;
            }
            UUID playerId = player.getUUID();
            if (progress(world, playerId, StoryQuestKeys.SILENT_FORGE_IRON_ORE) < COLD_HEARTH_IRON_TARGET
                    || progress(world, playerId, StoryQuestKeys.SILENT_FORGE_COAL_ORE) < COLD_HEARTH_COAL_TARGET
                    || progress(world, playerId, StoryQuestKeys.SILENT_FORGE_DIAMOND_ORE) < COLD_HEARTH_DIAMOND_TARGET
                    || (hasItem(player, Items.RAW_IRON, COLD_HEARTH_IRON_TARGET)
                    && hasItem(player, Items.COAL, COLD_HEARTH_COAL_TARGET)
                    && hasItem(player, Items.DIAMOND, COLD_HEARTH_DIAMOND_TARGET))) {
                return null;
            }
            return Texts.turnInMissing(
                    Items.RAW_IRON.getDefaultInstance().getHoverName(),
                    DailyQuestService.countInventoryItem(player, Items.RAW_IRON),
                    COLD_HEARTH_IRON_TARGET,
                    Items.COAL.getDefaultInstance().getHoverName(),
                    DailyQuestService.countInventoryItem(player, Items.COAL),
                    COLD_HEARTH_COAL_TARGET,
                    Items.DIAMOND.getDefaultInstance().getHoverName(),
                    DailyQuestService.countInventoryItem(player, Items.DIAMOND),
                    COLD_HEARTH_DIAMOND_TARGET
            );
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_1.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_1.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_1.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.SILVERMARK * 4L,
                    4,
                    ReputationService.ReputationTrack.CRAFTING,
                    10,
                    null
            );
        }

        @Override
        public void onTrackedItemPickup(ServerLevel world, ServerPlayer player, ItemStack stack, int count) {
            if (stack.is(Items.RAW_IRON)) {
                addProgress(world, player, StoryQuestKeys.SILENT_FORGE_IRON_ORE, count, COLD_HEARTH_IRON_TARGET);
            } else if (stack.is(Items.COAL)) {
                addProgress(world, player, StoryQuestKeys.SILENT_FORGE_COAL_ORE, count, COLD_HEARTH_COAL_TARGET);
            } else if (stack.is(Items.DIAMOND)) {
                addProgress(world, player, StoryQuestKeys.SILENT_FORGE_DIAMOND_ORE, count, COLD_HEARTH_DIAMOND_TARGET);
            }
        }
    }

    private static final class BellowsAgainChapter extends SilentForgeChapter {
        @Override
        public Component title() {
            return Component.translatable("quest.village-quest.story.silent_forge.chapter_2.title");
        }

        @Override
        public Component offerParagraph1() {
            return Component.translatable("quest.village-quest.story.silent_forge.chapter_2.offer.1").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public Component offerParagraph2() {
            return Component.translatable("quest.village-quest.story.silent_forge.chapter_2.offer.2").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
            int blastFurnace = player == null ? 0 : DailyQuestService.countInventoryItem(player, Items.BLAST_FURNACE);
            int cauldron = player == null ? 0 : DailyQuestService.countInventoryItem(player, Items.CAULDRON);
            return List.of(
                    Component.translatable(
                            "quest.village-quest.story.silent_forge.chapter_2.progress.1",
                            progress(world, playerId, StoryQuestKeys.SILENT_FORGE_IRON_INGOT),
                            BELLOWS_IRON_TARGET
                    ).withStyle(ChatFormatting.GRAY),
                    Component.translatable(
                            "quest.village-quest.story.silent_forge.chapter_2.progress.2",
                            Math.min(blastFurnace, 1),
                            1,
                            Math.min(cauldron, 1),
                            1
                    ).withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            UUID playerId = player.getUUID();
            return progress(world, playerId, StoryQuestKeys.SILENT_FORGE_IRON_INGOT) >= BELLOWS_IRON_TARGET
                    && hasItem(player, Items.BLAST_FURNACE, 1)
                    && hasItem(player, Items.CAULDRON, 1);
        }

        @Override
        public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
            if (!hasItem(player, Items.BLAST_FURNACE, 1) || !hasItem(player, Items.CAULDRON, 1)) {
                return false;
            }
            return consumeItem(player, Items.BLAST_FURNACE, 1)
                    && consumeItem(player, Items.CAULDRON, 1);
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_2.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_2.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_2.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.SILVERMARK * 5L,
                    6,
                    ReputationService.ReputationTrack.CRAFTING,
                    12,
                    null
            );
        }

        @Override
        public void onFurnaceOutput(ServerLevel world, ServerPlayer player, ItemStack stack) {
            if (!stack.is(Items.IRON_INGOT)) {
                return;
            }
            addProgress(world, player, StoryQuestKeys.SILENT_FORGE_IRON_INGOT, stack.getCount(), BELLOWS_IRON_TARGET);
        }
    }

    private static final class ToolsForTheHallChapter extends SilentForgeChapter {
        @Override
        public Component title() {
            return Component.translatable("quest.village-quest.story.silent_forge.chapter_3.title");
        }

        @Override
        public Component offerParagraph1() {
            return Component.translatable("quest.village-quest.story.silent_forge.chapter_3.offer.1").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public Component offerParagraph2() {
            return Component.translatable("quest.village-quest.story.silent_forge.chapter_3.offer.2").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public void onAccepted(ServerLevel world, ServerPlayer player) {
            StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SILENT_FORGE_PICKAXE_BASELINE, DailyQuestService.getCraftedStat(player, Items.IRON_PICKAXE) + 1);
            StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SILENT_FORGE_BUCKET_BASELINE, DailyQuestService.getCraftedStat(player, Items.BUCKET) + 1);
            StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SILENT_FORGE_SHEARS_BASELINE, DailyQuestService.getCraftedStat(player, Items.SHEARS) + 1);
            StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SILENT_FORGE_SHIELD_BASELINE, DailyQuestService.getCraftedStat(player, Items.SHIELD) + 1);
        }

        @Override
        public void onServerTick(ServerLevel world, ServerPlayer player) {
            updateCraftProgress(world, player, StoryQuestKeys.SILENT_FORGE_PICKAXE_BASELINE, StoryQuestKeys.SILENT_FORGE_PICKAXE_CRAFTED, Items.IRON_PICKAXE);
            updateCraftProgress(world, player, StoryQuestKeys.SILENT_FORGE_BUCKET_BASELINE, StoryQuestKeys.SILENT_FORGE_BUCKET_CRAFTED, Items.BUCKET);
            updateCraftProgress(world, player, StoryQuestKeys.SILENT_FORGE_SHEARS_BASELINE, StoryQuestKeys.SILENT_FORGE_SHEARS_CRAFTED, Items.SHEARS);
            updateCraftProgress(world, player, StoryQuestKeys.SILENT_FORGE_SHIELD_BASELINE, StoryQuestKeys.SILENT_FORGE_SHIELD_CRAFTED, Items.SHIELD);
            StoryQuestService.completeIfEligible(world, player);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
            int pickaxeReady = progress(world, playerId, StoryQuestKeys.SILENT_FORGE_PICKAXE_CRAFTED) >= 1
                    && player != null
                    && hasItem(player, Items.IRON_PICKAXE, 1) ? 1 : 0;
            int bucketReady = progress(world, playerId, StoryQuestKeys.SILENT_FORGE_BUCKET_CRAFTED) >= 1
                    && player != null
                    && hasItem(player, Items.BUCKET, 1) ? 1 : 0;
            int shearsReady = progress(world, playerId, StoryQuestKeys.SILENT_FORGE_SHEARS_CRAFTED) >= 1
                    && player != null
                    && hasItem(player, Items.SHEARS, 1) ? 1 : 0;
            int shieldReady = progress(world, playerId, StoryQuestKeys.SILENT_FORGE_SHIELD_CRAFTED) >= 1
                    && player != null
                    && hasItem(player, Items.SHIELD, 1) ? 1 : 0;
            return List.of(
                    Component.translatable(
                            "quest.village-quest.story.silent_forge.chapter_3.progress.1",
                            pickaxeReady,
                            1,
                            bucketReady,
                            1
                    ).withStyle(ChatFormatting.GRAY),
                    Component.translatable(
                            "quest.village-quest.story.silent_forge.chapter_3.progress.2",
                            shearsReady,
                            1,
                            shieldReady,
                            1
                    ).withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            UUID playerId = player.getUUID();
            return progress(world, playerId, StoryQuestKeys.SILENT_FORGE_PICKAXE_CRAFTED) >= 1
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_BUCKET_CRAFTED) >= 1
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_SHEARS_CRAFTED) >= 1
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_SHIELD_CRAFTED) >= 1
                    && hasItem(player, Items.IRON_PICKAXE, 1)
                    && hasItem(player, Items.BUCKET, 1)
                    && hasItem(player, Items.SHEARS, 1)
                    && hasItem(player, Items.SHIELD, 1);
        }

        @Override
        public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
            if (!isComplete(world, player)) {
                return false;
            }
            return consumeItem(player, Items.IRON_PICKAXE, 1)
                    && consumeItem(player, Items.BUCKET, 1)
                    && consumeItem(player, Items.SHEARS, 1)
                    && consumeItem(player, Items.SHIELD, 1);
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_3.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_3.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_3.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.SILVERMARK * 7L,
                    8,
                    ReputationService.ReputationTrack.CRAFTING,
                    15,
                    null
            );
        }
    }

    private static final class MastersEdgeChapter extends SilentForgeChapter {
        @Override
        public Component title() {
            return Component.translatable("quest.village-quest.story.silent_forge.chapter_4.title");
        }

        @Override
        public Component offerParagraph1() {
            return Component.translatable("quest.village-quest.story.silent_forge.chapter_4.offer.1").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public Component offerParagraph2() {
            return Component.translatable("quest.village-quest.story.silent_forge.chapter_4.offer.2").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
            int sharpenedBlade = progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE) >= 1 ? 1 : 0;
            int carriedBlade = progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE) >= 1
                    && player != null
                    && findSharpDiamondSwordSlot(player, world) >= 0 ? 1 : 0;
            return List.of(
                    Component.translatable(
                            "quest.village-quest.story.silent_forge.chapter_4.progress.1",
                            progress(world, playerId, StoryQuestKeys.SILENT_FORGE_SHARPNESS_BOOK),
                            1
                    ).withStyle(ChatFormatting.GRAY),
                    Component.translatable(
                            "quest.village-quest.story.silent_forge.chapter_4.progress.2",
                            sharpenedBlade,
                            1
                    ).withStyle(ChatFormatting.GRAY),
                    Component.translatable(
                            "quest.village-quest.story.silent_forge.chapter_4.progress.3",
                            carriedBlade,
                            1
                    ).withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            UUID playerId = player.getUUID();
            return progress(world, playerId, StoryQuestKeys.SILENT_FORGE_SHARPNESS_BOOK) >= 1
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE) >= 1
                    && findSharpDiamondSwordSlot(player, world) >= 0;
        }

        @Override
        public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
            int slot = findSharpDiamondSwordSlot(player, world);
            if (slot < 0) {
                return false;
            }
            player.getInventory().getItem(slot).shrink(1);
            player.containerMenu.broadcastChanges();
            return true;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_4.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_4.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_4.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.CROWN,
                    10,
                    ReputationService.ReputationTrack.CRAFTING,
                    20,
                    VillageProjectType.FORGE_CHARTER
            );
        }

        @Override
        public void onVillagerTrade(ServerLevel world, ServerPlayer player, ItemStack stack) {
            if (stack.is(Items.ENCHANTED_BOOK) && hasSharpness(world, stack)) {
                StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SILENT_FORGE_SHARPNESS_BOOK, 1);
                StoryQuestService.completeIfEligible(world, player);
            }
        }

        @Override
        public void onAnvilOutput(ServerLevel world,
                                  ServerPlayer player,
                                  ItemStack leftInput,
                                  ItemStack rightInput,
                                  ItemStack output) {
            if (progress(world, player.getUUID(), StoryQuestKeys.SILENT_FORGE_SHARPNESS_BOOK) < 1) {
                return;
            }
            if (leftInput == null || rightInput == null || output == null) {
                return;
            }
            if (!leftInput.is(Items.DIAMOND_SWORD) || !rightInput.is(Items.ENCHANTED_BOOK) || !output.is(Items.DIAMOND_SWORD)) {
                return;
            }
            if (!hasSharpness(world, rightInput) || !hasSharpness(world, output)) {
                return;
            }
            StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SILENT_FORGE_MASTER_EDGE, 1);
            StoryQuestService.completeIfEligible(world, player);
        }
    }
}
