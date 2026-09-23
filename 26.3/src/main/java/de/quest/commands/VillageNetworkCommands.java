package de.quest.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.quest.config.VillageQuestServerConfig;
import de.quest.guild.VillageGuildProject;
import de.quest.guild.VillageGuildService;
import de.quest.guildtown.GuildTownCommission;
import de.quest.guildtown.GuildTownService;
import de.quest.guildtown.GuildTownSharedProject;
import de.quest.content.story.ShadowsTradeRoadEncounterService;
import de.quest.shrine.VillageBondService;
import de.quest.village.GuildCornerPlacementService;
import de.quest.village.LivingVillageNetworkService;
import de.quest.village.LivingVillageNetworkState;
import de.quest.village.NetworkSpecialization;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Command surface for Living Village Network prestige and multiplayer guild administration. */
final class VillageNetworkCommands {
    private static final SuggestionProvider<CommandSourceStack> SPECIALIZATIONS = (ctx, builder) -> {
        for (NetworkSpecialization value : NetworkSpecialization.values()) {
            if (value != NetworkSpecialization.NONE) builder.suggest(value.key());
        }
        return builder.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> PROJECTS = (ctx, builder) -> {
        for (VillageGuildProject value : VillageGuildProject.values()) {
            if (value != VillageGuildProject.NONE) builder.suggest(value.key());
        }
        return builder.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> COMMISSIONS = (ctx, builder) -> {
        for (GuildTownCommission value : GuildTownCommission.values()) builder.suggest(value.key());
        return builder.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> SHARED_PROJECTS = (ctx, builder) -> {
        for (GuildTownSharedProject value : GuildTownSharedProject.values()) builder.suggest(value.key());
        return builder.buildFuture();
    };

    private VillageNetworkCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> networkCommand() {
        return literal("network")
                .executes(ctx -> showNetwork(ctx.getSource()))
                .then(literal("status").executes(ctx -> showNetwork(ctx.getSource())))
                .then(literal("specialize")
                        .then(argument("specialization", StringArgumentType.word())
                                .suggests(SPECIALIZATIONS)
                                .executes(ctx -> previewSpecialization(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "specialization")))
                                .then(literal("confirm")
                                        .executes(ctx -> specialize(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "specialization"))))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> guildCommand() {
        return literal("guild")
                .executes(ctx -> showGuild(ctx.getSource()))
                .then(literal("status").executes(ctx -> showGuild(ctx.getSource())))
                .then(literal("create").then(argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> createGuild(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(literal("invite").then(argument("player", GameProfileArgument.gameProfile())
                        .executes(ctx -> invite(ctx.getSource(), profile(ctx, "player")))))
                .then(literal("accept").executes(ctx -> accept(ctx.getSource())))
                .then(literal("leave").executes(ctx -> leave(ctx.getSource())))
                .then(literal("promote").then(argument("player", GameProfileArgument.gameProfile())
                        .executes(ctx -> promote(ctx.getSource(), profile(ctx, "player")))))
                .then(literal("transfer").then(argument("player", GameProfileArgument.gameProfile())
                        .executes(ctx -> transfer(ctx.getSource(), profile(ctx, "player")))))
                .then(literal("kick").then(argument("player", GameProfileArgument.gameProfile())
                        .executes(ctx -> kick(ctx.getSource(), profile(ctx, "player")))))
                .then(literal("project").then(argument("project", StringArgumentType.word())
                        .suggests(PROJECTS)
                        .executes(ctx -> selectProject(ctx.getSource(),
                                StringArgumentType.getString(ctx, "project")))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> townCommand() {
        return literal("town")
                .executes(ctx -> showTown(ctx.getSource()))
                .then(literal("status").executes(ctx -> showTown(ctx.getSource())))
                .then(literal("chronicle")
                        .executes(ctx -> showChronicle(ctx.getSource(), 1))
                        .then(argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> showChronicle(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "page")))))
                .then(literal("retrofit").executes(ctx -> retrofit(ctx.getSource())))
                .then(literal("story")
                        .then(literal("accept").executes(ctx -> acceptTownStory(ctx.getSource())))
                        .then(literal("pause").executes(ctx -> pauseTownStory(ctx.getSource(), true)))
                        .then(literal("resume").executes(ctx -> pauseTownStory(ctx.getSource(), false)))
                        .then(literal("recover").executes(ctx -> recoverTownStory(ctx.getSource())))
                        .then(literal("choose")
                                .then(literal("reserve").executes(ctx -> chooseSharedTable(ctx.getSource(), "reserve")))
                                .then(literal("share").executes(ctx -> chooseSharedTable(ctx.getSource(), "share"))))
                        .then(literal("deliver").executes(ctx -> deliverTownStory(ctx.getSource()))))
                .then(literal("commission")
                        .then(literal("accept")
                                .then(argument("commission", StringArgumentType.word()).suggests(COMMISSIONS)
                                        .executes(ctx -> acceptCommission(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "commission"), null))
                                        .then(argument("networkOwner", GameProfileArgument.gameProfile())
                                                .executes(ctx -> acceptCommission(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "commission"),
                                                        profile(ctx, "networkOwner"))))))
                        .then(literal("pause").executes(ctx -> pauseCommission(ctx.getSource(), true)))
                        .then(literal("resume").executes(ctx -> pauseCommission(ctx.getSource(), false)))
                        .then(literal("abandon").executes(ctx -> abandonCommission(ctx.getSource())))
                        .then(literal("deliver").executes(ctx -> deliverCommission(ctx.getSource()))))
                .then(literal("project")
                        .then(literal("accept")
                                .then(argument("project", StringArgumentType.word()).suggests(SHARED_PROJECTS)
                                        .executes(ctx -> acceptSharedProject(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "project")))))
                        .then(literal("contribute").executes(ctx -> contributeSharedProject(ctx.getSource())))
                        .then(literal("claim").executes(ctx -> claimSharedProject(ctx.getSource()))))
                .then(literal("concord")
                        .then(literal("claim").executes(ctx -> claimConcord(ctx.getSource()))));
    }

    private static NameAndId profile(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                     String argument) throws CommandSyntaxException {
        return GameProfileArgument.getGameProfiles(context, argument).iterator().next();
    }

    private static int showTown(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? 0 : GuildTownService.showStatus(source.getServer().overworld(), player);
    }

    private static int showChronicle(CommandSourceStack source, int page) {
        ServerPlayer player = source.getPlayer();
        return player == null ? 0 : GuildTownService.showChronicle(source.getServer().overworld(), player, page);
    }

    private static int acceptTownStory(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? 0 : GuildTownService.acceptCurrentStory((ServerLevel) player.level(), player);
    }

    private static int chooseSharedTable(CommandSourceStack source, String choice) {
        ServerPlayer player = source.getPlayer();
        return player == null ? 0 : GuildTownService.chooseSharedTable((ServerLevel) player.level(), player, choice);
    }

    private static int pauseTownStory(CommandSourceStack source, boolean paused) {
        ServerPlayer player = source.getPlayer();
        return player == null ? 0 : GuildTownService.pauseStory((ServerLevel) player.level(), player, paused);
    }

    private static int recoverTownStory(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? 0 : GuildTownService.recoverLongDrive((ServerLevel) player.level(), player);
    }

    private static int deliverTownStory(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? 0 : GuildTownService.deliverStory((ServerLevel) player.level(), player);
    }

    private static int acceptCommission(CommandSourceStack source, String raw, NameAndId owner) {
        ServerPlayer player = source.getPlayer();
        GuildTownCommission commission = GuildTownCommission.byKey(raw);
        if (player == null || commission == null) return 0;
        return GuildTownService.acceptCommission((ServerLevel) player.level(), player, commission,
                owner == null ? player.getUUID() : owner.id(),
                owner == null ? player.getName() : Component.literal(owner.name()));
    }

    private static int pauseCommission(CommandSourceStack source, boolean paused) {
        ServerPlayer player = source.getPlayer();
        return player == null ? 0 : GuildTownService.pauseCommission((ServerLevel) player.level(), player, paused);
    }

    private static int deliverCommission(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? 0 : GuildTownService.deliverCommission((ServerLevel) player.level(), player);
    }

    private static int abandonCommission(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? 0 : GuildTownService.abandonCommission((ServerLevel) player.level(), player);
    }

    private static int acceptSharedProject(CommandSourceStack source, String raw) {
        ServerPlayer player = source.getPlayer();
        GuildTownSharedProject project = GuildTownSharedProject.byKey(raw);
        return player == null || project == null ? 0
                : GuildTownService.acceptSharedProject((ServerLevel) player.level(), player, project);
    }

    private static int contributeSharedProject(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? 0 : GuildTownService.contributeSharedProject((ServerLevel) player.level(), player);
    }

    private static int claimSharedProject(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? 0 : GuildTownService.claimSharedProject((ServerLevel) player.level(), player);
    }

    private static int claimConcord(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? 0 : GuildTownService.claimConcord((ServerLevel) player.level(), player);
    }

    private static int retrofit(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !(player.level() instanceof ServerLevel world)) return 0;
        ShadowsTradeRoadEncounterService.VillageMarker marker =
                ShadowsTradeRoadEncounterService.currentVillage(world, player.blockPosition());
        GuildCornerPlacementService.ManualPlacementResult result =
                GuildCornerPlacementService.requestManualPlacement(world, player, marker);
        player.sendSystemMessage(Component.translatable(result.translationKey())
                .withStyle(result == GuildCornerPlacementService.ManualPlacementResult.PLACED
                        || result == GuildCornerPlacementService.ManualPlacementResult.ALREADY_GENERATED
                        ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        return result == GuildCornerPlacementService.ManualPlacementResult.PLACED
                || result == GuildCornerPlacementService.ManualPlacementResult.ALREADY_GENERATED ? 1 : 0;
    }

    private static int showNetwork(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        ServerLevel world = source.getServer().overworld();
        var progress = LivingVillageNetworkState.get(source.getServer()).network(player.getUUID());
        player.sendSystemMessage(Component.translatable("command.village-quest.network.status",
                progress.rank(), progress.renown(), progress.nextRankThreshold(),
                progress.specialization().label(), LivingVillageNetworkService.honorLabel(progress.rank()))
                .withStyle(ChatFormatting.GOLD), false);
        player.sendSystemMessage(Component.translatable("command.village-quest.network.villages",
                VillageBondService.villageCount(world, player.getUUID()),
                Component.translatable("text.village-quest.adventure_profile."
                        + VillageQuestServerConfig.get().adventureProfile().name().toLowerCase(java.util.Locale.ROOT)))
                .withStyle(ChatFormatting.AQUA), false);
        if (progress.specialization() != NetworkSpecialization.NONE) {
            player.sendSystemMessage(progress.specialization().benefit().copy().withStyle(ChatFormatting.GRAY), false);
        } else if (progress.rank() >= 2) {
            player.sendSystemMessage(Component.translatable("command.village-quest.network.specialization_ready")
                    .withStyle(ChatFormatting.GREEN), false);
        }
        return 1;
    }

    private static int specialize(CommandSourceStack source, String raw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        NetworkSpecialization specialization = NetworkSpecialization.byKey(raw);
        LivingVillageNetworkState state = LivingVillageNetworkState.get(source.getServer());
        if (!validateSpecialization(player, specialization, state.network(player.getUUID()))) return 0;
        if (!state.specialize(player.getUUID(), specialization)) {
            player.sendSystemMessage(Component.translatable("command.village-quest.network.specialization_failed")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        player.sendSystemMessage(Component.translatable("command.village-quest.network.specialized",
                specialization.label(), specialization.benefit()).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int previewSpecialization(CommandSourceStack source, String raw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        NetworkSpecialization specialization = NetworkSpecialization.byKey(raw);
        var progress = LivingVillageNetworkState.get(source.getServer()).network(player.getUUID());
        if (!validateSpecialization(player, specialization, progress)) return 0;
        player.sendSystemMessage(Component.translatable("command.village-quest.network.specialization_confirm",
                specialization.label(), specialization.benefit(), specialization.key())
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static boolean validateSpecialization(ServerPlayer player, NetworkSpecialization specialization,
                                                  LivingVillageNetworkState.NetworkSnapshot progress) {
        if (specialization == NetworkSpecialization.NONE) {
            player.sendSystemMessage(Component.translatable(
                    "command.village-quest.network.specialization_invalid").withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (progress.specialization() != NetworkSpecialization.NONE) {
            player.sendSystemMessage(Component.translatable(
                    "command.village-quest.network.specialization_already_chosen",
                    progress.specialization().label()).withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (progress.rank() < 2) {
            player.sendSystemMessage(Component.translatable(
                    "command.village-quest.network.specialization_rank_required")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        return true;
    }

    private static int showGuild(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? 0 : VillageGuildService.showStatus(source.getServer().overworld(), player);
    }

    private static int createGuild(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        return player == null ? 0 : VillageGuildService.create(source.getServer().overworld(), player, name);
    }

    private static int invite(CommandSourceStack source, NameAndId target) {
        ServerPlayer player = source.getPlayer();
        return player == null ? 0 : VillageGuildService.invite(source.getServer().overworld(), player,
                target.id(), Component.literal(target.name()));
    }

    private static int accept(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? 0 : VillageGuildService.accept(source.getServer().overworld(), player);
    }

    private static int leave(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? 0 : VillageGuildService.leave(source.getServer().overworld(), player);
    }

    private static int promote(CommandSourceStack source, NameAndId target) {
        ServerPlayer player = source.getPlayer();
        return player == null ? 0 : VillageGuildService.promote(source.getServer().overworld(), player,
                target.id(), Component.literal(target.name()));
    }

    private static int transfer(CommandSourceStack source, NameAndId target) {
        ServerPlayer player = source.getPlayer();
        return player == null ? 0 : VillageGuildService.transferLeadership(source.getServer().overworld(), player,
                target.id(), Component.literal(target.name()));
    }

    private static int kick(CommandSourceStack source, NameAndId target) {
        ServerPlayer player = source.getPlayer();
        return player == null ? 0 : VillageGuildService.kick(source.getServer().overworld(), player,
                target.id(), Component.literal(target.name()));
    }

    private static int selectProject(CommandSourceStack source, String raw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        VillageGuildProject project = VillageGuildProject.byKey(raw);
        if (project == VillageGuildProject.NONE) {
            player.sendSystemMessage(Component.translatable("command.village-quest.guild.project_failed")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        return VillageGuildService.selectProject(source.getServer().overworld(), player, project);
    }
}
