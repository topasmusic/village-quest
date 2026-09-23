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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SilentForgeStoryArc implements StoryArcDefinition {
    private static final int COLD_HEARTH_COAL_TARGET = 64;
    private static final int COLD_HEARTH_IRON_TARGET = 40;
    private static final int COLD_HEARTH_REDSTONE_TARGET = 24;
    private static final int COLD_HEARTH_GOLD_TARGET = 16;
    private static final int COLD_HEARTH_DIAMOND_TARGET = 4;
    private static final int BELLOWS_IRON_TARGET = 24;
    private static final int BELLOWS_BLAST_FURNACE_TARGET = 3;
    private static final int BELLOWS_CAULDRON_TARGET = 3;
    private static final int TOOLS_TARGET = 3;
    private static final int MASTER_BOOK_FAMILIES_TARGET = 2;

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

        protected boolean hasItem(ServerLevel world, ServerPlayer player, Item item, int amount) {
            return player != null && StoryQuestService.countCompletionItem(world, player.getUUID(), item) >= amount;
        }

        protected boolean consumeItem(ServerLevel world, ServerPlayer player, Item item, int amount) {
            return player != null && StoryQuestService.consumeCompletionItem(world, player.getUUID(), item, amount);
        }

        protected boolean hasPristineItem(ServerLevel world, ServerPlayer player, Item item, int amount) {
            return player != null && countPristineItems(world, player.getUUID(), item) >= amount;
        }

        protected int countPristineItems(ServerLevel world, UUID playerId, Item item) {
            if (world == null || playerId == null || item == null) {
                return 0;
            }
            return StoryQuestService.countMatchingCompletionItems(world, playerId, stack -> isPristineTurnInItem(stack, item));
        }

        protected boolean consumePristineItem(ServerLevel world, ServerPlayer player, Item item, int amount) {
            return player != null
                    && StoryQuestService.consumeMatchingCompletionItems(world, player.getUUID(), stack -> isPristineTurnInItem(stack, item), amount);
        }

        protected void updateCraftProgress(ServerLevel world,
                                           ServerPlayer player,
                                           String baselineKey,
                                           String progressKey,
                                           Item item,
                                           int target) {
            int baseline = StoryQuestService.getQuestInt(world, player.getUUID(), baselineKey);
            int crafted = DailyQuestService.getCraftedStat(player, item);
            if (baseline == 0) {
                StoryQuestService.setQuestInt(world, player.getUUID(), baselineKey, crafted + 1);
                return;
            }

            int delta = crafted - (baseline - 1);
            if (delta > 0) {
                StoryQuestService.addQuestIntClamped(world, player.getUUID(), progressKey, delta, target);
            }
            StoryQuestService.setQuestInt(world, player.getUUID(), baselineKey, crafted + 1);
        }

        protected Holder<Enchantment> enchantment(ServerLevel world, ResourceKey<Enchantment> key) {
            return world.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(key);
        }

        protected boolean hasEnchantment(ServerLevel world, ItemStack stack, ResourceKey<Enchantment> key) {
            return world != null
                    && stack != null
                    && !stack.isEmpty()
                    && EnchantmentHelper.getEnchantmentsForCrafting(stack).getLevel(enchantment(world, key)) > 0;
        }

        private boolean isPristineTurnInItem(ItemStack stack, Item item) {
            return stack != null
                    && !stack.isEmpty()
                    && stack.is(item)
                    && (!stack.isDamageableItem() || !stack.isDamaged());
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
                    progress(world, playerId, StoryQuestKeys.SILENT_FORGE_COAL_ORE),
                    COLD_HEARTH_COAL_TARGET
            ).withStyle(ChatFormatting.GRAY);
            Component line2 = Component.translatable(
                    "quest.village-quest.story.silent_forge.chapter_1.progress.2",
                    progress(world, playerId, StoryQuestKeys.SILENT_FORGE_IRON_ORE),
                    COLD_HEARTH_IRON_TARGET
            ).withStyle(ChatFormatting.GRAY);
            Component line3 = Component.translatable(
                    "quest.village-quest.story.silent_forge.chapter_1.progress.3",
                    progress(world, playerId, StoryQuestKeys.SILENT_FORGE_REDSTONE_ORE),
                    COLD_HEARTH_REDSTONE_TARGET
            ).withStyle(ChatFormatting.GRAY);
            Component line4 = Component.translatable(
                    "quest.village-quest.story.silent_forge.chapter_1.progress.4",
                    progress(world, playerId, StoryQuestKeys.SILENT_FORGE_GOLD_ORE),
                    COLD_HEARTH_GOLD_TARGET
            ).withStyle(ChatFormatting.GRAY);
            Component line5 = Component.translatable(
                    "quest.village-quest.story.silent_forge.chapter_1.progress.5",
                    progress(world, playerId, StoryQuestKeys.SILENT_FORGE_DIAMOND_ORE),
                    COLD_HEARTH_DIAMOND_TARGET
            ).withStyle(ChatFormatting.GRAY);
            ServerPlayer player = world == null ? null : world.getServer().getPlayerList().getPlayer(playerId);
            Component blocked = player == null ? null : claimBlockedMessage(world, player);
            return blocked == null
                    ? List.of(line1, line2, line3, line4, line5)
                    : List.of(line1, line2, line3, line4, line5, blocked);
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            UUID playerId = player.getUUID();
            return progress(world, playerId, StoryQuestKeys.SILENT_FORGE_COAL_ORE) >= COLD_HEARTH_COAL_TARGET
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_IRON_ORE) >= COLD_HEARTH_IRON_TARGET
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_REDSTONE_ORE) >= COLD_HEARTH_REDSTONE_TARGET
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_GOLD_ORE) >= COLD_HEARTH_GOLD_TARGET
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_DIAMOND_ORE) >= COLD_HEARTH_DIAMOND_TARGET
                    && hasItem(world, player, Items.COAL, COLD_HEARTH_COAL_TARGET)
                    && hasItem(world, player, Items.RAW_IRON, COLD_HEARTH_IRON_TARGET)
                    && hasItem(world, player, Items.REDSTONE, COLD_HEARTH_REDSTONE_TARGET)
                    && hasItem(world, player, Items.RAW_GOLD, COLD_HEARTH_GOLD_TARGET)
                    && hasItem(world, player, Items.DIAMOND, COLD_HEARTH_DIAMOND_TARGET);
        }

        @Override
        public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
            if (!isComplete(world, player)) {
                return false;
            }
            return StoryQuestService.consumeCompletionItems(
                    world,
                    player.getUUID(),
                    Map.of(
                            Items.COAL, COLD_HEARTH_COAL_TARGET,
                            Items.RAW_IRON, COLD_HEARTH_IRON_TARGET,
                            Items.REDSTONE, COLD_HEARTH_REDSTONE_TARGET,
                            Items.RAW_GOLD, COLD_HEARTH_GOLD_TARGET,
                            Items.DIAMOND, COLD_HEARTH_DIAMOND_TARGET
                    )
            );
        }

        @Override
        public Component claimBlockedMessage(ServerLevel world, ServerPlayer player) {
            if (player == null || world == null) {
                return null;
            }
            UUID playerId = player.getUUID();
            if (progress(world, playerId, StoryQuestKeys.SILENT_FORGE_COAL_ORE) < COLD_HEARTH_COAL_TARGET
                    || progress(world, playerId, StoryQuestKeys.SILENT_FORGE_IRON_ORE) < COLD_HEARTH_IRON_TARGET
                    || progress(world, playerId, StoryQuestKeys.SILENT_FORGE_REDSTONE_ORE) < COLD_HEARTH_REDSTONE_TARGET
                    || progress(world, playerId, StoryQuestKeys.SILENT_FORGE_GOLD_ORE) < COLD_HEARTH_GOLD_TARGET
                    || progress(world, playerId, StoryQuestKeys.SILENT_FORGE_DIAMOND_ORE) < COLD_HEARTH_DIAMOND_TARGET
                    || (hasItem(world, player, Items.COAL, COLD_HEARTH_COAL_TARGET)
                    && hasItem(world, player, Items.RAW_IRON, COLD_HEARTH_IRON_TARGET)
                    && hasItem(world, player, Items.REDSTONE, COLD_HEARTH_REDSTONE_TARGET)
                    && hasItem(world, player, Items.RAW_GOLD, COLD_HEARTH_GOLD_TARGET)
                    && hasItem(world, player, Items.DIAMOND, COLD_HEARTH_DIAMOND_TARGET))) {
                return null;
            }
            return Texts.turnInMissing(
                    Items.COAL.getDefaultInstance().getDisplayName(),
                    StoryQuestService.countCompletionItem(world, playerId, Items.COAL),
                    COLD_HEARTH_COAL_TARGET,
                    Items.RAW_IRON.getDefaultInstance().getDisplayName(),
                    StoryQuestService.countCompletionItem(world, playerId, Items.RAW_IRON),
                    COLD_HEARTH_IRON_TARGET,
                    Items.REDSTONE.getDefaultInstance().getDisplayName(),
                    StoryQuestService.countCompletionItem(world, playerId, Items.REDSTONE),
                    COLD_HEARTH_REDSTONE_TARGET,
                    Items.RAW_GOLD.getDefaultInstance().getDisplayName(),
                    StoryQuestService.countCompletionItem(world, playerId, Items.RAW_GOLD),
                    COLD_HEARTH_GOLD_TARGET,
                    Items.DIAMOND.getDefaultInstance().getDisplayName(),
                    StoryQuestService.countCompletionItem(world, playerId, Items.DIAMOND),
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
                    CurrencyService.SILVERMARK * 12L,
                    8,
                    ReputationService.ReputationTrack.CRAFTING,
                    10,
                    null
            );
        }

        @Override
        public void onTrackedItemPickup(ServerLevel world, ServerPlayer player, ItemStack stack, int count) {
            if (stack.is(Items.COAL)) {
                addProgress(world, player, StoryQuestKeys.SILENT_FORGE_COAL_ORE, count, COLD_HEARTH_COAL_TARGET);
            } else if (stack.is(Items.RAW_IRON)) {
                addProgress(world, player, StoryQuestKeys.SILENT_FORGE_IRON_ORE, count, COLD_HEARTH_IRON_TARGET);
            } else if (stack.is(Items.REDSTONE)) {
                addProgress(world, player, StoryQuestKeys.SILENT_FORGE_REDSTONE_ORE, count, COLD_HEARTH_REDSTONE_TARGET);
            } else if (stack.is(Items.RAW_GOLD)) {
                addProgress(world, player, StoryQuestKeys.SILENT_FORGE_GOLD_ORE, count, COLD_HEARTH_GOLD_TARGET);
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
            int blastFurnace = StoryQuestService.countCompletionItem(world, playerId, Items.BLAST_FURNACE);
            int cauldron = StoryQuestService.countCompletionItem(world, playerId, Items.CAULDRON);
            Component line1 = Component.translatable(
                    "quest.village-quest.story.silent_forge.chapter_2.progress.1",
                    progress(world, playerId, StoryQuestKeys.SILENT_FORGE_IRON_INGOT),
                    BELLOWS_IRON_TARGET
            ).withStyle(ChatFormatting.GRAY);
            Component line2 = Component.translatable(
                    "quest.village-quest.story.silent_forge.chapter_2.progress.2",
                    Math.min(blastFurnace, BELLOWS_BLAST_FURNACE_TARGET),
                    BELLOWS_BLAST_FURNACE_TARGET,
                    Math.min(cauldron, BELLOWS_CAULDRON_TARGET),
                    BELLOWS_CAULDRON_TARGET
            ).withStyle(ChatFormatting.GRAY);
            Component blocked = player == null ? null : claimBlockedMessage(world, player);
            return blocked == null ? List.of(line1, line2) : List.of(line1, line2, blocked);
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            UUID playerId = player.getUUID();
            return progress(world, playerId, StoryQuestKeys.SILENT_FORGE_IRON_INGOT) >= BELLOWS_IRON_TARGET
                    && hasItem(world, player, Items.IRON_INGOT, BELLOWS_IRON_TARGET)
                    && hasItem(world, player, Items.BLAST_FURNACE, BELLOWS_BLAST_FURNACE_TARGET)
                    && hasItem(world, player, Items.CAULDRON, BELLOWS_CAULDRON_TARGET);
        }

        @Override
        public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
            if (!isComplete(world, player)) {
                return false;
            }
            return StoryQuestService.consumeCompletionItems(
                    world,
                    player.getUUID(),
                    Map.of(
                            Items.IRON_INGOT, BELLOWS_IRON_TARGET,
                            Items.BLAST_FURNACE, BELLOWS_BLAST_FURNACE_TARGET,
                            Items.CAULDRON, BELLOWS_CAULDRON_TARGET
                    )
            );
        }

        @Override
        public Component claimBlockedMessage(ServerLevel world, ServerPlayer player) {
            if (player == null || world == null) {
                return null;
            }
            UUID playerId = player.getUUID();
            if (progress(world, playerId, StoryQuestKeys.SILENT_FORGE_IRON_INGOT) < BELLOWS_IRON_TARGET
                    || (hasItem(world, player, Items.IRON_INGOT, BELLOWS_IRON_TARGET)
                    && hasItem(world, player, Items.BLAST_FURNACE, BELLOWS_BLAST_FURNACE_TARGET)
                    && hasItem(world, player, Items.CAULDRON, BELLOWS_CAULDRON_TARGET))) {
                return null;
            }
            return Texts.turnInMissing(
                    Items.IRON_INGOT.getDefaultInstance().getDisplayName(),
                    StoryQuestService.countCompletionItem(world, playerId, Items.IRON_INGOT),
                    BELLOWS_IRON_TARGET,
                    Items.BLAST_FURNACE.getDefaultInstance().getDisplayName(),
                    StoryQuestService.countCompletionItem(world, playerId, Items.BLAST_FURNACE),
                    BELLOWS_BLAST_FURNACE_TARGET,
                    Items.CAULDRON.getDefaultInstance().getDisplayName(),
                    StoryQuestService.countCompletionItem(world, playerId, Items.CAULDRON),
                    BELLOWS_CAULDRON_TARGET
            );
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_2.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_2.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_2.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.SILVERMARK * 16L,
                    10,
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
            updateCraftProgress(world, player, StoryQuestKeys.SILENT_FORGE_PICKAXE_BASELINE, StoryQuestKeys.SILENT_FORGE_PICKAXE_CRAFTED, Items.IRON_PICKAXE, TOOLS_TARGET);
            updateCraftProgress(world, player, StoryQuestKeys.SILENT_FORGE_BUCKET_BASELINE, StoryQuestKeys.SILENT_FORGE_BUCKET_CRAFTED, Items.BUCKET, TOOLS_TARGET);
            updateCraftProgress(world, player, StoryQuestKeys.SILENT_FORGE_SHEARS_BASELINE, StoryQuestKeys.SILENT_FORGE_SHEARS_CRAFTED, Items.SHEARS, TOOLS_TARGET);
            updateCraftProgress(world, player, StoryQuestKeys.SILENT_FORGE_SHIELD_BASELINE, StoryQuestKeys.SILENT_FORGE_SHIELD_CRAFTED, Items.SHIELD, TOOLS_TARGET);
            StoryQuestService.completeIfEligible(world, player);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
            int pickaxeReady = player == null ? 0 : Math.min(progress(world, playerId, StoryQuestKeys.SILENT_FORGE_PICKAXE_CRAFTED), countPristineItems(world, playerId, Items.IRON_PICKAXE));
            int bucketReady = player == null ? 0 : Math.min(progress(world, playerId, StoryQuestKeys.SILENT_FORGE_BUCKET_CRAFTED), countPristineItems(world, playerId, Items.BUCKET));
            int shearsReady = player == null ? 0 : Math.min(progress(world, playerId, StoryQuestKeys.SILENT_FORGE_SHEARS_CRAFTED), countPristineItems(world, playerId, Items.SHEARS));
            int shieldReady = player == null ? 0 : Math.min(progress(world, playerId, StoryQuestKeys.SILENT_FORGE_SHIELD_CRAFTED), countPristineItems(world, playerId, Items.SHIELD));
            Component line1 = Component.translatable(
                    "quest.village-quest.story.silent_forge.chapter_3.progress.1",
                    pickaxeReady,
                    TOOLS_TARGET,
                    bucketReady,
                    TOOLS_TARGET
            ).withStyle(ChatFormatting.GRAY);
            Component line2 = Component.translatable(
                    "quest.village-quest.story.silent_forge.chapter_3.progress.2",
                    shearsReady,
                    TOOLS_TARGET,
                    shieldReady,
                    TOOLS_TARGET
            ).withStyle(ChatFormatting.GRAY);
            Component blocked = player == null ? null : claimBlockedMessage(world, player);
            return blocked == null ? List.of(line1, line2) : List.of(line1, line2, blocked);
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            UUID playerId = player.getUUID();
            return progress(world, playerId, StoryQuestKeys.SILENT_FORGE_PICKAXE_CRAFTED) >= TOOLS_TARGET
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_BUCKET_CRAFTED) >= TOOLS_TARGET
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_SHEARS_CRAFTED) >= TOOLS_TARGET
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_SHIELD_CRAFTED) >= TOOLS_TARGET
                    && hasPristineItem(world, player, Items.IRON_PICKAXE, TOOLS_TARGET)
                    && hasPristineItem(world, player, Items.BUCKET, TOOLS_TARGET)
                    && hasPristineItem(world, player, Items.SHEARS, TOOLS_TARGET)
                    && hasPristineItem(world, player, Items.SHIELD, TOOLS_TARGET);
        }

        @Override
        public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
            if (!isComplete(world, player)) {
                return false;
            }
            return consumePristineItem(world, player, Items.IRON_PICKAXE, TOOLS_TARGET)
                    && consumePristineItem(world, player, Items.BUCKET, TOOLS_TARGET)
                    && consumePristineItem(world, player, Items.SHEARS, TOOLS_TARGET)
                    && consumePristineItem(world, player, Items.SHIELD, TOOLS_TARGET);
        }

        @Override
        public Component claimBlockedMessage(ServerLevel world, ServerPlayer player) {
            if (player == null || world == null) {
                return null;
            }
            UUID playerId = player.getUUID();
            if (progress(world, playerId, StoryQuestKeys.SILENT_FORGE_PICKAXE_CRAFTED) < TOOLS_TARGET
                    || progress(world, playerId, StoryQuestKeys.SILENT_FORGE_BUCKET_CRAFTED) < TOOLS_TARGET
                    || progress(world, playerId, StoryQuestKeys.SILENT_FORGE_SHEARS_CRAFTED) < TOOLS_TARGET
                    || progress(world, playerId, StoryQuestKeys.SILENT_FORGE_SHIELD_CRAFTED) < TOOLS_TARGET
                    || (hasPristineItem(world, player, Items.IRON_PICKAXE, TOOLS_TARGET)
                    && hasPristineItem(world, player, Items.BUCKET, TOOLS_TARGET)
                    && hasPristineItem(world, player, Items.SHEARS, TOOLS_TARGET)
                    && hasPristineItem(world, player, Items.SHIELD, TOOLS_TARGET))) {
                return null;
            }
            return Texts.turnInMissing(
                    Items.IRON_PICKAXE.getDefaultInstance().getDisplayName(),
                    countPristineItems(world, playerId, Items.IRON_PICKAXE),
                    TOOLS_TARGET,
                    Items.BUCKET.getDefaultInstance().getDisplayName(),
                    countPristineItems(world, playerId, Items.BUCKET),
                    TOOLS_TARGET,
                    Items.SHEARS.getDefaultInstance().getDisplayName(),
                    countPristineItems(world, playerId, Items.SHEARS),
                    TOOLS_TARGET,
                    Items.SHIELD.getDefaultInstance().getDisplayName(),
                    countPristineItems(world, playerId, Items.SHIELD),
                    TOOLS_TARGET
            );
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_3.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_3.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_3.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.SILVERMARK * 22L,
                    12,
                    ReputationService.ReputationTrack.CRAFTING,
                    15,
                    null
            );
        }
    }

    private static final class MastersEdgeChapter extends SilentForgeChapter {
        private int bookProgressCount(ServerLevel world, UUID playerId) {
            int sharpness = Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_SHARPNESS_BOOK));
            int protectionFamily = Math.min(1,
                    progress(world, playerId, StoryQuestKeys.SILENT_FORGE_FIRE_PROTECTION_BOOK)
                            + progress(world, playerId, StoryQuestKeys.SILENT_FORGE_PROTECTION_BOOK)
                            + progress(world, playerId, StoryQuestKeys.SILENT_FORGE_BLAST_PROTECTION_BOOK)
                            + progress(world, playerId, StoryQuestKeys.SILENT_FORGE_PROJECTILE_PROTECTION_BOOK));
            return sharpness + protectionFamily;
        }

        private void recordRequiredBook(ServerLevel world, UUID playerId, ItemStack stack) {
            if (hasEnchantment(world, stack, Enchantments.SHARPNESS)) {
                StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_SHARPNESS_BOOK, 1);
            }
            if (hasEnchantment(world, stack, Enchantments.FIRE_PROTECTION)) {
                StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_FIRE_PROTECTION_BOOK, 1);
            }
            if (hasEnchantment(world, stack, Enchantments.PROTECTION)) {
                StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_PROTECTION_BOOK, 1);
            }
            if (hasEnchantment(world, stack, Enchantments.BLAST_PROTECTION)) {
                StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_BLAST_PROTECTION_BOOK, 1);
            }
            if (hasEnchantment(world, stack, Enchantments.PROJECTILE_PROTECTION)) {
                StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_PROJECTILE_PROTECTION_BOOK, 1);
            }
        }

        private void recoverMissedBookPurchases(ServerLevel world, ServerPlayer player) {
            UUID playerId = player.getUUID();
            if (bookProgressCount(world, playerId) >= MASTER_BOOK_FAMILIES_TARGET) {
                return;
            }
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.is(Items.ENCHANTED_BOOK)) {
                    recordRequiredBook(world, playerId, stack);
                }
            }
            StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_BOOK_MIGRATION, 1);
        }

        private int armorCraftedCount(ServerLevel world, UUID playerId) {
            int total = 0;
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_HELM_CRAFTED));
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_CHEST_CRAFTED));
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_LEGS_CRAFTED));
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_BOOTS_CRAFTED));
            return total;
        }

        private int armorEnchantedCount(ServerLevel world, UUID playerId) {
            int total = 0;
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_HELM));
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_CHEST));
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_LEGS));
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_BOOTS));
            return total;
        }

        private boolean hasProtectionEnchantment(ServerLevel world, ItemStack stack) {
            return hasEnchantment(world, stack, Enchantments.FIRE_PROTECTION)
                    || hasEnchantment(world, stack, Enchantments.PROTECTION)
                    || hasEnchantment(world, stack, Enchantments.BLAST_PROTECTION)
                    || hasEnchantment(world, stack, Enchantments.PROJECTILE_PROTECTION);
        }

        private boolean isProtectedArmor(ServerLevel world, ItemStack stack, Item item) {
            return stack != null && stack.is(item) && hasProtectionEnchantment(world, stack);
        }

        private boolean isSharpnessSword(ServerLevel world, ItemStack stack) {
            return stack != null
                    && stack.is(Items.DIAMOND_SWORD)
                    && hasEnchantment(world, stack, Enchantments.SHARPNESS);
        }

        private int countCarried(ServerLevel world, UUID playerId, Item item) {
            return StoryQuestService.countMatchingCompletionItems(
                    world,
                    playerId,
                    stack -> item == Items.DIAMOND_SWORD
                            ? isSharpnessSword(world, stack)
                            : isProtectedArmor(world, stack, item)
            );
        }

        private int carriedPieceCount(ServerPlayer player, ServerLevel world) {
            if (player == null || world == null) {
                return 0;
            }
            UUID playerId = player.getUUID();
            int total = 0;
            total += Math.min(1, countCarried(world, playerId, Items.DIAMOND_SWORD));
            total += Math.min(1, countCarried(world, playerId, Items.IRON_HELMET));
            total += Math.min(1, countCarried(world, playerId, Items.IRON_CHESTPLATE));
            total += Math.min(1, countCarried(world, playerId, Items.IRON_LEGGINGS));
            total += Math.min(1, countCarried(world, playerId, Items.IRON_BOOTS));
            return total;
        }

        private void migrateLegacyCraftProgress(ServerLevel world, UUID playerId) {
            if (progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_HELM) >= 1) {
                StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_HELM_CRAFTED, 1);
            }
            if (progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_CHEST) >= 1) {
                StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_CHEST_CRAFTED, 1);
            }
            if (progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_LEGS) >= 1) {
                StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_LEGS_CRAFTED, 1);
            }
            if (progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_BOOTS) >= 1) {
                StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_BOOTS_CRAFTED, 1);
            }
            if (progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE) >= 1) {
                StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE_CRAFTED, 1);
            }
        }

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
        public void onAccepted(ServerLevel world, ServerPlayer player) {
            recoverMissedBookPurchases(world, player);
        }

        @Override
        public void onServerTick(ServerLevel world, ServerPlayer player) {
            UUID playerId = player.getUUID();
            recoverMissedBookPurchases(world, player);
            if (bookProgressCount(world, playerId) < MASTER_BOOK_FAMILIES_TARGET) {
                return;
            }

            migrateLegacyCraftProgress(world, playerId);
            updateCraftProgress(world, player, StoryQuestKeys.SILENT_FORGE_MASTER_HELM_BASELINE, StoryQuestKeys.SILENT_FORGE_MASTER_HELM_CRAFTED, Items.IRON_HELMET, 1);
            updateCraftProgress(world, player, StoryQuestKeys.SILENT_FORGE_MASTER_CHEST_BASELINE, StoryQuestKeys.SILENT_FORGE_MASTER_CHEST_CRAFTED, Items.IRON_CHESTPLATE, 1);
            updateCraftProgress(world, player, StoryQuestKeys.SILENT_FORGE_MASTER_LEGS_BASELINE, StoryQuestKeys.SILENT_FORGE_MASTER_LEGS_CRAFTED, Items.IRON_LEGGINGS, 1);
            updateCraftProgress(world, player, StoryQuestKeys.SILENT_FORGE_MASTER_BOOTS_BASELINE, StoryQuestKeys.SILENT_FORGE_MASTER_BOOTS_CRAFTED, Items.IRON_BOOTS, 1);
            if (armorCraftedCount(world, playerId) < 4 || armorEnchantedCount(world, playerId) < 4) {
                return;
            }

            updateCraftProgress(world, player, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE_BASELINE, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE_CRAFTED, Items.DIAMOND_SWORD, 1);
            if (progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE_CRAFTED) < 1
                    || progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE) < 1) {
                return;
            }

            StoryQuestService.completeIfEligible(world, player);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            int books = bookProgressCount(world, playerId);
            if (books < MASTER_BOOK_FAMILIES_TARGET) {
                return List.of(Component.translatable(
                        "quest.village-quest.story.silent_forge.chapter_4.progress.1",
                        books,
                        MASTER_BOOK_FAMILIES_TARGET
                ).withStyle(ChatFormatting.GRAY));
            }

            int armorCrafted = armorCraftedCount(world, playerId);
            if (armorCrafted < 4) {
                return List.of(Component.translatable(
                        "quest.village-quest.story.silent_forge.chapter_4.progress.2",
                        armorCrafted,
                        4
                ).withStyle(ChatFormatting.GRAY));
            }

            int armorEnchanted = armorEnchantedCount(world, playerId);
            if (armorEnchanted < 4) {
                return List.of(Component.translatable(
                        "quest.village-quest.story.silent_forge.chapter_4.progress.3",
                        armorEnchanted,
                        4
                ).withStyle(ChatFormatting.GRAY));
            }

            int swordCrafted = Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE_CRAFTED));
            if (swordCrafted < 1) {
                return List.of(Component.translatable(
                        "quest.village-quest.story.silent_forge.chapter_4.progress.4",
                        swordCrafted,
                        1
                ).withStyle(ChatFormatting.GRAY));
            }

            int swordEnchanted = Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE));
            if (swordEnchanted < 1) {
                return List.of(Component.translatable(
                        "quest.village-quest.story.silent_forge.chapter_4.progress.5",
                        swordEnchanted,
                        1
                ).withStyle(ChatFormatting.GRAY));
            }

            ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
            Component carried = Component.translatable(
                    "quest.village-quest.story.silent_forge.chapter_4.progress.6",
                    player == null ? 0 : carriedPieceCount(player, world),
                    5
            ).withStyle(ChatFormatting.GRAY);
            Component blocked = player == null ? null : claimBlockedMessage(world, player);
            return blocked == null ? List.of(carried) : List.of(carried, blocked);
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            UUID playerId = player.getUUID();
            return bookProgressCount(world, playerId) >= MASTER_BOOK_FAMILIES_TARGET
                    && armorCraftedCount(world, playerId) >= 4
                    && armorEnchantedCount(world, playerId) >= 4
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE_CRAFTED) >= 1
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE) >= 1
                    && carriedPieceCount(player, world) >= 5;
        }

        @Override
        public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
            if (!isComplete(world, player)) {
                return false;
            }
            UUID playerId = player.getUUID();
            return StoryQuestService.consumeMatchingCompletionItems(world, playerId, stack -> isSharpnessSword(world, stack), 1)
                    && StoryQuestService.consumeMatchingCompletionItems(world, playerId, stack -> isProtectedArmor(world, stack, Items.IRON_HELMET), 1)
                    && StoryQuestService.consumeMatchingCompletionItems(world, playerId, stack -> isProtectedArmor(world, stack, Items.IRON_CHESTPLATE), 1)
                    && StoryQuestService.consumeMatchingCompletionItems(world, playerId, stack -> isProtectedArmor(world, stack, Items.IRON_LEGGINGS), 1)
                    && StoryQuestService.consumeMatchingCompletionItems(world, playerId, stack -> isProtectedArmor(world, stack, Items.IRON_BOOTS), 1);
        }

        @Override
        public Component claimBlockedMessage(ServerLevel world, ServerPlayer player) {
            if (player == null || world == null) {
                return null;
            }
            UUID playerId = player.getUUID();
            if (bookProgressCount(world, playerId) < MASTER_BOOK_FAMILIES_TARGET
                    || armorCraftedCount(world, playerId) < 4
                    || armorEnchantedCount(world, playerId) < 4
                    || progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE_CRAFTED) < 1
                    || progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE) < 1
                    || carriedPieceCount(player, world) >= 5) {
                return null;
            }
            return Texts.turnInMissing(
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_4.turnin.sword"),
                    countCarried(world, playerId, Items.DIAMOND_SWORD),
                    1,
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_4.turnin.helmet"),
                    countCarried(world, playerId, Items.IRON_HELMET),
                    1,
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_4.turnin.chestplate"),
                    countCarried(world, playerId, Items.IRON_CHESTPLATE),
                    1,
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_4.turnin.leggings"),
                    countCarried(world, playerId, Items.IRON_LEGGINGS),
                    1,
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_4.turnin.boots"),
                    countCarried(world, playerId, Items.IRON_BOOTS),
                    1
            );
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_4.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_4.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.silent_forge.chapter_4.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.SILVERMARK * 30L,
                    20,
                    ReputationService.ReputationTrack.CRAFTING,
                    40,
                    VillageProjectType.FORGE_CHARTER
            );
        }

        @Override
        public void onVillagerTrade(ServerLevel world, ServerPlayer player, ItemStack stack) {
            if (stack == null || !stack.is(Items.ENCHANTED_BOOK) || bookProgressCount(world, player.getUUID()) >= MASTER_BOOK_FAMILIES_TARGET) {
                return;
            }
            recordRequiredBook(world, player.getUUID(), stack);
        }

        @Override
        public void onTrackedItemPickup(ServerLevel world, ServerPlayer player, ItemStack stack, int count) {
            if (stack != null && stack.is(Items.ENCHANTED_BOOK)
                    && bookProgressCount(world, player.getUUID()) < MASTER_BOOK_FAMILIES_TARGET) {
                recordRequiredBook(world, player.getUUID(), stack);
                StoryQuestService.completeIfEligible(world, player);
            }
        }

        @Override
        public void onAnvilOutput(ServerLevel world,
                                  ServerPlayer player,
                                  ItemStack leftInput,
                                  ItemStack rightInput,
                                  ItemStack output) {
            if (leftInput == null || rightInput == null || output == null || !rightInput.is(Items.ENCHANTED_BOOK)) {
                return;
            }
            UUID playerId = player.getUUID();

            if (armorCraftedCount(world, playerId) >= 4
                    && armorEnchantedCount(world, playerId) < 4
                    && hasProtectionEnchantment(world, rightInput)
                    && hasProtectionEnchantment(world, output)) {
                if (leftInput.is(Items.IRON_HELMET) && output.is(Items.IRON_HELMET)) {
                    StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_HELM, 1);
                } else if (leftInput.is(Items.IRON_CHESTPLATE) && output.is(Items.IRON_CHESTPLATE)) {
                    StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_CHEST, 1);
                } else if (leftInput.is(Items.IRON_LEGGINGS) && output.is(Items.IRON_LEGGINGS)) {
                    StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_LEGS, 1);
                } else if (leftInput.is(Items.IRON_BOOTS) && output.is(Items.IRON_BOOTS)) {
                    StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_BOOTS, 1);
                }
                return;
            }

            if (armorEnchantedCount(world, playerId) >= 4
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE_CRAFTED) >= 1
                    && leftInput.is(Items.DIAMOND_SWORD)
                    && output.is(Items.DIAMOND_SWORD)
                    && hasEnchantment(world, rightInput, Enchantments.SHARPNESS)
                    && hasEnchantment(world, output, Enchantments.SHARPNESS)) {
                StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE, 1);
                StoryQuestService.completeIfEligible(world, player);
            }
        }
    }
}
