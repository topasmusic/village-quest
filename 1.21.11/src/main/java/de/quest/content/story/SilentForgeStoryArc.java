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
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SilentForgeStoryArc implements StoryArcDefinition {
    private static final int COLD_HEARTH_COAL_TARGET = 67;
    private static final int COLD_HEARTH_IRON_TARGET = 47;
    private static final int COLD_HEARTH_REDSTONE_TARGET = 29;
    private static final int COLD_HEARTH_GOLD_TARGET = 17;
    private static final int COLD_HEARTH_DIAMOND_TARGET = 4;
    private static final int BELLOWS_IRON_TARGET = 31;
    private static final int BELLOWS_BLAST_FURNACE_TARGET = 3;
    private static final int BELLOWS_CAULDRON_TARGET = 3;
    private static final int TOOLS_TARGET = 3;

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
    public Text title() {
        return Text.translatable("quest.village-quest.story.silent_forge.title");
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
    public boolean isUnlocked(ServerWorld world, UUID playerId) {
        return StoryQuestService.isCompleted(world, playerId, StoryArcType.FAILING_HARVEST);
    }

    private abstract static class SilentForgeChapter implements StoryChapterDefinition {
        protected void addProgress(ServerWorld world, ServerPlayerEntity player, String key, int amount, int target) {
            StoryQuestService.addQuestIntClamped(world, player.getUuid(), key, amount, target);
            StoryQuestService.completeIfEligible(world, player);
        }

        protected int progress(ServerWorld world, UUID playerId, String key) {
            return StoryQuestService.getQuestInt(world, playerId, key);
        }

        protected boolean hasItem(ServerWorld world, ServerPlayerEntity player, Item item, int amount) {
            return player != null && StoryQuestService.countCompletionItem(world, player.getUuid(), item) >= amount;
        }

        protected boolean consumeItem(ServerWorld world, ServerPlayerEntity player, Item item, int amount) {
            return player != null && StoryQuestService.consumeCompletionItem(world, player.getUuid(), item, amount);
        }

        protected boolean hasPristineItem(ServerWorld world, ServerPlayerEntity player, Item item, int amount) {
            return player != null && countPristineItems(world, player.getUuid(), item) >= amount;
        }

        protected int countPristineItems(ServerWorld world, UUID playerId, Item item) {
            if (world == null || playerId == null || item == null) {
                return 0;
            }
            return StoryQuestService.countMatchingCompletionItems(world, playerId, stack -> isPristineTurnInItem(stack, item));
        }

        protected boolean consumePristineItem(ServerWorld world, ServerPlayerEntity player, Item item, int amount) {
            return player != null
                    && StoryQuestService.consumeMatchingCompletionItems(world, player.getUuid(), stack -> isPristineTurnInItem(stack, item), amount);
        }

        protected void updateCraftProgress(ServerWorld world,
                                           ServerPlayerEntity player,
                                           String baselineKey,
                                           String progressKey,
                                           Item item,
                                           int target) {
            int baseline = StoryQuestService.getQuestInt(world, player.getUuid(), baselineKey);
            int crafted = DailyQuestService.getCraftedStat(player, item);
            if (baseline == 0) {
                StoryQuestService.setQuestInt(world, player.getUuid(), baselineKey, crafted + 1);
                return;
            }

            int delta = crafted - (baseline - 1);
            if (delta > 0) {
                StoryQuestService.addQuestIntClamped(world, player.getUuid(), progressKey, delta, target);
            }
            StoryQuestService.setQuestInt(world, player.getUuid(), baselineKey, crafted + 1);
        }

        protected RegistryEntry<Enchantment> enchantment(ServerWorld world, RegistryKey<Enchantment> key) {
            return world.getRegistryManager()
                    .getOrThrow(RegistryKeys.ENCHANTMENT)
                    .getOrThrow(key);
        }

        protected boolean hasEnchantment(ServerWorld world, ItemStack stack, RegistryKey<Enchantment> key) {
            return world != null
                    && stack != null
                    && !stack.isEmpty()
                    && EnchantmentHelper.getEnchantments(stack).getLevel(enchantment(world, key)) > 0;
        }

        protected int findEnchantedItemSlot(ServerPlayerEntity player, ServerWorld world, Item item, RegistryKey<Enchantment> enchantment) {
            if (player == null || world == null) {
                return -1;
            }
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack stack = player.getInventory().getStack(i);
                if (stack.isOf(item) && hasEnchantment(world, stack, enchantment)) {
                    return i;
                }
            }
            return -1;
        }

        protected boolean consumeEnchantedItem(ServerPlayerEntity player, ServerWorld world, Item item, RegistryKey<Enchantment> enchantment) {
            int slot = findEnchantedItemSlot(player, world, item, enchantment);
            if (slot < 0) {
                return false;
            }
            player.getInventory().getStack(slot).decrement(1);
            player.currentScreenHandler.sendContentUpdates();
            return true;
        }

        private boolean isPristineTurnInItem(ItemStack stack, Item item) {
            return stack != null
                    && !stack.isEmpty()
                    && stack.isOf(item)
                    && (!stack.isDamageable() || !stack.isDamaged());
        }
    }

    private static final class ColdHearthChapter extends SilentForgeChapter {
        @Override
        public Text title() {
            return Text.translatable("quest.village-quest.story.silent_forge.chapter_1.title");
        }

        @Override
        public Text offerParagraph1() {
            return Text.translatable("quest.village-quest.story.silent_forge.chapter_1.offer.1").formatted(Formatting.GRAY);
        }

        @Override
        public Text offerParagraph2() {
            return Text.translatable("quest.village-quest.story.silent_forge.chapter_1.offer.2").formatted(Formatting.GRAY);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            Text line1 = Text.translatable(
                    "quest.village-quest.story.silent_forge.chapter_1.progress.1",
                    progress(world, playerId, StoryQuestKeys.SILENT_FORGE_COAL_ORE),
                    COLD_HEARTH_COAL_TARGET
            ).formatted(Formatting.GRAY);
            Text line2 = Text.translatable(
                    "quest.village-quest.story.silent_forge.chapter_1.progress.2",
                    progress(world, playerId, StoryQuestKeys.SILENT_FORGE_IRON_ORE),
                    COLD_HEARTH_IRON_TARGET
            ).formatted(Formatting.GRAY);
            Text line3 = Text.translatable(
                    "quest.village-quest.story.silent_forge.chapter_1.progress.3",
                    progress(world, playerId, StoryQuestKeys.SILENT_FORGE_REDSTONE_ORE),
                    COLD_HEARTH_REDSTONE_TARGET
            ).formatted(Formatting.GRAY);
            Text line4 = Text.translatable(
                    "quest.village-quest.story.silent_forge.chapter_1.progress.4",
                    progress(world, playerId, StoryQuestKeys.SILENT_FORGE_GOLD_ORE),
                    COLD_HEARTH_GOLD_TARGET
            ).formatted(Formatting.GRAY);
            Text line5 = Text.translatable(
                    "quest.village-quest.story.silent_forge.chapter_1.progress.5",
                    progress(world, playerId, StoryQuestKeys.SILENT_FORGE_DIAMOND_ORE),
                    COLD_HEARTH_DIAMOND_TARGET
            ).formatted(Formatting.GRAY);
            ServerPlayerEntity player = world == null ? null : world.getServer().getPlayerManager().getPlayer(playerId);
            Text blocked = player == null ? null : claimBlockedMessage(world, player);
            return blocked == null
                    ? List.of(line1, line2, line3, line4, line5)
                    : List.of(line1, line2, line3, line4, line5, blocked);
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            UUID playerId = player.getUuid();
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
        public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
            if (!isComplete(world, player)) {
                return false;
            }
            return StoryQuestService.consumeCompletionItems(world, player.getUuid(), Map.of(
                    Items.COAL, COLD_HEARTH_COAL_TARGET,
                    Items.RAW_IRON, COLD_HEARTH_IRON_TARGET,
                    Items.REDSTONE, COLD_HEARTH_REDSTONE_TARGET,
                    Items.RAW_GOLD, COLD_HEARTH_GOLD_TARGET,
                    Items.DIAMOND, COLD_HEARTH_DIAMOND_TARGET
            ));
        }

        @Override
        public Text claimBlockedMessage(ServerWorld world, ServerPlayerEntity player) {
            if (player == null || world == null) {
                return null;
            }
            UUID playerId = player.getUuid();
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
                    Items.COAL.getDefaultStack().toHoverableText(),
                    StoryQuestService.countCompletionItem(world, playerId, Items.COAL),
                    COLD_HEARTH_COAL_TARGET,
                    Items.RAW_IRON.getDefaultStack().toHoverableText(),
                    StoryQuestService.countCompletionItem(world, playerId, Items.RAW_IRON),
                    COLD_HEARTH_IRON_TARGET,
                    Items.REDSTONE.getDefaultStack().toHoverableText(),
                    StoryQuestService.countCompletionItem(world, playerId, Items.REDSTONE),
                    COLD_HEARTH_REDSTONE_TARGET,
                    Items.RAW_GOLD.getDefaultStack().toHoverableText(),
                    StoryQuestService.countCompletionItem(world, playerId, Items.RAW_GOLD),
                    COLD_HEARTH_GOLD_TARGET,
                    Items.DIAMOND.getDefaultStack().toHoverableText(),
                    StoryQuestService.countCompletionItem(world, playerId, Items.DIAMOND),
                    COLD_HEARTH_DIAMOND_TARGET
            );
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_1.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_1.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_1.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.SILVERMARK * 12L,
                    8,
                    ReputationService.ReputationTrack.CRAFTING,
                    10,
                    null
            );
        }

        @Override
        public void onTrackedItemPickup(ServerWorld world, ServerPlayerEntity player, ItemStack stack, int count) {
            if (stack.isOf(Items.COAL)) {
                addProgress(world, player, StoryQuestKeys.SILENT_FORGE_COAL_ORE, count, COLD_HEARTH_COAL_TARGET);
            } else if (stack.isOf(Items.RAW_IRON)) {
                addProgress(world, player, StoryQuestKeys.SILENT_FORGE_IRON_ORE, count, COLD_HEARTH_IRON_TARGET);
            } else if (stack.isOf(Items.REDSTONE)) {
                addProgress(world, player, StoryQuestKeys.SILENT_FORGE_REDSTONE_ORE, count, COLD_HEARTH_REDSTONE_TARGET);
            } else if (stack.isOf(Items.RAW_GOLD)) {
                addProgress(world, player, StoryQuestKeys.SILENT_FORGE_GOLD_ORE, count, COLD_HEARTH_GOLD_TARGET);
            } else if (stack.isOf(Items.DIAMOND)) {
                addProgress(world, player, StoryQuestKeys.SILENT_FORGE_DIAMOND_ORE, count, COLD_HEARTH_DIAMOND_TARGET);
            }
        }
    }

    private static final class BellowsAgainChapter extends SilentForgeChapter {
        @Override
        public Text title() {
            return Text.translatable("quest.village-quest.story.silent_forge.chapter_2.title");
        }

        @Override
        public Text offerParagraph1() {
            return Text.translatable("quest.village-quest.story.silent_forge.chapter_2.offer.1").formatted(Formatting.GRAY);
        }

        @Override
        public Text offerParagraph2() {
            return Text.translatable("quest.village-quest.story.silent_forge.chapter_2.offer.2").formatted(Formatting.GRAY);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
            int blastFurnace = StoryQuestService.countCompletionItem(world, playerId, Items.BLAST_FURNACE);
            int cauldron = StoryQuestService.countCompletionItem(world, playerId, Items.CAULDRON);
            Text line1 = Text.translatable(
                    "quest.village-quest.story.silent_forge.chapter_2.progress.1",
                    progress(world, playerId, StoryQuestKeys.SILENT_FORGE_IRON_INGOT),
                    BELLOWS_IRON_TARGET
            ).formatted(Formatting.GRAY);
            Text line2 = Text.translatable(
                    "quest.village-quest.story.silent_forge.chapter_2.progress.2",
                    Math.min(blastFurnace, BELLOWS_BLAST_FURNACE_TARGET),
                    BELLOWS_BLAST_FURNACE_TARGET,
                    Math.min(cauldron, BELLOWS_CAULDRON_TARGET),
                    BELLOWS_CAULDRON_TARGET
            ).formatted(Formatting.GRAY);
            Text blocked = player == null ? null : claimBlockedMessage(world, player);
            return blocked == null ? List.of(line1, line2) : List.of(line1, line2, blocked);
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            UUID playerId = player.getUuid();
            return progress(world, playerId, StoryQuestKeys.SILENT_FORGE_IRON_INGOT) >= BELLOWS_IRON_TARGET
                    && hasItem(world, player, Items.IRON_INGOT, BELLOWS_IRON_TARGET)
                    && hasItem(world, player, Items.BLAST_FURNACE, BELLOWS_BLAST_FURNACE_TARGET)
                    && hasItem(world, player, Items.CAULDRON, BELLOWS_CAULDRON_TARGET);
        }

        @Override
        public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
            if (!isComplete(world, player)) {
                return false;
            }
            return StoryQuestService.consumeCompletionItems(world, player.getUuid(), Map.of(
                    Items.IRON_INGOT, BELLOWS_IRON_TARGET,
                    Items.BLAST_FURNACE, BELLOWS_BLAST_FURNACE_TARGET,
                    Items.CAULDRON, BELLOWS_CAULDRON_TARGET
            ));
        }

        @Override
        public Text claimBlockedMessage(ServerWorld world, ServerPlayerEntity player) {
            if (player == null || world == null) {
                return null;
            }
            UUID playerId = player.getUuid();
            if (progress(world, playerId, StoryQuestKeys.SILENT_FORGE_IRON_INGOT) < BELLOWS_IRON_TARGET
                    || (hasItem(world, player, Items.IRON_INGOT, BELLOWS_IRON_TARGET)
                    && hasItem(world, player, Items.BLAST_FURNACE, BELLOWS_BLAST_FURNACE_TARGET)
                    && hasItem(world, player, Items.CAULDRON, BELLOWS_CAULDRON_TARGET))) {
                return null;
            }
            return Texts.turnInMissing(
                    Items.IRON_INGOT.getDefaultStack().toHoverableText(),
                    StoryQuestService.countCompletionItem(world, playerId, Items.IRON_INGOT),
                    BELLOWS_IRON_TARGET,
                    Items.BLAST_FURNACE.getDefaultStack().toHoverableText(),
                    StoryQuestService.countCompletionItem(world, playerId, Items.BLAST_FURNACE),
                    BELLOWS_BLAST_FURNACE_TARGET,
                    Items.CAULDRON.getDefaultStack().toHoverableText(),
                    StoryQuestService.countCompletionItem(world, playerId, Items.CAULDRON),
                    BELLOWS_CAULDRON_TARGET
            );
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_2.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_2.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_2.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.SILVERMARK * 16L,
                    10,
                    ReputationService.ReputationTrack.CRAFTING,
                    12,
                    null
            );
        }

        @Override
        public void onFurnaceOutput(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
            if (!stack.isOf(Items.IRON_INGOT)) {
                return;
            }
            addProgress(world, player, StoryQuestKeys.SILENT_FORGE_IRON_INGOT, stack.getCount(), BELLOWS_IRON_TARGET);
        }
    }

    private static final class ToolsForTheHallChapter extends SilentForgeChapter {
        @Override
        public Text title() {
            return Text.translatable("quest.village-quest.story.silent_forge.chapter_3.title");
        }

        @Override
        public Text offerParagraph1() {
            return Text.translatable("quest.village-quest.story.silent_forge.chapter_3.offer.1").formatted(Formatting.GRAY);
        }

        @Override
        public Text offerParagraph2() {
            return Text.translatable("quest.village-quest.story.silent_forge.chapter_3.offer.2").formatted(Formatting.GRAY);
        }

        @Override
        public void onAccepted(ServerWorld world, ServerPlayerEntity player) {
            StoryQuestService.setQuestInt(world, player.getUuid(), StoryQuestKeys.SILENT_FORGE_PICKAXE_BASELINE, DailyQuestService.getCraftedStat(player, Items.IRON_PICKAXE) + 1);
            StoryQuestService.setQuestInt(world, player.getUuid(), StoryQuestKeys.SILENT_FORGE_BUCKET_BASELINE, DailyQuestService.getCraftedStat(player, Items.BUCKET) + 1);
            StoryQuestService.setQuestInt(world, player.getUuid(), StoryQuestKeys.SILENT_FORGE_SHEARS_BASELINE, DailyQuestService.getCraftedStat(player, Items.SHEARS) + 1);
            StoryQuestService.setQuestInt(world, player.getUuid(), StoryQuestKeys.SILENT_FORGE_SHIELD_BASELINE, DailyQuestService.getCraftedStat(player, Items.SHIELD) + 1);
        }

        @Override
        public void onServerTick(ServerWorld world, ServerPlayerEntity player) {
            updateCraftProgress(world, player, StoryQuestKeys.SILENT_FORGE_PICKAXE_BASELINE, StoryQuestKeys.SILENT_FORGE_PICKAXE_CRAFTED, Items.IRON_PICKAXE, TOOLS_TARGET);
            updateCraftProgress(world, player, StoryQuestKeys.SILENT_FORGE_BUCKET_BASELINE, StoryQuestKeys.SILENT_FORGE_BUCKET_CRAFTED, Items.BUCKET, TOOLS_TARGET);
            updateCraftProgress(world, player, StoryQuestKeys.SILENT_FORGE_SHEARS_BASELINE, StoryQuestKeys.SILENT_FORGE_SHEARS_CRAFTED, Items.SHEARS, TOOLS_TARGET);
            updateCraftProgress(world, player, StoryQuestKeys.SILENT_FORGE_SHIELD_BASELINE, StoryQuestKeys.SILENT_FORGE_SHIELD_CRAFTED, Items.SHIELD, TOOLS_TARGET);
            StoryQuestService.completeIfEligible(world, player);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
            int pickaxeReady = player == null ? 0 : Math.min(progress(world, playerId, StoryQuestKeys.SILENT_FORGE_PICKAXE_CRAFTED), countPristineItems(world, playerId, Items.IRON_PICKAXE));
            int bucketReady = player == null ? 0 : Math.min(progress(world, playerId, StoryQuestKeys.SILENT_FORGE_BUCKET_CRAFTED), countPristineItems(world, playerId, Items.BUCKET));
            int shearsReady = player == null ? 0 : Math.min(progress(world, playerId, StoryQuestKeys.SILENT_FORGE_SHEARS_CRAFTED), countPristineItems(world, playerId, Items.SHEARS));
            int shieldReady = player == null ? 0 : Math.min(progress(world, playerId, StoryQuestKeys.SILENT_FORGE_SHIELD_CRAFTED), countPristineItems(world, playerId, Items.SHIELD));
            Text line1 = Text.translatable(
                    "quest.village-quest.story.silent_forge.chapter_3.progress.1",
                    pickaxeReady,
                    TOOLS_TARGET,
                    bucketReady,
                    TOOLS_TARGET
            ).formatted(Formatting.GRAY);
            Text line2 = Text.translatable(
                    "quest.village-quest.story.silent_forge.chapter_3.progress.2",
                    shearsReady,
                    TOOLS_TARGET,
                    shieldReady,
                    TOOLS_TARGET
            ).formatted(Formatting.GRAY);
            Text blocked = player == null ? null : claimBlockedMessage(world, player);
            return blocked == null ? List.of(line1, line2) : List.of(line1, line2, blocked);
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            UUID playerId = player.getUuid();
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
        public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
            if (!isComplete(world, player)) {
                return false;
            }
            return consumePristineItem(world, player, Items.IRON_PICKAXE, TOOLS_TARGET)
                    && consumePristineItem(world, player, Items.BUCKET, TOOLS_TARGET)
                    && consumePristineItem(world, player, Items.SHEARS, TOOLS_TARGET)
                    && consumePristineItem(world, player, Items.SHIELD, TOOLS_TARGET);
        }

        @Override
        public Text claimBlockedMessage(ServerWorld world, ServerPlayerEntity player) {
            if (player == null || world == null) {
                return null;
            }
            UUID playerId = player.getUuid();
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
                    Items.IRON_PICKAXE.getDefaultStack().toHoverableText(),
                    countPristineItems(world, playerId, Items.IRON_PICKAXE),
                    TOOLS_TARGET,
                    Items.BUCKET.getDefaultStack().toHoverableText(),
                    countPristineItems(world, playerId, Items.BUCKET),
                    TOOLS_TARGET,
                    Items.SHEARS.getDefaultStack().toHoverableText(),
                    countPristineItems(world, playerId, Items.SHEARS),
                    TOOLS_TARGET,
                    Items.SHIELD.getDefaultStack().toHoverableText(),
                    countPristineItems(world, playerId, Items.SHIELD),
                    TOOLS_TARGET
            );
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_3.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_3.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_3.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.SILVERMARK * 22L,
                    12,
                    ReputationService.ReputationTrack.CRAFTING,
                    15,
                    null
            );
        }
    }

    private static final class MastersEdgeChapter extends SilentForgeChapter {
        private int bookProgressCount(ServerWorld world, UUID playerId) {
            int total = 0;
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_SHARPNESS_BOOK));
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_FIRE_PROTECTION_BOOK));
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_PROTECTION_BOOK));
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_BLAST_PROTECTION_BOOK));
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_PROJECTILE_PROTECTION_BOOK));
            return total;
        }

        private void recordRequiredBook(ServerWorld world, UUID playerId, ItemStack stack) {
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

        private void recoverMissedBookPurchases(ServerWorld world, ServerPlayerEntity player) {
            UUID playerId = player.getUuid();
            if (progress(world, playerId, StoryQuestKeys.SILENT_FORGE_BOOK_MIGRATION) >= 1) {
                return;
            }
            for (int slot = 0; slot < player.getInventory().size(); slot++) {
                ItemStack stack = player.getInventory().getStack(slot);
                if (stack.isOf(Items.ENCHANTED_BOOK)) {
                    recordRequiredBook(world, playerId, stack);
                }
            }
            StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_BOOK_MIGRATION, 1);
        }

        private int armorCraftedCount(ServerWorld world, UUID playerId) {
            int total = 0;
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_HELM_CRAFTED));
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_CHEST_CRAFTED));
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_LEGS_CRAFTED));
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_BOOTS_CRAFTED));
            return total;
        }

        private int armorEnchantedCount(ServerWorld world, UUID playerId) {
            int total = 0;
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_HELM));
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_CHEST));
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_LEGS));
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_BOOTS));
            return total;
        }

        private boolean hasProtectionEnchantment(ServerWorld world, ItemStack stack) {
            return hasEnchantment(world, stack, Enchantments.FIRE_PROTECTION)
                    || hasEnchantment(world, stack, Enchantments.PROTECTION)
                    || hasEnchantment(world, stack, Enchantments.BLAST_PROTECTION)
                    || hasEnchantment(world, stack, Enchantments.PROJECTILE_PROTECTION);
        }

        private boolean isProtectedArmor(ServerWorld world, ItemStack stack, Item item) {
            return stack != null && stack.isOf(item) && hasProtectionEnchantment(world, stack);
        }

        private boolean isSharpnessSword(ServerWorld world, ItemStack stack) {
            return stack != null
                    && stack.isOf(Items.DIAMOND_SWORD)
                    && hasEnchantment(world, stack, Enchantments.SHARPNESS);
        }

        private int countCarried(ServerWorld world, UUID playerId, Item item) {
            return StoryQuestService.countMatchingCompletionItems(
                    world,
                    playerId,
                    stack -> item == Items.DIAMOND_SWORD
                            ? isSharpnessSword(world, stack)
                            : isProtectedArmor(world, stack, item)
            );
        }

        private int carriedPieceCount(ServerPlayerEntity player, ServerWorld world) {
            if (player == null || world == null) {
                return 0;
            }
            UUID playerId = player.getUuid();
            int total = 0;
            total += Math.min(1, countCarried(world, playerId, Items.DIAMOND_SWORD));
            total += Math.min(1, countCarried(world, playerId, Items.IRON_HELMET));
            total += Math.min(1, countCarried(world, playerId, Items.IRON_CHESTPLATE));
            total += Math.min(1, countCarried(world, playerId, Items.IRON_LEGGINGS));
            total += Math.min(1, countCarried(world, playerId, Items.IRON_BOOTS));
            return total;
        }

        private void migrateLegacyCraftProgress(ServerWorld world, UUID playerId) {
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
        public Text title() {
            return Text.translatable("quest.village-quest.story.silent_forge.chapter_4.title");
        }

        @Override
        public Text offerParagraph1() {
            return Text.translatable("quest.village-quest.story.silent_forge.chapter_4.offer.1").formatted(Formatting.GRAY);
        }

        @Override
        public Text offerParagraph2() {
            return Text.translatable("quest.village-quest.story.silent_forge.chapter_4.offer.2").formatted(Formatting.GRAY);
        }

        @Override
        public void onAccepted(ServerWorld world, ServerPlayerEntity player) {
            StoryQuestService.setQuestInt(world, player.getUuid(), StoryQuestKeys.SILENT_FORGE_BOOK_MIGRATION, 1);
        }

        @Override
        public void onServerTick(ServerWorld world, ServerPlayerEntity player) {
            UUID playerId = player.getUuid();
            recoverMissedBookPurchases(world, player);
            if (bookProgressCount(world, playerId) < 5) {
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
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            int books = bookProgressCount(world, playerId);
            if (books < 5) {
                return List.of(Text.translatable(
                        "quest.village-quest.story.silent_forge.chapter_4.progress.1",
                        books,
                        5
                ).formatted(Formatting.GRAY));
            }

            int armorCrafted = armorCraftedCount(world, playerId);
            if (armorCrafted < 4) {
                return List.of(Text.translatable(
                        "quest.village-quest.story.silent_forge.chapter_4.progress.2",
                        armorCrafted,
                        4
                ).formatted(Formatting.GRAY));
            }

            int armorEnchanted = armorEnchantedCount(world, playerId);
            if (armorEnchanted < 4) {
                return List.of(Text.translatable(
                        "quest.village-quest.story.silent_forge.chapter_4.progress.3",
                        armorEnchanted,
                        4
                ).formatted(Formatting.GRAY));
            }

            int swordCrafted = Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE_CRAFTED));
            if (swordCrafted < 1) {
                return List.of(Text.translatable(
                        "quest.village-quest.story.silent_forge.chapter_4.progress.4",
                        swordCrafted,
                        1
                ).formatted(Formatting.GRAY));
            }

            int swordEnchanted = Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE));
            if (swordEnchanted < 1) {
                return List.of(Text.translatable(
                        "quest.village-quest.story.silent_forge.chapter_4.progress.5",
                        swordEnchanted,
                        1
                ).formatted(Formatting.GRAY));
            }

            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
            Text carried = Text.translatable(
                    "quest.village-quest.story.silent_forge.chapter_4.progress.6",
                    player == null ? 0 : carriedPieceCount(player, world),
                    5
            ).formatted(Formatting.GRAY);
            Text blocked = player == null ? null : claimBlockedMessage(world, player);
            return blocked == null ? List.of(carried) : List.of(carried, blocked);
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            UUID playerId = player.getUuid();
            return bookProgressCount(world, playerId) >= 5
                    && armorCraftedCount(world, playerId) >= 4
                    && armorEnchantedCount(world, playerId) >= 4
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE_CRAFTED) >= 1
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE) >= 1
                    && carriedPieceCount(player, world) >= 5;
        }

        @Override
        public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
            if (!isComplete(world, player)) {
                return false;
            }
            UUID playerId = player.getUuid();
            return StoryQuestService.consumeMatchingCompletionItems(world, playerId, stack -> isSharpnessSword(world, stack), 1)
                    && StoryQuestService.consumeMatchingCompletionItems(world, playerId, stack -> isProtectedArmor(world, stack, Items.IRON_HELMET), 1)
                    && StoryQuestService.consumeMatchingCompletionItems(world, playerId, stack -> isProtectedArmor(world, stack, Items.IRON_CHESTPLATE), 1)
                    && StoryQuestService.consumeMatchingCompletionItems(world, playerId, stack -> isProtectedArmor(world, stack, Items.IRON_LEGGINGS), 1)
                    && StoryQuestService.consumeMatchingCompletionItems(world, playerId, stack -> isProtectedArmor(world, stack, Items.IRON_BOOTS), 1);
        }

        @Override
        public Text claimBlockedMessage(ServerWorld world, ServerPlayerEntity player) {
            if (player == null || world == null) {
                return null;
            }
            UUID playerId = player.getUuid();
            if (bookProgressCount(world, playerId) < 5
                    || armorCraftedCount(world, playerId) < 4
                    || armorEnchantedCount(world, playerId) < 4
                    || progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE_CRAFTED) < 1
                    || progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE) < 1
                    || carriedPieceCount(player, world) >= 5) {
                return null;
            }
            return Texts.turnInMissing(
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_4.turnin.sword"),
                    countCarried(world, playerId, Items.DIAMOND_SWORD),
                    1,
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_4.turnin.helmet"),
                    countCarried(world, playerId, Items.IRON_HELMET),
                    1,
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_4.turnin.chestplate"),
                    countCarried(world, playerId, Items.IRON_CHESTPLATE),
                    1,
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_4.turnin.leggings"),
                    countCarried(world, playerId, Items.IRON_LEGGINGS),
                    1,
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_4.turnin.boots"),
                    countCarried(world, playerId, Items.IRON_BOOTS),
                    1
            );
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_4.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_4.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_4.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.SILVERMARK * 30L,
                    20,
                    ReputationService.ReputationTrack.CRAFTING,
                    40,
                    VillageProjectType.FORGE_CHARTER
            );
        }

        @Override
        public void onVillagerTrade(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
            if (stack == null || !stack.isOf(Items.ENCHANTED_BOOK) || bookProgressCount(world, player.getUuid()) >= 5) {
                return;
            }
            recordRequiredBook(world, player.getUuid(), stack);
        }

        @Override
        public void onAnvilOutput(ServerWorld world,
                                  ServerPlayerEntity player,
                                  ItemStack leftInput,
                                  ItemStack rightInput,
                                  ItemStack output) {
            if (leftInput == null || rightInput == null || output == null || !rightInput.isOf(Items.ENCHANTED_BOOK)) {
                return;
            }
            UUID playerId = player.getUuid();

            if (armorCraftedCount(world, playerId) >= 4
                    && armorEnchantedCount(world, playerId) < 4
                    && hasProtectionEnchantment(world, rightInput)
                    && hasProtectionEnchantment(world, output)) {
                if (leftInput.isOf(Items.IRON_HELMET) && output.isOf(Items.IRON_HELMET)) {
                    StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_HELM, 1);
                } else if (leftInput.isOf(Items.IRON_CHESTPLATE) && output.isOf(Items.IRON_CHESTPLATE)) {
                    StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_CHEST, 1);
                } else if (leftInput.isOf(Items.IRON_LEGGINGS) && output.isOf(Items.IRON_LEGGINGS)) {
                    StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_LEGS, 1);
                } else if (leftInput.isOf(Items.IRON_BOOTS) && output.isOf(Items.IRON_BOOTS)) {
                    StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_BOOTS, 1);
                }
                return;
            }

            if (armorEnchantedCount(world, playerId) >= 4
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE_CRAFTED) >= 1
                    && leftInput.isOf(Items.DIAMOND_SWORD)
                    && output.isOf(Items.DIAMOND_SWORD)
                    && hasEnchantment(world, rightInput, Enchantments.SHARPNESS)
                    && hasEnchantment(world, output, Enchantments.SHARPNESS)) {
                StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE, 1);
                StoryQuestService.completeIfEligible(world, player);
            }
        }
    }
}
