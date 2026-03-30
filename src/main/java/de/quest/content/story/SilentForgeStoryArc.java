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
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;

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

        protected void updateCraftProgress(ServerWorld world,
                                           ServerPlayerEntity player,
                                           String baselineKey,
                                           String progressKey,
                                           Item item) {
            int baseline = StoryQuestService.getQuestInt(world, player.getUuid(), baselineKey);
            if (baseline == 0) {
                StoryQuestService.setQuestInt(world, player.getUuid(), baselineKey, DailyQuestService.getCraftedStat(player, item) + 1);
                return;
            }

            int crafted = DailyQuestService.getCraftedStat(player, item);
            int delta = crafted - (baseline - 1);
            int progress = delta > 0 ? 1 : 0;
            if (StoryQuestService.getQuestInt(world, player.getUuid(), progressKey) != progress) {
                StoryQuestService.setQuestInt(world, player.getUuid(), progressKey, progress);
            }
        }

        protected RegistryEntry<Enchantment> sharpness(ServerWorld world) {
            return world.getRegistryManager()
                    .getOrThrow(RegistryKeys.ENCHANTMENT)
                    .getOrThrow(Enchantments.SHARPNESS);
        }

        protected boolean hasSharpness(ServerWorld world, ItemStack stack) {
            return world != null
                    && stack != null
                    && !stack.isEmpty()
                    && EnchantmentHelper.getLevel(sharpness(world), stack) > 0;
        }

        protected int findSharpDiamondSwordSlot(ServerPlayerEntity player, ServerWorld world) {
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack stack = player.getInventory().getStack(i);
                if (stack.isOf(Items.DIAMOND_SWORD) && hasSharpness(world, stack)) {
                    return i;
                }
            }
            return -1;
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
                    progress(world, playerId, StoryQuestKeys.SILENT_FORGE_IRON_ORE),
                    COLD_HEARTH_IRON_TARGET,
                    progress(world, playerId, StoryQuestKeys.SILENT_FORGE_COAL_ORE),
                    COLD_HEARTH_COAL_TARGET
            ).formatted(Formatting.GRAY);
            Text line2 = Text.translatable(
                    "quest.village-quest.story.silent_forge.chapter_1.progress.2",
                    progress(world, playerId, StoryQuestKeys.SILENT_FORGE_DIAMOND_ORE),
                    COLD_HEARTH_DIAMOND_TARGET
            ).formatted(Formatting.GRAY);
            ServerPlayerEntity player = world == null ? null : world.getServer().getPlayerManager().getPlayer(playerId);
            Text blocked = player == null ? null : claimBlockedMessage(world, player);
            return blocked == null ? List.of(line1, line2) : List.of(line1, line2, blocked);
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            UUID playerId = player.getUuid();
            return progress(world, playerId, StoryQuestKeys.SILENT_FORGE_IRON_ORE) >= COLD_HEARTH_IRON_TARGET
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_COAL_ORE) >= COLD_HEARTH_COAL_TARGET
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_DIAMOND_ORE) >= COLD_HEARTH_DIAMOND_TARGET
                    && hasItem(player, Items.RAW_IRON, COLD_HEARTH_IRON_TARGET)
                    && hasItem(player, Items.COAL, COLD_HEARTH_COAL_TARGET)
                    && hasItem(player, Items.DIAMOND, COLD_HEARTH_DIAMOND_TARGET);
        }

        @Override
        public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
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
        public Text claimBlockedMessage(ServerWorld world, ServerPlayerEntity player) {
            if (player == null || world == null) {
                return null;
            }
            UUID playerId = player.getUuid();
            if (progress(world, playerId, StoryQuestKeys.SILENT_FORGE_IRON_ORE) < COLD_HEARTH_IRON_TARGET
                    || progress(world, playerId, StoryQuestKeys.SILENT_FORGE_COAL_ORE) < COLD_HEARTH_COAL_TARGET
                    || progress(world, playerId, StoryQuestKeys.SILENT_FORGE_DIAMOND_ORE) < COLD_HEARTH_DIAMOND_TARGET
                    || (hasItem(player, Items.RAW_IRON, COLD_HEARTH_IRON_TARGET)
                    && hasItem(player, Items.COAL, COLD_HEARTH_COAL_TARGET)
                    && hasItem(player, Items.DIAMOND, COLD_HEARTH_DIAMOND_TARGET))) {
                return null;
            }
            return Texts.turnInMissing(
                    Items.RAW_IRON.getDefaultStack().toHoverableText(),
                    DailyQuestService.countInventoryItem(player, Items.RAW_IRON),
                    COLD_HEARTH_IRON_TARGET,
                    Items.COAL.getDefaultStack().toHoverableText(),
                    DailyQuestService.countInventoryItem(player, Items.COAL),
                    COLD_HEARTH_COAL_TARGET,
                    Items.DIAMOND.getDefaultStack().toHoverableText(),
                    DailyQuestService.countInventoryItem(player, Items.DIAMOND),
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
                    CurrencyService.SILVERMARK * 4L,
                    4,
                    ReputationService.ReputationTrack.CRAFTING,
                    10,
                    null
            );
        }

        @Override
        public void onTrackedItemPickup(ServerWorld world, ServerPlayerEntity player, ItemStack stack, int count) {
            if (stack.isOf(Items.RAW_IRON)) {
                addProgress(world, player, StoryQuestKeys.SILENT_FORGE_IRON_ORE, count, COLD_HEARTH_IRON_TARGET);
            } else if (stack.isOf(Items.COAL)) {
                addProgress(world, player, StoryQuestKeys.SILENT_FORGE_COAL_ORE, count, COLD_HEARTH_COAL_TARGET);
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
            return List.of(
                    Text.translatable(
                            "quest.village-quest.story.silent_forge.chapter_2.progress.1",
                            progress(world, playerId, StoryQuestKeys.SILENT_FORGE_IRON_INGOT),
                            BELLOWS_IRON_TARGET
                    ).formatted(Formatting.GRAY),
                    Text.translatable(
                            "quest.village-quest.story.silent_forge.chapter_2.progress.2",
                            Math.min(blastFurnace, 1),
                            1,
                            Math.min(cauldron, 1),
                            1
                    ).formatted(Formatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            UUID playerId = player.getUuid();
            return progress(world, playerId, StoryQuestKeys.SILENT_FORGE_IRON_INGOT) >= BELLOWS_IRON_TARGET
                    && hasItem(player, Items.BLAST_FURNACE, 1)
                    && hasItem(player, Items.CAULDRON, 1);
        }

        @Override
        public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
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
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_2.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_2.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_2.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.SILVERMARK * 5L,
                    6,
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
            updateCraftProgress(world, player, StoryQuestKeys.SILENT_FORGE_PICKAXE_BASELINE, StoryQuestKeys.SILENT_FORGE_PICKAXE_CRAFTED, Items.IRON_PICKAXE);
            updateCraftProgress(world, player, StoryQuestKeys.SILENT_FORGE_BUCKET_BASELINE, StoryQuestKeys.SILENT_FORGE_BUCKET_CRAFTED, Items.BUCKET);
            updateCraftProgress(world, player, StoryQuestKeys.SILENT_FORGE_SHEARS_BASELINE, StoryQuestKeys.SILENT_FORGE_SHEARS_CRAFTED, Items.SHEARS);
            updateCraftProgress(world, player, StoryQuestKeys.SILENT_FORGE_SHIELD_BASELINE, StoryQuestKeys.SILENT_FORGE_SHIELD_CRAFTED, Items.SHIELD);
            StoryQuestService.completeIfEligible(world, player);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
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
                    Text.translatable(
                            "quest.village-quest.story.silent_forge.chapter_3.progress.1",
                            pickaxeReady,
                            1,
                            bucketReady,
                            1
                    ).formatted(Formatting.GRAY),
                    Text.translatable(
                            "quest.village-quest.story.silent_forge.chapter_3.progress.2",
                            shearsReady,
                            1,
                            shieldReady,
                            1
                    ).formatted(Formatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            UUID playerId = player.getUuid();
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
        public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
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
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_3.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_3.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_3.complete.3").formatted(Formatting.GRAY),
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
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
            int sharpenedBlade = progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE) >= 1 ? 1 : 0;
            int carriedBlade = progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE) >= 1
                    && player != null
                    && findSharpDiamondSwordSlot(player, world) >= 0 ? 1 : 0;
            return List.of(
                    Text.translatable(
                            "quest.village-quest.story.silent_forge.chapter_4.progress.1",
                            progress(world, playerId, StoryQuestKeys.SILENT_FORGE_SHARPNESS_BOOK),
                            1
                    ).formatted(Formatting.GRAY),
                    Text.translatable(
                            "quest.village-quest.story.silent_forge.chapter_4.progress.2",
                            sharpenedBlade,
                            1
                    ).formatted(Formatting.GRAY),
                    Text.translatable(
                            "quest.village-quest.story.silent_forge.chapter_4.progress.3",
                            carriedBlade,
                            1
                    ).formatted(Formatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            UUID playerId = player.getUuid();
            return progress(world, playerId, StoryQuestKeys.SILENT_FORGE_SHARPNESS_BOOK) >= 1
                    && progress(world, playerId, StoryQuestKeys.SILENT_FORGE_MASTER_EDGE) >= 1
                    && findSharpDiamondSwordSlot(player, world) >= 0;
        }

        @Override
        public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
            int slot = findSharpDiamondSwordSlot(player, world);
            if (slot < 0) {
                return false;
            }
            player.getInventory().getStack(slot).decrement(1);
            player.currentScreenHandler.sendContentUpdates();
            return true;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_4.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_4.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.silent_forge.chapter_4.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.CROWN,
                    10,
                    ReputationService.ReputationTrack.CRAFTING,
                    20,
                    VillageProjectType.FORGE_CHARTER
            );
        }

        @Override
        public void onVillagerTrade(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
            if (stack.isOf(Items.ENCHANTED_BOOK) && hasSharpness(world, stack)) {
                StoryQuestService.setQuestInt(world, player.getUuid(), StoryQuestKeys.SILENT_FORGE_SHARPNESS_BOOK, 1);
                StoryQuestService.completeIfEligible(world, player);
            }
        }

        @Override
        public void onAnvilOutput(ServerWorld world,
                                  ServerPlayerEntity player,
                                  ItemStack leftInput,
                                  ItemStack rightInput,
                                  ItemStack output) {
            if (progress(world, player.getUuid(), StoryQuestKeys.SILENT_FORGE_SHARPNESS_BOOK) < 1) {
                return;
            }
            if (leftInput == null || rightInput == null || output == null) {
                return;
            }
            if (!leftInput.isOf(Items.DIAMOND_SWORD) || !rightInput.isOf(Items.ENCHANTED_BOOK) || !output.isOf(Items.DIAMOND_SWORD)) {
                return;
            }
            if (!hasSharpness(world, rightInput) || !hasSharpness(world, output)) {
                return;
            }
            StoryQuestService.setQuestInt(world, player.getUuid(), StoryQuestKeys.SILENT_FORGE_MASTER_EDGE, 1);
            StoryQuestService.completeIfEligible(world, player);
        }
    }
}
