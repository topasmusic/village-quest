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
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.GoatEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.UUID;

public final class RestlessPensStoryArc implements StoryArcDefinition {
    private static final int EMPTY_TROUGHS_BREED_TARGET = 17;
    private static final int EMPTY_TROUGHS_HAY_TARGET = 53;
    private static final int WOOL_BEFORE_WEATHER_SHEAR_TARGET = 29;
    private static final int WOOL_BEFORE_WEATHER_WOOL_TARGET = 57;
    private static final int NEW_PASTURES_RIDE_TARGET_CM = 93_000;
    private static final int NEW_PASTURES_RIDE_TARGET_BLOCKS = 930;
    private static final int SHEPHERDS_CALL_TARGET = 1;
    private static final int SHEPHERDS_CALL_ANIMAL_TARGET = 9;
    private static final Item[] WOOL_ITEMS = new Item[] {
            Items.WHITE_WOOL, Items.LIGHT_GRAY_WOOL, Items.GRAY_WOOL, Items.BLACK_WOOL,
            Items.BROWN_WOOL, Items.RED_WOOL, Items.ORANGE_WOOL, Items.YELLOW_WOOL,
            Items.LIME_WOOL, Items.GREEN_WOOL, Items.CYAN_WOOL, Items.LIGHT_BLUE_WOOL,
            Items.BLUE_WOOL, Items.PURPLE_WOOL, Items.MAGENTA_WOOL, Items.PINK_WOOL
    };

    private final List<StoryChapterDefinition> chapters = List.of(
            new EmptyTroughsChapter(),
            new WoolBeforeWeatherChapter(),
            new NewPasturesChapter(),
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

    private static boolean isWoolStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (Item wool : WOOL_ITEMS) {
            if (stack.isOf(wool)) {
                return true;
            }
        }
        return false;
    }

    private abstract static class RestlessPensChapter implements StoryChapterDefinition {
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

        protected boolean hasTotalWool(ServerWorld world, ServerPlayerEntity player, int amount) {
            return player != null && countTotalWool(world, player.getUuid()) >= amount;
        }

        protected boolean consumeTotalWool(ServerWorld world, ServerPlayerEntity player, int amount) {
            return player != null && StoryQuestService.consumeMatchingCompletionItems(world, player.getUuid(), RestlessPensStoryArc::isWoolStack, amount);
        }

        protected int countTotalWool(ServerWorld world, UUID playerId) {
            return StoryQuestService.countMatchingCompletionItems(world, playerId, RestlessPensStoryArc::isWoolStack);
        }

        protected boolean isPastureAnimal(Entity entity) {
            if (!(entity instanceof AnimalEntity animal)) {
                return false;
            }
            return animal instanceof SheepEntity
                    || animal instanceof CowEntity
                    || animal instanceof PigEntity
                    || animal instanceof ChickenEntity
                    || animal instanceof GoatEntity
                    || animal instanceof AbstractHorseEntity
                    || animal instanceof LlamaEntity;
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
            int hayReady = StoryQuestService.countCompletionItem(world, playerId, Items.HAY_BLOCK);
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
                    && hasItem(world, player, Items.HAY_BLOCK, EMPTY_TROUGHS_HAY_TARGET);
        }

        @Override
        public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
            return isComplete(world, player) && consumeItem(world, player, Items.HAY_BLOCK, EMPTY_TROUGHS_HAY_TARGET);
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.restless_pens.chapter_1.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.restless_pens.chapter_1.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.restless_pens.chapter_1.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.SILVERMARK * 8L,
                    8,
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
            int woolReady = countTotalWool(world, playerId);
            Text line1 = Text.translatable(
                    "quest.village-quest.story.restless_pens.chapter_2.progress.1",
                    progress(world, playerId, StoryQuestKeys.RESTLESS_PENS_SHEARS),
                    WOOL_BEFORE_WEATHER_SHEAR_TARGET
            ).formatted(Formatting.GRAY);
            Text line2 = Text.translatable(
                    "quest.village-quest.story.restless_pens.chapter_2.progress.2",
                    woolReady,
                    WOOL_BEFORE_WEATHER_WOOL_TARGET
            ).formatted(Formatting.GRAY);
            Text blocked = player == null ? null : claimBlockedMessage(world, player);
            return blocked == null ? List.of(line1, line2) : List.of(line1, line2, blocked);
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            return progress(world, player.getUuid(), StoryQuestKeys.RESTLESS_PENS_SHEARS) >= WOOL_BEFORE_WEATHER_SHEAR_TARGET
                    && hasTotalWool(world, player, WOOL_BEFORE_WEATHER_WOOL_TARGET);
        }

        @Override
        public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
            return isComplete(world, player) && consumeTotalWool(world, player, WOOL_BEFORE_WEATHER_WOOL_TARGET);
        }

        @Override
        public Text claimBlockedMessage(ServerWorld world, ServerPlayerEntity player) {
            if (player == null || world == null
                    || progress(world, player.getUuid(), StoryQuestKeys.RESTLESS_PENS_SHEARS) < WOOL_BEFORE_WEATHER_SHEAR_TARGET
                    || hasTotalWool(world, player, WOOL_BEFORE_WEATHER_WOOL_TARGET)) {
                return null;
            }
            return Texts.turnInMissing(
                    Text.translatable("text.village-quest.turnin.label.wool"),
                    countTotalWool(world, player.getUuid()),
                    WOOL_BEFORE_WEATHER_WOOL_TARGET
            );
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.restless_pens.chapter_2.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.restless_pens.chapter_2.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.restless_pens.chapter_2.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.CROWN,
                    10,
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

    private static final class NewPasturesChapter extends RestlessPensChapter {
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
        public void onAccepted(ServerWorld world, ServerPlayerEntity player) {
            StoryQuestService.setQuestInt(
                    world,
                    player.getUuid(),
                    StoryQuestKeys.RESTLESS_PENS_RIDE_BASELINE,
                    DailyQuestService.getCustomStat(player, Stats.HORSE_ONE_CM) + 1
            );
        }

        @Override
        public void onServerTick(ServerWorld world, ServerPlayerEntity player) {
            int baseline = StoryQuestService.getQuestInt(world, player.getUuid(), StoryQuestKeys.RESTLESS_PENS_RIDE_BASELINE);
            int ridden = DailyQuestService.getCustomStat(player, Stats.HORSE_ONE_CM);
            if (baseline == 0) {
                StoryQuestService.setQuestInt(world, player.getUuid(), StoryQuestKeys.RESTLESS_PENS_RIDE_BASELINE, ridden + 1);
                return;
            }

            int delta = ridden - (baseline - 1);
            if (delta > 0) {
                StoryQuestService.addQuestIntClamped(world, player.getUuid(), StoryQuestKeys.RESTLESS_PENS_RIDE, delta, NEW_PASTURES_RIDE_TARGET_CM);
                StoryQuestService.completeIfEligible(world, player);
            }
            StoryQuestService.setQuestInt(world, player.getUuid(), StoryQuestKeys.RESTLESS_PENS_RIDE_BASELINE, ridden + 1);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            return List.of(
                    Text.translatable(
                            "quest.village-quest.story.restless_pens.chapter_3.progress",
                            progress(world, playerId, StoryQuestKeys.RESTLESS_PENS_RIDE) / 100,
                            NEW_PASTURES_RIDE_TARGET_BLOCKS
                    ).formatted(Formatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            return progress(world, player.getUuid(), StoryQuestKeys.RESTLESS_PENS_RIDE) >= NEW_PASTURES_RIDE_TARGET_CM;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.restless_pens.chapter_3.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.restless_pens.chapter_3.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.restless_pens.chapter_3.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.CROWN + (CurrencyService.SILVERMARK * 4L),
                    12,
                    ReputationService.ReputationTrack.ANIMALS,
                    15,
                    null
            );
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
        public void onServerTick(ServerWorld world, ServerPlayerEntity player) {
            if (progress(world, player.getUuid(), StoryQuestKeys.RESTLESS_PENS_CALL) >= SHEPHERDS_CALL_TARGET
                    && hasItem(world, player, Items.DIAMOND_HORSE_ARMOR, 1)) {
                StoryQuestService.completeIfEligible(world, player);
            }
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
            int horseArmorReady = player != null && hasItem(world, player, Items.DIAMOND_HORSE_ARMOR, 1) ? 1 : 0;
            return List.of(
                    Text.translatable(
                            "quest.village-quest.story.restless_pens.chapter_4.progress.1",
                            progress(world, playerId, StoryQuestKeys.RESTLESS_PENS_CALL),
                            SHEPHERDS_CALL_TARGET
                    ).formatted(Formatting.GRAY),
                    Text.translatable(
                            "quest.village-quest.story.restless_pens.chapter_4.progress.2",
                            horseArmorReady,
                            1
                    ).formatted(Formatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            return progress(world, player.getUuid(), StoryQuestKeys.RESTLESS_PENS_CALL) >= SHEPHERDS_CALL_TARGET
                    && hasItem(world, player, Items.DIAMOND_HORSE_ARMOR, 1);
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.restless_pens.chapter_4.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.restless_pens.chapter_4.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.restless_pens.chapter_4.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.CROWN * 2L,
                    20,
                    ReputationService.ReputationTrack.ANIMALS,
                    40,
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
