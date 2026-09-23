package de.quest.party;

import de.quest.pilgrim.PilgrimService;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.questmaster.QuestMasterUiService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Online-member resolution, party chat, and open-UI refreshes. */
final class QuestPartyMessaging {
    private QuestPartyMessaging() {}

    static String formatRemainingMinutes(long millis) {
        long minutes = Math.max(1L, (long) Math.ceil(millis / 60000.0d));
        return Long.toString(minutes);
    }

    static List<ServerPlayer> orderedOnlineMembers(ServerLevel world, UUID playerId) {
        List<ServerPlayer> members = new ArrayList<>();
        if (world == null) return members;
        PartyRuntime party = QuestPartyService.partyFor(playerId);
        if (party == null) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) members.add(player);
            return members;
        }
        ServerPlayer owner = world.getServer().getPlayerList().getPlayer(playerId);
        if (owner != null) members.add(owner);
        for (UUID memberId : party.members()) {
            if (memberId.equals(playerId)) continue;
            ServerPlayer member = world.getServer().getPlayerList().getPlayer(memberId);
            if (member != null) members.add(member);
        }
        return members;
    }

    static List<ServerPlayer> onlineMembers(ServerLevel world, List<UUID> memberIds) {
        List<ServerPlayer> members = new ArrayList<>();
        if (world == null || memberIds == null) return members;
        for (UUID memberId : memberIds) {
            ServerPlayer member = world.getServer().getPlayerList().getPlayer(memberId);
            if (member != null) members.add(member);
        }
        return members;
    }

    static MutableComponent buildInviteMessage(ServerPlayer inviter) {
        MutableComponent accept = Component.translatable("message.village-quest.party.invite.accept")
                .withStyle(style -> style.withColor(0x57A550)
                        .withClickEvent(new ClickEvent.RunCommand("/vq party accept")));
        MutableComponent decline = Component.translatable("message.village-quest.party.invite.decline")
                .withStyle(style -> style.withColor(0xA55252)
                        .withClickEvent(new ClickEvent.RunCommand("/vq party decline")));
        return Component.translatable("message.village-quest.party.invite.receive", inviter.getDisplayName())
                .withStyle(ChatFormatting.GRAY).append(Component.literal(" ")).append(accept)
                .append(Component.literal(" ")).append(decline);
    }

    static Component nameOf(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return Component.translatable("message.village-quest.party.unknown");
        }
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
        return player == null
                ? Component.literal(shortId(playerId)).withStyle(ChatFormatting.GRAY)
                : player.getDisplayName().copy();
    }

    static void broadcast(ServerLevel world, PartyRuntime party, Component message) {
        if (world == null || party == null || message == null) return;
        for (UUID memberId : party.members()) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) player.sendSystemMessage(message, false);
        }
    }

    static void refreshPartyUi(ServerLevel world, PartyRuntime party) {
        if (world == null || party == null) return;
        for (UUID memberId : party.members()) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) refreshPlayer(world, player);
        }
    }

    static void refreshPlayer(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) return;
        QuestBookHelper.refreshQuestBook(world, player);
        QuestTrackerService.refresh(world, player);
        QuestMasterUiService.refreshIfOpen(world, player);
        PilgrimService.refreshIfTrading(world, player);
    }

    static void refreshOpenUi(ServerPlayer player) {
        if (player != null) QuestMasterUiService.refreshIfOpen((ServerLevel) player.level(), player);
    }

    private static String shortId(UUID playerId) {
        String value = playerId == null ? "player" : playerId.toString().replace("-", "");
        return value.length() <= 8 ? value : value.substring(0, 8);
    }
}
