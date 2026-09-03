package de.quest.guild;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Server-facing guild rules and benefits. */
public final class VillageGuildService {
    private VillageGuildService() {}

    public static VillageGuildState.GuildSnapshot guild(ServerLevel world, UUID playerId) {
        return world == null ? null : VillageGuildState.get(world.getServer()).guildFor(playerId).orElse(null);
    }

    public static int noticeSupportBonus(ServerLevel world, UUID playerId) {
        VillageGuildState.GuildSnapshot guild = guild(world, playerId);
        return guild != null && guild.project() == VillageGuildProject.COMMON_RESERVE ? 2 : 0;
    }

    public static int routeEnergyBonus(ServerLevel world, UUID playerId) {
        VillageGuildState.GuildSnapshot guild = guild(world, playerId);
        return guild != null && guild.project() == VillageGuildProject.WAYSTATION ? 1 : 0;
    }

    public static double noticeRewardBonus(ServerLevel world, UUID playerId) {
        VillageGuildState.GuildSnapshot guild = guild(world, playerId);
        return guild != null && guild.project() == VillageGuildProject.ARCHIVE_EXCHANGE ? 1.10 : 1.0;
    }

    public static void recordDelivery(ServerLevel world, UUID playerId, boolean matchingNeed) {
        if (world == null || playerId == null) return;
        VillageGuildState.get(world.getServer()).addRenown(playerId, matchingNeed ? 8 : 5);
    }

    public static void recordRouteArrival(ServerLevel world, UUID playerId, boolean suppliedFreight) {
        if (world == null || playerId == null) return;
        VillageGuildState.get(world.getServer()).addRenown(playerId, suppliedFreight ? 6 : 3);
    }

    public static int create(ServerLevel world, ServerPlayer player, String name) {
        if (guild(world, player.getUUID()) != null) return fail(player, "command.village-quest.guild.already_member");
        VillageGuildState.GuildSnapshot created = VillageGuildState.get(world.getServer())
                .create(player.getUUID(), VillageGuildState.sanitizeName(name));
        if (created == null) return fail(player, "command.village-quest.guild.create_failed");
        player.sendSystemMessage(Component.translatable("command.village-quest.guild.created", created.name())
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    public static int invite(ServerLevel world, ServerPlayer actor, ServerPlayer target) {
        return invite(world, actor, target.getUUID(), target.getName());
    }

    public static int invite(ServerLevel world, ServerPlayer actor, UUID targetId, Component targetName) {
        if (!VillageGuildState.get(world.getServer()).invite(actor.getUUID(), targetId)) {
            return fail(actor, "command.village-quest.guild.invite_failed");
        }
        VillageGuildState.GuildSnapshot guild = guild(world, actor.getUUID());
        actor.sendSystemMessage(Component.translatable("command.village-quest.guild.invited", targetName)
                .withStyle(ChatFormatting.GREEN), false);
        ServerPlayer onlineTarget = world.getServer().getPlayerList().getPlayer(targetId);
        if (onlineTarget != null) {
            onlineTarget.sendSystemMessage(Component.translatable("command.village-quest.guild.invitation",
                    guild == null ? Component.literal("Guild") : Component.literal(guild.name()))
                    .withStyle(ChatFormatting.GOLD), false);
        }
        return 1;
    }

    public static int accept(ServerLevel world, ServerPlayer player) {
        VillageGuildState.GuildSnapshot accepted = VillageGuildState.get(world.getServer()).accept(player.getUUID());
        if (accepted == null) return fail(player, "command.village-quest.guild.no_invitation");
        player.sendSystemMessage(Component.translatable("command.village-quest.guild.joined", accepted.name())
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    public static int leave(ServerLevel world, ServerPlayer player) {
        if (!VillageGuildState.get(world.getServer()).leave(player.getUUID())) {
            return fail(player, "command.village-quest.guild.leave_failed");
        }
        player.sendSystemMessage(Component.translatable("command.village-quest.guild.left")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    public static int promote(ServerLevel world, ServerPlayer actor, ServerPlayer target) {
        return promote(world, actor, target.getUUID(), target.getName());
    }

    public static int promote(ServerLevel world, ServerPlayer actor, UUID targetId, Component targetName) {
        if (!VillageGuildState.get(world.getServer()).promote(actor.getUUID(), targetId)) {
            return fail(actor, "command.village-quest.guild.promote_failed");
        }
        actor.sendSystemMessage(Component.translatable("command.village-quest.guild.promoted", targetName)
                .withStyle(ChatFormatting.GREEN), false);
        ServerPlayer onlineTarget = world.getServer().getPlayerList().getPlayer(targetId);
        if (onlineTarget != null) {
            onlineTarget.sendSystemMessage(Component.translatable("command.village-quest.guild.promoted_target")
                    .withStyle(ChatFormatting.GOLD), false);
        }
        return 1;
    }

    public static int transferLeadership(ServerLevel world, ServerPlayer actor, ServerPlayer target) {
        return transferLeadership(world, actor, target.getUUID(), target.getName());
    }

    public static int transferLeadership(ServerLevel world, ServerPlayer actor, UUID targetId, Component targetName) {
        if (!VillageGuildState.get(world.getServer()).transferLeadership(actor.getUUID(), targetId)) {
            return fail(actor, "command.village-quest.guild.transfer_failed");
        }
        actor.sendSystemMessage(Component.translatable("command.village-quest.guild.transferred", targetName)
                .withStyle(ChatFormatting.GOLD), false);
        ServerPlayer onlineTarget = world.getServer().getPlayerList().getPlayer(targetId);
        if (onlineTarget != null) {
            onlineTarget.sendSystemMessage(Component.translatable("command.village-quest.guild.transferred_target")
                    .withStyle(ChatFormatting.GREEN), false);
        }
        return 1;
    }

    public static int kick(ServerLevel world, ServerPlayer actor, ServerPlayer target) {
        return kick(world, actor, target.getUUID(), target.getName());
    }

    public static int kick(ServerLevel world, ServerPlayer actor, UUID targetId, Component targetName) {
        if (!VillageGuildState.get(world.getServer()).kick(actor.getUUID(), targetId)) {
            return fail(actor, "command.village-quest.guild.kick_failed");
        }
        actor.sendSystemMessage(Component.translatable("command.village-quest.guild.kicked", targetName)
                .withStyle(ChatFormatting.GRAY), false);
        ServerPlayer onlineTarget = world.getServer().getPlayerList().getPlayer(targetId);
        if (onlineTarget != null) {
            onlineTarget.sendSystemMessage(Component.translatable("command.village-quest.guild.kicked_target")
                    .withStyle(ChatFormatting.RED), false);
        }
        return 1;
    }

    public static int selectProject(ServerLevel world, ServerPlayer actor, VillageGuildProject project) {
        if (!VillageGuildState.get(world.getServer()).selectProject(actor.getUUID(), project)) {
            return fail(actor, "command.village-quest.guild.project_failed");
        }
        actor.sendSystemMessage(Component.translatable("command.village-quest.guild.project_selected",
                project.label(), project.benefit()).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    public static int showStatus(ServerLevel world, ServerPlayer player) {
        for (Component line : statusLines(world, player.getUUID())) player.sendSystemMessage(line, false);
        return 1;
    }

    public static List<Component> statusLines(ServerLevel world, UUID playerId) {
        VillageGuildState.GuildSnapshot guild = guild(world, playerId);
        if (guild == null) return List.of(Component.translatable("command.village-quest.guild.none")
                .withStyle(ChatFormatting.GRAY));
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("command.village-quest.guild.status", guild.name(),
                guild.role(playerId).label(), guild.members().size()).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("command.village-quest.guild.renown", guild.renown(), guild.rank(),
                guild.nextRankThreshold()).withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("command.village-quest.guild.project",
                guild.project().label(), guild.project().benefit()).withStyle(ChatFormatting.GRAY));
        return List.copyOf(lines);
    }

    private static int fail(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.RED), false);
        return 0;
    }
}
