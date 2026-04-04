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
import java.util.UUID;

public final class SilentForgeStoryArc implements StoryArcDefinition {
    private static final int COLD_HEARTH_COAL_TARGET = 70;
    private static final int COLD_HEARTH_IRON_TARGET = 50;
    private static final int COLD_HEARTH_REDSTONE_TARGET = 30;
    private static final int COLD_HEARTH_GOLD_TARGET = 20;
    private static final int COLD_HEARTH_DIAMOND_TARGET = 5;
    private static final int BELLOWS_IRON_TARGET = 36;
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

        protected boolean hasItem(ServerPlayerEntity player, Item item, int amount) {
            return DailyQuestService.countInventoryItem(player, item) >= amount;
        }

        protected boolean consumeItem(ServerPlayerEntity player, Item item, int amount) {
            return DailyQuestService.consumeInventoryItem(player, item, amount);
        }

        protected boolean hasPristineItem(ServerPlayerEntity player, Item item, int amount) {
            return countPristineItems(player, item) >= amount;
        }

        protected int countPristineItems(ServerPlayerEntity player, Item item) {
            if (player == null || item == null) {
                return 0;
            }

            int total = 0;
            PlayerInventory inventory = player.getInventory();
            for (int i = 0; i < inventory.size(); i++) {
                ItemStack stack = inventory.getStack(i);
                if (isPristineTurnInItem(stack, item)) {
                    total += stack.getCount();
                }
            }
            return total;
        }

        protected boolean consumePristineItem(ServerPlayerEntity player, Item item, int amount) {
            if (!hasPristineItem(player, item, amount)) {
                return false;
            }

            int remaining = amount;
            PlayerInventory inventory = player.getInventory();
            for (int i = 0; i < inventory.size() && remaining > 0; i++) {
                ItemStack stack = inventory.getStack(i);
                if (!isPristineTurnInItem(stack, item)) {
                    continue;
                }

                int removed = Math.min(remaining, stack.getCount());
                stack.decrement(removed);
                remaining -= removed;
            }

            player.currentScreenHandler.sendContentUpdates();
            return remaining <= 0;
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

            int delta = Math.max(0, crafted - (baseline - 1));
            int updated = Math.min(target, delta);
            if (StoryQuestService.getQuestInt(world, player.getUuid(), progressKey) != updated) {
                StoryQuestService.setQuestInt(world, player.getUuid(), progressKey, updated);
            }
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
                    && hasItem(player, Items.COAL, COLD_HEARTH_COAL_TARGET)
                    && hasItem(player, Items.RAW_IRON, COLD_HEARTH_IRON_TARGET)
                    && hasItem(player, Items.REDSTONE, COLD_HEARTH_REDSTONE_TARGET)
                    && hasItem(player, Items.RAW_GOLD, COLD_HEARTH_GOLD_TARGET)
                    && hasItem(player, Items.DIAMOND, COLD_HEARTH_DIAMOND_TARGET);
        }

        @Override
        public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
            if (!isComplete(world, player)) {
                return false;
            }
            return consumeItem(player, Items.COAL, COLD_HEARTH_COAL_TARGET)
                    && consumeItem(player, Items.RAW_IRON, COLD_HEARTH_IRON_TARGET)
                    && consumeItem(player, Items.REDSTONE, COLD_HEARTH_REDSTONE_TARGET)
                    && consumeItem(player, Items.RAW_GOLD, COLD_HEARTH_GOLD_TARGET)
                    && consumeItem(player, Items.DIAMOND, COLD_HEARTH_DIAMOND_TARGET);
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
                    || (hasItem(player, Items.COAL, COLD_HEARTH_COAL_TARGET)
                    && hasItem(player, Items.RAW_IRON, COLD_HEARTH_IRON_TARGET)
                    && hasItem(player, Items.REDSTONE, COLD_HEARTH_REDSTONE_TARGET)
                    && hasItem(player, Items.RAW_GOLD, COLD_HEARTH_GOLD_TARGET)
                    && hasItem(player, Items.DIAMOND, COLD_HEARTH_DIAMOND_TARGET))) {
                return null;
            }
            return Text.translatable(
                    "text.village-quest.turnin_missing.5",
                    Items.COAL.getDefaultStack().toHoverableText(),
                    DailyQuestService.countInventoryItem(player, Items.COAL),
                    COLD_HEARTH_COAL_TARGET,
                    Items.RAW_IRON.getDefaultStack().toHoverableText(),
                    DailyQuestService.countInventoryItem(player, Items.RAW_IRON),
                    COLD_HEARTH_IRON_TARGET,
                    Items.REDSTONE.getDefaultStack().toHoverableText(),
                    DailyQuestService.countInventoryItem(player, Items.REDSTONE),
                    COLD_HEARTH_REDSTONE_TARGET,
                    Items.RAW_GOLD.getDefaultStack().toHoverableText(),
                    DailyQuestService.countInventoryItem(player, Items.RAW_GOLD),
                    COLD_HEARTH_GOLD_TARGET,
                    Items.DIAMOND.getDefaultStack().toHoverableText(),
                    DailyQuestService.countInventoryItem(player, Items.DIAMOND),
                    COLD_HEARTH_DIAMOND_TARGET
            ).formatted(Formatting.RED);
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_1.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_1.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_1.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.SILVERMARK * 8L,
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
            int blastFurnace = player == null ? 0 : DailyQuestService.countInventoryItem(player, Items.BLAST_FURNACE);
            int cauldron = player == null ? 0 : DailyQuestService.countInventoryItem(player, Items.CAULDRON);
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
                    && hasItem(player, Items.IRON_INGOT, BELLOWS_IRON_TARGET)
                    && hasItem(player, Items.BLAST_FURNACE, BELLOWS_BLAST_FURNACE_TARGET)
                    && hasItem(player, Items.CAULDRON, BELLOWS_CAULDRON_TARGET);
        }

        @Override
        public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
            if (!isComplete(world, player)) {
                return false;
            }
            return consumeItem(player, Items.IRON_INGOT, BELLOWS_IRON_TARGET)
                    && consumeItem(player, Items.BLAST_FURNACE, BELLOWS_BLAST_FURNACE_TARGET)
                    && consumeItem(player, Items.CAULDRON, BELLOWS_CAULDRON_TARGET);
        }

        @Override
        public Text claimBlockedMessage(ServerWorld world, ServerPlayerEntity player) {
            if (player == null || world == null) {
                return null;
            }
            UUID playerId = player.getUuid();
            if (progress(world, playerId, StoryQuestKeys.SILENT_FORGE_IRON_INGOT) < BELLOWS_IRON_TARGET
                    || (hasItem(player, Items.IRON_INGOT, BELLOWS_IRON_TARGET)
                    && hasItem(player, Items.BLAST_FURNACE, BELLOWS_BLAST_FURNACE_TARGET)
                    && hasItem(player, Items.CAULDRON, BELLOWS_CAULDRON_TARGET))) {
                return null;
            }
            return Texts.turnInMissing(
                    Items.IRON_INGOT.getDefaultStack().toHoverableText(),
                    DailyQuestService.countInventoryItem(player, Items.IRON_INGOT),
                    BELLOWS_IRON_TARGET,
                    Items.BLAST_FURNACE.getDefaultStack().toHoverableText(),
                    DailyQuestService.countInventoryItem(player, Items.BLAST_FURNACE),
                    BELLOWS_BLAST_FURNACE_TARGET,
                    Items.CAULDRON.getDefaultStack().toHoverableText(),
                    DailyQuestService.countInventoryItem(player, Items.CAULDRON),
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
                    CurrencyService.CROWN,
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
            int pickaxeReady = player == null ? 0 : Math.min(progress(world, playerId, StoryQuestKeys.SILENT_FORGE_PICKAXE_CRAFTED), countPristineItems(player, Items.IRON_PICKAXE));
            int bucketReady = player == null ? 0 : Math.min(progress(world, playerId, StoryQuestKeys.SILENT_FORGE_BUCKET_CRAFTED), countPristineItems(player, Items.BUCKET));
            int shearsReady = player == null ? 0 : Math.min(progress(world, playerId, StoryQuestKeys.SILENT_FORGE_SHEARS_CRAFTED), countPristineItems(player, Items.SHEARS));
            int shieldReady = player == null ? 0 : Math.min(progress(world, playerId, StoryQuestKeys.SILENT_FORGE_SHIELD_CRAFTED), countPristineItems(player, Items.SHIELD));
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
                    && hasPristineItem(player, Items.IRON_PICKAXE, TOOLS_TARGET)
                    && hasPristineItem(player, Items.BUCKET, TOOLS_TARGET)
                    && hasPristineItem(player, Items.SHEARS, TOOLS_TARGET)
                    && hasPristineItem(player, Items.SHIELD, TOOLS_TARGET);
        }

        @Override
        public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
            if (!isComplete(world, player)) {
                return false;
            }
            return consumePristineItem(player, Items.IRON_PICKAXE, TOOLS_TARGET)
                    && consumePristineItem(player, Items.BUCKET, TOOLS_TARGET)
                    && consumePristineItem(player, Items.SHEARS, TOOLS_TARGET)
                    && consumePristineItem(player, Items.SHIELD, TOOLS_TARGET);
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
                    || (hasPristineItem(player, Items.IRON_PICKAXE, TOOLS_TARGET)
                    && hasPristineItem(player, Items.BUCKET, TOOLS_TARGET)
                    && hasPristineItem(player, Items.SHEARS, TOOLS_TARGET)
                    && hasPristineItem(player, Items.SHIELD, TOOLS_TARGET))) {
                return null;
            }
            return Texts.turnInMissing(
                    Items.IRON_PICKAXE.getDefaultStack().toHoverableText(),
                    countPristineItems(player, Items.IRON_PICKAXE),
                    TOOLS_TARGET,
                    Items.BUCKET.getDefaultStack().toHoverableText(),
                    countPristineItems(player, Items.BUCKET),
                    TOOLS_TARGET,
                    Items.SHEARS.getDefaultStack().toHoverableText(),
                    countPristineItems(player, Items.SHEARS),
                    TOOLS_TARGET,
                    Items.SHIELD.getDefaultStack().toHoverableText(),
                    countPristineItems(player, Items.SHIELD),
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
                    CurrencyService.CROWN + (CurrencyService.SILVERMARK * 4L),
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

        private int forgedPieceCount(ServerWorld world, UUID playerId) {
            int total = 0;
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE));
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_HELM));
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_CHEST));
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_LEGS));
            total += Math.min(1, progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_BOOTS));
            return total;
        }

        private int carriedPieceCount(ServerPlayerEntity player, ServerWorld world) {
            if (player == null || world == null) {
                return 0;
            }
            int total = 0;
            if (findEnchantedItemSlot(player, world, Items.DIAMOND_SWORD, Enchantments.SHARPNESS) >= 0) {
                total++;
            }
            if (findEnchantedItemSlot(player, world, Items.IRON_HELMET, Enchantments.FIRE_PROTECTION) >= 0) {
                total++;
            }
            if (findEnchantedItemSlot(player, world, Items.IRON_CHESTPLATE, Enchantments.PROTECTION) >= 0) {
                total++;
            }
            if (findEnchantedItemSlot(player, world, Items.IRON_LEGGINGS, Enchantments.BLAST_PROTECTION) >= 0) {
                total++;
            }
            if (findEnchantedItemSlot(player, world, Items.IRON_BOOTS, Enchantments.PROJECTILE_PROTECTION) >= 0) {
                total++;
            }
            return total;
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
        public void onServerTick(ServerWorld world, ServerPlayerEntity player) {
            if (bookProgressCount(world, player.getUuid()) >= 5
                    && forgedPieceCount(world, player.getUuid()) >= 5
                    && carriedPieceCount(player, world) >= 5) {
                StoryQuestService.completeIfEligible(world, player);
            }
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
            return List.of(
                    Text.translatable(
                            "quest.village-quest.story.silent_forge.chapter_4.progress.1",
                            bookProgressCount(world, playerId),
                            5
                    ).formatted(Formatting.GRAY),
                    Text.translatable(
                            "quest.village-quest.story.silent_forge.chapter_4.progress.2",
                            progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE),
                            1,
                            progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_HELM),
                            1
                    ).formatted(Formatting.GRAY),
                    Text.translatable(
                            "quest.village-quest.story.silent_forge.chapter_4.progress.3",
                            progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_CHEST),
                            1,
                            progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_LEGS),
                            1
                    ).formatted(Formatting.GRAY),
                    Text.translatable(
                            "quest.village-quest.story.silent_forge.chapter_4.progress.4",
                            progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_BOOTS),
                            1,
                            forgedPieceCount(world, playerId),
                            5
                    ).formatted(Formatting.GRAY),
                    Text.translatable(
                            "quest.village-quest.story.silent_forge.chapter_4.progress.5",
                            player == null ? 0 : carriedPieceCount(player, world),
                            5
                    ).formatted(Formatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            UUID playerId = player.getUuid();
            return progress(world, playerId, StoryQuestKeys.SILENT_FORGE_SHARPNESS_BOOK) >= 1
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_FIRE_PROTECTION_BOOK) >= 1
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_PROTECTION_BOOK) >= 1
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_BLAST_PROTECTION_BOOK) >= 1
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_PROJECTILE_PROTECTION_BOOK) >= 1
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE) >= 1
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_HELM) >= 1
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_CHEST) >= 1
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_LEGS) >= 1
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_BOOTS) >= 1
                    && carriedPieceCount(player, world) >= 5;
        }

        @Override
        public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
            return consumeEnchantedItem(player, world, Items.DIAMOND_SWORD, Enchantments.SHARPNESS)
                    && consumeEnchantedItem(player, world, Items.IRON_HELMET, Enchantments.FIRE_PROTECTION)
                    && consumeEnchantedItem(player, world, Items.IRON_CHESTPLATE, Enchantments.PROTECTION)
                    && consumeEnchantedItem(player, world, Items.IRON_LEGGINGS, Enchantments.BLAST_PROTECTION)
                    && consumeEnchantedItem(player, world, Items.IRON_BOOTS, Enchantments.PROJECTILE_PROTECTION);
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_4.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_4.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_4.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.CROWN * 2L,
                    20,
                    ReputationService.ReputationTrack.CRAFTING,
                    40,
                    VillageProjectType.FORGE_CHARTER
            );
        }

        @Override
        public void onVillagerTrade(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
            if (stack == null || !stack.isOf(Items.ENCHANTED_BOOK)) {
                return;
            }
            UUID playerId = player.getUuid();
            boolean changed = false;
            if (hasEnchantment(world, stack, Enchantments.SHARPNESS) && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_SHARPNESS_BOOK) < 1) {
                StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_SHARPNESS_BOOK, 1);
                changed = true;
            }
            if (hasEnchantment(world, stack, Enchantments.FIRE_PROTECTION) && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_FIRE_PROTECTION_BOOK) < 1) {
                StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_FIRE_PROTECTION_BOOK, 1);
                changed = true;
            }
            if (hasEnchantment(world, stack, Enchantments.PROTECTION) && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_PROTECTION_BOOK) < 1) {
                StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_PROTECTION_BOOK, 1);
                changed = true;
            }
            if (hasEnchantment(world, stack, Enchantments.BLAST_PROTECTION) && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_BLAST_PROTECTION_BOOK) < 1) {
                StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_BLAST_PROTECTION_BOOK, 1);
                changed = true;
            }
            if (hasEnchantment(world, stack, Enchantments.PROJECTILE_PROTECTION) && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_PROJECTILE_PROTECTION_BOOK) < 1) {
                StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_PROJECTILE_PROTECTION_BOOK, 1);
                changed = true;
            }
            if (changed) {
                StoryQuestService.completeIfEligible(world, player);
            }
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
            boolean changed = false;

            if (progress(world, playerId, StoryQuestKeys.SILENT_FORGE_SHARPNESS_BOOK) >= 1
                    && leftInput.isOf(Items.DIAMOND_SWORD)
                    && output.isOf(Items.DIAMOND_SWORD)
                    && hasEnchantment(world, rightInput, Enchantments.SHARPNESS)
                    && hasEnchantment(world, output, Enchantments.SHARPNESS)
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE) < 1) {
                StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE, 1);
                changed = true;
            }
            if (progress(world, playerId, StoryQuestKeys.SILENT_FORGE_FIRE_PROTECTION_BOOK) >= 1
                    && leftInput.isOf(Items.IRON_HELMET)
                    && output.isOf(Items.IRON_HELMET)
                    && hasEnchantment(world, rightInput, Enchantments.FIRE_PROTECTION)
                    && hasEnchantment(world, output, Enchantments.FIRE_PROTECTION)
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_HELM) < 1) {
                StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_HELM, 1);
                changed = true;
            }
            if (progress(world, playerId, StoryQuestKeys.SILENT_FORGE_PROTECTION_BOOK) >= 1
                    && leftInput.isOf(Items.IRON_CHESTPLATE)
                    && output.isOf(Items.IRON_CHESTPLATE)
                    && hasEnchantment(world, rightInput, Enchantments.PROTECTION)
                    && hasEnchantment(world, output, Enchantments.PROTECTION)
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_CHEST) < 1) {
                StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_CHEST, 1);
                changed = true;
            }
            if (progress(world, playerId, StoryQuestKeys.SILENT_FORGE_BLAST_PROTECTION_BOOK) >= 1
                    && leftInput.isOf(Items.IRON_LEGGINGS)
                    && output.isOf(Items.IRON_LEGGINGS)
                    && hasEnchantment(world, rightInput, Enchantments.BLAST_PROTECTION)
                    && hasEnchantment(world, output, Enchantments.BLAST_PROTECTION)
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_LEGS) < 1) {
                StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_LEGS, 1);
                changed = true;
            }
            if (progress(world, playerId, StoryQuestKeys.SILENT_FORGE_PROJECTILE_PROTECTION_BOOK) >= 1
                    && leftInput.isOf(Items.IRON_BOOTS)
                    && output.isOf(Items.IRON_BOOTS)
                    && hasEnchantment(world, rightInput, Enchantments.PROJECTILE_PROTECTION)
                    && hasEnchantment(world, output, Enchantments.PROJECTILE_PROTECTION)
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_BOOTS) < 1) {
                StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_BOOTS, 1);
                changed = true;
            }

            if (changed) {
                StoryQuestService.completeIfEligible(world, player);
            }
        }
    }
}
