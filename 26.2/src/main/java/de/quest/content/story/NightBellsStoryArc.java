package de.quest.content.story;

import de.quest.economy.CurrencyService;
import de.quest.quest.DifficultyObjectiveMode;
import de.quest.quest.DifficultyObjectiveState;
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
        keys.addAll(NightBellsMarkerProgress.keys(chapterIndex));
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
        private static final String PEACEFUL_VILLAGE_BOUND = StoryQuestKeys.NIGHT_BELLS_VILLAGE_BOUND;
        private static final String PEACEFUL_VILLAGE_X = StoryQuestKeys.NIGHT_BELLS_VILLAGE_X;
        private static final String PEACEFUL_VILLAGE_Z = StoryQuestKeys.NIGHT_BELLS_VILLAGE_Z;

        protected abstract int chapterIndex();

        @Override
        public StoryChapterCompletion buildCompletion(ServerLevel world, UUID id) {
            var base=buildCompletion();
            if(mode(world,id)!=DifficultyObjectiveMode.PEACEFUL) return base;
            String prefix="quest.village-quest.story.night_bells.peaceful.complete.";
            return new StoryChapterCompletion(base.title(),Component.translatable(prefix+chapterIndex()+".1"),
                    Component.translatable(prefix+"supplies"),chapterIndex()==3?base.completionLine3():Component.translatable(prefix+"recorded"),
                    base.currencyReward(),base.levels(),base.reputationTrack(),base.reputationAmount(),base.unlockedProject());
        }

        @Override
        public List<Component> descriptionLines(ServerLevel world, UUID id) {
            if (mode(world,id)!=DifficultyObjectiveMode.PEACEFUL) return descriptionLines();
            var result=new ArrayList<Component>();
            result.add(de.quest.quest.QuestRuleText.translate("quest.village-quest.story.night_bells.peaceful.description."+chapterIndex()));
            result.add(Component.translatable("quest.village-quest.story.night_bells.peaceful.protection_help"));
            for(var entry:orderedSupplies()) result.add(Component.translatable("quest.village-quest.story.night_bells.peaceful.supply_help",
                    Component.translatable(entry.getKey().getDescriptionId()),entry.getValue()));
            if(chapterIndex()==3)result.add(Component.translatable("quest.village-quest.story.night_bells.peaceful.final_help"));
            return List.copyOf(result);
        }

        @Override
        public Component shortObjective(ServerLevel world, UUID id, List<Component> lines) {
            if(mode(world,id)!=DifficultyObjectiveMode.PEACEFUL) return de.quest.quest.QuestShortObjective.current(lines);
            if(!NightBellsMarkerProgress.supported(k->progress(world,id,k),chapterIndex()))
                return Component.translatable("quest.village-quest.story.night_bells.peaceful.unsupported");
            if(progress(world,id,PEACEFUL_VILLAGE_BOUND)==0)return Component.translatable("quest.village-quest.story.night_bells.peaceful.bind");
            var objectives=lines.stream().filter(line->line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                    && (text.getKey().contains(".peaceful.progress.") || text.getKey().equals("screen.village-quest.ui_review.objective_row"))).toList();
            var next=de.quest.quest.QuestShortObjective.current(objectives);
            if(next.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                    && text.getKey().equals("quest.village-quest.guidance.claim")) {
                var player=world.getServer().getPlayerList().getPlayer(id);
                if(player==null || !inStoryVillage(world,id,player.blockPosition(),false))
                    return Component.translatable("quest.village-quest.story.night_bells.peaceful.return_village",
                            progress(world,id,PEACEFUL_VILLAGE_X),progress(world,id,PEACEFUL_VILLAGE_Z));
            }
            return next;
        }

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
            if (!bell && !marker && !confirmationPost) return;
            if (!inStoryVillage(world, player.getUUID(), pos, true)) {
                player.sendSystemMessage(Component.translatable("quest.village-quest.story.night_bells.peaceful.wrong_village"),true);
                return;
            }
            if (bell) {
                StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.NIGHT_BELLS_PEACEFUL_BELL, 1);
                if (chapterIndex() == 3) {
                    StoryQuestService.setStoryFlag(world, player.getUUID(), StoryQuestKeys.NIGHT_BELLS_PEACEFUL_CONFIRMED, true);
                }
            }
            if (marker) {
                recordMarker(world, player, pos);
            }
            if (confirmationPost) {
                StoryQuestService.setStoryFlag(world, player.getUUID(), StoryQuestKeys.NIGHT_BELLS_PEACEFUL_CONFIRMED, true);
            }
            StoryQuestService.completeIfEligible(world, player);
        }

        protected boolean peacefulComplete(ServerLevel world, ServerPlayer player) {
            UUID playerId = player.getUUID();
            if(!NightBellsMarkerProgress.supported(k->progress(world,playerId,k),chapterIndex()))return false;
            return NightBellsPeacefulProgress.complete(
                    player.level() == world && inStoryVillage(world, playerId, player.blockPosition(), false),
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
            var lines=new ArrayList<Component>();
            if(!NightBellsMarkerProgress.supported(k->progress(world,playerId,k),chapterIndex())) {
                lines.add(Component.translatable("quest.village-quest.story.night_bells.peaceful.unsupported"));return List.copyOf(lines);
            }
            if(progress(world,playerId,PEACEFUL_VILLAGE_BOUND)==0) {
                lines.add(Component.translatable("quest.village-quest.story.night_bells.peaceful.bind"));
                return List.copyOf(lines);
            }
            if(chapterIndex()==0 || chapterIndex()==3) lines.add(Component.translatable(
                    "quest.village-quest.story.night_bells.peaceful.progress.bell",progress(world,playerId,StoryQuestKeys.NIGHT_BELLS_PEACEFUL_BELL),1));
            lines.add(Component.translatable("quest.village-quest.story.night_bells.peaceful.progress.markers",markerCount(world,playerId),chapterIndex()==3?4:3));
            if(markerCount(world,playerId)<(chapterIndex()==3?4:3)) lines.add(nextSector(world,playerId));
            for(var entry:orderedSupplies())lines.add(Component.translatable("screen.village-quest.ui_review.objective_row",
                    Component.translatable(entry.getKey().getDescriptionId()),Math.min(entry.getValue(),StoryQuestService.countCompletionItem(world,playerId,entry.getKey())),entry.getValue()));
            if(chapterIndex()==3) {
                lines.add(Component.translatable("quest.village-quest.story.night_bells.peaceful.progress.golem",
                        StoryQuestService.hasStoryFlag(world,playerId,StoryQuestKeys.NIGHT_BELLS_PEACEFUL_GUARDIAN)?1:0,1));
                lines.add(Component.translatable("quest.village-quest.story.night_bells.peaceful.progress.confirm",
                        StoryQuestService.hasStoryFlag(world,playerId,StoryQuestKeys.NIGHT_BELLS_PEACEFUL_CONFIRMED)?1:0,1));
            }
            // Keep destination and known exact evidence in full details, after the next action.
            lines.add(Component.translatable("quest.village-quest.story.night_bells.peaceful.destination",
                    progress(world,playerId,PEACEFUL_VILLAGE_X),progress(world,playerId,PEACEFUL_VILLAGE_Z)));
            for(var sector:NightBellsMarkerProgress.exactSectors(k->progress(world,playerId,k),chapterIndex()))
                lines.add(Component.translatable("quest.village-quest.story.night_bells.peaceful.checked",sector.getX(),sector.getX()+7,sector.getZ(),sector.getZ()+7));
            return List.copyOf(lines);
        }

        private List<Map.Entry<Item,Integer>> orderedSupplies() {
            return peacefulSupplies().entrySet().stream().sorted(java.util.Comparator.comparing(entry->entry.getKey().getDescriptionId())).toList();
        }

        private Component nextSector(ServerLevel world,UUID id) {
            var player=world.getServer().getPlayerList().getPlayer(id);
            var village=player==null?null:ShadowsTradeRoadEncounterService.currentVillage(world,player.blockPosition());
            if(village!=null && village.centerX()==progress(world,id,PEACEFUL_VILLAGE_X) && village.centerZ()==progress(world,id,PEACEFUL_VILLAGE_Z)) {
                int startX=Math.floorDiv(player.blockPosition().getX(),8),startZ=Math.floorDiv(player.blockPosition().getZ(),8);
                for(int radius=0;radius<=4;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
                    if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
                    int x=startX+dx,z=startZ+dz;
                    var at=new BlockPos(x*8+4,player.blockPosition().getY(),z*8+4);
                    if(!world.hasChunkAt(at) || !NightBellsPeacefulProgress.inStoryVillage(village,at,true,village.centerX(),village.centerZ())
                            || !NightBellsMarkerProgress.available(k->progress(world,id,k),chapterIndex(),x,z))continue;
                    // Only suggest an entire square inside the known accepted footprint.
                    if(x*8<(long)village.minX()-16 || x*8+7>(long)village.maxX()+16
                            || z*8<(long)village.minZ()-16 || z*8+7>(long)village.maxZ()+16)continue;
                    var candidate=ShadowsTradeRoadEncounterService.currentVillage(world,at);
                    if(!NightBellsPeacefulProgress.inStoryVillage(candidate,at,true,village.centerX(),village.centerZ()))continue;
                    return Component.translatable("quest.village-quest.story.night_bells.peaceful.next_sector",x*8,x*8+7,z*8,z*8+7);
                }
            }
            return Component.translatable("quest.village-quest.story.night_bells.peaceful.return_village",
                    progress(world,id,PEACEFUL_VILLAGE_X),progress(world,id,PEACEFUL_VILLAGE_Z));
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

        private void recordMarker(ServerLevel world, ServerPlayer player, BlockPos pos) {
            UUID playerId = player.getUUID();
            if(!NightBellsMarkerProgress.supported(k->progress(world,playerId,k),chapterIndex())) {
                player.sendSystemMessage(Component.translatable("quest.village-quest.story.night_bells.peaceful.unsupported"),true);return;
            }
            boolean recorded=NightBellsMarkerProgress.record(k->progress(world,playerId,k),
                    (key,value)->StoryQuestService.setQuestIntQuietly(world,playerId,key,value),chapterIndex(),pos,chapterIndex()==3?4:3);
            player.sendSystemMessage(Component.translatable("quest.village-quest.story.night_bells.peaceful."
                    +(recorded?"new_sector":"repeat_sector"),markerCount(world,playerId),chapterIndex()==3?4:3),true);
        }

        private int markerCount(ServerLevel world, UUID playerId) {
            return NightBellsMarkerProgress.count(k->progress(world,playerId,k),chapterIndex());
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
                boolean nearVillage = player.level() == world && inStoryVillage(world, player.getUUID(), player.blockPosition(), false);
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
