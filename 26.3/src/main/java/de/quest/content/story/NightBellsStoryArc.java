package de.quest.content.story;

import de.quest.economy.CurrencyService;
import de.quest.quest.DifficultyObjectiveMode;
import de.quest.quest.DifficultyObjectiveState;
import de.quest.quest.DistinctObjectiveProgress;
import de.quest.quest.story.StoryArcDefinition;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryChapterCompletion;
import de.quest.quest.story.StoryChapterDefinition;
import de.quest.quest.story.StoryQuestKeys;
import de.quest.quest.story.StoryQuestService;
import de.quest.quest.story.VillageProjectType;
import de.quest.reputation.ReputationService;
import de.quest.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

public final class NightBellsStoryArc implements StoryArcDefinition {
    private static final int FIRST_WATCH_ZOMBIE_TARGET = 7;
    private static final int THIN_THE_DARK_SKELETON_TARGET = 5;
    private static final int THIN_THE_DARK_SPIDER_TARGET = 3;
    private static final int HOLD_THE_ROAD_CREEPER_TARGET = 3;
    private static final int HOLD_THE_ROAD_HOSTILE_TARGET = 11;

    static List<String> modeSpecificIntKeys(int chapterIndex) {
        List<String> keys = new ArrayList<>();
        switch (chapterIndex) {
            case 0 -> keys.add(StoryQuestKeys.NIGHT_BELLS_ZOMBIES);
            case 1 -> {
                keys.add(StoryQuestKeys.NIGHT_BELLS_SKELETONS);
                keys.add(StoryQuestKeys.NIGHT_BELLS_SPIDERS);
            }
            case 2 -> {
                keys.add(StoryQuestKeys.NIGHT_BELLS_CREEPERS);
                keys.add(StoryQuestKeys.NIGHT_BELLS_HOSTILES);
            }
            case 3 -> keys.add(StoryQuestKeys.NIGHT_BELLS_RAID_WON);
            default -> { return List.of(); }
        }
        keys.add(StoryQuestKeys.NIGHT_BELLS_PEACEFUL_BELL);
        for (int i = 0; i < 4; i++) {
            keys.add(StoryQuestKeys.NIGHT_BELLS_PEACEFUL_MARKER_PREFIX + chapterIndex + "_" + i);
        }
        return List.copyOf(keys);
    }

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
        private static final int PEACEFUL_MARKER_SLOTS = 4;
        private static final String PEACEFUL_VILLAGE_BOUND = StoryQuestKeys.NIGHT_BELLS_VILLAGE_BOUND;
        private static final String PEACEFUL_VILLAGE_X = StoryQuestKeys.NIGHT_BELLS_VILLAGE_X;
        private static final String PEACEFUL_VILLAGE_Z = StoryQuestKeys.NIGHT_BELLS_VILLAGE_Z;

        protected abstract int chapterIndex();

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

        protected DifficultyObjectiveMode mode(ServerLevel world, UUID playerId) {
            DifficultyObjectiveMode stored = DifficultyObjectiveMode.fromSerializedId(
                    progress(world, playerId, modeKey()));
            return stored == null ? DifficultyObjectiveMode.forDifficulty(world.getDifficulty()) : stored;
        }

        @Override
        public final void onAccepted(ServerLevel world, ServerPlayer player) {
            initializeMode(world, player, false);
            bindVillage(world, player.getUUID(), ShadowsTradeRoadEncounterService.currentVillage(
                    world, player.blockPosition()));
            onModeAccepted(world, player, mode(world, player.getUUID()));
        }

        @Override
        public final void onServerTick(ServerLevel world, ServerPlayer player) {
            DifficultyObjectiveState.Transition transition = initializeMode(world, player, true);
            onModeTick(world, player, transition.mode());
        }

        protected void onModeAccepted(ServerLevel world, ServerPlayer player, DifficultyObjectiveMode mode) {}

        protected void onModeTick(ServerLevel world, ServerPlayer player, DifficultyObjectiveMode mode) {}

        @Override
        public final void onUseBlock(ServerLevel world,
                                     ServerPlayer player,
                                     BlockPos pos,
                                     BlockState state,
                                     ItemStack inHand) {
            if (mode(world, player.getUUID()) != DifficultyObjectiveMode.PEACEFUL || state == null || pos == null) {
                return;
            }
            boolean bell = state.is(Blocks.BELL);
            boolean marker = isProtectionMarker(state);
            boolean confirmationPost = chapterIndex() == 3
                    && ModBlocks.GUILD_NOTICE_POST != null && state.is(ModBlocks.GUILD_NOTICE_POST);
            if ((!bell && !marker && !confirmationPost)
                    || !inStoryVillage(world, player.getUUID(), pos, true)) {
                return;
            }
            if (bell) {
                StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.NIGHT_BELLS_PEACEFUL_BELL, 1);
                if (chapterIndex() == 3) {
                    StoryQuestService.setStoryFlag(world, player.getUUID(), StoryQuestKeys.NIGHT_BELLS_PEACEFUL_CONFIRMED, true);
                }
            }
            if (marker) {
                recordMarker(world, player, markerToken(pos));
            }
            if (confirmationPost) {
                StoryQuestService.setStoryFlag(world, player.getUUID(), StoryQuestKeys.NIGHT_BELLS_PEACEFUL_CONFIRMED, true);
            }
            StoryQuestService.completeIfEligible(world, player);
        }

        protected boolean peacefulComplete(ServerLevel world, ServerPlayer player) {
            UUID playerId = player.getUUID();
            return NightBellsPeacefulProgress.complete(
                    inStoryVillage(world, playerId, player.blockPosition(), false),
                    chapterIndex(),
                    progress(world, playerId, StoryQuestKeys.NIGHT_BELLS_PEACEFUL_BELL),
                    markerCount(world, playerId),
                    peacefulSuppliesReady(world, playerId),
                    StoryQuestService.hasStoryFlag(world, playerId, StoryQuestKeys.NIGHT_BELLS_PEACEFUL_GUARDIAN),
                    StoryQuestService.hasStoryFlag(world, playerId, StoryQuestKeys.NIGHT_BELLS_PEACEFUL_CONFIRMED)
            );
        }

        @Override
        public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
            if (mode(world, player.getUUID()) != DifficultyObjectiveMode.PEACEFUL) {
                return true;
            }
            return StoryQuestService.consumeCompletionItems(world, player.getUUID(), peacefulSupplies());
        }

        @Override
        public Component claimBlockedMessage(ServerLevel world, ServerPlayer player) {
            return mode(world, player.getUUID()) == DifficultyObjectiveMode.PEACEFUL
                    ? Component.translatable("message.village-quest.story.night_bells.peaceful.incomplete")
                    : null;
        }

        protected Map<Item, Integer> peacefulSupplies() {
            return switch (chapterIndex()) {
                case 0 -> Map.of(Items.TORCH, 16);
                case 1 -> Map.of(Items.OAK_FENCE, 16);
                case 2 -> Map.of(Items.BREAD, 16);
                case 3 -> Map.of(Items.LANTERN, 8, Items.SHIELD, 1);
                default -> Map.of();
            };
        }

        protected List<Component> peacefulProgressLines(ServerLevel world, UUID playerId) {
            return List.of(Component.translatable(
                    peacefulProgressKey(),
                    progress(world, playerId, StoryQuestKeys.NIGHT_BELLS_PEACEFUL_BELL),
                    chapterIndex() == 0 || chapterIndex() == 3 ? 1 : 0,
                    markerCount(world, playerId),
                    chapterIndex() == 3 ? 4 : 3,
                    peacefulSupplyCount(world, playerId),
                    peacefulSupplyTarget()
            ).withStyle(ChatFormatting.GRAY));
        }

        private String peacefulProgressKey() {
            return switch (chapterIndex()) {
                case 0 -> "quest.village-quest.story.night_bells.chapter_1.progress_peaceful";
                case 1 -> "quest.village-quest.story.night_bells.chapter_2.progress_peaceful";
                case 2 -> "quest.village-quest.story.night_bells.chapter_3.progress_peaceful";
                case 3 -> "quest.village-quest.story.night_bells.chapter_4.progress_peaceful";
                default -> throw new IllegalStateException("Unexpected Night Bells chapter: " + chapterIndex());
            };
        }

        private DifficultyObjectiveState.Transition initializeMode(ServerLevel world,
                                                                    ServerPlayer player,
                                                                    boolean notifySwitch) {
            UUID playerId = player.getUUID();
            DifficultyObjectiveState.Transition transition = DifficultyObjectiveState.transition(
                    progress(world, playerId, modeKey()), world.getDifficulty());
            if (transition.switched()) {
                clearCurrentObjectiveProgress(world, playerId);
            }
            if (transition.initialized() || transition.switched()) {
                StoryQuestService.setQuestIntQuietly(world, playerId, modeKey(), transition.persistedValue());
            }
            if (transition.switched()) {
                onModeAccepted(world, player, transition.mode());
                if (notifySwitch) {
                    player.sendSystemMessage(Component.translatable(
                            "message.village-quest.difficulty_objective.switched",
                            Component.translatable(transition.mode() == DifficultyObjectiveMode.PEACEFUL
                                    ? "message.village-quest.difficulty_objective.mode.peaceful"
                                    : "message.village-quest.difficulty_objective.mode.combat")
                    ).withStyle(ChatFormatting.GOLD), false);
                }
            }
            return transition;
        }

        private String modeKey() {
            return StoryQuestKeys.NIGHT_BELLS_MODE_PREFIX + chapterIndex();
        }

        private void clearCurrentObjectiveProgress(ServerLevel world, UUID playerId) {
            for (String key : modeSpecificIntKeys(chapterIndex())) {
                StoryQuestService.setQuestIntQuietly(world, playerId, key, 0);
            }
            StoryQuestService.setStoryFlag(world, playerId, StoryQuestKeys.NIGHT_BELLS_RAID_WAIT_FOR_FRESH, false);
            StoryQuestService.setStoryFlag(world, playerId, StoryQuestKeys.NIGHT_BELLS_PEACEFUL_GUARDIAN, false);
            StoryQuestService.setStoryFlag(world, playerId, StoryQuestKeys.NIGHT_BELLS_PEACEFUL_CONFIRMED, false);
        }

        private boolean peacefulSuppliesReady(ServerLevel world, UUID playerId) {
            for (Map.Entry<Item, Integer> requirement : peacefulSupplies().entrySet()) {
                if (StoryQuestService.countCompletionItem(world, playerId, requirement.getKey()) < requirement.getValue()) {
                    return false;
                }
            }
            return !peacefulSupplies().isEmpty();
        }

        private int peacefulSupplyCount(ServerLevel world, UUID playerId) {
            int count = 0;
            for (Map.Entry<Item, Integer> requirement : peacefulSupplies().entrySet()) {
                count += Math.min(requirement.getValue(), StoryQuestService.countCompletionItem(world, playerId, requirement.getKey()));
            }
            return count;
        }

        private int peacefulSupplyTarget() {
            return peacefulSupplies().values().stream().mapToInt(Integer::intValue).sum();
        }

        private void recordMarker(ServerLevel world, ServerPlayer player, int token) {
            UUID playerId = player.getUUID();
            int[] slots = new int[PEACEFUL_MARKER_SLOTS];
            for (int i = 0; i < slots.length; i++) {
                slots[i] = progress(world, playerId, markerKey(i));
            }
            int[] updated = DistinctObjectiveProgress.record(slots, token);
            for (int i = 0; i < updated.length; i++) {
                if (slots[i] != updated[i]) {
                    StoryQuestService.setQuestInt(world, playerId, markerKey(i), updated[i]);
                }
            }
        }

        private int markerCount(ServerLevel world, UUID playerId) {
            int count = 0;
            for (int i = 0; i < PEACEFUL_MARKER_SLOTS; i++) {
                if (progress(world, playerId, markerKey(i)) != 0) {
                    count++;
                }
            }
            return count;
        }

        private String markerKey(int slot) {
            return StoryQuestKeys.NIGHT_BELLS_PEACEFUL_MARKER_PREFIX + chapterIndex() + "_" + slot;
        }

        private static int markerToken(BlockPos pos) {
            int sectorX = Math.floorDiv(pos.getX(), 8);
            int sectorZ = Math.floorDiv(pos.getZ(), 8);
            return 31 * sectorX + sectorZ;
        }

        private static boolean isProtectionMarker(BlockState state) {
            return state.is(Blocks.LANTERN)
                    || state.is(Blocks.SOUL_LANTERN)
                    || state.is(Blocks.CAMPFIRE)
                    || state.is(Blocks.SOUL_CAMPFIRE)
                    || state.getBlock() instanceof FenceGateBlock;
        }

        protected boolean inStoryVillage(ServerLevel world, UUID playerId, BlockPos actionPos,
                                         boolean bindIfMissing) {
            ShadowsTradeRoadEncounterService.VillageMarker village =
                    ShadowsTradeRoadEncounterService.currentVillage(world, actionPos);
            if (bindIfMissing && progress(world, playerId, PEACEFUL_VILLAGE_BOUND) == 0) {
                bindVillage(world, playerId, village);
            }
            return NightBellsPeacefulProgress.inStoryVillage(village, actionPos,
                    progress(world, playerId, PEACEFUL_VILLAGE_BOUND) != 0,
                    progress(world, playerId, PEACEFUL_VILLAGE_X),
                    progress(world, playerId, PEACEFUL_VILLAGE_Z));
        }

        private void bindVillage(ServerLevel world, UUID playerId,
                                 ShadowsTradeRoadEncounterService.VillageMarker village) {
            if (village == null || progress(world, playerId, PEACEFUL_VILLAGE_BOUND) != 0) return;
            StoryQuestService.setQuestIntQuietly(world, playerId, PEACEFUL_VILLAGE_X, village.centerX());
            StoryQuestService.setQuestIntQuietly(world, playerId, PEACEFUL_VILLAGE_Z, village.centerZ());
            StoryQuestService.setQuestIntQuietly(world, playerId, PEACEFUL_VILLAGE_BOUND, 1);
        }
    }

    private static final class FirstWatchChapter extends NightBellsChapter {
        @Override protected int chapterIndex() { return 0; }
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
            if (mode(world, playerId) == DifficultyObjectiveMode.PEACEFUL) return peacefulProgressLines(world, playerId);
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
            if (mode(world, player.getUUID()) == DifficultyObjectiveMode.PEACEFUL) return peacefulComplete(world, player);
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
        @Override protected int chapterIndex() { return 1; }
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
            if (mode(world, playerId) == DifficultyObjectiveMode.PEACEFUL) return peacefulProgressLines(world, playerId);
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
            if (mode(world, player.getUUID()) == DifficultyObjectiveMode.PEACEFUL) return peacefulComplete(world, player);
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
        @Override protected int chapterIndex() { return 2; }
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
            if (mode(world, playerId) == DifficultyObjectiveMode.PEACEFUL) return peacefulProgressLines(world, playerId);
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
            if (mode(world, player.getUUID()) == DifficultyObjectiveMode.PEACEFUL) return peacefulComplete(world, player);
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
        @Override protected int chapterIndex() { return 3; }
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
        protected void onModeAccepted(ServerLevel world, ServerPlayer player, DifficultyObjectiveMode mode) {
            if (mode == DifficultyObjectiveMode.COMBAT) {
                StoryQuestService.setStoryFlag(world, player.getUUID(), StoryQuestKeys.NIGHT_BELLS_RAID_WAIT_FOR_FRESH, player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE));
            }
        }

        @Override
        protected void onModeTick(ServerLevel world, ServerPlayer player, DifficultyObjectiveMode mode) {
            if (mode == DifficultyObjectiveMode.PEACEFUL) {
                boolean nearVillage = inStoryVillage(world, player.getUUID(), player.blockPosition(), false);
                boolean guardian = nearVillage && !world.getEntitiesOfClass(
                        IronGolem.class, player.getBoundingBox().inflate(48.0D), golem ->
                                golem.isAlive() && inStoryVillage(world, player.getUUID(),
                                        golem.blockPosition(), false)).isEmpty();
                StoryQuestService.setStoryFlag(world, player.getUUID(), StoryQuestKeys.NIGHT_BELLS_PEACEFUL_GUARDIAN, guardian);
                StoryQuestService.completeIfEligible(world, player);
                return;
            }
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
            if (mode(world, playerId) == DifficultyObjectiveMode.PEACEFUL) return peacefulProgressLines(world, playerId);
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
            if (mode(world, player.getUUID()) == DifficultyObjectiveMode.PEACEFUL) return peacefulComplete(world, player);
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
