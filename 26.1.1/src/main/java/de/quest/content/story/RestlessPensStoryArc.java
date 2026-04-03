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
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

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
    public Component title() {
        return Component.translatable("quest.village-quest.story.restless_pens.title");
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
        return StoryQuestService.isCompleted(world, playerId, StoryArcType.MARKET_ROAD_TROUBLES);
    }

    private abstract static class RestlessPensChapter implements StoryChapterDefinition {
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

        protected boolean hasTotalWool(ServerPlayer player, int amount) {
            return countTotalWool(player) >= amount;
        }

        protected boolean consumeTotalWool(ServerPlayer player, int amount) {
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

        protected int countTotalWool(ServerPlayer player) {
            int total = 0;
            for (Item wool : WOOL_ITEMS) {
                total += DailyQuestService.countInventoryItem(player, wool);
            }
            return total;
        }

        protected boolean isAnimalVillager(Entity entity) {
            if (!(entity instanceof Villager villager) || villager.isBaby()) {
                return false;
            }
            Holder<VillagerProfession> profession = villager.getVillagerData().profession();
            return profession.is(VillagerProfession.FARMER)
                    || profession.is(VillagerProfession.SHEPHERD)
                    || profession.is(VillagerProfession.BUTCHER)
                    || profession.is(VillagerProfession.LEATHERWORKER);
        }

        protected boolean isPastureAnimal(Entity entity) {
            if (!(entity instanceof Animal animal) || animal.isBaby()) {
                return false;
            }
            return animal instanceof Sheep
                    || animal instanceof Cow
                    || animal instanceof Pig
                    || animal instanceof Chicken
                    || animal instanceof Goat;
        }
    }

    private static final class EmptyTroughsChapter extends RestlessPensChapter {
        @Override
        public Component title() {
            return Component.translatable("quest.village-quest.story.restless_pens.chapter_1.title");
        }

        @Override
        public Component offerParagraph1() {
            return Component.translatable("quest.village-quest.story.restless_pens.chapter_1.offer.1").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public Component offerParagraph2() {
            return Component.translatable("quest.village-quest.story.restless_pens.chapter_1.offer.2").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
            int hayReady = player == null ? 0 : DailyQuestService.countInventoryItem(player, Items.HAY_BLOCK);
            return List.of(
                    Component.translatable(
                            "quest.village-quest.story.restless_pens.chapter_1.progress.1",
                            progress(world, playerId, StoryQuestKeys.RESTLESS_PENS_BREEDS),
                            EMPTY_TROUGHS_BREED_TARGET
                    ).withStyle(ChatFormatting.GRAY),
                    Component.translatable(
                            "quest.village-quest.story.restless_pens.chapter_1.progress.2",
                            hayReady,
                            EMPTY_TROUGHS_HAY_TARGET
                    ).withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            return progress(world, player.getUUID(), StoryQuestKeys.RESTLESS_PENS_BREEDS) >= EMPTY_TROUGHS_BREED_TARGET
                    && hasItem(player, Items.HAY_BLOCK, EMPTY_TROUGHS_HAY_TARGET);
        }

        @Override
        public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
            return isComplete(world, player) && consumeItem(player, Items.HAY_BLOCK, EMPTY_TROUGHS_HAY_TARGET);
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.restless_pens.chapter_1.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.restless_pens.chapter_1.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.restless_pens.chapter_1.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.SILVERMARK * 4L,
                    4,
                    ReputationService.ReputationTrack.ANIMALS,
                    10,
                    null
            );
        }

        @Override
        public void onAnimalLove(ServerLevel world, ServerPlayer player, Animal animal) {
            addProgress(world, player, StoryQuestKeys.RESTLESS_PENS_BREEDS, 1, EMPTY_TROUGHS_BREED_TARGET);
        }
    }

    private static final class WoolBeforeWeatherChapter extends RestlessPensChapter {
        @Override
        public Component title() {
            return Component.translatable("quest.village-quest.story.restless_pens.chapter_2.title");
        }

        @Override
        public Component offerParagraph1() {
            return Component.translatable("quest.village-quest.story.restless_pens.chapter_2.offer.1").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public Component offerParagraph2() {
            return Component.translatable("quest.village-quest.story.restless_pens.chapter_2.offer.2").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
            int woolReady = player == null ? 0 : countTotalWool(player);
            return List.of(
                    Component.translatable(
                            "quest.village-quest.story.restless_pens.chapter_2.progress.1",
                            progress(world, playerId, StoryQuestKeys.RESTLESS_PENS_SHEARS),
                            WOOL_BEFORE_WEATHER_SHEAR_TARGET
                    ).withStyle(ChatFormatting.GRAY),
                    Component.translatable(
                            "quest.village-quest.story.restless_pens.chapter_2.progress.2",
                            woolReady,
                            WOOL_BEFORE_WEATHER_WOOL_TARGET
                    ).withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            return progress(world, player.getUUID(), StoryQuestKeys.RESTLESS_PENS_SHEARS) >= WOOL_BEFORE_WEATHER_SHEAR_TARGET
                    && hasTotalWool(player, WOOL_BEFORE_WEATHER_WOOL_TARGET);
        }

        @Override
        public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
            return isComplete(world, player) && consumeTotalWool(player, WOOL_BEFORE_WEATHER_WOOL_TARGET);
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.restless_pens.chapter_2.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.restless_pens.chapter_2.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.restless_pens.chapter_2.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.SILVERMARK * 5L,
                    6,
                    ReputationService.ReputationTrack.ANIMALS,
                    12,
                    null
            );
        }

        @Override
        public void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack inHand) {
            if (!(entity instanceof Sheep sheep) || inHand == null || !inHand.is(Items.SHEARS) || !sheep.readyForShearing()) {
                return;
            }
            addProgress(world, player, StoryQuestKeys.RESTLESS_PENS_SHEARS, 1, WOOL_BEFORE_WEATHER_SHEAR_TARGET);
        }
    }

    private static final class WordsAtThePensChapter extends RestlessPensChapter {
        @Override
        public Component title() {
            return Component.translatable("quest.village-quest.story.restless_pens.chapter_3.title");
        }

        @Override
        public Component offerParagraph1() {
            return Component.translatable("quest.village-quest.story.restless_pens.chapter_3.offer.1").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public Component offerParagraph2() {
            return Component.translatable("quest.village-quest.story.restless_pens.chapter_3.offer.2").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            return List.of(
                    Component.translatable(
                            "quest.village-quest.story.restless_pens.chapter_3.progress",
                            progress(world, playerId, StoryQuestKeys.RESTLESS_PENS_VOICES),
                            WORDS_AT_THE_PENS_TARGET
                    ).withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            return progress(world, player.getUUID(), StoryQuestKeys.RESTLESS_PENS_VOICES) >= WORDS_AT_THE_PENS_TARGET;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.restless_pens.chapter_3.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.restless_pens.chapter_3.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.restless_pens.chapter_3.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.SILVERMARK * 6L,
                    8,
                    ReputationService.ReputationTrack.ANIMALS,
                    15,
                    null
            );
        }

        @Override
        public void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack inHand) {
            if (!isAnimalVillager(entity)) {
                return;
            }
            String heardFlag = StoryQuestKeys.RESTLESS_PENS_VOICE_PREFIX + entity.getStringUUID();
            if (StoryQuestService.hasStoryFlag(world, player.getUUID(), heardFlag)) {
                return;
            }
            StoryQuestService.setStoryFlag(world, player.getUUID(), heardFlag, true);
            int before = progress(world, player.getUUID(), StoryQuestKeys.RESTLESS_PENS_VOICES);
            addProgress(world, player, StoryQuestKeys.RESTLESS_PENS_VOICES, 1, WORDS_AT_THE_PENS_TARGET);
            if (before < WORDS_AT_THE_PENS_TARGET && progress(world, player.getUUID(), StoryQuestKeys.RESTLESS_PENS_VOICES) >= WORDS_AT_THE_PENS_TARGET) {
                player.sendSystemMessage(Component.translatable("message.village-quest.story.restless_pens.yard_settles").withStyle(ChatFormatting.GREEN), false);
            }
        }
    }

    private static final class ShepherdsCallChapter extends RestlessPensChapter {
        @Override
        public Component title() {
            return Component.translatable("quest.village-quest.story.restless_pens.chapter_4.title");
        }

        @Override
        public Component offerParagraph1() {
            return Component.translatable("quest.village-quest.story.restless_pens.chapter_4.offer.1").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public Component offerParagraph2() {
            return Component.translatable("quest.village-quest.story.restless_pens.chapter_4.offer.2").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            return List.of(
                    Component.translatable(
                            "quest.village-quest.story.restless_pens.chapter_4.progress",
                            progress(world, playerId, StoryQuestKeys.RESTLESS_PENS_CALL),
                            SHEPHERDS_CALL_TARGET
                    ).withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            return progress(world, player.getUUID(), StoryQuestKeys.RESTLESS_PENS_CALL) >= SHEPHERDS_CALL_TARGET;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.restless_pens.chapter_4.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.restless_pens.chapter_4.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.restless_pens.chapter_4.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.CROWN,
                    10,
                    ReputationService.ReputationTrack.ANIMALS,
                    20,
                    VillageProjectType.PASTURE_CHARTER
            );
        }

        @Override
        public void onUseBlock(ServerLevel world, ServerPlayer player, BlockPos pos, BlockState state, ItemStack inHand) {
            if (state == null || !state.is(Blocks.BELL) || progress(world, player.getUUID(), StoryQuestKeys.RESTLESS_PENS_CALL) >= SHEPHERDS_CALL_TARGET) {
                return;
            }
            int animalsNearby = world.getEntitiesOfClass(
                    Animal.class,
                    new AABB(pos).inflate(12.0),
                    this::isPastureAnimal
            ).size();
            if (animalsNearby < SHEPHERDS_CALL_ANIMAL_TARGET) {
                player.sendSystemMessage(Component.translatable(
                        "message.village-quest.story.restless_pens.call_too_thin",
                        animalsNearby,
                        SHEPHERDS_CALL_ANIMAL_TARGET
                ).withStyle(ChatFormatting.GRAY), true);
                return;
            }
            StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.RESTLESS_PENS_CALL, SHEPHERDS_CALL_TARGET);
            StoryQuestService.completeIfEligible(world, player);
        }
    }
}
