package de.quest.commands;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.CommandNode;
import de.quest.economy.CurrencyService;
import de.quest.pilgrim.PilgrimContractService;
import de.quest.pilgrim.PilgrimService;
import de.quest.quest.QuestTrackerService;
import de.quest.questmaster.QuestMasterService;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.special.AdminCoreTestQuestService;
import de.quest.quest.special.ShardRelicQuestService;
import de.quest.quest.special.SpecialQuestService;
import de.quest.quest.story.StoryQuestService;
import de.quest.quest.story.VillageProjectService;
import de.quest.quest.story.VillageProjectType;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.reputation.ReputationService;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class QuestCommands {
    private static final SuggestionProvider<CommandSourceStack> QUEST_SUGGESTIONS = (ctx, builder) -> {
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

    private static final SuggestionProvider<CommandSourceStack> WALLET_UNIT_SUGGESTIONS = (ctx, builder) -> {
        for (CurrencyService.CurrencyUnit unit : CurrencyService.CurrencyUnit.values()) {
            builder.suggest(unit.id());
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> REPUTATION_TRACK_SUGGESTIONS = (ctx, builder) -> {
        for (ReputationService.ReputationTrack track : ReputationService.ReputationTrack.values()) {
            builder.suggest(track.id());
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> PROJECT_SUGGESTIONS = (ctx, builder) -> {
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
            LiteralArgumentBuilder<CommandSourceStack> setQuestCommand = literal("setquest")
                    .requires(AdminCommands::canManageRespawn)
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.translatable("command.village-quest.setquest.usage").withStyle(ChatFormatting.GRAY), false);
                        return 0;
                    })
                    .then(argument("quest", StringArgumentType.word())
                            .suggests(QUEST_SUGGESTIONS)
                            .executes(ctx -> {
                                var player = ctx.getSource().getPlayer();
                                if (player instanceof ServerPlayer sp) {
                                    var world = ctx.getSource().getServer().overworld();
                                    String arg = StringArgumentType.getString(ctx, "quest");
                                    DailyQuestService.DailyQuestType chosen = DailyQuestService.questFromString(arg);
                                    if (chosen == null) {
                                        sp.sendSystemMessage(Component.translatable("command.village-quest.setquest.invalid").withStyle(ChatFormatting.RED), false);
                                        return 0;
                                    }
                                    DailyQuestService.setQuestChoiceForToday(world, sp.getUUID(), chosen);
                                    sp.sendSystemMessage(Component.translatable("command.village-quest.setquest.success", DailyQuestService.displayKey(chosen)).withStyle(ChatFormatting.GREEN), false);
                                }
                                return 1;
                            }));

            LiteralArgumentBuilder<CommandSourceStack> questAdminCommand = literal("admin")
                    .requires(AdminCommands::canManageRespawn)
                    .then(setQuestCommand)
                    .then(literal("resetdaily")
                            .executes(ctx -> resetDailyForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                            .then(argument("player", EntityArgument.player())
                                    .executes(ctx -> resetDailyForPlayer(
                                            ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "player")
                                    ))))
                    .then(literal("nextdaily")
                            .executes(ctx -> prepareNextDailyForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                            .then(argument("player", EntityArgument.player())
                                    .executes(ctx -> prepareNextDailyForPlayer(
                                            ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "player")
                                    ))))
                    .then(literal("resetweekly")
                            .executes(ctx -> resetWeeklyForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                            .then(argument("player", EntityArgument.player())
                                    .executes(ctx -> resetWeeklyForPlayer(
                                            ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "player")
                                    ))))
                    .then(literal("nextweekly")
                            .executes(ctx -> prepareNextWeeklyForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                            .then(argument("player", EntityArgument.player())
                                    .executes(ctx -> prepareNextWeeklyForPlayer(
                                            ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "player")
                                    ))))
                    .then(literal("completeweekly")
                            .executes(ctx -> completeWeeklyForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                            .then(argument("player", EntityArgument.player())
                                    .executes(ctx -> completeWeeklyForPlayer(
                                            ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "player")
                                    ))))
                    .then(literal("story")
                            .then(literal("show")
                                    .executes(ctx -> showStoryForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                                    .then(argument("player", EntityArgument.player())
                                            .executes(ctx -> showStoryForPlayer(
                                                    ctx.getSource(),
                                                    EntityArgument.getPlayer(ctx, "player")
                                            ))))
                            .then(literal("reset")
                                    .executes(ctx -> resetStoryForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                                    .then(argument("player", EntityArgument.player())
                                            .executes(ctx -> resetStoryForPlayer(
                                                    ctx.getSource(),
                                                    EntityArgument.getPlayer(ctx, "player")
                                            ))))
                            .then(literal("complete")
                                    .executes(ctx -> completeStoryForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                                    .then(argument("player", EntityArgument.player())
                                            .executes(ctx -> completeStoryForPlayer(
                                                    ctx.getSource(),
                                                    EntityArgument.getPlayer(ctx, "player")
                                            )))))
                    .then(literal("completedaily")
                            .executes(ctx -> completeDailyForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                            .then(argument("player", EntityArgument.player())
                                    .executes(ctx -> completeDailyForPlayer(
                                            ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "player")
                                    ))))
                    .then(literal("project")
                            .then(literal("show")
                                    .executes(ctx -> showProjectsForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                                    .then(argument("player", EntityArgument.player())
                                            .executes(ctx -> showProjectsForPlayer(
                                                    ctx.getSource(),
                                                    EntityArgument.getPlayer(ctx, "player")
                                            ))))
                            .then(literal("unlock")
                                    .then(argument("player", EntityArgument.player())
                                            .then(argument("project", StringArgumentType.word())
                                                    .suggests(PROJECT_SUGGESTIONS)
                                                    .executes(ctx -> setProjectForPlayer(
                                                            ctx.getSource(),
                                                            EntityArgument.getPlayer(ctx, "player"),
                                                            parseProject(StringArgumentType.getString(ctx, "project")),
                                                            true
                                                    )))))
                            .then(literal("lock")
                                    .then(argument("player", EntityArgument.player())
                                            .then(argument("project", StringArgumentType.word())
                                                    .suggests(PROJECT_SUGGESTIONS)
                                                    .executes(ctx -> setProjectForPlayer(
                                                            ctx.getSource(),
                                                            EntityArgument.getPlayer(ctx, "player"),
                                                            parseProject(StringArgumentType.getString(ctx, "project")),
                                                            false
                                                    ))))))
                    .then(literal("shardcache")
                            .executes(ctx -> startShardCacheForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                            .then(argument("player", EntityArgument.player())
                                    .executes(ctx -> startShardCacheForPlayer(
                                            ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "player")
                                    ))))
                    .then(literal("shardcachetp")
                            .executes(ctx -> teleportToShardCacheForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                            .then(argument("player", EntityArgument.player())
                                    .executes(ctx -> teleportToShardCacheForPlayer(
                                            ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "player")
                                    ))))
                    .then(literal("coretest")
                            .then(literal("start")
                                    .executes(ctx -> startCoreTestForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                                    .then(argument("player", EntityArgument.player())
                                            .executes(ctx -> startCoreTestForPlayer(
                                                    ctx.getSource(),
                                                    EntityArgument.getPlayer(ctx, "player")
                                            ))))
                            .then(literal("reset")
                                    .executes(ctx -> resetCoreTestForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                                    .then(argument("player", EntityArgument.player())
                                            .executes(ctx -> resetCoreTestForPlayer(
                                                    ctx.getSource(),
                                                    EntityArgument.getPlayer(ctx, "player")
                                            ))))
                            .then(literal("show")
                                    .executes(ctx -> showCoreTestForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                                    .then(argument("player", EntityArgument.player())
                                            .executes(ctx -> showCoreTestForPlayer(
                                                    ctx.getSource(),
                                                    EntityArgument.getPlayer(ctx, "player")
                                            )))))
                    .then(literal("pilgrim")
                            .then(literal("spawn")
                                    .executes(ctx -> spawnPilgrimForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                                    .then(argument("player", EntityArgument.player())
                                            .executes(ctx -> spawnPilgrimForPlayer(
                                                    ctx.getSource(),
                                                    EntityArgument.getPlayer(ctx, "player")
                                            ))))
                            .then(literal("rumor")
                                    .then(literal("unlock")
                                            .executes(ctx -> setPilgrimRumorOverride(ctx.getSource(), ctx.getSource().getPlayer(), true))
                                            .then(argument("player", EntityArgument.player())
                                                    .executes(ctx -> setPilgrimRumorOverride(
                                                            ctx.getSource(),
                                                            EntityArgument.getPlayer(ctx, "player"),
                                                            true
                                                    ))))
                                    .then(literal("lock")
                                            .executes(ctx -> setPilgrimRumorOverride(ctx.getSource(), ctx.getSource().getPlayer(), false))
                                            .then(argument("player", EntityArgument.player())
                                                    .executes(ctx -> setPilgrimRumorOverride(
                                                            ctx.getSource(),
                                                            EntityArgument.getPlayer(ctx, "player"),
                                                            false
                                                    )))))
                            .then(literal("despawn")
                                    .executes(ctx -> despawnPilgrims(ctx.getSource()))))
                    .then(buildAdminWalletCommand())
                    .then(buildAdminReputationCommand());

            LiteralArgumentBuilder<CommandSourceStack> journalCommand = literal("journal").executes(ctx -> {
                var player = ctx.getSource().getPlayer();
                if (player instanceof ServerPlayer sp) {
                    var world = ctx.getSource().getServer().overworld();
                    QuestBookHelper.toggleJournal(world, sp);
                }
                return 1;
            });

            LiteralArgumentBuilder<CommandSourceStack> questMasterCommand = literal("questmaster")
                    .executes(ctx -> summonQuestMaster(ctx.getSource()));

            LiteralArgumentBuilder<CommandSourceStack> questTrackerCommand = literal("questtracker")
                    .executes(ctx -> toggleQuestTracker(ctx.getSource()))
                    .then(literal("on").executes(ctx -> setQuestTracker(ctx.getSource(), true)))
                    .then(literal("off").executes(ctx -> setQuestTracker(ctx.getSource(), false)));

            LiteralArgumentBuilder<CommandSourceStack> dailyQuestCommand = literal("daily")
                    .then(literal("accept").executes(ctx -> acceptQuest(ctx.getSource())));

            LiteralArgumentBuilder<CommandSourceStack> walletCommand = literal("wallet")
                    .executes(ctx -> showWallet(ctx.getSource()));

            LiteralArgumentBuilder<CommandSourceStack> reputationCommand = literal("reputation")
                    .executes(ctx -> showReputation(ctx.getSource()));

            CommandNode<CommandSourceStack> villageQuestCommand = dispatcher.register(literal("villagequest")
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

    private static LiteralArgumentBuilder<CommandSourceStack> buildAdminWalletCommand() {
        return literal("wallet")
                .then(literal("show")
                        .executes(ctx -> showWalletForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                        .then(argument("player", EntityArgument.player())
                                .executes(ctx -> showWalletForPlayer(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")
                                ))))
                .then(literal("add")
                        .then(argument("player", EntityArgument.player())
                                .then(argument("amount", LongArgumentType.longArg(1L))
                                        .executes(ctx -> adjustWalletForPlayer(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                LongArgumentType.getLong(ctx, "amount"),
                                                CurrencyService.CurrencyUnit.SILVERMARK,
                                                WalletAdjustmentMode.ADD
                                        ))
                                        .then(argument("unit", StringArgumentType.word())
                                                .suggests(WALLET_UNIT_SUGGESTIONS)
                                                .executes(ctx -> adjustWalletForPlayer(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        LongArgumentType.getLong(ctx, "amount"),
                                                        parseCurrencyUnit(StringArgumentType.getString(ctx, "unit")),
                                                        WalletAdjustmentMode.ADD
                                                ))))))
                .then(literal("remove")
                        .then(argument("player", EntityArgument.player())
                                .then(argument("amount", LongArgumentType.longArg(1L))
                                        .executes(ctx -> adjustWalletForPlayer(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                LongArgumentType.getLong(ctx, "amount"),
                                                CurrencyService.CurrencyUnit.SILVERMARK,
                                                WalletAdjustmentMode.REMOVE
                                        ))
                                        .then(argument("unit", StringArgumentType.word())
                                                .suggests(WALLET_UNIT_SUGGESTIONS)
                                                .executes(ctx -> adjustWalletForPlayer(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        LongArgumentType.getLong(ctx, "amount"),
                                                        parseCurrencyUnit(StringArgumentType.getString(ctx, "unit")),
                                                        WalletAdjustmentMode.REMOVE
                                                ))))))
                .then(literal("set")
                        .then(argument("player", EntityArgument.player())
                                .then(argument("amount", LongArgumentType.longArg(0L))
                                        .executes(ctx -> setWalletForPlayer(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                LongArgumentType.getLong(ctx, "amount"),
                                                CurrencyService.CurrencyUnit.SILVERMARK
                                        ))
                                        .then(argument("unit", StringArgumentType.word())
                                                .suggests(WALLET_UNIT_SUGGESTIONS)
                                                .executes(ctx -> setWalletForPlayer(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        LongArgumentType.getLong(ctx, "amount"),
                                                        parseCurrencyUnit(StringArgumentType.getString(ctx, "unit"))
                                                ))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAdminReputationCommand() {
        return literal("reputation")
                .then(literal("show")
                        .executes(ctx -> showReputationForPlayer(ctx.getSource(), ctx.getSource().getPlayer()))
                        .then(argument("player", EntityArgument.player())
                                .executes(ctx -> showReputationForPlayer(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")
                                ))))
                .then(literal("add")
                        .then(argument("player", EntityArgument.player())
                                .then(argument("track", StringArgumentType.word())
                                        .suggests(REPUTATION_TRACK_SUGGESTIONS)
                                        .then(argument("amount", LongArgumentType.longArg(1L))
                                                .executes(ctx -> adjustReputationForPlayer(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        parseReputationTrack(StringArgumentType.getString(ctx, "track")),
                                                        LongArgumentType.getLong(ctx, "amount"),
                                                        false
                                                ))))))
                .then(literal("set")
                        .then(argument("player", EntityArgument.player())
                                .then(argument("track", StringArgumentType.word())
                                        .suggests(REPUTATION_TRACK_SUGGESTIONS)
                                        .then(argument("amount", LongArgumentType.longArg(0L))
                                                .executes(ctx -> adjustReputationForPlayer(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        parseReputationTrack(StringArgumentType.getString(ctx, "track")),
                                                        LongArgumentType.getLong(ctx, "amount"),
                                                        true
                                                ))))));
    }

    private static int acceptQuest(CommandSourceStack source) {
        var player = source.getPlayer();
        if (player instanceof ServerPlayer sp) {
            var world = source.getServer().overworld();
            if (SpecialQuestService.acceptPendingOffer(world, sp)) {
            } else if (DailyQuestService.consumePendingShardOffer(world, sp.getUUID())) {
                DailyQuestService.activateShardBonusQuestOffer(world, sp);
            } else if (DailyQuestService.consumePendingBonusOffer(world, sp.getUUID())) {
                DailyQuestService.acceptBonusQuest(world, sp);
            } else if (DailyQuestService.consumePendingDailyOffer(world, sp.getUUID())) {
                DailyQuestService.acceptQuest(world, sp);
            }
        }
        return 1;
    }

    private static int resetDailyForPlayer(CommandSourceStack source, ServerPlayer target) {
        if (target == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        var world = source.getServer().overworld();
        DailyQuestService.adminResetDailyState(world, target.getUUID());
        refreshQuestUi(world, target);

        ServerPlayer sourcePlayer = source.getPlayer();
        if (sourcePlayer != null && sourcePlayer.getUUID().equals(target.getUUID())) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.resetdaily.self").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }

        source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.resetdaily.other", target.getDisplayName()).withStyle(ChatFormatting.GREEN), false);
        target.sendSystemMessage(Component.translatable("command.village-quest.questadmin.resetdaily.notify").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int prepareNextDailyForPlayer(CommandSourceStack source, ServerPlayer target) {
        if (target == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        var world = source.getServer().overworld();
        DailyQuestService.DailyQuestType previousChoice = DailyQuestService.adminPrepareNextDaily(world, target.getUUID());
        refreshQuestUi(world, target);
        Component previousName = previousChoice == null
                ? Component.literal("-")
                : DailyQuestService.displayKey(previousChoice);

        ServerPlayer sourcePlayer = source.getPlayer();
        if (sourcePlayer != null && sourcePlayer.getUUID().equals(target.getUUID())) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.nextdaily.self", previousName).withStyle(ChatFormatting.GREEN), false);
            return 1;
        }

        source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.nextdaily.other", target.getDisplayName(), previousName).withStyle(ChatFormatting.GREEN), false);
        target.sendSystemMessage(Component.translatable("command.village-quest.questadmin.nextdaily.notify").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int completeDailyForPlayer(CommandSourceStack source, ServerPlayer target) {
        if (target == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        var world = source.getServer().overworld();
        DailyQuestService.DailyQuestType activeQuest = DailyQuestService.activeQuestType(world, target.getUUID());
        if (activeQuest == null || !DailyQuestService.adminForceCompleteDaily(world, target)) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.completedaily.none").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        Component questName = DailyQuestService.displayKey(activeQuest);
        ServerPlayer sourcePlayer = source.getPlayer();
        if (sourcePlayer != null && sourcePlayer.getUUID().equals(target.getUUID())) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.completedaily.self", questName).withStyle(ChatFormatting.GREEN), false);
            return 1;
        }

        source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.completedaily.other", target.getDisplayName(), questName).withStyle(ChatFormatting.GREEN), false);
        target.sendSystemMessage(Component.translatable("command.village-quest.questadmin.completedaily.notify", questName).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int resetWeeklyForPlayer(CommandSourceStack source, ServerPlayer target) {
        if (target == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        var world = source.getServer().overworld();
        WeeklyQuestService.adminResetWeeklyState(world, target.getUUID());
        refreshQuestUi(world, target);

        ServerPlayer sourcePlayer = source.getPlayer();
        if (sourcePlayer != null && sourcePlayer.getUUID().equals(target.getUUID())) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.resetweekly.self").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }

        source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.resetweekly.other", target.getDisplayName()).withStyle(ChatFormatting.GREEN), false);
        target.sendSystemMessage(Component.translatable("command.village-quest.questadmin.resetweekly.notify").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int prepareNextWeeklyForPlayer(CommandSourceStack source, ServerPlayer target) {
        if (target == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        var world = source.getServer().overworld();
        WeeklyQuestService.WeeklyQuestType previousChoice = WeeklyQuestService.adminPrepareNextWeekly(world, target.getUUID());
        refreshQuestUi(world, target);
        Component previousName = previousChoice == null ? Component.literal("-") : WeeklyQuestService.displayName(previousChoice);

        ServerPlayer sourcePlayer = source.getPlayer();
        if (sourcePlayer != null && sourcePlayer.getUUID().equals(target.getUUID())) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.nextweekly.self", previousName).withStyle(ChatFormatting.GREEN), false);
            return 1;
        }

        source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.nextweekly.other", target.getDisplayName(), previousName).withStyle(ChatFormatting.GREEN), false);
        target.sendSystemMessage(Component.translatable("command.village-quest.questadmin.nextweekly.notify").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int completeWeeklyForPlayer(CommandSourceStack source, ServerPlayer target) {
        if (target == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        var world = source.getServer().overworld();
        WeeklyQuestService.WeeklyQuestType activeQuest = WeeklyQuestService.activeQuestType(world, target.getUUID());
        if (activeQuest == null || !WeeklyQuestService.adminForceCompleteWeekly(world, target)) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.completeweekly.none").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        Component questName = WeeklyQuestService.displayName(activeQuest);
        ServerPlayer sourcePlayer = source.getPlayer();
        if (sourcePlayer != null && sourcePlayer.getUUID().equals(target.getUUID())) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.completeweekly.self", questName).withStyle(ChatFormatting.GREEN), false);
            return 1;
        }

        source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.completeweekly.other", target.getDisplayName(), questName).withStyle(ChatFormatting.GREEN), false);
        target.sendSystemMessage(Component.translatable("command.village-quest.questadmin.completeweekly.notify", questName).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int showStoryForPlayer(CommandSourceStack source, ServerPlayer target) {
        if (target == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        var world = source.getServer().overworld();
        source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.story.header", target.getDisplayName()).withStyle(ChatFormatting.GOLD), false);
        for (Component line : StoryQuestService.buildOverview(world, target.getUUID())) {
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int resetStoryForPlayer(CommandSourceStack source, ServerPlayer target) {
        if (target == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        var world = source.getServer().overworld();
        if (!StoryQuestService.adminResetStoryState(world, target.getUUID())) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.story.reset.none").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        refreshQuestUi(world, target);

        ServerPlayer sourcePlayer = source.getPlayer();
        if (sourcePlayer != null && sourcePlayer.getUUID().equals(target.getUUID())) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.story.reset.self").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }

        source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.story.reset.other", target.getDisplayName()).withStyle(ChatFormatting.GREEN), false);
        target.sendSystemMessage(Component.translatable("command.village-quest.questadmin.story.reset.notify").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int completeStoryForPlayer(CommandSourceStack source, ServerPlayer target) {
        if (target == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        var world = source.getServer().overworld();
        if (!StoryQuestService.adminForceCompleteActiveChapter(world, target)) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.story.complete.none").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        ServerPlayer sourcePlayer = source.getPlayer();
        if (sourcePlayer != null && sourcePlayer.getUUID().equals(target.getUUID())) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.story.complete.self").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }

        source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.story.complete.other", target.getDisplayName()).withStyle(ChatFormatting.GREEN), false);
        target.sendSystemMessage(Component.translatable("command.village-quest.questadmin.story.complete.notify").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int showProjectsForPlayer(CommandSourceStack source, ServerPlayer target) {
        if (target == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        var world = source.getServer().overworld();
        source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.project.header", target.getDisplayName()).withStyle(ChatFormatting.GOLD), false);
        for (Component line : VillageProjectService.buildOverview(world, target.getUUID())) {
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int setProjectForPlayer(CommandSourceStack source, ServerPlayer target, VillageProjectType project, boolean unlocked) {
        if (target == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        if (project == null || project.alwaysUnlocked()) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.project.invalid").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        var world = source.getServer().overworld();
        if (!VillageProjectService.setUnlocked(world, target.getUUID(), project, unlocked)) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.project.no_change").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        refreshQuestUi(world, target);

        source.sendSuccess(() -> Component.translatable(
                unlocked
                        ? "command.village-quest.questadmin.project.unlock"
                        : "command.village-quest.questadmin.project.lock",
                target.getDisplayName(),
                Component.translatable("quest.village-quest.project." + project.id() + ".title")
        ).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int showWallet(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        long balance = CurrencyService.getBalance(source.getServer().overworld(), player.getUUID());
        source.sendSuccess(() -> Component.empty()
                .append(Component.translatable("command.village-quest.wallet.balance").withStyle(ChatFormatting.GRAY))
                .append(CurrencyService.formatBalance(balance)), false);
        return 1;
    }

    private static int showReputation(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        var world = source.getServer().overworld();
        source.sendSuccess(() -> Component.translatable("command.village-quest.reputation.header").withStyle(ChatFormatting.GOLD), false);
        for (Component line : ReputationService.buildOverview(world, player.getUUID())) {
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int showReputationForPlayer(CommandSourceStack source, ServerPlayer target) {
        if (target == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        var world = source.getServer().overworld();
        source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.reputation.header", target.getDisplayName()).withStyle(ChatFormatting.GOLD), false);
        for (Component line : ReputationService.buildOverview(world, target.getUUID())) {
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int adjustReputationForPlayer(CommandSourceStack source,
                                                 ServerPlayer target,
                                                 ReputationService.ReputationTrack track,
                                                 long amount,
                                                 boolean setValue) {
        if (target == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        if (track == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.reputation.track.invalid").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        var world = source.getServer().overworld();
        long clamped = Math.max(0L, Math.min(Integer.MAX_VALUE, amount));
        int current = ReputationService.get(world, target.getUUID(), track);
        int next = setValue ? (int) clamped : current + (int) clamped;
        if (setValue) {
            int delta = next - current;
            if (delta != 0) {
                ReputationService.add(world, target.getUUID(), track, delta);
            }
        } else {
            ReputationService.add(world, target.getUUID(), track, (int) clamped);
        }

        source.sendSuccess(() -> Component.translatable(
                setValue ? "command.village-quest.questadmin.reputation.set" : "command.village-quest.questadmin.reputation.add",
                target.getDisplayName(),
                ReputationService.displayName(track),
                next
        ).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int summonQuestMaster(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        var world = source.getServer().overworld();
        long summonCooldown = QuestMasterService.getPlayerSummonCooldownRemainingTicks(world, player.getUUID());
        if (summonCooldown > 0L) {
            source.sendSuccess(() -> Component.translatable(
                    "command.village-quest.questmaster.spawn.cooldown",
                    QuestMasterService.formatDuration(summonCooldown)
            ).withStyle(ChatFormatting.RED), false);
            return 0;
        }
        if (QuestMasterService.findNearbyQuestMaster(world, player.getX(), player.getY(), player.getZ()) != null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questmaster.spawn.exists").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        if (QuestMasterService.spawnNearPlayer(world, player) == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questmaster.spawn.failed").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("command.village-quest.questmaster.spawn.success").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int toggleQuestTracker(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        boolean enabled = QuestTrackerService.toggle(source.getServer().overworld(), player);
        source.sendSuccess(() -> Component.translatable(
                enabled ? "command.village-quest.questtracker.enabled" : "command.village-quest.questtracker.disabled"
        ).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int setQuestTracker(CommandSourceStack source, boolean enabled) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        QuestTrackerService.setEnabled(source.getServer().overworld(), player, enabled);
        source.sendSuccess(() -> Component.translatable(
                enabled ? "command.village-quest.questtracker.enabled" : "command.village-quest.questtracker.disabled"
        ).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int startShardCacheForPlayer(CommandSourceStack source, ServerPlayer target) {
        if (target == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        var world = source.getServer().overworld();
        if (!ShardRelicQuestService.adminStartCacheHunt(world, target)) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.shardcache.failed").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        ServerPlayer sourcePlayer = source.getPlayer();
        if (sourcePlayer != null && sourcePlayer.getUUID().equals(target.getUUID())) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.shardcache.self").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }

        source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.shardcache.other", target.getDisplayName()).withStyle(ChatFormatting.GREEN), false);
        target.sendSystemMessage(Component.translatable("command.village-quest.questadmin.shardcache.notify").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int teleportToShardCacheForPlayer(CommandSourceStack source, ServerPlayer target) {
        if (target == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        var world = source.getServer().overworld();
        if (!ShardRelicQuestService.adminTeleportToCache(world, target)) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.shardcachetp.failed").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        ServerPlayer sourcePlayer = source.getPlayer();
        if (sourcePlayer != null && sourcePlayer.getUUID().equals(target.getUUID())) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.shardcachetp.self").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }

        source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.shardcachetp.other", target.getDisplayName()).withStyle(ChatFormatting.GREEN), false);
        target.sendSystemMessage(Component.translatable("command.village-quest.questadmin.shardcachetp.notify").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int startCoreTestForPlayer(CommandSourceStack source, ServerPlayer target) {
        if (target == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        var world = source.getServer().overworld();
        if (!AdminCoreTestQuestService.adminStart(world, target)) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.coretest.show.none", target.getDisplayName()).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        source.sendSuccess(() -> Component.translatable(
                "command.village-quest.questadmin.coretest.start",
                target.getDisplayName()
        ).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int resetCoreTestForPlayer(CommandSourceStack source, ServerPlayer target) {
        if (target == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        var world = source.getServer().overworld();
        AdminCoreTestQuestService.adminReset(world, target);
        source.sendSuccess(() -> Component.translatable(
                "command.village-quest.questadmin.coretest.reset",
                target.getDisplayName()
        ).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int showCoreTestForPlayer(CommandSourceStack source, ServerPlayer target) {
        if (target == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        var world = source.getServer().overworld();
        var status = AdminCoreTestQuestService.openStatus(world, target.getUUID());
        if (status == null) {
            source.sendSuccess(() -> Component.translatable(
                    "command.village-quest.questadmin.coretest.show.none",
                    target.getDisplayName()
            ).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        source.sendSuccess(() -> Component.translatable(
                "command.village-quest.questadmin.coretest.show.header",
                target.getDisplayName()
        ).withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> status.title().copy().withStyle(ChatFormatting.AQUA), false);
        for (Component line : status.lines()) {
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int showWalletForPlayer(CommandSourceStack source, ServerPlayer target) {
        if (target == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        long balance = CurrencyService.getBalance(source.getServer().overworld(), target.getUUID());
        source.sendSuccess(() -> Component.translatable(
                "command.village-quest.questadmin.wallet.show",
                target.getDisplayName(),
                CurrencyService.formatBalance(balance)
        ).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int adjustWalletForPlayer(CommandSourceStack source,
                                             ServerPlayer target,
                                             long amount,
                                             CurrencyService.CurrencyUnit unit,
                                             WalletAdjustmentMode mode) {
        if (target == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        if (unit == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.wallet.unit.invalid").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        var world = source.getServer().overworld();
        long delta = CurrencyService.toBase(amount, unit);
        long newBalance;
        if (mode == WalletAdjustmentMode.ADD) {
            newBalance = CurrencyService.addBalance(world, target.getUUID(), delta);
        } else {
            long currentBalance = CurrencyService.getBalance(world, target.getUUID());
            if (!CurrencyService.removeBalance(world, target.getUUID(), delta)) {
                source.sendSuccess(() -> Component.translatable(
                        "command.village-quest.questadmin.wallet.remove.insufficient",
                        target.getDisplayName(),
                        CurrencyService.formatBalance(currentBalance)
                ).withStyle(ChatFormatting.RED), false);
                return 0;
            }
            newBalance = CurrencyService.getBalance(world, target.getUUID());
            delta = -delta;
        }

        long finalDelta = delta;
        source.sendSuccess(() -> Component.translatable(
                "command.village-quest.questadmin.wallet.adjust",
                target.getDisplayName(),
                CurrencyService.formatDelta(finalDelta),
                CurrencyService.formatBalance(newBalance)
        ).withStyle(ChatFormatting.GREEN), false);
        if (shouldNotifyWalletTarget(source, target)) {
            target.sendSystemMessage(Component.translatable(
                    "command.village-quest.questadmin.wallet.adjust.notify",
                    CurrencyService.formatDelta(finalDelta),
                    CurrencyService.formatBalance(newBalance)
            ).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int setWalletForPlayer(CommandSourceStack source,
                                          ServerPlayer target,
                                          long amount,
                                          CurrencyService.CurrencyUnit unit) {
        if (target == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        if (unit == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.wallet.unit.invalid").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        var world = source.getServer().overworld();
        long newBalance = CurrencyService.setBalance(world, target.getUUID(), CurrencyService.toBase(amount, unit));
        source.sendSuccess(() -> Component.translatable(
                "command.village-quest.questadmin.wallet.set",
                target.getDisplayName(),
                CurrencyService.formatBalance(newBalance)
        ).withStyle(ChatFormatting.GREEN), false);
        if (shouldNotifyWalletTarget(source, target)) {
            target.sendSystemMessage(Component.translatable(
                    "command.village-quest.questadmin.wallet.set.notify",
                    CurrencyService.formatBalance(newBalance)
            ).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static boolean shouldNotifyWalletTarget(CommandSourceStack source, ServerPlayer target) {
        ServerPlayer executor = source.getPlayer();
        return executor == null || !executor.getUUID().equals(target.getUUID());
    }

    private static int spawnPilgrimForPlayer(CommandSourceStack source, ServerPlayer target) {
        if (target == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        var world = source.getServer().overworld();
        if (PilgrimService.findActivePilgrim(world) != null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.pilgrim.spawn.exists").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        var pilgrim = PilgrimService.spawnNearPlayer(world, target, true);
        if (pilgrim == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.pilgrim.spawn.failed").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        source.sendSuccess(() -> Component.translatable(
                "command.village-quest.questadmin.pilgrim.spawn.success",
                target.getDisplayName()
        ).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int despawnPilgrims(CommandSourceStack source) {
        int removed = PilgrimService.despawnAll(source.getServer().overworld());
        if (removed <= 0) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.pilgrim.despawn.none").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "command.village-quest.questadmin.pilgrim.despawn.success",
                removed
        ).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int setPilgrimRumorOverride(CommandSourceStack source, ServerPlayer target, boolean enabled) {
        if (target == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        var world = source.getServer().overworld();
        PilgrimContractService.adminSetRumorUnlocked(world, target, enabled);
        refreshQuestUi(world, target);

        ServerPlayer sourcePlayer = source.getPlayer();
        if (sourcePlayer != null && sourcePlayer.getUUID().equals(target.getUUID())) {
            source.sendSuccess(() -> Component.translatable(
                    enabled
                            ? "command.village-quest.questadmin.pilgrim.rumor.unlock.self"
                            : "command.village-quest.questadmin.pilgrim.rumor.lock.self"
            ).withStyle(ChatFormatting.GREEN), false);
            return 1;
        }

        source.sendSuccess(() -> Component.translatable(
                enabled
                        ? "command.village-quest.questadmin.pilgrim.rumor.unlock.other"
                        : "command.village-quest.questadmin.pilgrim.rumor.lock.other",
                target.getDisplayName()
        ).withStyle(ChatFormatting.GREEN), false);
        target.sendSystemMessage(Component.translatable(
                enabled
                        ? "command.village-quest.questadmin.pilgrim.rumor.unlock.notify"
                        : "command.village-quest.questadmin.pilgrim.rumor.lock.notify"
        ).withStyle(ChatFormatting.GRAY), false);
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

    private static void refreshQuestUi(net.minecraft.server.level.ServerLevel world, ServerPlayer player) {
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
