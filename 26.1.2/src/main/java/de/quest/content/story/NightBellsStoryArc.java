package de.quest.content.story;

import de.quest.economy.CurrencyService;
import de.quest.quest.story.StoryArcDefinition;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryChapterCompletion;
import de.quest.quest.story.StoryChapterDefinition;
import de.quest.quest.story.StoryQuestKeys;
import de.quest.quest.story.StoryQuestService;
import de.quest.quest.story.VillageProjectType;
import de.quest.reputation.ReputationService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;
import java.util.UUID;

public final class NightBellsStoryArc implements StoryArcDefinition {
    private static final int FIRST_WATCH_ZOMBIE_TARGET = 7;
    private static final int THIN_THE_DARK_SKELETON_TARGET = 5;
    private static final int THIN_THE_DARK_SPIDER_TARGET = 3;
    private static final int HOLD_THE_ROAD_CREEPER_TARGET = 3;
    private static final int HOLD_THE_ROAD_HOSTILE_TARGET = 11;

    private final List<StoryChapterDefinition> chapters = List.of(
            new FirstWatchChapter(),
            new ThinTheDarkChapter(),
            new HoldTheRoadChapter(),
            new DawnAfterRaidChapter()
    );

    @Override
    public StoryArcType type() {
        return StoryArcType.NIGHT_BELLS;
    }

    @Override
    public Component title() {
        return Component.translatable("quest.village-quest.story.night_bells.title");
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
        return StoryQuestService.isCompleted(world, playerId, StoryArcType.RESTLESS_PENS);
    }

    private abstract static class NightBellsChapter implements StoryChapterDefinition {
        protected void addProgress(ServerLevel world, ServerPlayer player, String key, int amount, int target) {
            StoryQuestService.addQuestIntClamped(world, player.getUUID(), key, amount, target);
            StoryQuestService.completeIfEligible(world, player);
        }

        protected int progress(ServerLevel world, UUID playerId, String key) {
            return StoryQuestService.getQuestInt(world, playerId, key);
        }

        protected boolean isNight(ServerLevel world) {
            if (world == null) {
                return false;
            }
            long dayTime = Math.floorMod(world.getOverworldClockTime(), 24000L);
            return dayTime >= 13000L && dayTime <= 23000L;
        }
    }

    private static final class FirstWatchChapter extends NightBellsChapter {
        @Override
        public Component title() {
            return Component.translatable("quest.village-quest.story.night_bells.chapter_1.title");
        }

        @Override
        public Component offerParagraph1() {
            return Component.translatable("quest.village-quest.story.night_bells.chapter_1.offer.1").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public Component offerParagraph2() {
            return Component.translatable("quest.village-quest.story.night_bells.chapter_1.offer.2").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            return List.of(
                    Component.translatable(
                            "quest.village-quest.story.night_bells.chapter_1.progress",
                            progress(world, playerId, StoryQuestKeys.NIGHT_BELLS_ZOMBIES),
                            FIRST_WATCH_ZOMBIE_TARGET
                    ).withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            return progress(world, player.getUUID(), StoryQuestKeys.NIGHT_BELLS_ZOMBIES) >= FIRST_WATCH_ZOMBIE_TARGET;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.night_bells.chapter_1.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.night_bells.chapter_1.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.night_bells.chapter_1.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.SILVERMARK * 4L,
                    4,
                    ReputationService.ReputationTrack.MONSTER_HUNTING,
                    10,
                    null
            );
        }

        @Override
        public void onMonsterKill(ServerLevel world, ServerPlayer player, Entity killedEntity) {
            if (!isNight(world) || !(killedEntity instanceof Zombie)) {
                return;
            }
            addProgress(world, player, StoryQuestKeys.NIGHT_BELLS_ZOMBIES, 1, FIRST_WATCH_ZOMBIE_TARGET);
        }
    }

    private static final class ThinTheDarkChapter extends NightBellsChapter {
        @Override
        public Component title() {
            return Component.translatable("quest.village-quest.story.night_bells.chapter_2.title");
        }

        @Override
        public Component offerParagraph1() {
            return Component.translatable("quest.village-quest.story.night_bells.chapter_2.offer.1").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public Component offerParagraph2() {
            return Component.translatable("quest.village-quest.story.night_bells.chapter_2.offer.2").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            return List.of(
                    Component.translatable(
                            "quest.village-quest.story.night_bells.chapter_2.progress",
                            progress(world, playerId, StoryQuestKeys.NIGHT_BELLS_SKELETONS),
                            THIN_THE_DARK_SKELETON_TARGET,
                            progress(world, playerId, StoryQuestKeys.NIGHT_BELLS_SPIDERS),
                            THIN_THE_DARK_SPIDER_TARGET
                    ).withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            UUID playerId = player.getUUID();
            return progress(world, playerId, StoryQuestKeys.NIGHT_BELLS_SKELETONS) >= THIN_THE_DARK_SKELETON_TARGET
                    && progress(world, playerId, StoryQuestKeys.NIGHT_BELLS_SPIDERS) >= THIN_THE_DARK_SPIDER_TARGET;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.night_bells.chapter_2.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.night_bells.chapter_2.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.night_bells.chapter_2.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.SILVERMARK * 5L,
                    6,
                    ReputationService.ReputationTrack.MONSTER_HUNTING,
                    12,
                    null
            );
        }

        @Override
        public void onMonsterKill(ServerLevel world, ServerPlayer player, Entity killedEntity) {
            if (!isNight(world)) {
                return;
            }
            if (killedEntity instanceof Skeleton) {
                addProgress(world, player, StoryQuestKeys.NIGHT_BELLS_SKELETONS, 1, THIN_THE_DARK_SKELETON_TARGET);
            } else if (killedEntity instanceof Spider) {
                addProgress(world, player, StoryQuestKeys.NIGHT_BELLS_SPIDERS, 1, THIN_THE_DARK_SPIDER_TARGET);
            }
        }
    }

    private static final class HoldTheRoadChapter extends NightBellsChapter {
        @Override
        public Component title() {
            return Component.translatable("quest.village-quest.story.night_bells.chapter_3.title");
        }

        @Override
        public Component offerParagraph1() {
            return Component.translatable("quest.village-quest.story.night_bells.chapter_3.offer.1").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public Component offerParagraph2() {
            return Component.translatable("quest.village-quest.story.night_bells.chapter_3.offer.2").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            return List.of(
                    Component.translatable(
                            "quest.village-quest.story.night_bells.chapter_3.progress",
                            progress(world, playerId, StoryQuestKeys.NIGHT_BELLS_CREEPERS),
                            HOLD_THE_ROAD_CREEPER_TARGET,
                            progress(world, playerId, StoryQuestKeys.NIGHT_BELLS_HOSTILES),
                            HOLD_THE_ROAD_HOSTILE_TARGET
                    ).withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            UUID playerId = player.getUUID();
            return progress(world, playerId, StoryQuestKeys.NIGHT_BELLS_CREEPERS) >= HOLD_THE_ROAD_CREEPER_TARGET
                    && progress(world, playerId, StoryQuestKeys.NIGHT_BELLS_HOSTILES) >= HOLD_THE_ROAD_HOSTILE_TARGET;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.night_bells.chapter_3.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.night_bells.chapter_3.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.night_bells.chapter_3.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.SILVERMARK * 7L,
                    8,
                    ReputationService.ReputationTrack.MONSTER_HUNTING,
                    15,
                    null
            );
        }

        @Override
        public void onMonsterKill(ServerLevel world, ServerPlayer player, Entity killedEntity) {
            if (!isNight(world) || !(killedEntity instanceof Enemy)) {
                return;
            }
            addProgress(world, player, StoryQuestKeys.NIGHT_BELLS_HOSTILES, 1, HOLD_THE_ROAD_HOSTILE_TARGET);
            if (killedEntity instanceof Creeper) {
                addProgress(world, player, StoryQuestKeys.NIGHT_BELLS_CREEPERS, 1, HOLD_THE_ROAD_CREEPER_TARGET);
            }
        }
    }

    private static final class DawnAfterRaidChapter extends NightBellsChapter {
        @Override
        public Component title() {
            return Component.translatable("quest.village-quest.story.night_bells.chapter_4.title");
        }

        @Override
        public Component offerParagraph1() {
            return Component.translatable("quest.village-quest.story.night_bells.chapter_4.offer.1").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public Component offerParagraph2() {
            return Component.translatable("quest.village-quest.story.night_bells.chapter_4.offer.2").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public void onAccepted(ServerLevel world, ServerPlayer player) {
            StoryQuestService.setStoryFlag(world, player.getUUID(), StoryQuestKeys.NIGHT_BELLS_RAID_WAIT_FOR_FRESH, player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE));
        }

        @Override
        public void onServerTick(ServerLevel world, ServerPlayer player) {
            boolean hasHero = player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE);
            boolean waitForFresh = StoryQuestService.hasStoryFlag(world, player.getUUID(), StoryQuestKeys.NIGHT_BELLS_RAID_WAIT_FOR_FRESH);
            if (waitForFresh) {
                if (!hasHero) {
                    StoryQuestService.setStoryFlag(world, player.getUUID(), StoryQuestKeys.NIGHT_BELLS_RAID_WAIT_FOR_FRESH, false);
                }
                return;
            }
            if (!hasHero || progress(world, player.getUUID(), StoryQuestKeys.NIGHT_BELLS_RAID_WON) >= 1) {
                return;
            }
            StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.NIGHT_BELLS_RAID_WON, 1);
            StoryQuestService.completeIfEligible(world, player);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            if (StoryQuestService.hasStoryFlag(world, playerId, StoryQuestKeys.NIGHT_BELLS_RAID_WAIT_FOR_FRESH)) {
                return List.of(Component.translatable("quest.village-quest.story.night_bells.chapter_4.progress_wait").withStyle(ChatFormatting.GRAY));
            }
            return List.of(
                    Component.translatable(
                            "quest.village-quest.story.night_bells.chapter_4.progress",
                            progress(world, playerId, StoryQuestKeys.NIGHT_BELLS_RAID_WON),
                            1
                    ).withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            return progress(world, player.getUUID(), StoryQuestKeys.NIGHT_BELLS_RAID_WON) >= 1;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.night_bells.chapter_4.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.night_bells.chapter_4.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.night_bells.chapter_4.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.CROWN,
                    10,
                    ReputationService.ReputationTrack.MONSTER_HUNTING,
                    20,
                    VillageProjectType.WATCH_BELL
            );
        }
    }
}
