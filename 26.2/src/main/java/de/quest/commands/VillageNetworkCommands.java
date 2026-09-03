package de.quest.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.quest.config.VillageQuestServerConfig;
import de.quest.guild.VillageGuildProject;
import de.quest.guild.VillageGuildService;
import de.quest.shrine.VillageBondService;
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

    private static NameAndId profile(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                     String argument) throws CommandSyntaxException {
        return GameProfileArgument.getGameProfiles(context, argument).iterator().next();
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
