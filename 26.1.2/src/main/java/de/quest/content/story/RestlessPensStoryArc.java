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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class RestlessPensStoryArc implements StoryArcDefinition {
    private static final int EMPTY_TROUGHS_BREED_TARGET = 20;
    private static final int EMPTY_TROUGHS_HAY_TARGET = 64;
    private static final int WOOL_BEFORE_WEATHER_SHEAR_TARGET = 32;
    private static final int WOOL_BEFORE_WEATHER_WOOL_TARGET = 64;
    private static final int NEW_PASTURES_RIDE_TARGET_CM = 100_000;
    private static final int NEW_PASTURES_RIDE_TARGET_BLOCKS = 1_000;
    private static final int SHEPHERDS_CALL_TARGET = 1;
    private static final int SHEPHERDS_CALL_ANIMAL_TARGET = 10;
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

        protected boolean isPastureAnimal(Entity entity) {
            if (!(entity instanceof Animal animal)) {
                return false;
            }
            return animal instanceof Sheep
                    || animal instanceof Cow
                    || animal instanceof Pig
                    || animal instanceof Chicken
                    || animal instanceof Goat
                    || animal instanceof AbstractHorse
                    || animal instanceof Llama;
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
                    CurrencyService.SILVERMARK * 8L,
                    8,
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
            Component line1 = Component.translatable(
                    "quest.village-quest.story.restless_pens.chapter_2.progress.1",
                    progress(world, playerId, StoryQuestKeys.RESTLESS_PENS_SHEARS),
                    WOOL_BEFORE_WEATHER_SHEAR_TARGET
            ).withStyle(ChatFormatting.GRAY);
            Component line2 = Component.translatable(
                    "quest.village-quest.story.restless_pens.chapter_2.progress.2",
                    woolReady,
                    WOOL_BEFORE_WEATHER_WOOL_TARGET
            ).withStyle(ChatFormatting.GRAY);
            Component blocked = player == null ? null : claimBlockedMessage(world, player);
            return blocked == null ? List.of(line1, line2) : List.of(line1, line2, blocked);
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
        public Component claimBlockedMessage(ServerLevel world, ServerPlayer player) {
            if (player == null || world == null
                    || progress(world, player.getUUID(), StoryQuestKeys.RESTLESS_PENS_SHEARS) < WOOL_BEFORE_WEATHER_SHEAR_TARGET
                    || hasTotalWool(player, WOOL_BEFORE_WEATHER_WOOL_TARGET)) {
                return null;
            }
            return Texts.turnInMissing(
                    Component.translatable("text.village-quest.turnin.label.wool"),
                    countTotalWool(player),
                    WOOL_BEFORE_WEATHER_WOOL_TARGET
            );
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.restless_pens.chapter_2.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.restless_pens.chapter_2.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.restless_pens.chapter_2.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.CROWN,
                    10,
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

    private static final class NewPasturesChapter extends RestlessPensChapter {
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
        public void onAccepted(ServerLevel world, ServerPlayer player) {
            StoryQuestService.setQuestInt(
                    world,
                    player.getUUID(),
                    StoryQuestKeys.RESTLESS_PENS_RIDE_BASELINE,
                    DailyQuestService.getCustomStat(player, Stats.HORSE_ONE_CM) + 1
            );
        }

        @Override
        public void onServerTick(ServerLevel world, ServerPlayer player) {
            int baseline = StoryQuestService.getQuestInt(world, player.getUUID(), StoryQuestKeys.RESTLESS_PENS_RIDE_BASELINE);
            int ridden = DailyQuestService.getCustomStat(player, Stats.HORSE_ONE_CM);
            if (baseline == 0) {
                StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.RESTLESS_PENS_RIDE_BASELINE, ridden + 1);
                return;
            }

            int delta = Math.max(0, ridden - (baseline - 1));
            int progress = Math.min(NEW_PASTURES_RIDE_TARGET_CM, delta);
            if (progress != StoryQuestService.getQuestInt(world, player.getUUID(), StoryQuestKeys.RESTLESS_PENS_RIDE)) {
                StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.RESTLESS_PENS_RIDE, progress);
                StoryQuestService.completeIfEligible(world, player);
            }
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            return List.of(
                    Component.translatable(
                            "quest.village-quest.story.restless_pens.chapter_3.progress",
                            progress(world, playerId, StoryQuestKeys.RESTLESS_PENS_RIDE) / 100,
                            NEW_PASTURES_RIDE_TARGET_BLOCKS
                    ).withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            return progress(world, player.getUUID(), StoryQuestKeys.RESTLESS_PENS_RIDE) >= NEW_PASTURES_RIDE_TARGET_CM;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.restless_pens.chapter_3.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.restless_pens.chapter_3.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.restless_pens.chapter_3.complete.3").withStyle(ChatFormatting.GRAY),
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
        public void onServerTick(ServerLevel world, ServerPlayer player) {
            if (progress(world, player.getUUID(), StoryQuestKeys.RESTLESS_PENS_CALL) >= SHEPHERDS_CALL_TARGET
                    && hasItem(player, Items.DIAMOND_HORSE_ARMOR, 1)) {
                StoryQuestService.completeIfEligible(world, player);
            }
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
            int horseArmorReady = player != null && hasItem(player, Items.DIAMOND_HORSE_ARMOR, 1) ? 1 : 0;
            return List.of(
                    Component.translatable(
                            "quest.village-quest.story.restless_pens.chapter_4.progress.1",
                            progress(world, playerId, StoryQuestKeys.RESTLESS_PENS_CALL),
                            SHEPHERDS_CALL_TARGET
                    ).withStyle(ChatFormatting.GRAY),
                    Component.translatable(
                            "quest.village-quest.story.restless_pens.chapter_4.progress.2",
                            horseArmorReady,
                            1
                    ).withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            return progress(world, player.getUUID(), StoryQuestKeys.RESTLESS_PENS_CALL) >= SHEPHERDS_CALL_TARGET
                    && hasItem(player, Items.DIAMOND_HORSE_ARMOR, 1);
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.restless_pens.chapter_4.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.restless_pens.chapter_4.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.restless_pens.chapter_4.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.CROWN * 2L,
                    20,
                    ReputationService.ReputationTrack.ANIMALS,
                    40,
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
