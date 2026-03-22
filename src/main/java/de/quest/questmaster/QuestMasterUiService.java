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
import de.quest.quest.special.RelicQuestStage;
import de.quest.quest.special.ShardRelicQuestService;
import de.quest.quest.special.ShardRelicQuestStage;
import de.quest.quest.special.ShepherdFluteQuestService;
import de.quest.quest.special.SurveyorCompassQuestService;
import de.quest.quest.weekly.WeeklyQuestCompletion;
import de.quest.quest.weekly.WeeklyQuestDefinition;
import de.quest.quest.weekly.WeeklyQuestGenerator;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.reputation.ReputationService;
import de.quest.registry.ModItems;
import de.quest.util.TimeUtil;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class QuestMasterUiService {
    private static final String CATEGORY_DAILY = "daily";
    private static final String CATEGORY_WEEKLY = "weekly";
    private static final String CATEGORY_SPECIAL = "special";

    private static final String ENTRY_DAILY_MAIN = "daily_main";
    private static final String ENTRY_DAILY_BONUS = "daily_bonus";
    private static final String ENTRY_WEEKLY = "weekly_main";
    private static final String ENTRY_SPECIAL_SHARD = "special_shard";
    private static final String ENTRY_SPECIAL_MERCHANT = "special_merchant";
    private static final String ENTRY_SPECIAL_FLUTE = "special_flute";
    private static final String ENTRY_SPECIAL_SMOKER = "special_smoker";
    private static final String ENTRY_SPECIAL_COMPASS = "special_compass";

    private static final ConcurrentMap<UUID, Integer> OPEN_SESSIONS = new ConcurrentHashMap<>();

    private record ActionSpec(int action, Text label, boolean enabled) {
        private static final ActionSpec NONE = new ActionSpec(
                Payloads.QuestMasterActionPayload.ACTION_NONE,
                Text.empty(),
                false
        );
    }

    private QuestMasterUiService() {}

    public static void open(ServerWorld world, ServerPlayerEntity player, QuestMasterEntity questMaster) {
        if (world == null || player == null || questMaster == null) {
            return;
        }
        OPEN_SESSIONS.put(player.getUuid(), questMaster.getId());
        sendPayload(player, buildPayload(Payloads.QuestMasterPayload.ACTION_OPEN, world, player, questMaster));
    }

    public static void refreshIfOpen(ServerWorld world, ServerPlayerEntity player) {
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

    public static void refreshIfOpen(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return;
        }
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
        if (player != null) {
            refreshIfOpen(world, player);
        }
    }

    public static boolean isOpen(UUID playerId) {
        return playerId != null && OPEN_SESSIONS.containsKey(playerId);
    }

    public static void close(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        Integer entityId = OPEN_SESSIONS.remove(player.getUuid());
        if (entityId == null) {
            return;
        }
        sendPayload(player, new Payloads.QuestMasterPayload(
                Payloads.QuestMasterPayload.ACTION_CLOSE,
                entityId,
                Text.empty(),
                List.of(),
                List.of()
        ));
    }

    public static void handleSession(ServerPlayerEntity player, Payloads.QuestMasterSessionPayload payload) {
        if (player == null || payload == null || payload.action() != Payloads.QuestMasterSessionPayload.ACTION_CLOSE) {
            return;
        }

        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Integer entityId = OPEN_SESSIONS.get(player.getUuid());
        if (entityId == null || entityId != payload.entityId()) {
            return;
        }

        OPEN_SESSIONS.remove(player.getUuid());
        QuestMasterEntity questMaster = resolveQuestMaster(world, payload.entityId());
        if (questMaster != null && questMaster.isCustomer(player)) {
            questMaster.clearCustomer();
        }
    }

    public static void handleAction(ServerPlayerEntity player, Payloads.QuestMasterActionPayload payload) {
        if (player == null || payload == null || payload.action() == Payloads.QuestMasterActionPayload.ACTION_NONE) {
            return;
        }

        ServerWorld world = (ServerWorld) player.getEntityWorld();
        QuestMasterEntity questMaster = validateSession(world, player, payload.entityId());
        if (questMaster == null) {
            close(player);
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

    private static boolean handleDailyMainAction(ServerWorld world, ServerPlayerEntity player, int action) {
        UUID playerId = player.getUuid();
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

    private static boolean handleDailyBonusAction(ServerWorld world, ServerPlayerEntity player, int action) {
        UUID playerId = player.getUuid();
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

    private static boolean handleWeeklyAction(ServerWorld world, ServerPlayerEntity player, int action) {
        if (action == Payloads.QuestMasterActionPayload.ACTION_ACCEPT) {
            return WeeklyQuestService.acceptQuest(world, player);
        }
        if (action == Payloads.QuestMasterActionPayload.ACTION_CLAIM) {
            return WeeklyQuestService.completeIfEligible(world, player);
        }
        return false;
    }

    private static boolean handleSpecialShardAction(ServerWorld world, ServerPlayerEntity player, int action) {
        return switch (action) {
            case Payloads.QuestMasterActionPayload.ACTION_ACCEPT -> ShardRelicQuestService.acceptSpecialQuest(world, player);
            case Payloads.QuestMasterActionPayload.ACTION_CLAIM -> ShardRelicQuestService.claimFromQuestMaster(world, player);
            default -> false;
        };
    }

    private static boolean handleSpecialMerchantAction(ServerWorld world, ServerPlayerEntity player, int action) {
        return switch (action) {
            case Payloads.QuestMasterActionPayload.ACTION_ACCEPT -> MerchantSealQuestService.acceptQuest(world, player);
            case Payloads.QuestMasterActionPayload.ACTION_CLAIM -> MerchantSealQuestService.claimFromQuestMaster(world, player);
            default -> false;
        };
    }

    private static boolean handleSpecialFluteAction(ServerWorld world, ServerPlayerEntity player, int action) {
        return switch (action) {
            case Payloads.QuestMasterActionPayload.ACTION_ACCEPT -> ShepherdFluteQuestService.acceptQuest(world, player);
            case Payloads.QuestMasterActionPayload.ACTION_CLAIM -> ShepherdFluteQuestService.claimFromQuestMaster(world, player);
            default -> false;
        };
    }

    private static boolean handleSpecialSmokerAction(ServerWorld world, ServerPlayerEntity player, int action) {
        return switch (action) {
            case Payloads.QuestMasterActionPayload.ACTION_ACCEPT -> ApiaristSmokerQuestService.acceptQuest(world, player);
            case Payloads.QuestMasterActionPayload.ACTION_CLAIM -> ApiaristSmokerQuestService.claimFromQuestMaster(world, player);
            default -> false;
        };
    }

    private static boolean handleSpecialCompassAction(ServerWorld world, ServerPlayerEntity player, int action) {
        return switch (action) {
            case Payloads.QuestMasterActionPayload.ACTION_ACCEPT -> SurveyorCompassQuestService.acceptQuest(world, player);
            case Payloads.QuestMasterActionPayload.ACTION_CLAIM -> SurveyorCompassQuestService.claimFromQuestMaster(world, player);
            default -> false;
        };
    }

    private static Payloads.QuestMasterPayload buildPayload(int action,
                                                            ServerWorld world,
                                                            ServerPlayerEntity player,
                                                            QuestMasterEntity questMaster) {
        List<Payloads.QuestMasterEntryData> dailyEntries = buildDailyEntries(world, player);
        List<Payloads.QuestMasterEntryData> weeklyEntries = buildWeeklyEntries(world, player);
        List<Payloads.QuestMasterEntryData> specialEntries = buildSpecialEntries(world, player);

        List<Payloads.QuestMasterCategoryData> categories = List.of(
                new Payloads.QuestMasterCategoryData(CATEGORY_DAILY, Text.translatable("screen.village-quest.questmaster.category.daily"), displayCount(CATEGORY_DAILY, dailyEntries)),
                new Payloads.QuestMasterCategoryData(CATEGORY_WEEKLY, Text.translatable("screen.village-quest.questmaster.category.weekly"), displayCount(CATEGORY_WEEKLY, weeklyEntries)),
                new Payloads.QuestMasterCategoryData(CATEGORY_SPECIAL, Text.translatable("screen.village-quest.questmaster.category.special"), displayCount(CATEGORY_SPECIAL, specialEntries))
        );

        List<Payloads.QuestMasterEntryData> entries = new ArrayList<>();
        entries.addAll(dailyEntries);
        entries.addAll(weeklyEntries);
        entries.addAll(specialEntries);

        return new Payloads.QuestMasterPayload(
                action,
                questMaster.getId(),
                questMaster.getDisplayName(),
                categories,
                entries
        );
    }

    private static List<Payloads.QuestMasterEntryData> buildDailyEntries(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        PlayerQuestData data = data(world, playerId);
        DailyQuestService.DailyQuestType normalQuest = DailyQuestService.previewQuestChoice(world, playerId);
        DailyQuestDefinition normalDefinition = DailyQuestGenerator.definition(normalQuest);
        List<Payloads.QuestMasterEntryData> entries = new ArrayList<>();

        Text mainTitle = normalDefinition == null
                ? Text.translatable("screen.village-quest.questmaster.daily.none")
                : normalDefinition.title();
        List<Text> mainDescription = normalDefinition == null
                ? List.of(Text.translatable("screen.village-quest.questmaster.daily.unavailable").formatted(Formatting.GRAY))
                : List.of(normalDefinition.offerParagraph1(), normalDefinition.offerParagraph2());
        List<Text> mainObjectives = normalDefinition == null
                ? List.of()
                : List.of(normalDefinition.progressLine(world, playerId).copy().formatted(Formatting.GRAY));
        List<Text> mainRewards = rewardLines(normalQuest);
        ActionSpec primary = ActionSpec.NONE;
        ActionSpec secondary = ActionSpec.NONE;
        Text status;

        boolean normalActive = DailyQuestService.isAcceptedToday(world, playerId) && !DailyQuestService.hasCompletedToday(world, playerId);
        if (normalActive) {
            boolean ready = normalDefinition != null && normalDefinition.isComplete(world, player);
            status = ready
                    ? Text.translatable("screen.village-quest.questmaster.status.ready").formatted(Formatting.GOLD)
                    : Text.translatable("screen.village-quest.questmaster.status.active").formatted(Formatting.GREEN);
            DailyQuestService.QuestStatus questStatus = DailyQuestService.openQuestStatus(world, playerId);
            if (questStatus != null) {
                mainObjectives = List.of(questStatus.progressLine().copy().formatted(Formatting.GRAY));
            }
            primary = ready
                    ? new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CLAIM, Text.translatable("screen.village-quest.questmaster.action.claim"), true)
                    : ActionSpec.NONE;
            secondary = new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CANCEL, Text.translatable("screen.village-quest.questmaster.action.cancel"), true);
        } else if (DailyQuestService.hasCompletedToday(world, playerId)) {
            status = Text.translatable("screen.village-quest.questmaster.status.completed").formatted(Formatting.AQUA);
            mainDescription = List.of(Text.translatable("screen.village-quest.questmaster.daily.completed_today").formatted(Formatting.GRAY));
            mainObjectives = List.of();
        } else {
            status = Text.translatable("screen.village-quest.questmaster.status.available").formatted(Formatting.YELLOW);
            primary = new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_ACCEPT, Text.translatable("screen.village-quest.questmaster.action.accept"), true);
        }

        entries.add(entry(
                ENTRY_DAILY_MAIN,
                CATEGORY_DAILY,
                mainTitle,
                Text.translatable("screen.village-quest.questmaster.subtitle.daily"),
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

    private static Payloads.QuestMasterEntryData buildDailyBonusEntry(ServerWorld world,
                                                                      ServerPlayerEntity player,
                                                                      PlayerQuestData data) {
        UUID playerId = player.getUuid();
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
                    Text.translatable("text.village-quest.daily.shard.title").formatted(Formatting.LIGHT_PURPLE),
                    Text.translatable("screen.village-quest.questmaster.subtitle.daily_bonus"),
                    Text.translatable("screen.village-quest.questmaster.status.available").formatted(Formatting.YELLOW),
                    List.of(
                            Text.translatable("text.village-quest.daily.shard.body.1").formatted(Formatting.GRAY),
                            Text.translatable("text.village-quest.daily.shard.body.2").formatted(Formatting.GRAY)
                    ),
                    List.of(Text.translatable("screen.village-quest.questmaster.daily.shard_cost").formatted(Formatting.GRAY)),
                    List.of(Text.translatable("screen.village-quest.questmaster.daily.shard_reward").formatted(Formatting.LIGHT_PURPLE)),
                    new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_ACCEPT, Text.translatable("screen.village-quest.questmaster.action.use_shard"), true),
                    ActionSpec.NONE,
                    false
            );
        }

        DailyQuestDefinition bonusDefinition = DailyQuestGenerator.definition(data.getBonusChoice());
        Text title = bonusDefinition == null
                ? Text.translatable("screen.village-quest.questmaster.daily.bonus")
                : bonusDefinition.title();
        List<Text> description = bonusDefinition == null
                ? List.of(Text.translatable("screen.village-quest.questmaster.daily.bonus_waiting").formatted(Formatting.GRAY))
                : List.of(bonusDefinition.offerParagraph1(), bonusDefinition.offerParagraph2());
        List<Text> objectives = bonusDefinition == null
                ? List.of()
                : List.of(bonusDefinition.progressLine(world, playerId).copy().formatted(Formatting.GRAY));
        List<Text> rewards = rewardLines(data.getBonusChoice());
        ActionSpec primary = ActionSpec.NONE;
        ActionSpec secondary = ActionSpec.NONE;
        Text status;

        if (bonusAccepted && !bonusCompleted) {
            boolean ready = bonusDefinition != null && bonusDefinition.isComplete(world, player);
            status = ready
                    ? Text.translatable("screen.village-quest.questmaster.status.ready").formatted(Formatting.GOLD)
                    : Text.translatable("screen.village-quest.questmaster.status.active").formatted(Formatting.GREEN);
            DailyQuestService.QuestStatus questStatus = DailyQuestService.openQuestStatus(world, playerId);
            if (questStatus != null) {
                objectives = List.of(questStatus.progressLine().copy().formatted(Formatting.GRAY));
            }
            primary = ready
                    ? new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CLAIM, Text.translatable("screen.village-quest.questmaster.action.claim"), true)
                    : ActionSpec.NONE;
            secondary = new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CANCEL, Text.translatable("screen.village-quest.questmaster.action.cancel"), true);
        } else if (bonusCompleted) {
            status = Text.translatable("screen.village-quest.questmaster.status.completed").formatted(Formatting.AQUA);
            description = List.of(Text.translatable("screen.village-quest.questmaster.daily.bonus_completed").formatted(Formatting.GRAY));
            objectives = List.of();
        } else {
            status = Text.translatable("screen.village-quest.questmaster.status.available").formatted(Formatting.YELLOW);
            primary = new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_ACCEPT, Text.translatable("screen.village-quest.questmaster.action.accept"), true);
        }

        return entry(
                ENTRY_DAILY_BONUS,
                CATEGORY_DAILY,
                title,
                Text.translatable("screen.village-quest.questmaster.subtitle.daily_bonus"),
                status,
                description,
                objectives,
                rewards,
                primary,
                secondary,
                false
        );
    }

    private static List<Payloads.QuestMasterEntryData> buildWeeklyEntries(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        WeeklyQuestService.WeeklyQuestType questType = WeeklyQuestService.previewQuestChoice(world, playerId);
        WeeklyQuestDefinition definition = WeeklyQuestGenerator.definition(questType);
        if (definition == null) {
            return List.of(entry(
                    ENTRY_WEEKLY,
                    CATEGORY_WEEKLY,
                    Text.translatable("screen.village-quest.questmaster.weekly.title"),
                    Text.translatable("screen.village-quest.questmaster.subtitle.weekly"),
                    Text.translatable("screen.village-quest.questmaster.status.locked").formatted(Formatting.DARK_GRAY),
                    List.of(Text.translatable("screen.village-quest.questmaster.weekly.unavailable").formatted(Formatting.GRAY)),
                    List.of(),
                    List.of(),
                    ActionSpec.NONE,
                    ActionSpec.NONE,
                    true
            ));
        }

        WeeklyQuestCompletion completion = WeeklyQuestService.previewCompletion(questType);
        List<Text> rewards = weeklyRewards(completion);
        boolean accepted = WeeklyQuestService.isAcceptedThisWeek(world, playerId);
        boolean completed = WeeklyQuestService.hasCompletedThisWeek(world, playerId);
        List<Text> description = List.of(definition.offerParagraph1(), definition.offerParagraph2());
        List<Text> objectives = List.copyOf(definition.progressLines(world, playerId));
        ActionSpec primary = ActionSpec.NONE;
        Text status;
        boolean locked = false;

        if (accepted && !completed) {
            boolean ready = definition.isComplete(world, player);
            status = ready
                    ? Text.translatable("screen.village-quest.questmaster.status.ready").formatted(Formatting.GOLD)
                    : Text.translatable("screen.village-quest.questmaster.status.active").formatted(Formatting.GREEN);
            if (ready) {
                primary = new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CLAIM, Text.translatable("screen.village-quest.questmaster.action.claim"), true);
            }
        } else if (completed) {
            status = Text.translatable("screen.village-quest.questmaster.status.completed").formatted(Formatting.AQUA);
            description = List.of(Text.translatable("screen.village-quest.questmaster.weekly.completed").formatted(Formatting.GRAY));
            objectives = List.of(Text.translatable("screen.village-quest.questmaster.weekly.next_reset").formatted(Formatting.GRAY));
        } else {
            status = Text.translatable("screen.village-quest.questmaster.status.available").formatted(Formatting.YELLOW);
            primary = new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_ACCEPT, Text.translatable("screen.village-quest.questmaster.action.accept"), true);
        }

        return List.of(entry(
                ENTRY_WEEKLY,
                CATEGORY_WEEKLY,
                definition.title(),
                Text.translatable("screen.village-quest.questmaster.subtitle.weekly"),
                status,
                description,
                objectives,
                rewards,
                primary,
                ActionSpec.NONE,
                locked
        ));
    }

    private static List<Payloads.QuestMasterEntryData> buildSpecialEntries(ServerWorld world, ServerPlayerEntity player) {
        return List.of(
                buildShardEntry(world, player),
                buildSmokerEntry(world, player),
                buildCompassEntry(world, player),
                buildMerchantEntry(world, player),
                buildFluteEntry(world, player)
        );
    }

    private static Payloads.QuestMasterEntryData buildShardEntry(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        PlayerQuestData data = data(world, playerId);
        int shardCount = ModItems.MAGIC_SHARD == null ? 0 : DailyQuestService.countInventoryItem(player, ModItems.MAGIC_SHARD);
        boolean dailyActive = DailyQuestService.openQuestStatus(world, playerId) != null;
        boolean otherSpecialActive = hasOtherActiveSpecial(world, playerId, ENTRY_SPECIAL_SHARD);

        if (data.getShardRelicQuestStage() == ShardRelicQuestStage.TRIAL_ACTIVE) {
            return entry(
                    ENTRY_SPECIAL_SHARD,
                    CATEGORY_SPECIAL,
                    Text.translatable("quest.village-quest.special.shards.title"),
                    Text.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Text.translatable("screen.village-quest.questmaster.status.active").formatted(Formatting.GREEN),
                    List.of(Text.translatable("screen.village-quest.questmaster.special.shards.active").formatted(Formatting.GRAY)),
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
                    Text.translatable("quest.village-quest.special.shards.title"),
                    Text.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Text.translatable("screen.village-quest.questmaster.status.ready").formatted(Formatting.GOLD),
                    List.of(Text.translatable("screen.village-quest.questmaster.special.shards.ready").formatted(Formatting.GRAY)),
                    List.copyOf(ShardRelicQuestService.openStatus(world, playerId).lines()),
                    starreachRewards(),
                    new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CLAIM, Text.translatable("screen.village-quest.questmaster.action.receive_map"), true),
                    ActionSpec.NONE,
                    false
            );
        }
        if (data.getShardRelicQuestStage() == ShardRelicQuestStage.CACHE_HUNT) {
            return entry(
                    ENTRY_SPECIAL_SHARD,
                    CATEGORY_SPECIAL,
                    Text.translatable("quest.village-quest.special.shards.title"),
                    Text.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Text.translatable("screen.village-quest.questmaster.status.active").formatted(Formatting.GREEN),
                    List.of(Text.translatable("screen.village-quest.questmaster.special.shards.cache").formatted(Formatting.GRAY)),
                    List.copyOf(ShardRelicQuestService.openStatus(world, playerId).lines()),
                    starreachRewards(),
                    new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CLAIM, Text.translatable("screen.village-quest.questmaster.action.reissue_map"), true),
                    ActionSpec.NONE,
                    false
            );
        }
        if (data.getShardRelicQuestStage() == ShardRelicQuestStage.COMPLETED) {
            return entry(
                    ENTRY_SPECIAL_SHARD,
                    CATEGORY_SPECIAL,
                    Text.translatable("quest.village-quest.special.shards.title"),
                    Text.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Text.translatable("screen.village-quest.questmaster.status.completed").formatted(Formatting.AQUA),
                    List.of(Text.translatable("screen.village-quest.questmaster.special.completed").formatted(Formatting.GRAY)),
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
                    Text.translatable("quest.village-quest.special.shards.title"),
                    Text.translatable("screen.village-quest.questmaster.special.locked_other").formatted(Formatting.GRAY),
                    starreachRewards()
            );
        }
        if (dailyActive) {
            return lockedSpecialEntry(
                    ENTRY_SPECIAL_SHARD,
                    Text.translatable("quest.village-quest.special.shards.title"),
                    Text.translatable("screen.village-quest.questmaster.special.locked_daily").formatted(Formatting.GRAY),
                    starreachRewards()
            );
        }
        if (shardCount >= 10) {
            return entry(
                    ENTRY_SPECIAL_SHARD,
                    CATEGORY_SPECIAL,
                    Text.translatable("quest.village-quest.special.shards.title"),
                    Text.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Text.translatable("screen.village-quest.questmaster.status.available").formatted(Formatting.YELLOW),
                    List.of(
                            Text.translatable("quest.village-quest.special.shards.offer.1").formatted(Formatting.GRAY),
                            Text.translatable("quest.village-quest.special.shards.offer.2", 10).formatted(Formatting.GRAY)
                    ),
                    List.of(Text.translatable("screen.village-quest.questmaster.special.shards.requirement", shardCount, 10).formatted(Formatting.GRAY)),
                    starreachRewards(),
                    new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_ACCEPT, Text.translatable("screen.village-quest.questmaster.action.accept"), true),
                    ActionSpec.NONE,
                    false
            );
        }

        return lockedSpecialEntry(
                ENTRY_SPECIAL_SHARD,
                Text.translatable("quest.village-quest.special.shards.title"),
                Text.translatable("screen.village-quest.questmaster.special.shards.locked", shardCount, 10).formatted(Formatting.GRAY),
                starreachRewards()
        );
    }

    private static int displayCount(String categoryId, List<Payloads.QuestMasterEntryData> entries) {
        return entries == null ? 0 : entries.size();
    }

    private static Payloads.QuestMasterEntryData buildSmokerEntry(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        PlayerQuestData data = data(world, playerId);
        int reputation = ReputationService.get(world, playerId, ReputationService.ReputationTrack.FARMING);
        boolean dailyActive = DailyQuestService.openQuestStatus(world, playerId) != null;
        boolean otherSpecialActive = hasOtherActiveSpecial(world, playerId, ENTRY_SPECIAL_SMOKER);

        if (data.getApiaristSmokerQuestStage() == RelicQuestStage.ACTIVE) {
            return entry(
                    ENTRY_SPECIAL_SMOKER,
                    CATEGORY_SPECIAL,
                    Text.translatable("quest.village-quest.special.apiarist_smoker.title"),
                    Text.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Text.translatable("screen.village-quest.questmaster.status.active").formatted(Formatting.GREEN),
                    List.of(Text.translatable("screen.village-quest.questmaster.special.apiarist_smoker.active").formatted(Formatting.GRAY)),
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
                    Text.translatable("quest.village-quest.special.apiarist_smoker.title"),
                    Text.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Text.translatable("screen.village-quest.questmaster.status.ready").formatted(Formatting.GOLD),
                    List.of(Text.translatable("screen.village-quest.questmaster.special.apiarist_smoker.ready").formatted(Formatting.GRAY)),
                    List.copyOf(ApiaristSmokerQuestService.openStatus(world, playerId).lines()),
                    smokerRewards(),
                    new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CLAIM, Text.translatable("screen.village-quest.questmaster.action.claim"), true),
                    ActionSpec.NONE,
                    false
            );
        }
        if (data.getApiaristSmokerQuestStage() == RelicQuestStage.COMPLETED) {
            return entry(
                    ENTRY_SPECIAL_SMOKER,
                    CATEGORY_SPECIAL,
                    Text.translatable("quest.village-quest.special.apiarist_smoker.title"),
                    Text.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Text.translatable("screen.village-quest.questmaster.status.completed").formatted(Formatting.AQUA),
                    List.of(Text.translatable("screen.village-quest.questmaster.special.completed").formatted(Formatting.GRAY)),
                    List.of(),
                    smokerRewards(),
                    ActionSpec.NONE,
                    ActionSpec.NONE,
                    false
            );
        }

        if (otherSpecialActive) {
            return lockedSpecialEntry(
                    ENTRY_SPECIAL_SMOKER,
                    Text.translatable("quest.village-quest.special.apiarist_smoker.title"),
                    Text.translatable("screen.village-quest.questmaster.special.locked_other").formatted(Formatting.GRAY),
                    smokerRewards()
            );
        }
        if (dailyActive) {
            return lockedSpecialEntry(
                    ENTRY_SPECIAL_SMOKER,
                    Text.translatable("quest.village-quest.special.apiarist_smoker.title"),
                    Text.translatable("screen.village-quest.questmaster.special.locked_daily").formatted(Formatting.GRAY),
                    smokerRewards()
            );
        }
        if (reputation >= ApiaristSmokerQuestService.REQUIRED_FARMING_REPUTATION) {
            return entry(
                    ENTRY_SPECIAL_SMOKER,
                    CATEGORY_SPECIAL,
                    Text.translatable("quest.village-quest.special.apiarist_smoker.title"),
                    Text.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Text.translatable("screen.village-quest.questmaster.status.available").formatted(Formatting.YELLOW),
                    List.of(
                            Text.translatable("quest.village-quest.special.apiarist_smoker.offer.1").formatted(Formatting.GRAY),
                            Text.translatable("quest.village-quest.special.apiarist_smoker.offer.2").formatted(Formatting.GRAY)
                    ),
                    List.of(Text.translatable("screen.village-quest.questmaster.special.apiarist_smoker.requirement").formatted(Formatting.GRAY)),
                    smokerRewards(),
                    new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_ACCEPT, Text.translatable("screen.village-quest.questmaster.action.accept"), true),
                    ActionSpec.NONE,
                    false
            );
        }

        return lockedSpecialEntry(
                ENTRY_SPECIAL_SMOKER,
                Text.translatable("quest.village-quest.special.apiarist_smoker.title"),
                Text.translatable(
                        "screen.village-quest.questmaster.special.apiarist_smoker.locked",
                        reputation,
                        ApiaristSmokerQuestService.REQUIRED_FARMING_REPUTATION
                ).formatted(Formatting.GRAY),
                smokerRewards()
        );
    }

    private static Payloads.QuestMasterEntryData buildCompassEntry(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        PlayerQuestData data = data(world, playerId);
        int reputation = ReputationService.get(world, playerId, ReputationService.ReputationTrack.CRAFTING);
        boolean dailyActive = DailyQuestService.openQuestStatus(world, playerId) != null;
        boolean otherSpecialActive = hasOtherActiveSpecial(world, playerId, ENTRY_SPECIAL_COMPASS);

        if (data.getSurveyorCompassQuestStage() == RelicQuestStage.ACTIVE) {
            return entry(
                    ENTRY_SPECIAL_COMPASS,
                    CATEGORY_SPECIAL,
                    Text.translatable("quest.village-quest.special.surveyor_compass.title"),
                    Text.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Text.translatable("screen.village-quest.questmaster.status.active").formatted(Formatting.GREEN),
                    List.of(Text.translatable("screen.village-quest.questmaster.special.surveyor_compass.active").formatted(Formatting.GRAY)),
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
                    Text.translatable("quest.village-quest.special.surveyor_compass.title"),
                    Text.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Text.translatable("screen.village-quest.questmaster.status.ready").formatted(Formatting.GOLD),
                    List.of(Text.translatable("screen.village-quest.questmaster.special.surveyor_compass.ready").formatted(Formatting.GRAY)),
                    List.copyOf(SurveyorCompassQuestService.openStatus(world, playerId).lines()),
                    compassRewards(),
                    new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CLAIM, Text.translatable("screen.village-quest.questmaster.action.claim"), true),
                    ActionSpec.NONE,
                    false
            );
        }
        if (data.getSurveyorCompassQuestStage() == RelicQuestStage.COMPLETED) {
            return entry(
                    ENTRY_SPECIAL_COMPASS,
                    CATEGORY_SPECIAL,
                    Text.translatable("quest.village-quest.special.surveyor_compass.title"),
                    Text.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Text.translatable("screen.village-quest.questmaster.status.completed").formatted(Formatting.AQUA),
                    List.of(Text.translatable("screen.village-quest.questmaster.special.completed").formatted(Formatting.GRAY)),
                    List.of(),
                    compassRewards(),
                    ActionSpec.NONE,
                    ActionSpec.NONE,
                    false
            );
        }

        if (otherSpecialActive) {
            return lockedSpecialEntry(
                    ENTRY_SPECIAL_COMPASS,
                    Text.translatable("quest.village-quest.special.surveyor_compass.title"),
                    Text.translatable("screen.village-quest.questmaster.special.locked_other").formatted(Formatting.GRAY),
                    compassRewards()
            );
        }
        if (dailyActive) {
            return lockedSpecialEntry(
                    ENTRY_SPECIAL_COMPASS,
                    Text.translatable("quest.village-quest.special.surveyor_compass.title"),
                    Text.translatable("screen.village-quest.questmaster.special.locked_daily").formatted(Formatting.GRAY),
                    compassRewards()
            );
        }
        if (reputation >= SurveyorCompassQuestService.REQUIRED_CRAFTING_REPUTATION) {
            return entry(
                    ENTRY_SPECIAL_COMPASS,
                    CATEGORY_SPECIAL,
                    Text.translatable("quest.village-quest.special.surveyor_compass.title"),
                    Text.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Text.translatable("screen.village-quest.questmaster.status.available").formatted(Formatting.YELLOW),
                    List.of(
                            Text.translatable("quest.village-quest.special.surveyor_compass.offer.1").formatted(Formatting.GRAY),
                            Text.translatable("quest.village-quest.special.surveyor_compass.offer.2").formatted(Formatting.GRAY)
                    ),
                    List.of(Text.translatable("screen.village-quest.questmaster.special.surveyor_compass.requirement").formatted(Formatting.GRAY)),
                    compassRewards(),
                    new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_ACCEPT, Text.translatable("screen.village-quest.questmaster.action.accept"), true),
                    ActionSpec.NONE,
                    false
            );
        }

        return lockedSpecialEntry(
                ENTRY_SPECIAL_COMPASS,
                Text.translatable("quest.village-quest.special.surveyor_compass.title"),
                Text.translatable(
                        "screen.village-quest.questmaster.special.surveyor_compass.locked",
                        reputation,
                        SurveyorCompassQuestService.REQUIRED_CRAFTING_REPUTATION
                ).formatted(Formatting.GRAY),
                compassRewards()
        );
    }

    private static Payloads.QuestMasterEntryData buildMerchantEntry(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        PlayerQuestData data = data(world, playerId);
        int reputation = ReputationService.get(world, playerId, ReputationService.ReputationTrack.TRADE);
        boolean dailyActive = DailyQuestService.openQuestStatus(world, playerId) != null;
        boolean otherSpecialActive = hasOtherActiveSpecial(world, playerId, ENTRY_SPECIAL_MERCHANT);

        if (data.getMerchantSealQuestStage() == RelicQuestStage.ACTIVE) {
            return entry(
                    ENTRY_SPECIAL_MERCHANT,
                    CATEGORY_SPECIAL,
                    Text.translatable("quest.village-quest.special.merchant.title"),
                    Text.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Text.translatable("screen.village-quest.questmaster.status.active").formatted(Formatting.GREEN),
                    List.of(Text.translatable("screen.village-quest.questmaster.special.merchant.active").formatted(Formatting.GRAY)),
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
                    Text.translatable("quest.village-quest.special.merchant.title"),
                    Text.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Text.translatable("screen.village-quest.questmaster.status.ready").formatted(Formatting.GOLD),
                    List.of(Text.translatable("screen.village-quest.questmaster.special.merchant.ready").formatted(Formatting.GRAY)),
                    List.copyOf(MerchantSealQuestService.openStatus(world, playerId).lines()),
                    merchantRewards(),
                    new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CLAIM, Text.translatable("screen.village-quest.questmaster.action.claim"), true),
                    ActionSpec.NONE,
                    false
            );
        }
        if (data.getMerchantSealQuestStage() == RelicQuestStage.COMPLETED) {
            return entry(
                    ENTRY_SPECIAL_MERCHANT,
                    CATEGORY_SPECIAL,
                    Text.translatable("quest.village-quest.special.merchant.title"),
                    Text.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Text.translatable("screen.village-quest.questmaster.status.completed").formatted(Formatting.AQUA),
                    List.of(Text.translatable("screen.village-quest.questmaster.special.completed").formatted(Formatting.GRAY)),
                    List.of(),
                    merchantRewards(),
                    ActionSpec.NONE,
                    ActionSpec.NONE,
                    false
            );
        }

        if (otherSpecialActive) {
            return lockedSpecialEntry(
                    ENTRY_SPECIAL_MERCHANT,
                    Text.translatable("quest.village-quest.special.merchant.title"),
                    Text.translatable("screen.village-quest.questmaster.special.locked_other").formatted(Formatting.GRAY),
                    merchantRewards()
            );
        }
        if (dailyActive) {
            return lockedSpecialEntry(
                    ENTRY_SPECIAL_MERCHANT,
                    Text.translatable("quest.village-quest.special.merchant.title"),
                    Text.translatable("screen.village-quest.questmaster.special.locked_daily").formatted(Formatting.GRAY),
                    merchantRewards()
            );
        }
        if (reputation >= MerchantSealQuestService.REQUIRED_TRADE_REPUTATION) {
            return entry(
                    ENTRY_SPECIAL_MERCHANT,
                    CATEGORY_SPECIAL,
                    Text.translatable("quest.village-quest.special.merchant.title"),
                    Text.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Text.translatable("screen.village-quest.questmaster.status.available").formatted(Formatting.YELLOW),
                    List.of(
                            Text.translatable("quest.village-quest.special.merchant.offer.1").formatted(Formatting.GRAY),
                            Text.translatable("quest.village-quest.special.merchant.offer.2").formatted(Formatting.GRAY)
                    ),
                    List.of(Text.translatable("screen.village-quest.questmaster.special.merchant.requirement").formatted(Formatting.GRAY)),
                    merchantRewards(),
                    new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_ACCEPT, Text.translatable("screen.village-quest.questmaster.action.accept"), true),
                    ActionSpec.NONE,
                    false
            );
        }

        return lockedSpecialEntry(
                ENTRY_SPECIAL_MERCHANT,
                Text.translatable("quest.village-quest.special.merchant.title"),
                Text.translatable(
                        "screen.village-quest.questmaster.special.merchant.locked",
                        reputation,
                        MerchantSealQuestService.REQUIRED_TRADE_REPUTATION
                ).formatted(Formatting.GRAY),
                merchantRewards()
        );
    }

    private static Payloads.QuestMasterEntryData buildFluteEntry(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        PlayerQuestData data = data(world, playerId);
        int reputation = ReputationService.get(world, playerId, ReputationService.ReputationTrack.ANIMALS);
        boolean dailyActive = DailyQuestService.openQuestStatus(world, playerId) != null;
        boolean otherSpecialActive = hasOtherActiveSpecial(world, playerId, ENTRY_SPECIAL_FLUTE);

        if (data.getShepherdFluteQuestStage() == RelicQuestStage.ACTIVE) {
            return entry(
                    ENTRY_SPECIAL_FLUTE,
                    CATEGORY_SPECIAL,
                    Text.translatable("quest.village-quest.special.flute.title"),
                    Text.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Text.translatable("screen.village-quest.questmaster.status.active").formatted(Formatting.GREEN),
                    List.of(Text.translatable("screen.village-quest.questmaster.special.flute.active").formatted(Formatting.GRAY)),
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
                    Text.translatable("quest.village-quest.special.flute.title"),
                    Text.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Text.translatable("screen.village-quest.questmaster.status.ready").formatted(Formatting.GOLD),
                    List.of(Text.translatable("screen.village-quest.questmaster.special.flute.ready").formatted(Formatting.GRAY)),
                    List.copyOf(ShepherdFluteQuestService.openStatus(world, playerId).lines()),
                    fluteRewards(),
                    new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_CLAIM, Text.translatable("screen.village-quest.questmaster.action.claim"), true),
                    ActionSpec.NONE,
                    false
            );
        }
        if (data.getShepherdFluteQuestStage() == RelicQuestStage.COMPLETED) {
            return entry(
                    ENTRY_SPECIAL_FLUTE,
                    CATEGORY_SPECIAL,
                    Text.translatable("quest.village-quest.special.flute.title"),
                    Text.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Text.translatable("screen.village-quest.questmaster.status.completed").formatted(Formatting.AQUA),
                    List.of(Text.translatable("screen.village-quest.questmaster.special.completed").formatted(Formatting.GRAY)),
                    List.of(),
                    fluteRewards(),
                    ActionSpec.NONE,
                    ActionSpec.NONE,
                    false
            );
        }

        if (otherSpecialActive) {
            return lockedSpecialEntry(
                    ENTRY_SPECIAL_FLUTE,
                    Text.translatable("quest.village-quest.special.flute.title"),
                    Text.translatable("screen.village-quest.questmaster.special.locked_other").formatted(Formatting.GRAY),
                    fluteRewards()
            );
        }
        if (dailyActive) {
            return lockedSpecialEntry(
                    ENTRY_SPECIAL_FLUTE,
                    Text.translatable("quest.village-quest.special.flute.title"),
                    Text.translatable("screen.village-quest.questmaster.special.locked_daily").formatted(Formatting.GRAY),
                    fluteRewards()
            );
        }
        if (reputation >= ShepherdFluteQuestService.REQUIRED_ANIMAL_REPUTATION) {
            return entry(
                    ENTRY_SPECIAL_FLUTE,
                    CATEGORY_SPECIAL,
                    Text.translatable("quest.village-quest.special.flute.title"),
                    Text.translatable("screen.village-quest.questmaster.subtitle.special"),
                    Text.translatable("screen.village-quest.questmaster.status.available").formatted(Formatting.YELLOW),
                    List.of(
                            Text.translatable("quest.village-quest.special.flute.offer.1").formatted(Formatting.GRAY),
                            Text.translatable("quest.village-quest.special.flute.offer.2").formatted(Formatting.GRAY)
                    ),
                    List.of(Text.translatable("screen.village-quest.questmaster.special.flute.requirement").formatted(Formatting.GRAY)),
                    fluteRewards(),
                    new ActionSpec(Payloads.QuestMasterActionPayload.ACTION_ACCEPT, Text.translatable("screen.village-quest.questmaster.action.accept"), true),
                    ActionSpec.NONE,
                    false
            );
        }

        return lockedSpecialEntry(
                ENTRY_SPECIAL_FLUTE,
                Text.translatable("quest.village-quest.special.flute.title"),
                Text.translatable(
                        "screen.village-quest.questmaster.special.flute.locked",
                        reputation,
                        ShepherdFluteQuestService.REQUIRED_ANIMAL_REPUTATION
                ).formatted(Formatting.GRAY),
                fluteRewards()
        );
    }

    private static boolean hasOtherActiveSpecial(ServerWorld world, UUID playerId, String exceptEntryId) {
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
                                                                    Text title,
                                                                    Text bodyLine,
                                                                    List<Text> rewards) {
        return entry(
                entryId,
                CATEGORY_SPECIAL,
                title,
                Text.translatable("screen.village-quest.questmaster.subtitle.special"),
                Text.translatable("screen.village-quest.questmaster.status.locked").formatted(Formatting.DARK_GRAY),
                List.of(bodyLine),
                List.of(),
                rewards,
                ActionSpec.NONE,
                ActionSpec.NONE,
                true
        );
    }

    private static List<Text> rewardLines(DailyQuestService.DailyQuestType questType) {
        if (questType == null) {
            return List.of();
        }
        long currencyReward = switch (DailyQuestService.difficulty(questType)) {
            case EASY -> CurrencyService.SILVERMARK * 2L;
            case STANDARD -> CurrencyService.SILVERMARK * 5L;
            case HARD -> CurrencyService.CROWN;
        };
        int xpReward = switch (DailyQuestService.difficulty(questType)) {
            case EASY -> 120;
            case STANDARD -> 150;
            case HARD -> 180;
        };
        return List.of(
                Text.translatable("screen.village-quest.questmaster.reward.currency", CurrencyService.formatBalance(currencyReward)).formatted(Formatting.GOLD),
                Text.translatable("screen.village-quest.questmaster.reward.xp", xpReward).formatted(Formatting.GREEN)
        );
    }

    private static List<Text> starreachRewards() {
        return List.of(
                Text.translatable("screen.village-quest.questmaster.reward.item", Text.translatable("item.village-quest.starreach_ring")).formatted(Formatting.GOLD),
                Text.translatable("screen.village-quest.questmaster.reward.effect.starreach").formatted(Formatting.GRAY)
        );
    }

    private static List<Text> merchantRewards() {
        return List.of(
                Text.translatable("screen.village-quest.questmaster.reward.item", Text.translatable("item.village-quest.merchant_seal")).formatted(Formatting.GOLD),
                Text.translatable("screen.village-quest.questmaster.reward.effect.merchant").formatted(Formatting.GRAY)
        );
    }

    private static List<Text> fluteRewards() {
        return List.of(
                Text.translatable("screen.village-quest.questmaster.reward.item", Text.translatable("item.village-quest.shepherd_flute")).formatted(Formatting.GOLD),
                Text.translatable("screen.village-quest.questmaster.reward.effect.flute").formatted(Formatting.GRAY)
        );
    }

    private static List<Text> smokerRewards() {
        return List.of(
                Text.translatable("screen.village-quest.questmaster.reward.item", Text.translatable("item.village-quest.apiarists_smoker")).formatted(Formatting.GOLD),
                Text.translatable("screen.village-quest.questmaster.reward.effect.apiarist_smoker").formatted(Formatting.GRAY)
        );
    }

    private static List<Text> compassRewards() {
        return List.of(
                Text.translatable("screen.village-quest.questmaster.reward.item", Text.translatable("item.village-quest.surveyors_compass")).formatted(Formatting.GOLD),
                Text.translatable("screen.village-quest.questmaster.reward.item", Text.translatable("item.minecraft.netherite_ingot").copy().append(Text.literal(" x10").formatted(Formatting.GRAY))).formatted(Formatting.GOLD),
                Text.translatable("screen.village-quest.questmaster.reward.effect.surveyor_compass").formatted(Formatting.GRAY)
        );
    }

    private static List<Text> weeklyRewards(WeeklyQuestCompletion completion) {
        if (completion == null) {
            return List.of();
        }

        List<Text> rewards = new ArrayList<>();
        if (completion.currencyReward() > 0L) {
            rewards.add(Text.translatable("screen.village-quest.questmaster.reward.currency", CurrencyService.formatBalance(completion.currencyReward())).formatted(Formatting.GOLD));
        }
        if (completion.reputationTrack() != null && completion.reputationAmount() > 0) {
            rewards.add(ReputationService.formatRewardLine(completion.reputationTrack(), completion.reputationAmount()));
        }
        appendRewardStack(rewards, completion.rewardB());
        appendRewardStack(rewards, completion.rewardC());
        if (completion.xp() > 0) {
            rewards.add(Text.translatable("screen.village-quest.questmaster.reward.xp", completion.xp()).formatted(Formatting.GREEN));
        }
        return rewards;
    }

    private static void appendRewardStack(List<Text> rewards, net.minecraft.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Text name = stack.getName().copy();
        if (stack.getCount() > 1) {
            name = Text.empty().append(name).append(Text.literal(" x" + stack.getCount()).formatted(Formatting.GRAY));
        }
        rewards.add(Text.translatable("screen.village-quest.questmaster.reward.item", name).formatted(Formatting.AQUA));
    }

    private static Payloads.QuestMasterEntryData entry(String entryId,
                                                       String categoryId,
                                                       Text title,
                                                       Text subtitle,
                                                       Text status,
                                                       List<Text> descriptionLines,
                                                       List<Text> objectiveLines,
                                                       List<Text> rewardLines,
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

    private static QuestMasterEntity validateSession(ServerWorld world, ServerPlayerEntity player, int entityId) {
        Integer sessionEntityId = OPEN_SESSIONS.get(player.getUuid());
        if (sessionEntityId == null || sessionEntityId != entityId) {
            return null;
        }
        QuestMasterEntity questMaster = resolveQuestMaster(world, entityId);
        if (questMaster == null || !questMaster.isCustomer(player) || player.squaredDistanceTo(questMaster) > QuestMasterService.getMaxInteractDistanceSquared()) {
            return null;
        }
        return questMaster;
    }

    private static QuestMasterEntity resolveOpenQuestMaster(ServerWorld world, ServerPlayerEntity player) {
        Integer entityId = OPEN_SESSIONS.get(player.getUuid());
        if (entityId == null) {
            return null;
        }
        QuestMasterEntity questMaster = resolveQuestMaster(world, entityId);
        if (questMaster == null || !questMaster.isCustomer(player) || player.squaredDistanceTo(questMaster) > QuestMasterService.getMaxInteractDistanceSquared()) {
            return null;
        }
        return questMaster;
    }

    private static QuestMasterEntity resolveQuestMaster(ServerWorld world, int entityId) {
        if (world == null) {
            return null;
        }
        net.minecraft.entity.Entity entity = world.getEntityById(entityId);
        return entity instanceof QuestMasterEntity questMaster && questMaster.isAlive() ? questMaster : null;
    }

    private static PlayerQuestData data(ServerWorld world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    private static void sendPayload(ServerPlayerEntity player, Payloads.QuestMasterPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }
}
