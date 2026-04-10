package de.quest.questmaster;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.economy.CurrencyService;
import de.quest.entity.QuestMasterEntity;
import de.quest.network.Payloads;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestGenerator;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.special.ApiaristSmokerQuestService;
import de.quest.quest.special.MerchantSealQuestService;
import de.quest.quest.special.RelicQuestProgressionService;
import de.quest.quest.special.RelicQuestStage;
import de.quest.quest.special.ShardRelicQuestService;
import de.quest.quest.special.ShardRelicQuestStage;
import de.quest.quest.special.ShepherdFluteQuestService;
import de.quest.quest.special.SpecialQuestKind;
import de.quest.quest.special.SurveyorCompassQuestService;
import de.quest.quest.story.StoryArcDefinition;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryChapterCompletion;
import de.quest.quest.story.StoryChapterDefinition;
import de.quest.quest.story.StoryQuestService;
import de.quest.quest.story.VillageProjectService;
import de.quest.quest.story.VillageProjectType;
import de.quest.quest.weekly.WeeklyQuestCompletion;
import de.quest.quest.weekly.WeeklyQuestDefinition;
import de.quest.quest.weekly.WeeklyQuestGenerator;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.reputation.ReputationService;
import de.quest.registry.ModItems;
import de.quest.util.TimeUtil;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class QuestMasterUiService {
    private static final String CATEGORY_DAILY = "daily";
    private static final String CATEGORY_WEEKLY = "weekly";
    private static final String CATEGORY_STORY = "story";
    private static final String CATEGORY_SPECIAL = "special";

    private static final String ENTRY_DAILY_MAIN = "daily_main";
    private static final String ENTRY_DAILY_BONUS = "daily_bonus";
    private static final String ENTRY_WEEKLY = "weekly_main";
    private static final String ENTRY_STORY_PREFIX = "story_";
    private static final String ENTRY_SPECIAL_INTRO = "special_intro";
    private static final String ENTRY_SPECIAL_SHARD = "special_shard";
    private static final String ENTRY_SPECIAL_MERCHANT = "special_merchant";
    private static final String ENTRY_SPECIAL_FLUTE = "special_flute";
    private static final String ENTRY_SPECIAL_SMOKER = "special_smoker";
    private static final String ENTRY_SPECIAL_COMPASS = "special_compass";

    private static final ConcurrentMap<UUID, Integer> OPEN_SESSIONS = new ConcurrentHashMap<>();

    private record ActionSpec(int action, Component label, boolean enabled) {
        private static final ActionSpec NONE = new ActionSpec(
                Payloads.QuestMasterActionPayload.ACTION_NONE,
                Component.empty(),
                false
        );
    }

    private QuestMasterUiService() {}

    public static void open(ServerLevel world, ServerPlayer player, QuestMasterEntity questMaster) {
        if (world == null || player == null || questMaster == null) {
            return;
        }
        OPEN_SESSIONS.put(player.getUUID(), questMaster.getId());
        sendPayload(player, buildPayload(Payloads.QuestMasterPayload.ACTION_OPEN, world, player, questMaster));
    }

    public static void refreshIfOpen(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return;
        }
        QuestMasterEntity questMaster = resolveOpenQuestMaster(world, player);
        if (questMaster == null) {
            close(player);
            return;
        }
        sendPayload(player, buildPayload(Payloads.QuestMasterPayload.ACTION_UPDATE, world, player, questMaster));
    }

    public static void refreshIfOpen(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return;
        }
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) {
            refreshIfOpen(world, player);
        }
    }

    public static boolean isOpen(UUID playerId) {
        return playerId != null && OPEN_SESSIONS.containsKey(playerId);
    }

    public static void close(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Integer entityId = OPEN_SESSIONS.remove(player.getUUID());
        if (entityId == null) {
            return;
        }
        sendPayload(player, new Payloads.QuestMasterPayload(
                Payloads.QuestMasterPayload.ACTION_CLOSE,
                entityId,
                Component.empty(),
                List.of(),
                List.of(),
                0L
        ));
    }

    public static void handleSession(ServerPlayer player, Payloads.QuestMasterSessionPayload payload) {
        if (player == null || payload == null || payload.action() != Payloads.QuestMasterSessionPayload.ACTION_CLOSE) {
            return;
        }

        ServerLevel world = (ServerLevel) player.level();
        Integer entityId = OPEN_SESSIONS.get(player.getUUID());
        if (entityId == null || entityId != payload.entityId()) {
            return;
        }

        OPEN_SESSIONS.remove(player.getUUID());
        QuestMasterEntity questMaster = resolveQuestMaster(world, payload.entityId());
        if (questMaster != null && questMaster.isCustomer(player)) {
            questMaster.clearCustomer();
        }
    }

    public static void handleAction(ServerPlayer player, Payloads.QuestMasterActionPayload payload) {
        if (player == null || payload == null || payload.action() == Payloads.QuestMasterActionPayload.ACTION_NONE) {
            return;
        }

        ServerLevel world = (ServerLevel) player.level();
        QuestMasterEntity questMaster = validateSession(world, player, payload.entityId());
        if (questMaster == null) {
            close(player);
            return;
        }

        StoryArcType storyArc = storyArcFromEntryId(payload.entryId());
        if (storyArc != null) {
            handleStoryAction(world, player, payload.action(), storyArc);
            refreshIfOpen(world, player);
            return;
        }

        switch (payload.entryId()) {
            case ENTRY_DAILY_MAIN -> handleDailyMainAction(world, player, payload.action());
            case ENTRY_DAILY_BONUS -> handleDailyBonusAction(world, player, payload.action());
            case ENTRY_WEEKLY -> handleWeeklyAction(world, player, payload.action());
            case ENTRY_SPECIAL_SHARD -> handleSpecialShardAction(world, player, payload.action());
            case ENTRY_SPECIAL_MERCHANT -> handleSpecialMerchantAction(world, player, payload.action());
            case ENTRY_SPECIAL_FLUTE -> handleSpecialFluteAction(world, player, payload.action());
            case ENTRY_SPECIAL_SMOKER -> handleSpecialSmokerAction(world, player, payload.action());
            case ENTRY_SPECIAL_COMPASS -> handleSpecialCompassAction(world, player, payload.action());
            default -> {
            }
        }

        refreshIfOpen(world, player);
    }

    private static boolean handleDailyMainAction(ServerLevel world, ServerPlayer player, int action) {
        UUID playerId = player.getUUID();
        if (action == Payloads.QuestMasterActionPayload.ACTION_ACCEPT) {
            DailyQuestService.acceptQuest(world, player);
            return true;
        }
        if (action == Payloads.QuestMasterActionPayload.ACTION_CLAIM) {
            return DailyQuestService.completeIfEligible(world, player);
        }
        if (action == Payloads.QuestMasterActionPayload.ACTION_CANCEL) {
            return DailyQuestService.cancelToday(world, playerId);
        }
        return false;
    }

    private static boolean handleDailyBonusAction(ServerLevel world, ServerPlayer player, int action) {
        UUID playerId = player.getUUID();
        if (action == Payloads.QuestMasterActionPayload.ACTION_ACCEPT) {
            PlayerQuestData data = data(world, playerId);
            long day = TimeUtil.currentDay();
            if (data.getBonusChoice() != null
                    && data.getBonusChoiceDay() == day
                    && !DailyQuestService.isBonusAcceptedToday(world, playerId)
                    && !DailyQuestService.hasCompletedBonusToday(world, playerId)) {
                DailyQuestService.acceptBonusQuest(world, player);
                return true;
            }
            return DailyQuestService.activateShardBonusQuestOffer(world, player);
        }
        if (action == Payloads.QuestMasterActionPayload.ACTION_CLAIM) {
            return DailyQuestService.completeIfEligible(world, player);
        }
        if (action == Payloads.QuestMasterActionPayload.ACTION_CANCEL) {
            return DailyQuestService.cancelToday(world, playerId);
        }
        return false;
    }

    private static boolean handleWeeklyAction(ServerLevel world, ServerPlayer player, int action) {
        if (action == Payloads.QuestMasterActionPayload.ACTION_ACCEPT) {
            return WeeklyQuestService.acceptQuest(world, player);
        }
        if (action == Payloads.QuestMasterActionPayload.ACTION_CLAIM) {
            return WeeklyQuestService.completeIfEligible(world, player);
        }
        if (action == Payloads.QuestMasterActionPayload.ACTION_CANCEL) {
            return WeeklyQuestService.cancelThisWeek(world, player.getUUID());
        }
        return false;
    }

    private static boolean handleStoryAction(ServerLevel world, ServerPlayer player, int action, StoryArcType arcType) {
        if (arcType == null) {
            return false;
        }
        if (action == Payloads.QuestMasterActionPayload.ACTION_ACCEPT) {
            return StoryQuestService.acceptQuest(world, player, arcType);
        }
        if (action == Payloads.QuestMasterActionPayload.ACTION_CLAIM) {
            return StoryQuestService.claimFromQuestMaster(world, player, arcType);
        }
        return false;
    }

    private static boolean handleSpecialShardAction(ServerLevel world, ServerPlayer player, int action) {
        return switch (action) {
            case Payloads.QuestMasterActionPayload.ACTION_ACCEPT -> ShardRelicQuestService.acceptSpecialQuest(world, player);
            case Payloads.QuestMasterActionPayload.ACTION_CLAIM -> ShardRelicQuestService.claimFromQuestMaster(world, player);
            default -> false;
        };
    }

    private static boolean handleSpecialMerchantAction(ServerLevel world, ServerPlayer player, int action) {
        return switch (action) {
            case Payloads.QuestMasterActionPayload.ACTION_ACCEPT -> MerchantSealQuestService.acceptQuest(world, player);
            case Payloads.QuestMasterActionPayload.ACTION_CLAIM -> MerchantSealQuestService.claimFromQuestMaster(world, player);
            default -> false;
        };
    }

    private static boolean handleSpecialFluteAction(ServerLevel world, ServerPlayer player, int action) {
        return switch (action) {
            case Payloads.QuestMasterActionPayload.ACTION_ACCEPT -> ShepherdFluteQuestService.acceptQuest(world, player);
            case Payloads.QuestMasterActionPayload.ACTION_CLAIM -> ShepherdFluteQuestService.claimFromQuestMaster(world, player);
            default -> false;
        };
    }

    private static boolean handleSpecialSmokerAction(ServerLevel world, ServerPlayer player, int action) {
        return switch (action) {
            case Payloads.QuestMasterActionPayload.ACTION_ACCEPT -> ApiaristSmokerQuestService.acceptQuest(world, player);
            case Payloads.QuestMasterActionPayload.ACTION_CLAIM -> ApiaristSmokerQuestService.claimFromQuestMaster(world, player);
            default -> false;
        };
    }

    private static boolean handleSpecialCompassAction(ServerLevel world, ServerPlayer player, int action) {
        return switch (action) {
            case Payloads.QuestMasterActionPayload.ACTION_ACCEPT -> SurveyorCompassQuestService.handleQuestMasterAccept(world, player);
            case Payloads.QuestMasterActionPayload.ACTION_CLAIM -> SurveyorCompassQuestService.handleQuestMasterClaim(world, player);
            default -> false;
        };
    }

    private static Payloads.QuestMasterPayload buildPayload(int action,
                                                            ServerLevel world,
                                                            ServerPlayer player,
                                                            QuestMasterEntity questMaster) {
        List<Payloads.QuestMasterEntryData> dailyEntries = buildDailyEntries(world, player);
        List<Payloads.QuestMasterEntryData> weeklyEntries = buildWeeklyEntries(world, player);
        List<Payloads.QuestMasterEntryData> storyEntries = buildStoryEntries(world, player);
        List<Payloads.QuestMasterEntryData> specialEntries = buildSpecialEntries(world, player);

        List<Payloads.QuestMasterCategoryData> categories = List.of(
                new Payloads.QuestMasterCategoryData(CATEGORY_DAILY, Component.translatable("screen.village-quest.questmaster.category.daily"), displayCount(CATEGORY_DAILY, dailyEntries)),
                new Payloads.QuestMasterCategoryData(CATEGORY_WEEKLY, Component.translatable("screen.village-quest.questmaster.category.weekly"), displayCount(CATEGORY_WEEKLY, weeklyEntries)),
                new Payloads.QuestMasterCategoryData(CATEGORY_STORY, Component.translatable("screen.village-quest.questmaster.category.story"), displayCount(CATEGORY_STORY, storyEntries)),
                new Payloads.QuestMasterCategoryData(CATEGORY_SPECIAL, Component.translatable("screen.village-quest.questmaster.category.special"), displayCount(CATEGORY_SPECIAL, specialEntries))
        );

        List<Payloads.QuestMasterEntryData> entries = new ArrayList<>();
        entries.addAll(dailyEntries);
        entries.addAll(weeklyEntries);
        entries.addAll(storyEntries);
        entries.addAll(specialEntries);

        return new Payloads.QuestMasterPayload(
                action,
                questMaster.getId(),
                questMaster.getDisplayName(),
                categories,
                entries,
                StoryQuestService.getStoryCooldownUntil(world, player.getUUID())
        );
    }

    private static List<Payloads.QuestMasterEntryData> buildDailyEntries(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerQuestData data = data(world, playerId);
        DailyQuestService.DailyQuestType normalQuest = DailyQuestService.previewQuestChoice(world, playerId);
        DailyQuestDefinition normalDefinition = DailyQuestGenerator.definition(normalQuest);
        List<Payloads.QuestMasterEntryData> entries = new ArrayList<>();

        Component mainTitle = normalDefinition == null
                ? Component.translatable("screen.village-quest.questmaster.daily.none")
                : normalDefinition.title();
        List<Component> mainDescription = normalDefinition == null
                ? List.of(Component.translatable("screen.village-quest.questmaster.daily.unavailable").withStyle(ChatFormatting.GRAY))
                : List.of(normalDefinition.offerParagraph1(), normalDefinition.offerParagraph2());
        List<Component> mainObjectives = normalDefinition == null
                ? List.of()
                : List.of(normalDefinition.progressLine(world, playerId).copy().withStyle(ChatFormatting.GRAY));
        List<Component> mainRewards = rewardLines(world, playerId, normalQuest);
        ActionSpec primary = ActionSpec.NONE;
        ActionSpec secondary = ActionSpec.NONE;
        Component status;

        boolean normalActive = DailyQuestService.isAcceptedToday(world, playerId) && !DailyQuestService.hasCompletedToday(world, playerId);
        if (normalActive) {
            boolean ready = normalDefinition != null && normalDefinition.isComplete(world, player);
            status = ready
                    ? Component.translatable("screen.village-quest.questmaster.status.ready").withStyle(ChatFormatting.GOLD)
                    : Component.translatable("screen.village-quest.questmaster.status.active").withStyle(ChatFormatting.GREEN);
            DailyQuestService.QuestStatus questStatus = DailyQuestService.openQuestStatus(world, playerId);
            if (questStatus != null) {
                mainObjectives = List.of(questStatus.progressLine().copy().withStyle(ChatFormatting.GRAY));
            }
            primary = ready
                    ? new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CLAIM, Component.translatable("screen.village-quest.questmaster.action.claim"), true)
                    : ActionSpec.NONE;
            secondary = new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CANCEL, Component.translatable("screen.village-quest.questmaster.action.cancel"), true);
        } else if (DailyQuestService.hasCompletedToday(world, playerId)) {
            status = Component.translatable("screen.village-quest.questmaster.status.completed").withStyle(ChatFormatting.AQUA);
            mainDescription = List.of(Component.translatable("screen.village-quest.questmaster.daily.completed_today").withStyle(ChatFormatting.GRAY));
            mainObjectives = List.of();
        } else {
            status = Component.translatable("screen.village-quest.questmaster.status.available").withStyle(ChatFormatting.YELLOW);
            primary = new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_ACCEPT, Component.translatable("screen.village-quest.questmaster.action.accept"), true);
        }

        mainDescription = appendQuestEcho(world, playerId, mainDescription, dailyTrack(normalQuest));

        entries.add(entry(
                ENTRY_DAILY_MAIN,
                CATEGORY_DAILY,
                mainTitle,
                Component.translatable("screen.village-quest.questmaster.subtitle.daily"),
                status,
                mainDescription,
                mainObjectives,
                mainRewards,
                primary,
                secondary,
                false
        ));

        Payloads.QuestMasterEntryData bonusEntry = buildDailyBonusEntry(world, player, data);
        if (bonusEntry != null) {
            entries.add(bonusEntry);
        }

        return entries;
    }

    private static Payloads.QuestMasterEntryData buildDailyBonusEntry(ServerLevel world,
                                                                      ServerPlayer player,
                                                                      PlayerQuestData data) {
        UUID playerId = player.getUUID();
        long currentDay = TimeUtil.currentDay();
        boolean bonusAccepted = DailyQuestService.isBonusAcceptedToday(world, playerId);
        boolean bonusCompleted = DailyQuestService.hasCompletedBonusToday(world, playerId);
        boolean hasBonusChoice = data.getBonusChoice() != null && data.getBonusChoiceDay() == currentDay;
        boolean shardAvailable = DailyQuestService.hasCompletedToday(world, playerId)
                && !bonusAccepted
                && !bonusCompleted
                && !hasBonusChoice
                && ModItems.MAGIC_SHARD != null
                && DailyQuestService.countInventoryItem(player, ModItems.MAGIC_SHARD) > 0;

        if (!shardAvailable && !hasBonusChoice && !bonusAccepted && !bonusCompleted) {
            return null;
        }

        if (shardAvailable) {
            return entry(
                    ENTRY_DAILY_BONUS,
                    CATEGORY_DAILY,
                    Component.translatable("text.village-quest.daily.shard.title").withStyle(ChatFormatting.LIGHT_PURPLE),
                    Component.translatable("screen.village-quest.questmaster.subtitle.daily_bonus"),
                    Component.translatable("screen.village-quest.questmaster.status.available").withStyle(ChatFormatting.YELLOW),
                    List.of(
                            Component.translatable("text.village-quest.daily.shard.body.1").withStyle(ChatFormatting.GRAY),
                            Component.translatable("text.village-quest.daily.shard.body.2").withStyle(ChatFormatting.GRAY)
                    ),
                    List.of(Component.translatable("screen.village-quest.questmaster.daily.shard_cost").withStyle(ChatFormatting.GRAY)),
                    List.of(Component.translatable("screen.village-quest.questmaster.daily.shard_reward").withStyle(ChatFormatting.LIGHT_PURPLE)),
                    new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_ACCEPT, Component.translatable("screen.village-quest.questmaster.action.use_shard"), true),
                    ActionSpec.NONE,
                    false
            );
        }

        DailyQuestDefinition bonusDefinition = DailyQuestGenerator.definition(data.getBonusChoice());
        Component title = bonusDefinition == null
                ? Component.translatable("screen.village-quest.questmaster.daily.bonus")
                : bonusDefinition.title();
        List<Component> description = bonusDefinition == null
                ? List.of(Component.translatable("screen.village-quest.questmaster.daily.bonus_waiting").withStyle(ChatFormatting.GRAY))
                : List.of(bonusDefinition.offerParagraph1(), bonusDefinition.offerParagraph2());
        List<Component> objectives = bonusDefinition == null
                ? List.of()
                : List.of(bonusDefinition.progressLine(world, playerId).copy().withStyle(ChatFormatting.GRAY));
        List<Component> rewards = rewardLines(world, playerId, data.getBonusChoice());
        ActionSpec primary = ActionSpec.NONE;
        ActionSpec secondary = ActionSpec.NONE;
        Component status;

        if (bonusAccepted && !bonusCompleted) {
            boolean ready = bonusDefinition != null && bonusDefinition.isComplete(world, player);
            status = ready
                    ? Component.translatable("screen.village-quest.questmaster.status.ready").withStyle(ChatFormatting.GOLD)
                    : Component.translatable("screen.village-quest.questmaster.status.active").withStyle(ChatFormatting.GREEN);
            DailyQuestService.QuestStatus questStatus = DailyQuestService.openQuestStatus(world, playerId);
            if (questStatus != null) {
                objectives = List.of(questStatus.progressLine().copy().withStyle(ChatFormatting.GRAY));
            }
            primary = ready
                    ? new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CLAIM, Component.translatable("screen.village-quest.questmaster.action.claim"), true)
                    : ActionSpec.NONE;
            secondary = new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CANCEL, Component.translatable("screen.village-quest.questmaster.action.cancel"), true);
        } else if (bonusCompleted) {
            status = Component.translatable("screen.village-quest.questmaster.status.completed").withStyle(ChatFormatting.AQUA);
            description = List.of(Component.translatable("screen.village-quest.questmaster.daily.bonus_completed").withStyle(ChatFormatting.GRAY));
            objectives = List.of();
        } else {
            status = Component.translatable("screen.village-quest.questmaster.status.available").withStyle(ChatFormatting.YELLOW);
            primary = new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_ACCEPT, Component.translatable("screen.village-quest.questmaster.action.accept"), true);
        }

        return entry(
                ENTRY_DAILY_BONUS,
                CATEGORY_DAILY,
                title,
                Component.translatable("screen.village-quest.questmaster.subtitle.daily_bonus"),
                status,
                description,
                objectives,
                rewards,
                primary,
                secondary,
                false
        );
    }

    private static List<Payloads.QuestMasterEntryData> buildWeeklyEntries(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        WeeklyQuestService.WeeklyQuestType questType = WeeklyQuestService.previewQuestChoice(world, playerId);
        WeeklyQuestDefinition definition = WeeklyQuestGenerator.definition(questType);
        if (definition == null) {
            return List.of(entry(
                    ENTRY_WEEKLY,
                    CATEGORY_WEEKLY,
                    Component.translatable("screen.village-quest.questmaster.weekly.title"),
                    Component.translatable("screen.village-quest.questmaster.subtitle.weekly"),
                    Component.translatable("screen.village-quest.questmaster.status.locked").withStyle(ChatFormatting.DARK_GRAY),
                    List.of(Component.translatable("screen.village-quest.questmaster.weekly.unavailable").withStyle(ChatFormatting.GRAY)),
                    List.of(),
                    List.of(),
                    ActionSpec.NONE,
                    ActionSpec.NONE,
                    true
            ));
        }

        WeeklyQuestCompletion completion = WeeklyQuestService.previewCompletion(questType);
        List<Component> rewards = weeklyRewards(world, playerId, completion);
        boolean accepted = WeeklyQuestService.isAcceptedThisWeek(world, playerId);
        boolean completed = WeeklyQuestService.hasCompletedThisWeek(world, playerId);
        List<Component> description = appendQuestEcho(world, playerId, List.of(definition.offerParagraph1(), definition.offerParagraph2()), completion == null ? null : completion.reputationTrack());
        List<Component> objectives = List.copyOf(definition.progressLines(world, playerId));
        ActionSpec primary = ActionSpec.NONE;
        ActionSpec secondary = ActionSpec.NONE;
        Component status;
        boolean locked = false;

        if (accepted && !completed) {
            boolean ready = definition.isComplete(world, player);
            status = ready
                    ? Component.translatable("screen.village-quest.questmaster.status.ready").withStyle(ChatFormatting.GOLD)
                    : Component.translatable("screen.village-quest.questmaster.status.active").withStyle(ChatFormatting.GREEN);
            if (ready) {
                primary = new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CLAIM, Component.translatable("screen.village-quest.questmaster.action.claim"), true);
            }
            secondary = new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CANCEL, Component.translatable("screen.village-quest.questmaster.action.cancel"), true);
        } else if (completed) {
            status = Component.translatable("screen.village-quest.questmaster.status.completed").withStyle(ChatFormatting.AQUA);
            description = List.of(Component.translatable("screen.village-quest.questmaster.weekly.completed").withStyle(ChatFormatting.GRAY));
            objectives = List.of(Component.translatable("screen.village-quest.questmaster.weekly.next_reset").withStyle(ChatFormatting.GRAY));
        } else {
            status = Component.translatable("screen.village-quest.questmaster.status.available").withStyle(ChatFormatting.YELLOW);
            primary = new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_ACCEPT, Component.translatable("screen.village-quest.questmaster.action.accept"), true);
        }

        return List.of(entry(
                ENTRY_WEEKLY,
                CATEGORY_WEEKLY,
                definition.title(),
                Component.translatable("screen.village-quest.questmaster.subtitle.weekly"),
                status,
                description,
                objectives,
                rewards,
                primary,
                secondary,
                locked
        ));
    }

    private static List<Payloads.QuestMasterEntryData> buildStoryEntries(ServerLevel world, ServerPlayer player) {
        if (!QuestMasterProgressionService.isStoryCategoryUnlocked(world, player.getUUID())) {
            return List.of();
        }
        UUID playerId = player.getUUID();
        StoryArcType focus = StoryQuestService.activeArcType(world, playerId);
        if (focus != null) {
            Payloads.QuestMasterEntryData entry = buildStoryEntry(world, player, focus);
            return entry == null ? List.of() : List.of(entry);
        }
        if (StoryQuestService.isStoryCooldownActive(world, playerId)) {
            return List.of(buildStoryCooldownEntry());
        }
        focus = StoryQuestService.availableArcType(world, playerId);
        if (focus != null) {
            Payloads.QuestMasterEntryData entry = buildStoryEntry(world, player, focus);
            return entry == null ? List.of() : List.of(entry);
        }
        return StoryQuestService.completedCount(world, playerId) > 0
                ? List.of(buildStoryArchiveEntry(world, playerId))
                : List.of();
    }

    private static Payloads.QuestMasterEntryData buildStoryEntry(ServerLevel world, ServerPlayer player, StoryArcType arcType) {
        UUID playerId = player.getUUID();
        StoryArcDefinition arc = StoryQuestService.definition(arcType);
        if (arc == null) {
            return null;
        }

        boolean completed = StoryQuestService.isCompleted(world, playerId, arcType);
        boolean active = StoryQuestService.isActive(world, playerId, arcType);
        boolean unlocked = arc.isUnlocked(world, playerId);
        if (!completed && !active && !unlocked) {
            return null;
        }

        String entryId = storyEntryId(arcType);
        if (completed) {
            StoryChapterCompletion completion = arc.chapter(arc.chapterCount() - 1).buildCompletion();
            return entry(
                    entryId,
                    CATEGORY_STORY,
                    arc.title(),
                    Component.translatable("screen.village-quest.questmaster.subtitle.story_chapter", arc.chapterCount()),
                    Component.translatable("screen.village-quest.questmaster.status.completed").withStyle(ChatFormatting.AQUA),
                    List.of(Component.translatable("screen.village-quest.questmaster.story." + arcType.id() + ".completed").withStyle(ChatFormatting.GRAY)),
                    List.of(),
                    appendRewardEcho(world, playerId, StoryQuestService.previewRewardLines(completion), completion.reputationTrack()),
                    ActionSpec.NONE,
                    ActionSpec.NONE,
                    false
            );
        }

        StoryArcType activeArc = StoryQuestService.activeArcType(world, playerId);
        if (activeArc != null && activeArc != arcType) {
            return lockedStoryEntry(
                    entryId,
                    arc.title(),
                    Component.translatable("screen.village-quest.questmaster.story.locked_other").withStyle(ChatFormatting.GRAY)
            );
        }

        int chapterIndex = StoryQuestService.chapterIndex(world, playerId, arcType);
        StoryChapterDefinition chapter = StoryQuestService.chapter(world, playerId, arcType);
        if (chapter == null) {
            return lockedStoryEntry(
                    entryId,
                    arc.title(),
                    Component.translatable("screen.village-quest.questmaster.story.unavailable").withStyle(ChatFormatting.GRAY)
            );
        }

        StoryChapterCompletion completion = chapter.buildCompletion();
        List<Component> description = appendQuestEcho(world, playerId, List.of(chapter.offerParagraph1(), chapter.offerParagraph2()), completion.reputationTrack());
        List<Component> objectives = List.copyOf(chapter.progressLines(world, playerId));
        ActionSpec primary = ActionSpec.NONE;
        Component status;

        if (active) {
            boolean ready = chapter.isComplete(world, player);
            status = ready
                    ? Component.translatable("screen.village-quest.questmaster.status.ready").withStyle(ChatFormatting.GOLD)
                    : Component.translatable("screen.village-quest.questmaster.status.active").withStyle(ChatFormatting.GREEN);
            if (ready) {
                primary = new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CLAIM, Component.translatable("screen.village-quest.questmaster.action.claim"), true);
            }
        } else {
            status = Component.translatable("screen.village-quest.questmaster.status.available").withStyle(ChatFormatting.YELLOW);
            primary = new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_ACCEPT, Component.translatable("screen.village-quest.questmaster.action.accept"), true);
        }

        return entry(
                entryId,
                CATEGORY_STORY,
                arc.title(),
                Component.translatable("screen.village-quest.questmaster.subtitle.story_chapter", chapterIndex + 1),
                status,
                description,
                objectives,
                appendRewardEcho(world, playerId, StoryQuestService.previewRewardLines(completion), completion.reputationTrack()),
                primary,
                ActionSpec.NONE,
                false
        );
    }

    private static List<Payloads.QuestMasterEntryData> buildSpecialEntries(ServerLevel world, ServerPlayer player) {
        if (!QuestMasterProgressionService.isSpecialCategoryUnlocked(world, player.getUUID())) {
            return List.of();
        }

        List<Payloads.QuestMasterEntryData> entries = new ArrayList<>();
        addEntry(entries, buildShardEntry(world, player));
        addEntry(entries, buildSmokerEntry(world, player));
        addEntry(entries, buildCompassEntry(world, player));
        addEntry(entries, buildMerchantEntry(world, player));
        addEntry(entries, buildFluteEntry(world, player));
        if (entries.isEmpty()) {
            entries.add(buildSpecialIntroEntry());
        }
        return List.copyOf(entries);
    }

    private static Payloads.QuestMasterEntryData buildShardEntry(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerQuestData data = data(world, playerId);
        int shardCount = ModItems.MAGIC_SHARD == null ? 0 : DailyQuestService.countInventoryItem(player, ModItems.MAGIC_SHARD);
        boolean dailyActive = DailyQuestService.openQuestStatus(world, playerId) != null;
        boolean otherSpecialActive = hasOtherActiveSpecial(world, playerId, ENTRY_SPECIAL_SHARD);

        if (data.getShardRelicQuestStage() == ShardRelicQuestStage.TRIAL_ACTIVE) {
            return entry(
                    ENTRY_SPECIAL_SHARD,
                    CATEGORY_SPECIAL,
                    Component.translatable("quest.village-quest.special.shards.title"),
                    Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Component.translatable("screen.village-quest.questmaster.status.active").withStyle(ChatFormatting.GREEN),
                    List.of(Component.translatable("screen.village-quest.questmaster.special.shards.active").withStyle(ChatFormatting.GRAY)),
                    List.copyOf(ShardRelicQuestService.openStatus(world, playerId).lines()),
                    starreachRewards(),
                    ActionSpec.NONE,
                    ActionSpec.NONE,
                    false
            );
        }
        if (data.getShardRelicQuestStage() == ShardRelicQuestStage.TRIAL_READY) {
            return entry(
                    ENTRY_SPECIAL_SHARD,
                    CATEGORY_SPECIAL,
                    Component.translatable("quest.village-quest.special.shards.title"),
                    Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Component.translatable("screen.village-quest.questmaster.status.ready").withStyle(ChatFormatting.GOLD),
                    List.of(Component.translatable("screen.village-quest.questmaster.special.shards.ready").withStyle(ChatFormatting.GRAY)),
                    List.copyOf(ShardRelicQuestService.openStatus(world, playerId).lines()),
                    starreachRewards(),
                    new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CLAIM, Component.translatable("screen.village-quest.questmaster.action.receive_map"), true),
                    ActionSpec.NONE,
                    false
            );
        }
        if (data.getShardRelicQuestStage() == ShardRelicQuestStage.CACHE_HUNT) {
            return entry(
                    ENTRY_SPECIAL_SHARD,
                    CATEGORY_SPECIAL,
                    Component.translatable("quest.village-quest.special.shards.title"),
                    Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Component.translatable("screen.village-quest.questmaster.status.active").withStyle(ChatFormatting.GREEN),
                    List.of(Component.translatable("screen.village-quest.questmaster.special.shards.cache").withStyle(ChatFormatting.GRAY)),
                    List.copyOf(ShardRelicQuestService.openStatus(world, playerId).lines()),
                    starreachRewards(),
                    new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CLAIM, Component.translatable("screen.village-quest.questmaster.action.reissue_map"), true),
                    ActionSpec.NONE,
                    false
            );
        }
        if (data.getShardRelicQuestStage() == ShardRelicQuestStage.COMPLETED) {
            return entry(
                    ENTRY_SPECIAL_SHARD,
                    CATEGORY_SPECIAL,
                    Component.translatable("quest.village-quest.special.shards.title"),
                    Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Component.translatable("screen.village-quest.questmaster.status.completed").withStyle(ChatFormatting.AQUA),
                    List.of(Component.translatable("screen.village-quest.questmaster.special.completed").withStyle(ChatFormatting.GRAY)),
                    List.of(),
                    starreachRewards(),
                    ActionSpec.NONE,
                    ActionSpec.NONE,
                    false
            );
        }

        if (otherSpecialActive) {
            return lockedSpecialEntry(
                    ENTRY_SPECIAL_SHARD,
                    Component.translatable("quest.village-quest.special.shards.title"),
                    Component.translatable("screen.village-quest.questmaster.special.locked_other").withStyle(ChatFormatting.GRAY),
                    starreachRewards()
            );
        }
        if (dailyActive) {
            return lockedSpecialEntry(
                    ENTRY_SPECIAL_SHARD,
                    Component.translatable("quest.village-quest.special.shards.title"),
                    Component.translatable("screen.village-quest.questmaster.special.locked_daily").withStyle(ChatFormatting.GRAY),
                    starreachRewards()
            );
        }
        if (shardCount >= 10) {
            return entry(
                    ENTRY_SPECIAL_SHARD,
                    CATEGORY_SPECIAL,
                    Component.translatable("quest.village-quest.special.shards.title"),
                    Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Component.translatable("screen.village-quest.questmaster.status.available").withStyle(ChatFormatting.YELLOW),
                    List.of(
                            Component.translatable("quest.village-quest.special.shards.offer.1").withStyle(ChatFormatting.GRAY),
                            Component.translatable("quest.village-quest.special.shards.offer.2", 10).withStyle(ChatFormatting.GRAY)
                    ),
                    List.of(Component.translatable("screen.village-quest.questmaster.special.shards.requirement", shardCount, 10).withStyle(ChatFormatting.GRAY)),
                    starreachRewards(),
                    new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_ACCEPT, Component.translatable("screen.village-quest.questmaster.action.accept"), true),
                    ActionSpec.NONE,
                    false
            );
        }

        return null;
    }

    private static int displayCount(String categoryId, List<Payloads.QuestMasterEntryData> entries) {
        return entries == null ? 0 : entries.size();
    }

    private static Payloads.QuestMasterEntryData buildSmokerEntry(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerQuestData data = data(world, playerId);
        int reputation = ReputationService.get(world, playerId, ReputationService.ReputationTrack.FARMING);
        boolean dailyActive = DailyQuestService.openQuestStatus(world, playerId) != null;
        boolean otherSpecialActive = hasOtherActiveSpecial(world, playerId, ENTRY_SPECIAL_SMOKER);
        List<Component> description = List.of(
                Component.translatable("quest.village-quest.special.apiarist_smoker.offer.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.special.apiarist_smoker.offer.2").withStyle(ChatFormatting.GRAY)
        );

        if (data.getApiaristSmokerQuestStage() == RelicQuestStage.ACTIVE) {
            return entry(
                    ENTRY_SPECIAL_SMOKER,
                    CATEGORY_SPECIAL,
                    Component.translatable("quest.village-quest.special.apiarist_smoker.title"),
                    Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Component.translatable("screen.village-quest.questmaster.status.active").withStyle(ChatFormatting.GREEN),
                    List.of(Component.translatable("screen.village-quest.questmaster.special.apiarist_smoker.active").withStyle(ChatFormatting.GRAY)),
                    List.copyOf(ApiaristSmokerQuestService.openStatus(world, playerId).lines()),
                    smokerRewards(),
                    ActionSpec.NONE,
                    ActionSpec.NONE,
                    false
            );
        }
        if (data.getApiaristSmokerQuestStage() == RelicQuestStage.READY) {
            return entry(
                    ENTRY_SPECIAL_SMOKER,
                    CATEGORY_SPECIAL,
                    Component.translatable("quest.village-quest.special.apiarist_smoker.title"),
                    Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Component.translatable("screen.village-quest.questmaster.status.ready").withStyle(ChatFormatting.GOLD),
                    List.of(Component.translatable("screen.village-quest.questmaster.special.apiarist_smoker.ready").withStyle(ChatFormatting.GRAY)),
                    List.copyOf(ApiaristSmokerQuestService.openStatus(world, playerId).lines()),
                    smokerRewards(),
                    new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CLAIM, Component.translatable("screen.village-quest.questmaster.action.claim"), true),
                    ActionSpec.NONE,
                    false
            );
        }
        if (data.getApiaristSmokerQuestStage() == RelicQuestStage.COMPLETED) {
            return entry(
                    ENTRY_SPECIAL_SMOKER,
                    CATEGORY_SPECIAL,
                    Component.translatable("quest.village-quest.special.apiarist_smoker.title"),
                    Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Component.translatable("screen.village-quest.questmaster.status.completed").withStyle(ChatFormatting.AQUA),
                    List.of(Component.translatable("screen.village-quest.questmaster.special.completed").withStyle(ChatFormatting.GRAY)),
                    List.of(),
                    smokerRewards(),
                    ActionSpec.NONE,
                    ActionSpec.NONE,
                    false
            );
        }
        if (!RelicQuestProgressionService.hasStoryRequirement(world, playerId, SpecialQuestKind.APIARIST_SMOKER)) {
            return null;
        }
        if (reputation < ApiaristSmokerQuestService.REQUIRED_FARMING_REPUTATION) {
            return lockedSpecialReputationEntry(
                    world,
                    playerId,
                    ENTRY_SPECIAL_SMOKER,
                    Component.translatable("quest.village-quest.special.apiarist_smoker.title"),
                    SpecialQuestKind.APIARIST_SMOKER,
                    description,
                    smokerRewards()
            );
        }

        if (otherSpecialActive) {
            return lockedSpecialEntry(
                    ENTRY_SPECIAL_SMOKER,
                    Component.translatable("quest.village-quest.special.apiarist_smoker.title"),
                    Component.translatable("screen.village-quest.questmaster.special.locked_other").withStyle(ChatFormatting.GRAY),
                    smokerRewards()
            );
        }
        if (dailyActive) {
            return lockedSpecialEntry(
                    ENTRY_SPECIAL_SMOKER,
                    Component.translatable("quest.village-quest.special.apiarist_smoker.title"),
                    Component.translatable("screen.village-quest.questmaster.special.locked_daily").withStyle(ChatFormatting.GRAY),
                    smokerRewards()
            );
        }
        if (reputation >= ApiaristSmokerQuestService.REQUIRED_FARMING_REPUTATION) {
            return entry(
                    ENTRY_SPECIAL_SMOKER,
                    CATEGORY_SPECIAL,
                    Component.translatable("quest.village-quest.special.apiarist_smoker.title"),
                    Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Component.translatable("screen.village-quest.questmaster.status.available").withStyle(ChatFormatting.YELLOW),
                    description,
                    List.of(Component.translatable("screen.village-quest.questmaster.special.apiarist_smoker.requirement").withStyle(ChatFormatting.GRAY)),
                    smokerRewards(),
                    new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_ACCEPT, Component.translatable("screen.village-quest.questmaster.action.accept"), true),
                    ActionSpec.NONE,
                    false
            );
        }
        return null;
    }

    private static Payloads.QuestMasterEntryData buildCompassEntry(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerQuestData data = data(world, playerId);
        int reputation = ReputationService.get(world, playerId, ReputationService.ReputationTrack.CRAFTING);
        boolean dailyActive = DailyQuestService.openQuestStatus(world, playerId) != null;
        boolean otherSpecialActive = hasOtherActiveSpecial(world, playerId, ENTRY_SPECIAL_COMPASS);
        List<Component> description = List.of(
                Component.translatable("quest.village-quest.special.surveyor_compass.offer.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.special.surveyor_compass.offer.2").withStyle(ChatFormatting.GRAY)
        );
        List<Component> biomeDescription = List.of(
                Component.translatable("quest.village-quest.special.surveyor_compass_biomes.offer.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.special.surveyor_compass_biomes.offer.2").withStyle(ChatFormatting.GRAY)
        );

        if (data.getSurveyorCompassQuestStage() == RelicQuestStage.ACTIVE) {
            return entry(
                    ENTRY_SPECIAL_COMPASS,
                    CATEGORY_SPECIAL,
                    Component.translatable("quest.village-quest.special.surveyor_compass.title"),
                    Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Component.translatable("screen.village-quest.questmaster.status.active").withStyle(ChatFormatting.GREEN),
                    List.of(Component.translatable("screen.village-quest.questmaster.special.surveyor_compass.active").withStyle(ChatFormatting.GRAY)),
                    List.copyOf(SurveyorCompassQuestService.openStatus(world, playerId).lines()),
                    compassRewards(),
                    ActionSpec.NONE,
                    ActionSpec.NONE,
                    false
            );
        }
        if (data.getSurveyorCompassQuestStage() == RelicQuestStage.READY) {
            return entry(
                    ENTRY_SPECIAL_COMPASS,
                    CATEGORY_SPECIAL,
                    Component.translatable("quest.village-quest.special.surveyor_compass.title"),
                    Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Component.translatable("screen.village-quest.questmaster.status.ready").withStyle(ChatFormatting.GOLD),
                    List.of(Component.translatable("screen.village-quest.questmaster.special.surveyor_compass.ready").withStyle(ChatFormatting.GRAY)),
                    List.copyOf(SurveyorCompassQuestService.openStatus(world, playerId).lines()),
                    compassRewards(),
                    new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CLAIM, Component.translatable("screen.village-quest.questmaster.action.claim"), true),
                    ActionSpec.NONE,
                    false
            );
        }
        if (data.getSurveyorCompassQuestStage() == RelicQuestStage.COMPLETED) {
            if (!SurveyorCompassQuestService.hasBiomeModesUnlocked(world, playerId)) {
                if (!VillageProjectService.isUnlocked(world, playerId, VillageProjectType.APIARY_CHARTER)) {
                    return entry(
                            ENTRY_SPECIAL_COMPASS,
                            CATEGORY_SPECIAL,
                            Component.translatable("quest.village-quest.special.surveyor_compass.title"),
                            Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                            Component.translatable("screen.village-quest.questmaster.status.completed").withStyle(ChatFormatting.AQUA),
                            List.of(Component.translatable("screen.village-quest.questmaster.special.surveyor_compass_biomes.awaiting_apiary").withStyle(ChatFormatting.GRAY)),
                            List.of(),
                            compassBiomeRewards(),
                            ActionSpec.NONE,
                            ActionSpec.NONE,
                            false
                    );
                }

                if (SurveyorCompassQuestService.hasBiomeCalibrationActive(world, playerId)) {
                    boolean ready = SurveyorCompassQuestService.hasBiomeCalibrationReady(world, playerId);
                    return entry(
                            ENTRY_SPECIAL_COMPASS,
                            CATEGORY_SPECIAL,
                            Component.translatable("quest.village-quest.special.surveyor_compass_biomes.title"),
                            Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                            Component.translatable(
                                    ready
                                            ? "screen.village-quest.questmaster.status.ready"
                                            : "screen.village-quest.questmaster.status.active"
                            ).withStyle(ready ? ChatFormatting.GOLD : ChatFormatting.GREEN),
                            List.of(Component.translatable(
                                    ready
                                            ? "screen.village-quest.questmaster.special.surveyor_compass_biomes.ready"
                                            : "screen.village-quest.questmaster.special.surveyor_compass_biomes.active"
                            ).withStyle(ChatFormatting.GRAY)),
                            List.copyOf(SurveyorCompassQuestService.biomeCalibrationProgressLines(world, playerId)),
                            compassBiomeRewards(),
                            ready
                                    ? new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CLAIM, Component.translatable("screen.village-quest.questmaster.action.claim"), true)
                                    : ActionSpec.NONE,
                            ActionSpec.NONE,
                            false
                    );
                }

                if (otherSpecialActive) {
                    return lockedSpecialEntry(
                            ENTRY_SPECIAL_COMPASS,
                            Component.translatable("quest.village-quest.special.surveyor_compass_biomes.title"),
                            Component.translatable("screen.village-quest.questmaster.special.locked_other").withStyle(ChatFormatting.GRAY),
                            compassBiomeRewards()
                    );
                }
                if (dailyActive) {
                    return lockedSpecialEntry(
                            ENTRY_SPECIAL_COMPASS,
                            Component.translatable("quest.village-quest.special.surveyor_compass_biomes.title"),
                            Component.translatable("screen.village-quest.questmaster.special.locked_daily").withStyle(ChatFormatting.GRAY),
                            compassBiomeRewards()
                    );
                }
                if (!SurveyorCompassQuestService.hasCompassInInventory(player)) {
                    return lockedSpecialEntry(
                            ENTRY_SPECIAL_COMPASS,
                            Component.translatable("quest.village-quest.special.surveyor_compass_biomes.title"),
                            Component.translatable("message.village-quest.special.surveyor_compass_biomes.compass_missing").withStyle(ChatFormatting.GRAY),
                            compassBiomeRewards()
                    );
                }
                return entry(
                        ENTRY_SPECIAL_COMPASS,
                        CATEGORY_SPECIAL,
                        Component.translatable("quest.village-quest.special.surveyor_compass_biomes.title"),
                        Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                        Component.translatable("screen.village-quest.questmaster.status.available").withStyle(ChatFormatting.YELLOW),
                        biomeDescription,
                        List.of(Component.translatable("screen.village-quest.questmaster.special.surveyor_compass_biomes.requirement").withStyle(ChatFormatting.GRAY)),
                        compassBiomeRewards(),
                        new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_ACCEPT, Component.translatable("screen.village-quest.questmaster.action.accept"), true),
                        ActionSpec.NONE,
                        false
                );
            }
            return entry(
                    ENTRY_SPECIAL_COMPASS,
                    CATEGORY_SPECIAL,
                    Component.translatable("quest.village-quest.special.surveyor_compass.title"),
                    Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Component.translatable("screen.village-quest.questmaster.status.completed").withStyle(ChatFormatting.AQUA),
                    List.of(Component.translatable("screen.village-quest.questmaster.special.completed").withStyle(ChatFormatting.GRAY)),
                    List.of(),
                    compassRewards(),
                    ActionSpec.NONE,
                    ActionSpec.NONE,
                    false
            );
        }
        if (!RelicQuestProgressionService.hasStoryRequirement(world, playerId, SpecialQuestKind.SURVEYOR_COMPASS)) {
            return null;
        }
        if (reputation < SurveyorCompassQuestService.REQUIRED_CRAFTING_REPUTATION) {
            return lockedSpecialReputationEntry(
                    world,
                    playerId,
                    ENTRY_SPECIAL_COMPASS,
                    Component.translatable("quest.village-quest.special.surveyor_compass.title"),
                    SpecialQuestKind.SURVEYOR_COMPASS,
                    description,
                    compassRewards()
            );
        }

        if (otherSpecialActive) {
            return lockedSpecialEntry(
                    ENTRY_SPECIAL_COMPASS,
                    Component.translatable("quest.village-quest.special.surveyor_compass.title"),
                    Component.translatable("screen.village-quest.questmaster.special.locked_other").withStyle(ChatFormatting.GRAY),
                    compassRewards()
            );
        }
        if (dailyActive) {
            return lockedSpecialEntry(
                    ENTRY_SPECIAL_COMPASS,
                    Component.translatable("quest.village-quest.special.surveyor_compass.title"),
                    Component.translatable("screen.village-quest.questmaster.special.locked_daily").withStyle(ChatFormatting.GRAY),
                    compassRewards()
            );
        }
        if (reputation >= SurveyorCompassQuestService.REQUIRED_CRAFTING_REPUTATION) {
            return entry(
                    ENTRY_SPECIAL_COMPASS,
                    CATEGORY_SPECIAL,
                    Component.translatable("quest.village-quest.special.surveyor_compass.title"),
                    Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Component.translatable("screen.village-quest.questmaster.status.available").withStyle(ChatFormatting.YELLOW),
                    description,
                    List.of(Component.translatable("screen.village-quest.questmaster.special.surveyor_compass.requirement").withStyle(ChatFormatting.GRAY)),
                    compassRewards(),
                    new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_ACCEPT, Component.translatable("screen.village-quest.questmaster.action.accept"), true),
                    ActionSpec.NONE,
                    false
            );
        }
        return null;
    }

    private static Payloads.QuestMasterEntryData buildMerchantEntry(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerQuestData data = data(world, playerId);
        int reputation = ReputationService.get(world, playerId, ReputationService.ReputationTrack.TRADE);
        boolean dailyActive = DailyQuestService.openQuestStatus(world, playerId) != null;
        boolean otherSpecialActive = hasOtherActiveSpecial(world, playerId, ENTRY_SPECIAL_MERCHANT);
        List<Component> description = List.of(
                Component.translatable("quest.village-quest.special.merchant.offer.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.special.merchant.offer.2").withStyle(ChatFormatting.GRAY)
        );

        if (data.getMerchantSealQuestStage() == RelicQuestStage.ACTIVE) {
            return entry(
                    ENTRY_SPECIAL_MERCHANT,
                    CATEGORY_SPECIAL,
                    Component.translatable("quest.village-quest.special.merchant.title"),
                    Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Component.translatable("screen.village-quest.questmaster.status.active").withStyle(ChatFormatting.GREEN),
                    List.of(Component.translatable("screen.village-quest.questmaster.special.merchant.active").withStyle(ChatFormatting.GRAY)),
                    List.copyOf(MerchantSealQuestService.openStatus(world, playerId).lines()),
                    merchantRewards(),
                    ActionSpec.NONE,
                    ActionSpec.NONE,
                    false
            );
        }
        if (data.getMerchantSealQuestStage() == RelicQuestStage.READY) {
            return entry(
                    ENTRY_SPECIAL_MERCHANT,
                    CATEGORY_SPECIAL,
                    Component.translatable("quest.village-quest.special.merchant.title"),
                    Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Component.translatable("screen.village-quest.questmaster.status.ready").withStyle(ChatFormatting.GOLD),
                    List.of(Component.translatable("screen.village-quest.questmaster.special.merchant.ready").withStyle(ChatFormatting.GRAY)),
                    List.copyOf(MerchantSealQuestService.openStatus(world, playerId).lines()),
                    merchantRewards(),
                    new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CLAIM, Component.translatable("screen.village-quest.questmaster.action.claim"), true),
                    ActionSpec.NONE,
                    false
            );
        }
        if (data.getMerchantSealQuestStage() == RelicQuestStage.COMPLETED) {
            return entry(
                    ENTRY_SPECIAL_MERCHANT,
                    CATEGORY_SPECIAL,
                    Component.translatable("quest.village-quest.special.merchant.title"),
                    Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Component.translatable("screen.village-quest.questmaster.status.completed").withStyle(ChatFormatting.AQUA),
                    List.of(Component.translatable("screen.village-quest.questmaster.special.completed").withStyle(ChatFormatting.GRAY)),
                    List.of(),
                    merchantRewards(),
                    ActionSpec.NONE,
                    ActionSpec.NONE,
                    false
            );
        }
        if (!RelicQuestProgressionService.hasStoryRequirement(world, playerId, SpecialQuestKind.MERCHANT_SEAL)) {
            return null;
        }
        if (reputation < MerchantSealQuestService.REQUIRED_TRADE_REPUTATION) {
            return lockedSpecialReputationEntry(
                    world,
                    playerId,
                    ENTRY_SPECIAL_MERCHANT,
                    Component.translatable("quest.village-quest.special.merchant.title"),
                    SpecialQuestKind.MERCHANT_SEAL,
                    description,
                    merchantRewards()
            );
        }

        if (otherSpecialActive) {
            return lockedSpecialEntry(
                    ENTRY_SPECIAL_MERCHANT,
                    Component.translatable("quest.village-quest.special.merchant.title"),
                    Component.translatable("screen.village-quest.questmaster.special.locked_other").withStyle(ChatFormatting.GRAY),
                    merchantRewards()
            );
        }
        if (dailyActive) {
            return lockedSpecialEntry(
                    ENTRY_SPECIAL_MERCHANT,
                    Component.translatable("quest.village-quest.special.merchant.title"),
                    Component.translatable("screen.village-quest.questmaster.special.locked_daily").withStyle(ChatFormatting.GRAY),
                    merchantRewards()
            );
        }
        if (reputation >= MerchantSealQuestService.REQUIRED_TRADE_REPUTATION) {
            return entry(
                    ENTRY_SPECIAL_MERCHANT,
                    CATEGORY_SPECIAL,
                    Component.translatable("quest.village-quest.special.merchant.title"),
                    Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Component.translatable("screen.village-quest.questmaster.status.available").withStyle(ChatFormatting.YELLOW),
                    description,
                    List.of(Component.translatable("screen.village-quest.questmaster.special.merchant.requirement").withStyle(ChatFormatting.GRAY)),
                    merchantRewards(),
                    new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_ACCEPT, Component.translatable("screen.village-quest.questmaster.action.accept"), true),
                    ActionSpec.NONE,
                    false
            );
        }
        return null;
    }

    private static Payloads.QuestMasterEntryData buildFluteEntry(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerQuestData data = data(world, playerId);
        int reputation = ReputationService.get(world, playerId, ReputationService.ReputationTrack.ANIMALS);
        boolean dailyActive = DailyQuestService.openQuestStatus(world, playerId) != null;
        boolean otherSpecialActive = hasOtherActiveSpecial(world, playerId, ENTRY_SPECIAL_FLUTE);
        List<Component> description = List.of(
                Component.translatable("quest.village-quest.special.flute.offer.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.special.flute.offer.2").withStyle(ChatFormatting.GRAY)
        );

        if (data.getShepherdFluteQuestStage() == RelicQuestStage.ACTIVE) {
            return entry(
                    ENTRY_SPECIAL_FLUTE,
                    CATEGORY_SPECIAL,
                    Component.translatable("quest.village-quest.special.flute.title"),
                    Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Component.translatable("screen.village-quest.questmaster.status.active").withStyle(ChatFormatting.GREEN),
                    List.of(Component.translatable("screen.village-quest.questmaster.special.flute.active").withStyle(ChatFormatting.GRAY)),
                    List.copyOf(ShepherdFluteQuestService.openStatus(world, playerId).lines()),
                    fluteRewards(),
                    ActionSpec.NONE,
                    ActionSpec.NONE,
                    false
            );
        }
        if (data.getShepherdFluteQuestStage() == RelicQuestStage.READY) {
            return entry(
                    ENTRY_SPECIAL_FLUTE,
                    CATEGORY_SPECIAL,
                    Component.translatable("quest.village-quest.special.flute.title"),
                    Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Component.translatable("screen.village-quest.questmaster.status.ready").withStyle(ChatFormatting.GOLD),
                    List.of(Component.translatable("screen.village-quest.questmaster.special.flute.ready").withStyle(ChatFormatting.GRAY)),
                    List.copyOf(ShepherdFluteQuestService.openStatus(world, playerId).lines()),
                    fluteRewards(),
                    new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CLAIM, Component.translatable("screen.village-quest.questmaster.action.claim"), true),
                    ActionSpec.NONE,
                    false
            );
        }
        if (data.getShepherdFluteQuestStage() == RelicQuestStage.COMPLETED) {
            return entry(
                    ENTRY_SPECIAL_FLUTE,
                    CATEGORY_SPECIAL,
                    Component.translatable("quest.village-quest.special.flute.title"),
                    Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Component.translatable("screen.village-quest.questmaster.status.completed").withStyle(ChatFormatting.AQUA),
                    List.of(Component.translatable("screen.village-quest.questmaster.special.completed").withStyle(ChatFormatting.GRAY)),
                    List.of(),
                    fluteRewards(),
                    ActionSpec.NONE,
                    ActionSpec.NONE,
                    false
            );
        }
        if (!RelicQuestProgressionService.hasStoryRequirement(world, playerId, SpecialQuestKind.SHEPHERD_FLUTE)) {
            return null;
        }
        if (reputation < ShepherdFluteQuestService.REQUIRED_ANIMAL_REPUTATION) {
            return lockedSpecialReputationEntry(
                    world,
                    playerId,
                    ENTRY_SPECIAL_FLUTE,
                    Component.translatable("quest.village-quest.special.flute.title"),
                    SpecialQuestKind.SHEPHERD_FLUTE,
                    description,
                    fluteRewards()
            );
        }

        if (otherSpecialActive) {
            return lockedSpecialEntry(
                    ENTRY_SPECIAL_FLUTE,
                    Component.translatable("quest.village-quest.special.flute.title"),
                    Component.translatable("screen.village-quest.questmaster.special.locked_other").withStyle(ChatFormatting.GRAY),
                    fluteRewards()
            );
        }
        if (dailyActive) {
            return lockedSpecialEntry(
                    ENTRY_SPECIAL_FLUTE,
                    Component.translatable("quest.village-quest.special.flute.title"),
                    Component.translatable("screen.village-quest.questmaster.special.locked_daily").withStyle(ChatFormatting.GRAY),
                    fluteRewards()
            );
        }
        if (reputation >= ShepherdFluteQuestService.REQUIRED_ANIMAL_REPUTATION) {
            return entry(
                    ENTRY_SPECIAL_FLUTE,
                    CATEGORY_SPECIAL,
                    Component.translatable("quest.village-quest.special.flute.title"),
                    Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Component.translatable("screen.village-quest.questmaster.status.available").withStyle(ChatFormatting.YELLOW),
                    description,
                    List.of(Component.translatable("screen.village-quest.questmaster.special.flute.requirement").withStyle(ChatFormatting.GRAY)),
                    fluteRewards(),
                    new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_ACCEPT, Component.translatable("screen.village-quest.questmaster.action.accept"), true),
                    ActionSpec.NONE,
                    false
            );
        }

        return null;
    }

    private static boolean hasOtherActiveSpecial(ServerLevel world, UUID playerId, String exceptEntryId) {
        if (world == null || playerId == null) {
            return false;
        }
        PlayerQuestData data = data(world, playerId);
        if (!ENTRY_SPECIAL_SHARD.equals(exceptEntryId)
                && (data.getShardRelicQuestStage() == ShardRelicQuestStage.TRIAL_ACTIVE
                || data.getShardRelicQuestStage() == ShardRelicQuestStage.TRIAL_READY
                || data.getShardRelicQuestStage() == ShardRelicQuestStage.CACHE_HUNT)) {
            return true;
        }
        if (!ENTRY_SPECIAL_MERCHANT.equals(exceptEntryId)
                && (data.getMerchantSealQuestStage() == RelicQuestStage.ACTIVE
                || data.getMerchantSealQuestStage() == RelicQuestStage.READY)) {
            return true;
        }
        if (!ENTRY_SPECIAL_FLUTE.equals(exceptEntryId)
                && (data.getShepherdFluteQuestStage() == RelicQuestStage.ACTIVE
                || data.getShepherdFluteQuestStage() == RelicQuestStage.READY)) {
            return true;
        }
        if (!ENTRY_SPECIAL_SMOKER.equals(exceptEntryId)
                && (data.getApiaristSmokerQuestStage() == RelicQuestStage.ACTIVE
                || data.getApiaristSmokerQuestStage() == RelicQuestStage.READY)) {
            return true;
        }
        return !ENTRY_SPECIAL_COMPASS.equals(exceptEntryId)
                && (data.getSurveyorCompassQuestStage() == RelicQuestStage.ACTIVE
                || data.getSurveyorCompassQuestStage() == RelicQuestStage.READY);
    }

    private static Payloads.QuestMasterEntryData lockedSpecialEntry(String entryId,
                                                                    Component title,
                                                                    Component bodyLine,
                                                                    List<Component> rewards) {
        return entry(
                entryId,
                CATEGORY_SPECIAL,
                title,
                Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                Component.translatable("screen.village-quest.questmaster.status.locked").withStyle(ChatFormatting.DARK_GRAY),
                List.of(bodyLine),
                List.of(),
                rewards,
                ActionSpec.NONE,
                ActionSpec.NONE,
                true
        );
    }

    private static Payloads.QuestMasterEntryData lockedSpecialReputationEntry(ServerLevel world,
                                                                              UUID playerId,
                                                                              String entryId,
                                                                              Component title,
                                                                              SpecialQuestKind kind,
                                                                              List<Component> descriptionLines,
                                                                              List<Component> rewards) {
        RelicQuestProgressionService.RelicUnlockPath path = RelicQuestProgressionService.pathFor(kind);
        int current = path == null ? 0 : ReputationService.get(world, playerId, path.track());
        int required = path == null ? 0 : path.requiredReputation();
        Component progressLine = Component.translatable(
                "screen.village-quest.questmaster.special.reputation_progress",
                current,
                required,
                path == null ? Component.empty() : ReputationService.displayName(path.track())
        ).withStyle(ChatFormatting.GRAY);
        return entry(
                entryId,
                CATEGORY_SPECIAL,
                title,
                Component.translatable("screen.village-quest.questmaster.subtitle.special"),
                Component.translatable("screen.village-quest.questmaster.status.locked").withStyle(ChatFormatting.DARK_GRAY),
                descriptionLines,
                List.of(progressLine),
                rewards,
                ActionSpec.NONE,
                ActionSpec.NONE,
                true
        );
    }

    private static Payloads.QuestMasterEntryData buildSpecialIntroEntry() {
        return entry(
                ENTRY_SPECIAL_INTRO,
                CATEGORY_SPECIAL,
                Component.translatable("screen.village-quest.questmaster.special.intro.title"),
                Component.empty(),
                Component.translatable("screen.village-quest.questmaster.status.info").withStyle(ChatFormatting.AQUA),
                List.of(
                        Component.translatable("screen.village-quest.questmaster.special.intro.line_1").withStyle(ChatFormatting.GRAY),
                        Component.translatable("screen.village-quest.questmaster.special.intro.line_2").withStyle(ChatFormatting.GRAY)
                ),
                List.of(),
                List.of(),
                ActionSpec.NONE,
                ActionSpec.NONE,
                false
        );
    }

    private static Payloads.QuestMasterEntryData lockedStoryEntry(String entryId, Component title, Component bodyLine) {
        return entry(
                entryId,
                CATEGORY_STORY,
                title,
                Component.translatable("screen.village-quest.questmaster.subtitle.story"),
                Component.translatable("screen.village-quest.questmaster.status.locked").withStyle(ChatFormatting.DARK_GRAY),
                List.of(bodyLine),
                List.of(),
                List.of(),
                ActionSpec.NONE,
                ActionSpec.NONE,
                true
        );
    }

    private static String storyEntryId(StoryArcType type) {
        return type == null ? ENTRY_STORY_PREFIX : ENTRY_STORY_PREFIX + type.id();
    }

    private static StoryArcType storyArcFromEntryId(String entryId) {
        if (entryId == null || !entryId.startsWith(ENTRY_STORY_PREFIX)) {
            return null;
        }
        return StoryArcType.fromId(entryId.substring(ENTRY_STORY_PREFIX.length()));
    }

    private static void addEntry(List<Payloads.QuestMasterEntryData> entries, Payloads.QuestMasterEntryData entry) {
        if (entries != null && entry != null) {
            entries.add(entry);
        }
    }

    private static List<Component> rewardLines(ServerLevel world, UUID playerId, DailyQuestService.DailyQuestType questType) {
        if (questType == null) {
            return List.of();
        }
        long currencyReward = DailyQuestService.rewardCurrency(questType);
        int levelReward = DailyQuestService.rewardLevels(questType);
        List<Component> rewards = new ArrayList<>();
        rewards.add(Component.translatable("screen.village-quest.questmaster.reward.currency", CurrencyService.formatBalance(currencyReward)).withStyle(ChatFormatting.GOLD));
        ReputationService.ReputationReward reputationReward = ReputationService.rewardFor(questType);
        if (reputationReward != null && reputationReward.amount() > 0) {
            rewards.add(ReputationService.formatRewardLine(reputationReward.track(), reputationReward.amount()));
            appendNonEmpty(rewards, VillageProjectService.formatBonusRewardLine(world, playerId, reputationReward.track()));
            appendNonEmpty(rewards, VillageProjectService.formatRewardEchoLine(world, playerId, reputationReward.track()));
        }
        rewards.add(Component.translatable("screen.village-quest.questmaster.reward.levels", levelReward).withStyle(ChatFormatting.GREEN));
        return List.copyOf(rewards);
    }

    private static ReputationService.ReputationTrack dailyTrack(DailyQuestService.DailyQuestType questType) {
        ReputationService.ReputationReward reward = questType == null ? null : ReputationService.rewardFor(questType);
        return reward == null ? null : reward.track();
    }

    private static List<Component> starreachRewards() {
        return List.of(
                Component.translatable("screen.village-quest.questmaster.reward.item", Component.translatable("item.village-quest.starreach_ring")).withStyle(ChatFormatting.GOLD),
                Component.translatable("screen.village-quest.questmaster.reward.effect.starreach").withStyle(ChatFormatting.GRAY)
        );
    }

    private static List<Component> merchantRewards() {
        return List.of(
                Component.translatable("screen.village-quest.questmaster.reward.item", Component.translatable("item.village-quest.merchant_seal")).withStyle(ChatFormatting.GOLD),
                Component.translatable("screen.village-quest.questmaster.reward.effect.merchant").withStyle(ChatFormatting.GRAY)
        );
    }

    private static List<Component> fluteRewards() {
        return List.of(
                Component.translatable("screen.village-quest.questmaster.reward.item", Component.translatable("item.village-quest.shepherd_flute")).withStyle(ChatFormatting.GOLD),
                Component.translatable("screen.village-quest.questmaster.reward.effect.flute").withStyle(ChatFormatting.GRAY)
        );
    }

    private static List<Component> smokerRewards() {
        return List.of(
                Component.translatable("screen.village-quest.questmaster.reward.item", Component.translatable("item.village-quest.apiarists_smoker")).withStyle(ChatFormatting.GOLD),
                Component.translatable("screen.village-quest.questmaster.reward.effect.apiarist_smoker").withStyle(ChatFormatting.GRAY)
        );
    }

    private static List<Component> compassRewards() {
        return List.of(
                Component.translatable("screen.village-quest.questmaster.reward.item", Component.translatable("item.village-quest.surveyors_compass")).withStyle(ChatFormatting.GOLD),
                Component.translatable("screen.village-quest.questmaster.reward.item", Component.translatable("item.minecraft.netherite_ingot").copy().append(Component.literal(" x10").withStyle(ChatFormatting.GRAY))).withStyle(ChatFormatting.GOLD),
                Component.translatable("screen.village-quest.questmaster.reward.effect.surveyor_compass").withStyle(ChatFormatting.GRAY)
        );
    }

    private static List<Component> compassBiomeRewards() {
        return List.of(
                Component.translatable("screen.village-quest.questmaster.reward.effect.surveyor_compass_biomes").withStyle(ChatFormatting.GRAY)
        );
    }

    private static List<Component> weeklyRewards(ServerLevel world, UUID playerId, WeeklyQuestCompletion completion) {
        if (completion == null) {
            return List.of();
        }

        List<Component> rewards = new ArrayList<>();
        if (completion.currencyReward() > 0L) {
            rewards.add(Component.translatable("screen.village-quest.questmaster.reward.currency", CurrencyService.formatBalance(completion.currencyReward())).withStyle(ChatFormatting.GOLD));
        }
        if (completion.reputationTrack() != null && completion.reputationAmount() > 0) {
            rewards.add(ReputationService.formatRewardLine(completion.reputationTrack(), completion.reputationAmount()));
            appendNonEmpty(rewards, VillageProjectService.formatBonusRewardLine(world, playerId, completion.reputationTrack()));
            appendNonEmpty(rewards, VillageProjectService.formatRewardEchoLine(world, playerId, completion.reputationTrack()));
        }
        appendRewardStack(rewards, completion.rewardB());
        appendRewardStack(rewards, completion.rewardC());
        if (completion.levels() > 0) {
            rewards.add(Component.translatable("screen.village-quest.questmaster.reward.levels", completion.levels()).withStyle(ChatFormatting.GREEN));
        }
        return rewards;
    }

    private static List<Component> appendQuestEcho(ServerLevel world,
                                              UUID playerId,
                                              List<Component> baseLines,
                                              ReputationService.ReputationTrack track) {
        List<Component> lines = new ArrayList<>(baseLines == null ? List.of() : baseLines);
        appendNonEmpty(lines, VillageProjectService.formatQuestEchoLine(world, playerId, track));
        return List.copyOf(lines);
    }

    private static List<Component> appendRewardEcho(ServerLevel world,
                                               UUID playerId,
                                               List<Component> baseLines,
                                               ReputationService.ReputationTrack track) {
        List<Component> lines = new ArrayList<>(baseLines == null ? List.of() : baseLines);
        appendNonEmpty(lines, VillageProjectService.formatBonusRewardLine(world, playerId, track));
        appendNonEmpty(lines, VillageProjectService.formatRewardEchoLine(world, playerId, track));
        return List.copyOf(lines);
    }

    private static Payloads.QuestMasterEntryData buildStoryArchiveEntry(ServerLevel world, UUID playerId) {
        return entry(
                ENTRY_STORY_PREFIX + "archive",
                CATEGORY_STORY,
                Component.translatable("screen.village-quest.questmaster.story.archive.title"),
                Component.translatable("screen.village-quest.questmaster.subtitle.story"),
                Component.translatable("screen.village-quest.questmaster.status.completed").withStyle(ChatFormatting.AQUA),
                List.of(Component.translatable(
                        "screen.village-quest.questmaster.story.archive.body",
                        StoryQuestService.completedCount(world, playerId),
                        StoryQuestService.getStoryArcCount()
                ).withStyle(ChatFormatting.GRAY)),
                List.of(),
                List.of(),
                ActionSpec.NONE,
                ActionSpec.NONE,
                false
        );
    }

    private static Payloads.QuestMasterEntryData buildStoryCooldownEntry() {
        return entry(
                ENTRY_STORY_PREFIX + "cooldown",
                CATEGORY_STORY,
                Component.translatable("screen.village-quest.questmaster.story.cooldown.title"),
                Component.translatable("screen.village-quest.questmaster.subtitle.story"),
                Component.translatable("screen.village-quest.questmaster.status.info").withStyle(ChatFormatting.AQUA),
                List.of(Component.translatable("screen.village-quest.questmaster.story.cooldown.body").withStyle(ChatFormatting.GRAY)),
                List.of(),
                List.of(),
                ActionSpec.NONE,
                ActionSpec.NONE,
                false
        );
    }

    private static void appendNonEmpty(List<Component> lines, Component line) {
        if (lines == null || line == null || line.getString().isEmpty()) {
            return;
        }
        lines.add(line);
    }

    private static void appendRewardStack(List<Component> rewards, net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Component name = stack.getHoverName().copy();
        if (stack.getCount() > 1) {
            name = Component.empty().append(name).append(Component.literal(" x" + stack.getCount()).withStyle(ChatFormatting.GRAY));
        }
        rewards.add(Component.translatable("screen.village-quest.questmaster.reward.item", name).withStyle(ChatFormatting.AQUA));
    }

    private static Payloads.QuestMasterEntryData entry(String entryId,
                                                       String categoryId,
                                                       Component title,
                                                       Component subtitle,
                                                       Component status,
                                                       List<Component> descriptionLines,
                                                       List<Component> objectiveLines,
                                                       List<Component> rewardLines,
                                                       ActionSpec primary,
                                                       ActionSpec secondary,
                                                       boolean locked) {
        return new Payloads.QuestMasterEntryData(
                entryId,
                categoryId,
                title,
                subtitle,
                status,
                descriptionLines,
                objectiveLines,
                rewardLines,
                primary.action(),
                primary.label(),
                primary.enabled(),
                secondary.action(),
                secondary.label(),
                secondary.enabled(),
                locked
        );
    }

    private static QuestMasterEntity validateSession(ServerLevel world, ServerPlayer player, int entityId) {
        Integer sessionEntityId = OPEN_SESSIONS.get(player.getUUID());
        if (sessionEntityId == null || sessionEntityId != entityId) {
            return null;
        }
        QuestMasterEntity questMaster = resolveQuestMaster(world, entityId);
        if (questMaster == null || !questMaster.isCustomer(player) || player.distanceToSqr(questMaster) > QuestMasterService.getMaxInteractDistanceSquared()) {
            return null;
        }
        return questMaster;
    }

    private static QuestMasterEntity resolveOpenQuestMaster(ServerLevel world, ServerPlayer player) {
        Integer entityId = OPEN_SESSIONS.get(player.getUUID());
        if (entityId == null) {
            return null;
        }
        QuestMasterEntity questMaster = resolveQuestMaster(world, entityId);
        if (questMaster == null || !questMaster.isCustomer(player) || player.distanceToSqr(questMaster) > QuestMasterService.getMaxInteractDistanceSquared()) {
            return null;
        }
        return questMaster;
    }

    private static QuestMasterEntity resolveQuestMaster(ServerLevel world, int entityId) {
        if (world == null) {
            return null;
        }
        net.minecraft.world.entity.Entity entity = world.getEntity(entityId);
        return entity instanceof QuestMasterEntity questMaster && questMaster.isAlive() ? questMaster : null;
    }

    private static PlayerQuestData data(ServerLevel world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    private static void sendPayload(ServerPlayer player, Payloads.QuestMasterPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }
}
