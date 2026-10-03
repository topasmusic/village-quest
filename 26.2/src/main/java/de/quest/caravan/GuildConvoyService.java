package de.quest.caravan;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.guild.VillageGuildState;
import de.quest.guildtown.GuildTownProgress;
import de.quest.guildtown.GuildTownSharedState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Late guild relay over three existing hub-and-spoke caravan connections. */
public final class GuildConvoyService {
    private GuildConvoyService() {}

    public static int status(ServerLevel world, ServerPlayer player) {
        if (!valid(world, player)) return 0;
        VillageGuildState.GuildSnapshot guild = VillageGuildState.get(world.getServer())
                .guildFor(player.getUUID()).orElse(null);
        if (guild == null) {
            player.sendSystemMessage(Component.translatable("message.village-quest.convoy.need_guild")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        GuildConvoyState.Convoy convoy = GuildConvoyState.get(world.getServer()).active(guild.id());
        if (convoy == null) {
            player.sendSystemMessage(Component.translatable("message.village-quest.convoy.idle")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        PlayerQuestData ownerData = QuestState.get(world.getServer()).getPlayerData(convoy.ownerId());
        int route = routeIndex(ownerData, convoy.currentConnection());
        Component routeName = route < 0 ? Component.translatable("message.village-quest.convoy.route_missing")
                : TradeRouteData.routeName(ownerData, route);
        player.sendSystemMessage(Component.translatable("message.village-quest.convoy.status",
                convoy.stopNumber(), 3, routeName,
                Component.translatable(convoy.headingToVillage()
                        ? "message.village-quest.convoy.to_village"
                        : "message.village-quest.convoy.to_hub"),
                convoy.credited().size(), convoy.participants().size())
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    public static int start(ServerLevel world, ServerPlayer player) {
        if (!valid(world, player)) return 0;
        if (!de.quest.reputation.ReputationAccessService.require(world, player,
                de.quest.reputation.SocialReputationRules.ServiceKind.CONVOY_START, null)) return 0;
        VillageGuildState.GuildSnapshot guild = VillageGuildState.get(world.getServer())
                .guildFor(player.getUUID()).orElse(null);
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(player.getUUID());
        if (guild == null || !guild.role(player.getUUID()).canChooseProject()
                || !GuildTownProgress.hasReward(data, "concord_title")
                || !TradeRouteService.isNearHome(world, player.getUUID(), player.blockPosition(), 20)) {
            player.sendSystemMessage(Component.translatable("message.village-quest.convoy.locked")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        List<UUID> connections = new ArrayList<>();
        int count = Math.min(TradeRouteService.MAX_ROUTES, data.getTradeRouteInt("route_count"));
        for (int route = 0; route < count && connections.size() < 3; route++) {
            if (!TradeRouteService.dispatchReady(world, data, route)) continue;
            connections.add(TradeRouteData.ensureConnectionId(data, route));
        }
        if (connections.size() != 3) {
            player.sendSystemMessage(Component.translatable("message.village-quest.convoy.need_routes")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        if (!GuildConvoyState.get(world.getServer()).start(guild.id(), player.getUUID(),
                connections, world.getGameTime())) {
            player.sendSystemMessage(Component.translatable("message.village-quest.convoy.cooldown")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        QuestState.get(world.getServer()).setDirty();
        GuildTownSharedState.get(world.getServer()).addChronicle(guild.id(),
                "convoy.started." + GuildConvoyState.get(world.getServer()).active(guild.id()).id());
        player.sendSystemMessage(Component.translatable("message.village-quest.convoy.started")
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    public static int join(ServerLevel world, ServerPlayer player) {
        if (!valid(world, player)) return 0;
        if (!de.quest.reputation.ReputationAccessService.require(world, player,
                de.quest.reputation.SocialReputationRules.ServiceKind.CONVOY_JOIN, null)) return 0;
        VillageGuildState.GuildSnapshot guild = VillageGuildState.get(world.getServer())
                .guildFor(player.getUUID()).orElse(null);
        GuildConvoyState.Convoy convoy = guild == null ? null
                : GuildConvoyState.get(world.getServer()).active(guild.id());
        if (convoy == null) return status(world, player);
        PlayerQuestData ownerData = QuestState.get(world.getServer()).getPlayerData(convoy.ownerId());
        if (!TradeRouteService.isNearHome(world, convoy.ownerId(), player.blockPosition(), 20)
                && !TradeRouteService.isNearRouteMaster(world, player, convoy.ownerId(),
                convoy.currentConnection())) {
            player.sendSystemMessage(Component.translatable("message.village-quest.convoy.join_nearby")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        if (routeIndex(ownerData, convoy.currentConnection()) < 0
                || !GuildConvoyState.get(world.getServer()).join(guild.id(), player.getUUID())) return 0;
        player.sendSystemMessage(Component.translatable("message.village-quest.convoy.joined")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    static void onArrival(ServerLevel world, UUID ownerId, PlayerQuestData data,
                          UUID connection, boolean atVillage) {
        GuildConvoyState state = GuildConvoyState.get(world.getServer());
        GuildConvoyState.Convoy active = state.forOwner(ownerId);
        if (active == null) return;
        if (!ownerStillInGuild(world, active)) {
            cancel(world, state.cancelGuild(active.guildId()));
            return;
        }
        if (atVillage && active.headingToVillage()
                && connection != null && connection.equals(active.currentConnection())) {
            creditNearbyEscorts(world, state, active, data, connection);
        }
        GuildConvoyState.AdvanceResult result = state.arrive(ownerId, connection, atVillage);
        if (!result.advanced()) return;
        if (result.completed()) {
            finish(world, data, result.convoy());
            reconcileTrust(world.getServer());
        } else {
            notifyOwner(world, ownerId, Component.translatable("message.village-quest.convoy.next_stop",
                    result.convoy().stopNumber(), 3));
        }
    }

    static void onOwnerTick(ServerLevel world, UUID ownerId, PlayerQuestData data) {
        GuildConvoyState state = GuildConvoyState.get(world.getServer());
        GuildConvoyState.Convoy convoy = state.forOwner(ownerId);
        if (convoy == null) return;
        if (!ownerStillInGuild(world, convoy)) {
            cancel(world, state.cancelGuild(convoy.guildId()));
            return;
        }
        for (UUID connection : convoy.connections()) {
            if (routeIndex(data, connection) >= 0) continue;
            cancel(world, state.cancelGuild(convoy.guildId()));
            return;
        }
    }

    static void onRouteRemoved(ServerLevel world, UUID ownerId, UUID connection) {
        cancel(world, GuildConvoyState.get(world.getServer())
                .cancelForRemovedRoute(ownerId, connection));
    }

    static boolean currentLeg(ServerLevel world, UUID ownerId, UUID connection) {
        GuildConvoyState.Convoy convoy = GuildConvoyState.get(world.getServer()).forOwner(ownerId);
        return convoy != null && connection != null && connection.equals(convoy.currentConnection());
    }

    private static void finish(ServerLevel world, PlayerQuestData ownerData,
                               GuildConvoyState.Convoy convoy) {
        GuildTownSharedState.get(world.getServer()).addChronicle(convoy.guildId(),
                "convoy.completed." + convoy.id());
        VillageGuildState.get(world.getServer()).addRenown(convoy.ownerId(), 20);
        int hubX = ownerData.getTradeRouteInt("home_x"), hubZ = ownerData.getTradeRouteInt("home_z");
        for (UUID member : convoy.credited()) {
            PlayerQuestData memberData = QuestState.get(world.getServer()).getPlayerData(member);
            GuildTownProgress.addPersonalChronicle(memberData,
                    new GuildTownProgress.VillageIdentity(convoy.ownerId(), hubX, hubZ),
                    "convoy.completed." + convoy.id(), true, world.getGameTime());
            GuildTownProgress.grantReward(memberData, "convoy_escort_title");
            ServerPlayer online = world.getServer().getPlayerList().getPlayer(member);
            if (online != null) online.sendSystemMessage(Component.translatable(
                    "message.village-quest.convoy.completed").withStyle(ChatFormatting.GOLD), false);
        }
        QuestState.get(world.getServer()).setDirty();
    }

    /** Replays unacknowledged completions; receipts and a save barrier prevent reward loss/duplication. */
    public static void reconcileTrust(net.minecraft.server.MinecraftServer server) {
        GuildConvoyState state = GuildConvoyState.get(server);
        java.util.List<UUID> acknowledged = new java.util.ArrayList<>();
        if (state.pendingTrust().isEmpty()) return;
        // Persist the terminal outbox before personal receipts, then persist receipts before acknowledging.
        server.overworld().getDataStorage().saveAndJoin();
        for (var completion : state.pendingTrust()) {
            boolean complete = true;
            for (var entry : completion.beneficiaries().entrySet()) {
                var data = QuestState.get(server).getPlayerData(entry.getKey()).socialReputation();
                if (!data.writable()) { complete = false; continue; }
                de.quest.reputation.SocialReputationService.recordBenefit(server,
                        new de.quest.reputation.SocialReputationRules.BenefitEvent(completion.convoyId(),
                                de.quest.reputation.SocialReputationRules.BenefitKind.CONVOY, entry.getKey(),
                                entry.getValue().stream().sorted().toList(), completion.resetToken()));
            }
            if (complete) acknowledged.add(completion.convoyId());
        }
        if (acknowledged.isEmpty()) return;
        // Only at rare terminal convoy completion/startup replay, never on ordinary route ticks.
        server.overworld().getDataStorage().saveAndJoin();
        acknowledged.forEach(state::acknowledgeTrust);
    }

    private static void cancel(ServerLevel world, GuildConvoyState.Convoy convoy) {
        if (convoy == null) return;
        GuildTownSharedState.get(world.getServer()).addChronicle(convoy.guildId(),
                "convoy.cancelled." + convoy.id());
        notifyOwner(world, convoy.ownerId(), Component.translatable("message.village-quest.convoy.cancelled"));
    }

    private static boolean ownerStillInGuild(ServerLevel world, GuildConvoyState.Convoy convoy) {
        return VillageGuildState.get(world.getServer()).guildFor(convoy.ownerId())
                .map(guild -> guild.id().equals(convoy.guildId())).orElse(false);
    }

    private static void creditNearbyEscorts(ServerLevel world, GuildConvoyState state,
                                            GuildConvoyState.Convoy convoy, PlayerQuestData ownerData,
                                            UUID connection) {
        int route = routeIndex(ownerData, connection);
        if (route < 0) return;
        int x = TradeRouteData.routeInt(ownerData, route, "x");
        int z = TradeRouteData.routeInt(ownerData, route, "z");
        VillageGuildState guilds = VillageGuildState.get(world.getServer());
        for (UUID memberId : convoy.participants()) {
            ServerPlayer member = world.getServer().getPlayerList().getPlayer(memberId);
            if (member == null || member.level() != world || !member.isAlive() || member.isSpectator()
                    || !guilds.guildFor(memberId).map(guild -> guild.id().equals(convoy.guildId()))
                    .orElse(false)) continue;
            long dx = (long) member.getBlockX() - x;
            long dz = (long) member.getBlockZ() - z;
            if (dx * dx + dz * dz > 24L * 24L) continue;
            var social = QuestState.get(world.getServer()).getPlayerData(memberId).socialReputation();
            if (social.activeCase() == null) state.recordTrustArrival(convoy.guildId(), memberId, connection,
                    new de.quest.village.VillageLifeState.VillageKey(world.dimension().identifier().toString(), x, z));
            if (state.credit(convoy.guildId(), memberId)) {
                member.sendSystemMessage(Component.translatable("message.village-quest.convoy.credited")
                        .withStyle(ChatFormatting.GOLD), false);
            }
        }
    }

    private static int routeIndex(PlayerQuestData data, UUID connection) {
        if (data == null || connection == null) return -1;
        int count = Math.min(TradeRouteService.MAX_ROUTES, data.getTradeRouteInt("route_count"));
        for (int route = 0; route < count; route++) {
            if (connection.toString().equals(data.getTradeRouteString(
                    TradeRouteData.routeKey(route, "connection_id")))) return route;
        }
        return -1;
    }

    private static void notifyOwner(ServerLevel world, UUID ownerId, Component message) {
        ServerPlayer owner = world.getServer().getPlayerList().getPlayer(ownerId);
        if (owner != null) owner.sendSystemMessage(message.copy().withStyle(ChatFormatting.AQUA), false);
    }

    private static boolean valid(ServerLevel world, ServerPlayer player) {
        return world != null && player != null && player.level() == world
                && world == world.getServer().overworld();
    }
}
