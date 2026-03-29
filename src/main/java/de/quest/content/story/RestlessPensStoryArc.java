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
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.GoatEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.village.VillagerProfession;

import java.util.List;
import java.util.UUID;

public final class RestlessPensStoryArc implements StoryArcDefinition {
    private static final int EMPTY_TROUGHS_BREED_TARGET = 4;
    private static final int EMPTY_TROUGHS_HAY_TARGET = 2;
    private static final int WOOL_BEFORE_WEATHER_SHEAR_TARGET = 4;
    private static final int WOOL_BEFORE_WEATHER_WOOL_TARGET = 12;
    private static final int WORDS_AT_THE_PENS_TARGET = 3;
    private static final int SHEPHERDS_CALL_TARGET = 1;
    private static final int SHEPHERDS_CALL_ANIMAL_TARGET = 6;
    private static final Item[] WOOL_ITEMS = new Item[] {
            Items.WHITE_WOOL, Items.LIGHT_GRAY_WOOL, Items.GRAY_WOOL, Items.BLACK_WOOL,
            Items.BROWN_WOOL, Items.RED_WOOL, Items.ORANGE_WOOL, Items.YELLOW_WOOL,
            Items.LIME_WOOL, Items.GREEN_WOOL, Items.CYAN_WOOL, Items.LIGHT_BLUE_WOOL,
            Items.BLUE_WOOL, Items.PURPLE_WOOL, Items.MAGENTA_WOOL, Items.PINK_WOOL
    };

    private final List<StoryChapterDefinition> chapters = List.of(
            new EmptyTroughsChapter(),
            new WoolBeforeWeatherChapter(),
            new WordsAtThePensChapter(),
            new ShepherdsCallChapter()
    );

    @Override
    public StoryArcType type() {
        return StoryArcType.RESTLESS_PENS;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.story.restless_pens.title");
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
        return StoryQuestService.isCompleted(world, playerId, StoryArcType.MARKET_ROAD_TROUBLES);
    }

    private abstract static class RestlessPensChapter implements StoryChapterDefinition {
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

        protected boolean hasTotalWool(ServerPlayerEntity player, int amount) {
            return countTotalWool(player) >= amount;
        }

        protected boolean consumeTotalWool(ServerPlayerEntity player, int amount) {
            int remaining = amount;
            for (Item wool : WOOL_ITEMS) {
                int count = DailyQuestService.countInventoryItem(player, wool);
                if (count <= 0) {
                    continue;
                }
                int remove = Math.min(remaining, count);
                if (!DailyQuestService.consumeInventoryItem(player, wool, remove)) {
                    return false;
                }
                remaining -= remove;
                if (remaining <= 0) {
                    return true;
                }
            }
            return remaining <= 0;
        }

        protected int countTotalWool(ServerPlayerEntity player) {
            int total = 0;
            for (Item wool : WOOL_ITEMS) {
                total += DailyQuestService.countInventoryItem(player, wool);
            }
            return total;
        }

        protected boolean isAnimalVillager(Entity entity) {
            if (!(entity instanceof VillagerEntity villager) || villager.isBaby()) {
                return false;
            }
            RegistryEntry<VillagerProfession> profession = villager.getVillagerData().profession();
            return profession.matchesKey(VillagerProfession.FARMER)
                    || profession.matchesKey(VillagerProfession.SHEPHERD)
                    || profession.matchesKey(VillagerProfession.BUTCHER)
                    || profession.matchesKey(VillagerProfession.LEATHERWORKER);
        }

        protected boolean isPastureAnimal(Entity entity) {
            if (!(entity instanceof AnimalEntity animal) || animal.isBaby()) {
                return false;
            }
            return animal instanceof SheepEntity
                    || animal instanceof CowEntity
                    || animal instanceof PigEntity
                    || animal instanceof ChickenEntity
                    || animal instanceof GoatEntity;
        }
    }

    private static final class EmptyTroughsChapter extends RestlessPensChapter {
        @Override
        public Text title() {
            return Text.translatable("quest.village-quest.story.restless_pens.chapter_1.title");
        }

        @Override
        public Text offerParagraph1() {
            return Text.translatable("quest.village-quest.story.restless_pens.chapter_1.offer.1").formatted(Formatting.GRAY);
        }

        @Override
        public Text offerParagraph2() {
            return Text.translatable("quest.village-quest.story.restless_pens.chapter_1.offer.2").formatted(Formatting.GRAY);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
            int hayReady = player == null ? 0 : DailyQuestService.countInventoryItem(player, Items.HAY_BLOCK);
            return List.of(
                    Text.translatable(
                            "quest.village-quest.story.restless_pens.chapter_1.progress.1",
                            progress(world, playerId, StoryQuestKeys.RESTLESS_PENS_BREEDS),
                            EMPTY_TROUGHS_BREED_TARGET
                    ).formatted(Formatting.GRAY),
                    Text.translatable(
                            "quest.village-quest.story.restless_pens.chapter_1.progress.2",
                            hayReady,
                            EMPTY_TROUGHS_HAY_TARGET
                    ).formatted(Formatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            return progress(world, player.getUuid(), StoryQuestKeys.RESTLESS_PENS_BREEDS) >= EMPTY_TROUGHS_BREED_TARGET
                    && hasItem(player, Items.HAY_BLOCK, EMPTY_TROUGHS_HAY_TARGET);
        }

        @Override
        public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
            return isComplete(world, player) && consumeItem(player, Items.HAY_BLOCK, EMPTY_TROUGHS_HAY_TARGET);
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.restless_pens.chapter_1.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.restless_pens.chapter_1.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.restless_pens.chapter_1.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.SILVERMARK * 4L,
                    4,
                    ReputationService.ReputationTrack.ANIMALS,
                    10,
                    null
            );
        }

        @Override
        public void onAnimalLove(ServerWorld world, ServerPlayerEntity player, AnimalEntity animal) {
            addProgress(world, player, StoryQuestKeys.RESTLESS_PENS_BREEDS, 1, EMPTY_TROUGHS_BREED_TARGET);
        }
    }

    private static final class WoolBeforeWeatherChapter extends RestlessPensChapter {
        @Override
        public Text title() {
            return Text.translatable("quest.village-quest.story.restless_pens.chapter_2.title");
        }

        @Override
        public Text offerParagraph1() {
            return Text.translatable("quest.village-quest.story.restless_pens.chapter_2.offer.1").formatted(Formatting.GRAY);
        }

        @Override
        public Text offerParagraph2() {
            return Text.translatable("quest.village-quest.story.restless_pens.chapter_2.offer.2").formatted(Formatting.GRAY);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
            int woolReady = player == null ? 0 : countTotalWool(player);
            return List.of(
                    Text.translatable(
                            "quest.village-quest.story.restless_pens.chapter_2.progress.1",
                            progress(world, playerId, StoryQuestKeys.RESTLESS_PENS_SHEARS),
                            WOOL_BEFORE_WEATHER_SHEAR_TARGET
                    ).formatted(Formatting.GRAY),
                    Text.translatable(
                            "quest.village-quest.story.restless_pens.chapter_2.progress.2",
                            woolReady,
                            WOOL_BEFORE_WEATHER_WOOL_TARGET
                    ).formatted(Formatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            return progress(world, player.getUuid(), StoryQuestKeys.RESTLESS_PENS_SHEARS) >= WOOL_BEFORE_WEATHER_SHEAR_TARGET
                    && hasTotalWool(player, WOOL_BEFORE_WEATHER_WOOL_TARGET);
        }

        @Override
        public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
            return isComplete(world, player) && consumeTotalWool(player, WOOL_BEFORE_WEATHER_WOOL_TARGET);
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.restless_pens.chapter_2.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.restless_pens.chapter_2.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.restless_pens.chapter_2.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.SILVERMARK * 5L,
                    6,
                    ReputationService.ReputationTrack.ANIMALS,
                    12,
                    null
            );
        }

        @Override
        public void onEntityUse(ServerWorld world, ServerPlayerEntity player, Entity entity, ItemStack inHand) {
            if (!(entity instanceof SheepEntity sheep) || inHand == null || !inHand.isOf(Items.SHEARS) || !sheep.isShearable()) {
                return;
            }
            addProgress(world, player, StoryQuestKeys.RESTLESS_PENS_SHEARS, 1, WOOL_BEFORE_WEATHER_SHEAR_TARGET);
        }
    }

    private static final class WordsAtThePensChapter extends RestlessPensChapter {
        @Override
        public Text title() {
            return Text.translatable("quest.village-quest.story.restless_pens.chapter_3.title");
        }

        @Override
        public Text offerParagraph1() {
            return Text.translatable("quest.village-quest.story.restless_pens.chapter_3.offer.1").formatted(Formatting.GRAY);
        }

        @Override
        public Text offerParagraph2() {
            return Text.translatable("quest.village-quest.story.restless_pens.chapter_3.offer.2").formatted(Formatting.GRAY);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            return List.of(
                    Text.translatable(
                            "quest.village-quest.story.restless_pens.chapter_3.progress",
                            progress(world, playerId, StoryQuestKeys.RESTLESS_PENS_VOICES),
                            WORDS_AT_THE_PENS_TARGET
                    ).formatted(Formatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            return progress(world, player.getUuid(), StoryQuestKeys.RESTLESS_PENS_VOICES) >= WORDS_AT_THE_PENS_TARGET;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.restless_pens.chapter_3.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.restless_pens.chapter_3.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.restless_pens.chapter_3.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.SILVERMARK * 6L,
                    8,
                    ReputationService.ReputationTrack.ANIMALS,
                    15,
                    null
            );
        }

        @Override
        public void onEntityUse(ServerWorld world, ServerPlayerEntity player, Entity entity, ItemStack inHand) {
            if (!isAnimalVillager(entity)) {
                return;
            }
            String heardFlag = StoryQuestKeys.RESTLESS_PENS_VOICE_PREFIX + entity.getUuidAsString();
            if (StoryQuestService.hasStoryFlag(world, player.getUuid(), heardFlag)) {
                return;
            }
            StoryQuestService.setStoryFlag(world, player.getUuid(), heardFlag, true);
            int before = progress(world, player.getUuid(), StoryQuestKeys.RESTLESS_PENS_VOICES);
            addProgress(world, player, StoryQuestKeys.RESTLESS_PENS_VOICES, 1, WORDS_AT_THE_PENS_TARGET);
            if (before < WORDS_AT_THE_PENS_TARGET && progress(world, player.getUuid(), StoryQuestKeys.RESTLESS_PENS_VOICES) >= WORDS_AT_THE_PENS_TARGET) {
                player.sendMessage(Text.translatable("message.village-quest.story.restless_pens.yard_settles").formatted(Formatting.GREEN), false);
            }
        }
    }

    private static final class ShepherdsCallChapter extends RestlessPensChapter {
        @Override
        public Text title() {
            return Text.translatable("quest.village-quest.story.restless_pens.chapter_4.title");
        }

        @Override
        public Text offerParagraph1() {
            return Text.translatable("quest.village-quest.story.restless_pens.chapter_4.offer.1").formatted(Formatting.GRAY);
        }

        @Override
        public Text offerParagraph2() {
            return Text.translatable("quest.village-quest.story.restless_pens.chapter_4.offer.2").formatted(Formatting.GRAY);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            return List.of(
                    Text.translatable(
                            "quest.village-quest.story.restless_pens.chapter_4.progress",
                            progress(world, playerId, StoryQuestKeys.RESTLESS_PENS_CALL),
                            SHEPHERDS_CALL_TARGET
                    ).formatted(Formatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            return progress(world, player.getUuid(), StoryQuestKeys.RESTLESS_PENS_CALL) >= SHEPHERDS_CALL_TARGET;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.restless_pens.chapter_4.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.restless_pens.chapter_4.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.restless_pens.chapter_4.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.CROWN,
                    10,
                    ReputationService.ReputationTrack.ANIMALS,
                    20,
                    VillageProjectType.PASTURE_CHARTER
            );
        }

        @Override
        public void onUseBlock(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state, ItemStack inHand) {
            if (state == null || !state.isOf(Blocks.BELL) || progress(world, player.getUuid(), StoryQuestKeys.RESTLESS_PENS_CALL) >= SHEPHERDS_CALL_TARGET) {
                return;
            }
            int animalsNearby = world.getEntitiesByClass(
                    AnimalEntity.class,
                    new Box(pos).expand(12.0),
                    this::isPastureAnimal
            ).size();
            if (animalsNearby < SHEPHERDS_CALL_ANIMAL_TARGET) {
                player.sendMessage(Text.translatable(
                        "message.village-quest.story.restless_pens.call_too_thin",
                        animalsNearby,
                        SHEPHERDS_CALL_ANIMAL_TARGET
                ).formatted(Formatting.GRAY), true);
                return;
            }
            StoryQuestService.setQuestInt(world, player.getUuid(), StoryQuestKeys.RESTLESS_PENS_CALL, SHEPHERDS_CALL_TARGET);
            StoryQuestService.completeIfEligible(world, player);
        }
    }
}
