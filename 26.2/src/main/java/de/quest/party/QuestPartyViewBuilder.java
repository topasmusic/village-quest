package de.quest.party;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Read-only party presentation for the Questmaster UI. */
final class QuestPartyViewBuilder {
    private QuestPartyViewBuilder() {}

    static QuestPartyService.PartySnapshot build(MinecraftServer server, ServerPlayer viewer,
                                                  PartyRuntime party, Map<UUID, UUID> memberToParty) {
        List<QuestPartyService.PartyMemberView> members = new ArrayList<>();
        if (party != null) {
            for (UUID memberId : party.members()) {
                ServerPlayer member = server.getPlayerList().getPlayer(memberId);
                Component name = member == null
                        ? Component.literal(shortId(memberId)).withStyle(ChatFormatting.GRAY)
                        : member.getDisplayName().copy();
                Long disconnectUntil = party.disconnectDeadlines().get(memberId);
                if (disconnectUntil != null && disconnectUntil > System.currentTimeMillis()) {
                    name = name.copy().append(Component.translatable(
                            "screen.village-quest.questmaster.party.member.offline",
                            remainingMinutes(disconnectUntil - System.currentTimeMillis()))
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
                members.add(new QuestPartyService.PartyMemberView(memberId.toString(), name,
                        Objects.equals(party.leaderId(), memberId), Objects.equals(viewer.getUUID(), memberId)));
            }
        }

        List<QuestPartyService.PartyInviteCandidateView> candidates = new ArrayList<>();
        boolean inviteEnabled = party == null || Objects.equals(party.leaderId(), viewer.getUUID());
        int currentSize = party == null ? 1 : party.members().size();
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other.getUUID().equals(viewer.getUUID())) {
                continue;
            }
            boolean sameParty = party != null && party.members().contains(other.getUUID());
            Component status;
            boolean inviteable;
            if (sameParty) {
                status = status("in_party", ChatFormatting.GRAY);
                inviteable = false;
            } else if (memberToParty.containsKey(other.getUUID())) {
                status = status("busy", ChatFormatting.DARK_GRAY);
                inviteable = false;
            } else if (!inviteEnabled) {
                status = status("only_leader", ChatFormatting.DARK_GRAY);
                inviteable = false;
            } else if (currentSize >= QuestPartyService.MAX_PARTY_SIZE) {
                status = status("full", ChatFormatting.DARK_GRAY);
                inviteable = false;
            } else {
                status = status("invite", ChatFormatting.GREEN);
                inviteable = true;
            }
            candidates.add(new QuestPartyService.PartyInviteCandidateView(
                    other.getUUID().toString(), other.getDisplayName().copy(), status, inviteable));
        }
        candidates.sort(Comparator.comparing(candidate -> candidate.name().getString(),
                String.CASE_INSENSITIVE_ORDER));

        Component summary;
        if (party == null) {
            summary = Component.translatable("screen.village-quest.questmaster.party.summary.solo")
                    .withStyle(ChatFormatting.GRAY);
        } else if (Objects.equals(party.leaderId(), viewer.getUUID())) {
            summary = Component.translatable("screen.village-quest.questmaster.party.summary.leader",
                    party.members().size(), QuestPartyService.MAX_PARTY_SIZE).withStyle(ChatFormatting.GOLD);
        } else {
            summary = Component.translatable("screen.village-quest.questmaster.party.summary.member",
                    party.members().size(), QuestPartyService.MAX_PARTY_SIZE).withStyle(ChatFormatting.GRAY);
        }
        return new QuestPartyService.PartySnapshot(party != null,
                party != null && Objects.equals(party.leaderId(), viewer.getUUID()), summary,
                List.copyOf(members), List.copyOf(candidates));
    }

    private static Component status(String suffix, ChatFormatting color) {
        return Component.translatable("screen.village-quest.questmaster.party.candidate." + suffix)
                .withStyle(color);
    }

    private static String remainingMinutes(long millis) {
        return Long.toString(Math.max(1L, (long) Math.ceil(millis / 60000.0d)));
    }

    private static String shortId(UUID playerId) {
        String value = playerId == null ? "player" : playerId.toString().replace("-", "");
        return value.length() <= 8 ? value : value.substring(0, 8);
    }
}
