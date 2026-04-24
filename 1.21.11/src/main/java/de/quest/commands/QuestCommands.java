package de.quest.commands;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.CommandNode;
import de.quest.content.story.ShadowsTradeRoadEncounterService;
import de.quest.data.QuestState;
import de.quest.economy.CurrencyService;
import de.quest.pilgrim.PilgrimContractService;
import de.quest.pilgrim.PilgrimService;
import de.quest.quest.QuestTrackerService;
import de.quest.questmaster.QuestMasterService;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.special.AdminCoreTestQuestService;
import de.quest.quest.special.MerchantSealQuestService;
import de.quest.quest.special.ShardRelicQuestService;
import de.quest.quest.special.SpecialQuestService;
import de.quest.quest.special.SurveyorCompassQuestService;
import de.quest.quest.story.StoryQuestService;
import de.quest.quest.story.VillageProjectService;
import de.quest.quest.story.VillageProjectType;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.reputation.ReputationService;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class QuestCommands {
    private static final SuggestionProvider<ServerCommandSource> QUEST_SUGGESTIONS = (ctx, builder) -> {
        builder.suggest("honey");
        builder.suggest("pet");
        builder.suggest("bakery");
        builder.suggest("kitchen");
        builder.suggest("workshop");
        builder.suggest("smith");
        builder.suggest("wool");
        builder.suggest("river");
        builder.suggest("harvest");
        builder.suggest("smelt");
        builder.suggest("stall");
        builder.suggest("trade");
        builder.suggest("zombie");
        builder.suggest("skeleton");
        builder.suggest("spider");
        builder.suggest("creeper");
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> WALLET_UNIT_SUGGESTIONS = (ctx, builder) -> {
        for (CurrencyService.CurrencyUnit unit : CurrencyService.CurrencyUnit.values()) {
            builder.suggest(unit.id());
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> REPUTATION_TRACK_SUGGESTIONS = (ctx, builder) -> {
        for (ReputationService.ReputationTrack track : ReputationService.ReputationTrack.values()) {
            builder.suggest(track.id());
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> PROJECT_SUGGESTIONS = (ctx, builder) -> {
        for (VillageProjectType project : VillageProjectType.values()) {
            if (!project.alwaysUnlocked()) {
                builder.suggest(project.id());
            }
        }
        return builder.buildFuture();
    };

    private QuestCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<ServerCommandSource> setQuestCommand = literal("setquest")
                    .requires(AdminCommands::canManageRespawn)
                    .executes(ctx -> {
                        ctx.getSource().sendFeedback(() -> Text.translatable("command.village-quest.setquest.usage").formatted(Formatting.GRAY), false);
                        return 0;
                    })
                    .then(argument("quest", StringArgumentType.word())
                            .suggests(QUEST_SUGGESTIONS)
                            .executes(ctx -> {
                                var player = ctx.getSource().getPlayer();
                                if (player instanceof ServerPlayerEntity sp) {
                                    var world = ctx.getSource().getServer().getOverworld();
                                    String arg = StringArgumentType.getString(ctx, "quest");
                                    DailyQuestService.DailyQuestType chosen = DailyQuestService.questFromString(arg);
                                    if (chosen == null) {
                                        sp.sendMessage(Text.translatable("command.village-quest.setquest.invalid").formatted(Formatting.RED), false);
                                        return 0;
                                    }
                                    DailyQuestService.setQuestChoiceForToday(world, sp.getUuid(), chosen);
                                    sp.sendMessage(Text.translatable("command.village-quest.setquest.success", DailyQuestService.displayKey(chosen)).formatted(Formatting.GREEN), false);
                                }
                                return 1;
                            }));

            LiteralArgumentBuilder<ServerCommandSource> questAdminCommand = literal("admin")
                    .requires(AdminCommands::canManageRespawn)
                    .then(setQuestCommand)
                    .then(literal("resetdaily")
                            .executes(ctx -> resetDailyForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                            .then(argument("player", EntityArgumentType.player())
                                    .executes(ctx -> resetDailyForPlayer(
                                            ctx.getSource(),
                                            EntityArgumentType.getPlayer(ctx, "player")
                                    ))))
                    .then(literal("nextdaily")
                            .executes(ctx -> prepareNextDailyForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                            .then(argument("player", EntityArgumentType.player())
                                    .executes(ctx -> prepareNextDailyForPlayer(
                                            ctx.getSource(),
                                            EntityArgumentType.getPlayer(ctx, "player")
                                    ))))
                    .then(literal("resetweekly")
                            .executes(ctx -> resetWeeklyForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                            .then(argument("player", EntityArgumentType.player())
                                    .executes(ctx -> resetWeeklyForPlayer(
                                            ctx.getSource(),
                                            EntityArgumentType.getPlayer(ctx, "player")
                                    ))))
                    .then(literal("nextweekly")
                            .executes(ctx -> prepareNextWeeklyForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                            .then(argument("player", EntityArgumentType.player())
                                    .executes(ctx -> prepareNextWeeklyForPlayer(
                                            ctx.getSource(),
                                            EntityArgumentType.getPlayer(ctx, "player")
                                    ))))
                    .then(literal("completeweekly")
                            .executes(ctx -> completeWeeklyForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                            .then(argument("player", EntityArgumentType.player())
                                    .executes(ctx -> completeWeeklyForPlayer(
                                            ctx.getSource(),
                                            EntityArgumentType.getPlayer(ctx, "player")
                                    ))))
                    .then(literal("story")
                            .then(literal("show")
                                    .executes(ctx -> showStoryForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                                    .then(argument("player", EntityArgumentType.player())
                                            .executes(ctx -> showStoryForPlayer(
                                                    ctx.getSource(),
                                                    EntityArgumentType.getPlayer(ctx, "player")
                                            ))))
                            .then(literal("reset")
                                    .executes(ctx -> resetStoryForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                                    .then(argument("player", EntityArgumentType.player())
                                            .executes(ctx -> resetStoryForPlayer(
                                                    ctx.getSource(),
                                                    EntityArgumentType.getPlayer(ctx, "player")
                                            ))))
                            .then(literal("complete")
                                    .executes(ctx -> completeStoryForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                                    .then(argument("player", EntityArgumentType.player())
                                            .executes(ctx -> completeStoryForPlayer(
                                                    ctx.getSource(),
                                                    EntityArgumentType.getPlayer(ctx, "player")
                                            ))))
                            .then(literal("shadows")
                                    .then(literal("unlock")
                                            .executes(ctx -> unlockShadowsForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                                            .then(argument("player", EntityArgumentType.player())
                                                    .executes(ctx -> unlockShadowsForPlayer(
                                                            ctx.getSource(),
                                                            EntityArgumentType.getPlayer(ctx, "player")
                                                    ))))
                                    .then(literal("testrescue")
                                            .executes(ctx -> prepareShadowsEncounterTest(ctx.getSource(), ctx.getSource().getPlayer(), false))
                                            .then(argument("player", EntityArgumentType.player())
                                                    .executes(ctx -> prepareShadowsEncounterTest(
                                                            ctx.getSource(),
                                                            EntityArgumentType.getPlayer(ctx, "player"),
                                                            false
                                                    ))))
                                    .then(literal("testfinal")
                                            .executes(ctx -> prepareShadowsEncounterTest(ctx.getSource(), ctx.getSource().getPlayer(), true))
                                            .then(argument("player", EntityArgumentType.player())
                                                    .executes(ctx -> prepareShadowsEncounterTest(
                                                            ctx.getSource(),
                                                            EntityArgumentType.getPlayer(ctx, "player"),
                                                            true
                                                    ))))))
                    .then(literal("completedaily")
                            .executes(ctx -> completeDailyForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                            .then(argument("player", EntityArgumentType.player())
                                    .executes(ctx -> completeDailyForPlayer(
                                            ctx.getSource(),
                                            EntityArgumentType.getPlayer(ctx, "player")
                                    ))))
                    .then(literal("reset")
                            .then(literal("complete")
                                    .executes(ctx -> resetComplete(ctx.getSource()))))
                    .then(literal("project")
                            .then(literal("show")
                                    .executes(ctx -> showProjectsForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                                    .then(argument("player", EntityArgumentType.player())
                                            .executes(ctx -> showProjectsForPlayer(
                                                    ctx.getSource(),
                                                    EntityArgumentType.getPlayer(ctx, "player")
                                            ))))
                            .then(literal("unlock")
                                    .then(argument("player", EntityArgumentType.player())
                                            .then(argument("project", StringArgumentType.word())
                                                    .suggests(PROJECT_SUGGESTIONS)
                                                    .executes(ctx -> setProjectForPlayer(
                                                            ctx.getSource(),
                                                            EntityArgumentType.getPlayer(ctx, "player"),
                                                            parseProject(StringArgumentType.getString(ctx, "project")),
                                                            true
                                                    )))))
                            .then(literal("lock")
                                    .then(argument("player", EntityArgumentType.player())
                                            .then(argument("project", StringArgumentType.word())
                                                    .suggests(PROJECT_SUGGESTIONS)
                                                    .executes(ctx -> setProjectForPlayer(
                                                            ctx.getSource(),
                                                            EntityArgumentType.getPlayer(ctx, "player"),
                                                            parseProject(StringArgumentType.getString(ctx, "project")),
                                                            false
                                                    ))))))
                    .then(literal("shardcache")
                            .executes(ctx -> startShardCacheForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                            .then(argument("player", EntityArgumentType.player())
                                    .executes(ctx -> startShardCacheForPlayer(
                                            ctx.getSource(),
                                            EntityArgumentType.getPlayer(ctx, "player")
                                    ))))
                    .then(literal("shardcachetp")
                            .executes(ctx -> teleportToShardCacheForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                            .then(argument("player", EntityArgumentType.player())
                                    .executes(ctx -> teleportToShardCacheForPlayer(
                                            ctx.getSource(),
                                            EntityArgumentType.getPlayer(ctx, "player")
                                    ))))
                    .then(literal("coretest")
                            .then(literal("start")
                                    .executes(ctx -> startCoreTestForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                                    .then(argument("player", EntityArgumentType.player())
                                            .executes(ctx -> startCoreTestForPlayer(
                                                    ctx.getSource(),
                                                    EntityArgumentType.getPlayer(ctx, "player")
                                            ))))
                            .then(literal("reset")
                                    .executes(ctx -> resetCoreTestForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                                    .then(argument("player", EntityArgumentType.player())
                                            .executes(ctx -> resetCoreTestForPlayer(
                                                    ctx.getSource(),
                                                    EntityArgumentType.getPlayer(ctx, "player")
                                            ))))
                            .then(literal("show")
                                    .executes(ctx -> showCoreTestForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                                    .then(argument("player", EntityArgumentType.player())
                                            .executes(ctx -> showCoreTestForPlayer(
                                                    ctx.getSource(),
                                                    EntityArgumentType.getPlayer(ctx, "player")
                                            )))))
                    .then(literal("pilgrim")
                            .then(literal("spawn")
                                    .executes(ctx -> spawnPilgrimForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                                    .then(argument("player", EntityArgumentType.player())
                                            .executes(ctx -> spawnPilgrimForPlayer(
                                                    ctx.getSource(),
                                                    EntityArgumentType.getPlayer(ctx, "player")
                                            ))))
                            .then(literal("rumor")
                                    .then(literal("unlock")
                                            .executes(ctx -> setPilgrimRumorOverride(ctx.getSource(), ctx.getSource().getPlayer(), true))
                                            .then(argument("player", EntityArgumentType.player())
                                                    .executes(ctx -> setPilgrimRumorOverride(
                                                            ctx.getSource(),
                                                            EntityArgumentType.getPlayer(ctx, "player"),
                                                            true
                                                    ))))
                                    .then(literal("lock")
                                            .executes(ctx -> setPilgrimRumorOverride(ctx.getSource(), ctx.getSource().getPlayer(), false))
                                            .then(argument("player", EntityArgumentType.player())
                                                    .executes(ctx -> setPilgrimRumorOverride(
                                                            ctx.getSource(),
                                                            EntityArgumentType.getPlayer(ctx, "player"),
                                                            false
                                                    )))))
                            .then(literal("despawn")
                                    .executes(ctx -> despawnPilgrims(ctx.getSource()))))
                    .then(buildAdminWalletCommand())
                    .then(buildAdminReputationCommand());

            LiteralArgumentBuilder<ServerCommandSource> journalCommand = literal("journal").executes(ctx -> {
                var player = ctx.getSource().getPlayer();
                if (player instanceof ServerPlayerEntity sp) {
                    var world = ctx.getSource().getServer().getOverworld();
                    QuestBookHelper.toggleJournal(world, sp);
                }
                return 1;
            });

            LiteralArgumentBuilder<ServerCommandSource> questMasterCommand = literal("questmaster")
                    .executes(ctx -> summonQuestMaster(ctx.getSource()));

            LiteralArgumentBuilder<ServerCommandSource> questTrackerCommand = literal("questtracker")
                    .executes(ctx -> toggleQuestTracker(ctx.getSource()))
                    .then(literal("on").executes(ctx -> setQuestTracker(ctx.getSource(), true)))
                    .then(literal("off").executes(ctx -> setQuestTracker(ctx.getSource(), false)));

            LiteralArgumentBuilder<ServerCommandSource> dailyQuestCommand = literal("daily")
                    .then(literal("accept").executes(ctx -> acceptQuest(ctx.getSource())));

            LiteralArgumentBuilder<ServerCommandSource> walletCommand = literal("wallet")
                    .executes(ctx -> showWallet(ctx.getSource()));

            LiteralArgumentBuilder<ServerCommandSource> reputationCommand = literal("reputation")
                    .executes(ctx -> showReputation(ctx.getSource()));

            CommandNode<ServerCommandSource> villageQuestCommand = dispatcher.register(literal("villagequest")
                    .then(questAdminCommand)
                    .then(journalCommand)
                    .then(questMasterCommand)
                    .then(questTrackerCommand)
                    .then(dailyQuestCommand)
                    .then(walletCommand)
                    .then(reputationCommand));
            dispatcher.register(literal("vq").redirect(villageQuestCommand));
        });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildAdminWalletCommand() {
        return literal("wallet")
                .then(literal("show")
                        .executes(ctx -> showWalletForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                        .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> showWalletForPlayer(
                                        ctx.getSource(),
                                        EntityArgumentType.getPlayer(ctx, "player")
                                ))))
                .then(literal("add")
                        .then(argument("player", EntityArgumentType.player())
                                .then(argument("amount", LongArgumentType.longArg(1L))
                                        .executes(ctx -> adjustWalletForPlayer(
                                                ctx.getSource(),
                                                EntityArgumentType.getPlayer(ctx, "player"),
                                                LongArgumentType.getLong(ctx, "amount"),
                                                CurrencyService.CurrencyUnit.SILVERMARK,
                                                WalletAdjustmentMode.ADD
                                        ))
                                        .then(argument("unit", StringArgumentType.word())
                                                .suggests(WALLET_UNIT_SUGGESTIONS)
                                                .executes(ctx -> adjustWalletForPlayer(
                                                        ctx.getSource(),
                                                        EntityArgumentType.getPlayer(ctx, "player"),
                                                        LongArgumentType.getLong(ctx, "amount"),
                                                        parseCurrencyUnit(StringArgumentType.getString(ctx, "unit")),
                                                        WalletAdjustmentMode.ADD
                                                ))))))
                .then(literal("remove")
                        .then(argument("player", EntityArgumentType.player())
                                .then(argument("amount", LongArgumentType.longArg(1L))
                                        .executes(ctx -> adjustWalletForPlayer(
                                                ctx.getSource(),
                                                EntityArgumentType.getPlayer(ctx, "player"),
                                                LongArgumentType.getLong(ctx, "amount"),
                                                CurrencyService.CurrencyUnit.SILVERMARK,
                                                WalletAdjustmentMode.REMOVE
                                        ))
                                        .then(argument("unit", StringArgumentType.word())
                                                .suggests(WALLET_UNIT_SUGGESTIONS)
                                                .executes(ctx -> adjustWalletForPlayer(
                                                        ctx.getSource(),
                                                        EntityArgumentType.getPlayer(ctx, "player"),
                                                        LongArgumentType.getLong(ctx, "amount"),
                                                        parseCurrencyUnit(StringArgumentType.getString(ctx, "unit")),
                                                        WalletAdjustmentMode.REMOVE
                                                ))))))
                .then(literal("set")
                        .then(argument("player", EntityArgumentType.player())
                                .then(argument("amount", LongArgumentType.longArg(0L))
                                        .executes(ctx -> setWalletForPlayer(
                                                ctx.getSource(),
                                                EntityArgumentType.getPlayer(ctx, "player"),
                                                LongArgumentType.getLong(ctx, "amount"),
                                                CurrencyService.CurrencyUnit.SILVERMARK
                                        ))
                                        .then(argument("unit", StringArgumentType.word())
                                                .suggests(WALLET_UNIT_SUGGESTIONS)
                                                .executes(ctx -> setWalletForPlayer(
                                                        ctx.getSource(),
                                                        EntityArgumentType.getPlayer(ctx, "player"),
                                                        LongArgumentType.getLong(ctx, "amount"),
                                                        parseCurrencyUnit(StringArgumentType.getString(ctx, "unit"))
                                                ))))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildAdminReputationCommand() {
        return literal("reputation")
                .then(literal("show")
                        .executes(ctx -> showReputationForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                        .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> showReputationForPlayer(
                                        ctx.getSource(),
                                        EntityArgumentType.getPlayer(ctx, "player")
                                ))))
                .then(literal("add")
                        .then(argument("player", EntityArgumentType.player())
                                .then(argument("track", StringArgumentType.word())
                                        .suggests(REPUTATION_TRACK_SUGGESTIONS)
                                        .then(argument("amount", LongArgumentType.longArg(1L))
                                                .executes(ctx -> adjustReputationForPlayer(
                                                        ctx.getSource(),
                                                        EntityArgumentType.getPlayer(ctx, "player"),
                                                        parseReputationTrack(StringArgumentType.getString(ctx, "track")),
                                                        LongArgumentType.getLong(ctx, "amount"),
                                                        false
                                                ))))))
                .then(literal("set")
                        .then(argument("player", EntityArgumentType.player())
                                .then(argument("track", StringArgumentType.word())
                                        .suggests(REPUTATION_TRACK_SUGGESTIONS)
                                        .then(argument("amount", LongArgumentType.longArg(0L))
                                                .executes(ctx -> adjustReputationForPlayer(
                                                        ctx.getSource(),
                                                        EntityArgumentType.getPlayer(ctx, "player"),
                                                        parseReputationTrack(StringArgumentType.getString(ctx, "track")),
                                                        LongArgumentType.getLong(ctx, "amount"),
                                                        true
                                                ))))));
    }

    private static int acceptQuest(ServerCommandSource source) {
        var player = source.getPlayer();
        if (player instanceof ServerPlayerEntity sp) {
            var world = source.getServer().getOverworld();
            if (SpecialQuestService.acceptPendingOffer(world, sp)) {
            } else if (DailyQuestService.consumePendingShardOffer(world, sp.getUuid())) {
                DailyQuestService.activateShardBonusQuestOffer(world, sp);
            } else if (DailyQuestService.consumePendingBonusOffer(world, sp.getUuid())) {
                DailyQuestService.acceptBonusQuest(world, sp);
            } else if (DailyQuestService.consumePendingDailyOffer(world, sp.getUuid())) {
                DailyQuestService.acceptQuest(world, sp);
            }
        }
        return 1;
    }

    private static int resetDailyForPlayer(ServerCommandSource source, ServerPlayerEntity target) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }

        var world = source.getServer().getOverworld();
        DailyQuestService.adminResetDailyState(world, target.getUuid());
        refreshQuestUi(world, target);

        ServerPlayerEntity sourcePlayer = source.getPlayer();
        if (sourcePlayer != null && sourcePlayer.getUuid().equals(target.getUuid())) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.resetdaily.self").formatted(Formatting.GREEN), false);
            return 1;
        }

        source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.resetdaily.other", target.getDisplayName()).formatted(Formatting.GREEN), false);
        target.sendMessage(Text.translatable("command.village-quest.questadmin.resetdaily.notify").formatted(Formatting.GRAY), false);
        return 1;
    }

    private static int prepareNextDailyForPlayer(ServerCommandSource source, ServerPlayerEntity target) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }

        var world = source.getServer().getOverworld();
        DailyQuestService.DailyQuestType previousChoice = DailyQuestService.adminPrepareNextDaily(world, target.getUuid());
        refreshQuestUi(world, target);
        Text previousName = previousChoice == null
                ? Text.literal("-")
                : DailyQuestService.displayKey(previousChoice);

        ServerPlayerEntity sourcePlayer = source.getPlayer();
        if (sourcePlayer != null && sourcePlayer.getUuid().equals(target.getUuid())) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.nextdaily.self", previousName).formatted(Formatting.GREEN), false);
            return 1;
        }

        source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.nextdaily.other", target.getDisplayName(), previousName).formatted(Formatting.GREEN), false);
        target.sendMessage(Text.translatable("command.village-quest.questadmin.nextdaily.notify").formatted(Formatting.GRAY), false);
        return 1;
    }

    private static int completeDailyForPlayer(ServerCommandSource source, ServerPlayerEntity target) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }

        var world = source.getServer().getOverworld();
        DailyQuestService.DailyQuestType activeQuest = DailyQuestService.activeQuestType(world, target.getUuid());
        if (activeQuest == null || !DailyQuestService.adminForceCompleteDaily(world, target)) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.completedaily.none").formatted(Formatting.RED), false);
            return 0;
        }

        Text questName = DailyQuestService.displayKey(activeQuest);
        ServerPlayerEntity sourcePlayer = source.getPlayer();
        if (sourcePlayer != null && sourcePlayer.getUuid().equals(target.getUuid())) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.completedaily.self", questName).formatted(Formatting.GREEN), false);
            return 1;
        }

        source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.completedaily.other", target.getDisplayName(), questName).formatted(Formatting.GREEN), false);
        target.sendMessage(Text.translatable("command.village-quest.questadmin.completedaily.notify", questName).formatted(Formatting.GRAY), false);
        return 1;
    }

    private static int resetWeeklyForPlayer(ServerCommandSource source, ServerPlayerEntity target) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }

        var world = source.getServer().getOverworld();
        WeeklyQuestService.adminResetWeeklyState(world, target.getUuid());
        refreshQuestUi(world, target);

        ServerPlayerEntity sourcePlayer = source.getPlayer();
        if (sourcePlayer != null && sourcePlayer.getUuid().equals(target.getUuid())) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.resetweekly.self").formatted(Formatting.GREEN), false);
            return 1;
        }

        source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.resetweekly.other", target.getDisplayName()).formatted(Formatting.GREEN), false);
        target.sendMessage(Text.translatable("command.village-quest.questadmin.resetweekly.notify").formatted(Formatting.GRAY), false);
        return 1;
    }

    private static int prepareNextWeeklyForPlayer(ServerCommandSource source, ServerPlayerEntity target) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }

        var world = source.getServer().getOverworld();
        WeeklyQuestService.WeeklyQuestType previousChoice = WeeklyQuestService.adminPrepareNextWeekly(world, target.getUuid());
        refreshQuestUi(world, target);
        Text previousName = previousChoice == null ? Text.literal("-") : WeeklyQuestService.displayName(previousChoice);

        ServerPlayerEntity sourcePlayer = source.getPlayer();
        if (sourcePlayer != null && sourcePlayer.getUuid().equals(target.getUuid())) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.nextweekly.self", previousName).formatted(Formatting.GREEN), false);
            return 1;
        }

        source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.nextweekly.other", target.getDisplayName(), previousName).formatted(Formatting.GREEN), false);
        target.sendMessage(Text.translatable("command.village-quest.questadmin.nextweekly.notify").formatted(Formatting.GRAY), false);
        return 1;
    }

    private static int completeWeeklyForPlayer(ServerCommandSource source, ServerPlayerEntity target) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }

        var world = source.getServer().getOverworld();
        WeeklyQuestService.WeeklyQuestType activeQuest = WeeklyQuestService.activeQuestType(world, target.getUuid());
        if (activeQuest == null || !WeeklyQuestService.adminForceCompleteWeekly(world, target)) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.completeweekly.none").formatted(Formatting.RED), false);
            return 0;
        }

        Text questName = WeeklyQuestService.displayName(activeQuest);
        ServerPlayerEntity sourcePlayer = source.getPlayer();
        if (sourcePlayer != null && sourcePlayer.getUuid().equals(target.getUuid())) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.completeweekly.self", questName).formatted(Formatting.GREEN), false);
            return 1;
        }

        source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.completeweekly.other", target.getDisplayName(), questName).formatted(Formatting.GREEN), false);
        target.sendMessage(Text.translatable("command.village-quest.questadmin.completeweekly.notify", questName).formatted(Formatting.GRAY), false);
        return 1;
    }

    private static int showStoryForPlayer(ServerCommandSource source, ServerPlayerEntity target) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }
        var world = source.getServer().getOverworld();
        source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.story.header", target.getDisplayName()).formatted(Formatting.GOLD), false);
        for (Text line : StoryQuestService.buildOverview(world, target.getUuid())) {
            source.sendFeedback(() -> line, false);
        }
        return 1;
    }

    private static int resetStoryForPlayer(ServerCommandSource source, ServerPlayerEntity target) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }

        var world = source.getServer().getOverworld();
        if (!StoryQuestService.adminResetStoryState(world, target.getUuid())) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.story.reset.none").formatted(Formatting.RED), false);
            return 0;
        }
        ShadowsTradeRoadEncounterService.adminResetPlayer(world, target.getUuid());
        refreshQuestUi(world, target);

        ServerPlayerEntity sourcePlayer = source.getPlayer();
        if (sourcePlayer != null && sourcePlayer.getUuid().equals(target.getUuid())) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.story.reset.self").formatted(Formatting.GREEN), false);
            return 1;
        }

        source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.story.reset.other", target.getDisplayName()).formatted(Formatting.GREEN), false);
        target.sendMessage(Text.translatable("command.village-quest.questadmin.story.reset.notify").formatted(Formatting.GRAY), false);
        return 1;
    }

    private static int completeStoryForPlayer(ServerCommandSource source, ServerPlayerEntity target) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }

        var world = source.getServer().getOverworld();
        if (!StoryQuestService.adminForceCompleteActiveChapter(world, target)) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.story.complete.none").formatted(Formatting.RED), false);
            return 0;
        }

        ServerPlayerEntity sourcePlayer = source.getPlayer();
        if (sourcePlayer != null && sourcePlayer.getUuid().equals(target.getUuid())) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.story.complete.self").formatted(Formatting.GREEN), false);
            return 1;
        }

        source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.story.complete.other", target.getDisplayName()).formatted(Formatting.GREEN), false);
        target.sendMessage(Text.translatable("command.village-quest.questadmin.story.complete.notify").formatted(Formatting.GRAY), false);
        return 1;
    }

    private static int unlockShadowsForPlayer(ServerCommandSource source, ServerPlayerEntity target) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }

        var world = source.getServer().getOverworld();
        if (!ShadowsTradeRoadEncounterService.adminUnlockForTesting(world, target)) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.story.shadows.unlock.none").formatted(Formatting.RED), false);
            return 0;
        }
        source.sendFeedback(() -> Text.translatable(
                "command.village-quest.questadmin.story.shadows.unlock.success",
                target.getDisplayName()
        ).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int prepareShadowsEncounterTest(ServerCommandSource source, ServerPlayerEntity target, boolean finalConvoy) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }

        var world = source.getServer().getOverworld();
        if (!ShadowsTradeRoadEncounterService.adminPrepareEncounterTest(world, target, finalConvoy)) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.story.shadows.test.failed").formatted(Formatting.RED), false);
            return 0;
        }
        source.sendFeedback(() -> Text.translatable(
                finalConvoy
                        ? "command.village-quest.questadmin.story.shadows.test.final"
                        : "command.village-quest.questadmin.story.shadows.test.rescue",
                target.getDisplayName()
        ).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int showProjectsForPlayer(ServerCommandSource source, ServerPlayerEntity target) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }
        var world = source.getServer().getOverworld();
        source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.project.header", target.getDisplayName()).formatted(Formatting.GOLD), false);
        for (Text line : VillageProjectService.buildOverview(world, target.getUuid())) {
            source.sendFeedback(() -> line, false);
        }
        return 1;
    }

    private static int setProjectForPlayer(ServerCommandSource source, ServerPlayerEntity target, VillageProjectType project, boolean unlocked) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }
        if (project == null || project.alwaysUnlocked()) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.project.invalid").formatted(Formatting.RED), false);
            return 0;
        }

        var world = source.getServer().getOverworld();
        if (!VillageProjectService.setUnlocked(world, target.getUuid(), project, unlocked)) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.project.no_change").formatted(Formatting.RED), false);
            return 0;
        }
        refreshQuestUi(world, target);

        source.sendFeedback(() -> Text.translatable(
                unlocked
                        ? "command.village-quest.questadmin.project.unlock"
                        : "command.village-quest.questadmin.project.lock",
                target.getDisplayName(),
                Text.translatable("quest.village-quest.project." + project.id() + ".title")
        ).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int showWallet(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }

        long balance = CurrencyService.getBalance(source.getServer().getOverworld(), player.getUuid());
        source.sendFeedback(() -> Text.empty()
                .append(Text.translatable("command.village-quest.wallet.balance").formatted(Formatting.GRAY))
                .append(CurrencyService.formatBalance(balance)), false);
        return 1;
    }

    private static int showReputation(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }

        var world = source.getServer().getOverworld();
        source.sendFeedback(() -> Text.translatable("command.village-quest.reputation.header").formatted(Formatting.GOLD), false);
        for (Text line : ReputationService.buildOverview(world, player.getUuid())) {
            source.sendFeedback(() -> line, false);
        }
        return 1;
    }

    private static int showReputationForPlayer(ServerCommandSource source, ServerPlayerEntity target) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }
        var world = source.getServer().getOverworld();
        source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.reputation.header", target.getDisplayName()).formatted(Formatting.GOLD), false);
        for (Text line : ReputationService.buildOverview(world, target.getUuid())) {
            source.sendFeedback(() -> line, false);
        }
        return 1;
    }

    private static int adjustReputationForPlayer(ServerCommandSource source,
                                                 ServerPlayerEntity target,
                                                 ReputationService.ReputationTrack track,
                                                 long amount,
                                                 boolean setValue) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }
        if (track == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.reputation.track.invalid").formatted(Formatting.RED), false);
            return 0;
        }

        var world = source.getServer().getOverworld();
        long clamped = Math.max(0L, Math.min(Integer.MAX_VALUE, amount));
        int current = ReputationService.get(world, target.getUuid(), track);
        int next = setValue ? (int) clamped : current + (int) clamped;
        if (setValue) {
            int delta = next - current;
            if (delta != 0) {
                ReputationService.add(world, target.getUuid(), track, delta);
            }
        } else {
            ReputationService.add(world, target.getUuid(), track, (int) clamped);
        }

        source.sendFeedback(() -> Text.translatable(
                setValue ? "command.village-quest.questadmin.reputation.set" : "command.village-quest.questadmin.reputation.add",
                target.getDisplayName(),
                ReputationService.displayName(track),
                next
        ).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int summonQuestMaster(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }

        var world = source.getServer().getOverworld();
        long summonCooldown = QuestMasterService.getPlayerSummonCooldownRemainingTicks(world, player.getUuid());
        if (summonCooldown > 0L) {
            source.sendFeedback(() -> Text.translatable(
                    "command.village-quest.questmaster.spawn.cooldown",
                    QuestMasterService.formatDuration(summonCooldown)
            ).formatted(Formatting.RED), false);
            return 0;
        }
        if (QuestMasterService.findNearbyQuestMaster(world, player.getX(), player.getY(), player.getZ()) != null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questmaster.spawn.exists").formatted(Formatting.RED), false);
            return 0;
        }

        if (QuestMasterService.spawnNearPlayer(world, player) == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questmaster.spawn.failed").formatted(Formatting.RED), false);
            return 0;
        }

        source.sendFeedback(() -> Text.translatable("command.village-quest.questmaster.spawn.success").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int toggleQuestTracker(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }
        boolean enabled = QuestTrackerService.toggle(source.getServer().getOverworld(), player);
        source.sendFeedback(() -> Text.translatable(
                enabled ? "command.village-quest.questtracker.enabled" : "command.village-quest.questtracker.disabled"
        ).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int setQuestTracker(ServerCommandSource source, boolean enabled) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }
        QuestTrackerService.setEnabled(source.getServer().getOverworld(), player, enabled);
        source.sendFeedback(() -> Text.translatable(
                enabled ? "command.village-quest.questtracker.enabled" : "command.village-quest.questtracker.disabled"
        ).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int startShardCacheForPlayer(ServerCommandSource source, ServerPlayerEntity target) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }

        var world = source.getServer().getOverworld();
        if (!ShardRelicQuestService.adminStartCacheHunt(world, target)) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.shardcache.failed").formatted(Formatting.RED), false);
            return 0;
        }

        ServerPlayerEntity sourcePlayer = source.getPlayer();
        if (sourcePlayer != null && sourcePlayer.getUuid().equals(target.getUuid())) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.shardcache.self").formatted(Formatting.GREEN), false);
            return 1;
        }

        source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.shardcache.other", target.getDisplayName()).formatted(Formatting.GREEN), false);
        target.sendMessage(Text.translatable("command.village-quest.questadmin.shardcache.notify").formatted(Formatting.GRAY), false);
        return 1;
    }

    private static int teleportToShardCacheForPlayer(ServerCommandSource source, ServerPlayerEntity target) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }

        var world = source.getServer().getOverworld();
        if (!ShardRelicQuestService.adminTeleportToCache(world, target)) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.shardcachetp.failed").formatted(Formatting.RED), false);
            return 0;
        }

        ServerPlayerEntity sourcePlayer = source.getPlayer();
        if (sourcePlayer != null && sourcePlayer.getUuid().equals(target.getUuid())) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.shardcachetp.self").formatted(Formatting.GREEN), false);
            return 1;
        }

        source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.shardcachetp.other", target.getDisplayName()).formatted(Formatting.GREEN), false);
        target.sendMessage(Text.translatable("command.village-quest.questadmin.shardcachetp.notify").formatted(Formatting.GRAY), false);
        return 1;
    }

    private static int startCoreTestForPlayer(ServerCommandSource source, ServerPlayerEntity target) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }

        var world = source.getServer().getOverworld();
        if (!AdminCoreTestQuestService.adminStart(world, target)) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.coretest.show.none", target.getDisplayName()).formatted(Formatting.RED), false);
            return 0;
        }

        source.sendFeedback(() -> Text.translatable(
                "command.village-quest.questadmin.coretest.start",
                target.getDisplayName()
        ).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int resetCoreTestForPlayer(ServerCommandSource source, ServerPlayerEntity target) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }

        var world = source.getServer().getOverworld();
        AdminCoreTestQuestService.adminReset(world, target);
        source.sendFeedback(() -> Text.translatable(
                "command.village-quest.questadmin.coretest.reset",
                target.getDisplayName()
        ).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int showCoreTestForPlayer(ServerCommandSource source, ServerPlayerEntity target) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }

        var world = source.getServer().getOverworld();
        var status = AdminCoreTestQuestService.openStatus(world, target.getUuid());
        if (status == null) {
            source.sendFeedback(() -> Text.translatable(
                    "command.village-quest.questadmin.coretest.show.none",
                    target.getDisplayName()
            ).formatted(Formatting.RED), false);
            return 0;
        }

        source.sendFeedback(() -> Text.translatable(
                "command.village-quest.questadmin.coretest.show.header",
                target.getDisplayName()
        ).formatted(Formatting.GOLD), false);
        source.sendFeedback(() -> status.title().copy().formatted(Formatting.AQUA), false);
        for (Text line : status.lines()) {
            source.sendFeedback(() -> line, false);
        }
        return 1;
    }

    private static int showWalletForPlayer(ServerCommandSource source, ServerPlayerEntity target) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }

        long balance = CurrencyService.getBalance(source.getServer().getOverworld(), target.getUuid());
        source.sendFeedback(() -> Text.translatable(
                "command.village-quest.questadmin.wallet.show",
                target.getDisplayName(),
                CurrencyService.formatBalance(balance)
        ).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int adjustWalletForPlayer(ServerCommandSource source,
                                             ServerPlayerEntity target,
                                             long amount,
                                             CurrencyService.CurrencyUnit unit,
                                             WalletAdjustmentMode mode) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }
        if (unit == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.wallet.unit.invalid").formatted(Formatting.RED), false);
            return 0;
        }

        var world = source.getServer().getOverworld();
        long delta = CurrencyService.toBase(amount, unit);
        long newBalance;
        if (mode == WalletAdjustmentMode.ADD) {
            newBalance = CurrencyService.addBalance(world, target.getUuid(), delta);
        } else {
            long currentBalance = CurrencyService.getBalance(world, target.getUuid());
            if (!CurrencyService.removeBalance(world, target.getUuid(), delta)) {
                source.sendFeedback(() -> Text.translatable(
                        "command.village-quest.questadmin.wallet.remove.insufficient",
                        target.getDisplayName(),
                        CurrencyService.formatBalance(currentBalance)
                ).formatted(Formatting.RED), false);
                return 0;
            }
            newBalance = CurrencyService.getBalance(world, target.getUuid());
            delta = -delta;
        }

        long finalDelta = delta;
        source.sendFeedback(() -> Text.translatable(
                "command.village-quest.questadmin.wallet.adjust",
                target.getDisplayName(),
                CurrencyService.formatDelta(finalDelta),
                CurrencyService.formatBalance(newBalance)
        ).formatted(Formatting.GREEN), false);
        if (shouldNotifyWalletTarget(source, target)) {
            target.sendMessage(Text.translatable(
                    "command.village-quest.questadmin.wallet.adjust.notify",
                    CurrencyService.formatDelta(finalDelta),
                    CurrencyService.formatBalance(newBalance)
            ).formatted(Formatting.GRAY), false);
        }
        return 1;
    }

    private static int setWalletForPlayer(ServerCommandSource source,
                                          ServerPlayerEntity target,
                                          long amount,
                                          CurrencyService.CurrencyUnit unit) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }
        if (unit == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.wallet.unit.invalid").formatted(Formatting.RED), false);
            return 0;
        }

        var world = source.getServer().getOverworld();
        long newBalance = CurrencyService.setBalance(world, target.getUuid(), CurrencyService.toBase(amount, unit));
        source.sendFeedback(() -> Text.translatable(
                "command.village-quest.questadmin.wallet.set",
                target.getDisplayName(),
                CurrencyService.formatBalance(newBalance)
        ).formatted(Formatting.GREEN), false);
        if (shouldNotifyWalletTarget(source, target)) {
            target.sendMessage(Text.translatable(
                    "command.village-quest.questadmin.wallet.set.notify",
                    CurrencyService.formatBalance(newBalance)
            ).formatted(Formatting.GRAY), false);
        }
        return 1;
    }

    private static boolean shouldNotifyWalletTarget(ServerCommandSource source, ServerPlayerEntity target) {
        return !(source.getEntity() instanceof ServerPlayerEntity executor) || !executor.getUuid().equals(target.getUuid());
    }

    private static int spawnPilgrimForPlayer(ServerCommandSource source, ServerPlayerEntity target) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }

        var world = source.getServer().getOverworld();
        if (PilgrimService.findActivePilgrim(world) != null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.pilgrim.spawn.exists").formatted(Formatting.RED), false);
            return 0;
        }

        var pilgrim = PilgrimService.spawnNearPlayer(world, target, true);
        if (pilgrim == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.pilgrim.spawn.failed").formatted(Formatting.RED), false);
            return 0;
        }

        source.sendFeedback(() -> Text.translatable(
                "command.village-quest.questadmin.pilgrim.spawn.success",
                target.getDisplayName()
        ).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int despawnPilgrims(ServerCommandSource source) {
        int removed = PilgrimService.despawnAll(source.getServer().getOverworld());
        if (removed <= 0) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.pilgrim.despawn.none").formatted(Formatting.RED), false);
            return 0;
        }
        source.sendFeedback(() -> Text.translatable(
                "command.village-quest.questadmin.pilgrim.despawn.success",
                removed
        ).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int resetComplete(ServerCommandSource source) {
        var server = source.getServer();
        var world = server.getOverworld();
        int onlinePlayers = server.getPlayerManager().getPlayerList().size();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            QuestMasterUiService.close(player);
            QuestBookHelper.closeJournal(player);
            QuestTrackerService.closeTracker(player);
            PilgrimService.closeTrade(player);
        }

        QuestBookHelper.resetAllSessions();
        QuestTrackerService.resetAllRuntimeState();
        QuestMasterUiService.resetAllSessions();
        MerchantSealQuestService.resetRuntimeState();
        SurveyorCompassQuestService.resetRuntimeState();
        ShadowsTradeRoadEncounterService.resetRuntimeState();
        int removedQuestMasters = QuestMasterService.despawnAll(world);
        int removedPilgrims = PilgrimService.despawnAll(world);
        int removedTradeRoad = ShadowsTradeRoadEncounterService.despawnAll(world);
        QuestState.get(server).resetAllProgress();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(Text.translatable("command.village-quest.questadmin.reset.complete.notify").formatted(Formatting.GRAY), false);
        }

        source.sendFeedback(() -> Text.translatable(
                "command.village-quest.questadmin.reset.complete.success",
                onlinePlayers,
                removedQuestMasters,
                removedPilgrims + removedTradeRoad
        ).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int setPilgrimRumorOverride(ServerCommandSource source, ServerPlayerEntity target, boolean enabled) {
        if (target == null) {
            source.sendFeedback(() -> Text.translatable("command.village-quest.questadmin.player_required").formatted(Formatting.RED), false);
            return 0;
        }

        var world = source.getServer().getOverworld();
        PilgrimContractService.adminSetRumorUnlocked(world, target, enabled);
        refreshQuestUi(world, target);

        ServerPlayerEntity sourcePlayer = source.getPlayer();
        if (sourcePlayer != null && sourcePlayer.getUuid().equals(target.getUuid())) {
            source.sendFeedback(() -> Text.translatable(
                    enabled
                            ? "command.village-quest.questadmin.pilgrim.rumor.unlock.self"
                            : "command.village-quest.questadmin.pilgrim.rumor.lock.self"
            ).formatted(Formatting.GREEN), false);
            return 1;
        }

        source.sendFeedback(() -> Text.translatable(
                enabled
                        ? "command.village-quest.questadmin.pilgrim.rumor.unlock.other"
                        : "command.village-quest.questadmin.pilgrim.rumor.lock.other",
                target.getDisplayName()
        ).formatted(Formatting.GREEN), false);
        target.sendMessage(Text.translatable(
                enabled
                        ? "command.village-quest.questadmin.pilgrim.rumor.unlock.notify"
                        : "command.village-quest.questadmin.pilgrim.rumor.lock.notify"
        ).formatted(Formatting.GRAY), false);
        return 1;
    }

    private static CurrencyService.CurrencyUnit parseCurrencyUnit(String raw) {
        return CurrencyService.parseUnit(raw);
    }

    private static ReputationService.ReputationTrack parseReputationTrack(String raw) {
        return ReputationService.parseTrack(raw);
    }

    private static VillageProjectType parseProject(String raw) {
        return VillageProjectType.fromId(raw);
    }

    private static void refreshQuestUi(net.minecraft.server.world.ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return;
        }
        QuestBookHelper.refreshQuestBook(world, player);
        QuestTrackerService.refresh(world, player);
        QuestMasterUiService.refreshIfOpen(world, player);
        PilgrimService.refreshIfTrading(world, player);
    }

    private enum WalletAdjustmentMode {
        ADD,
        REMOVE
    }
}
