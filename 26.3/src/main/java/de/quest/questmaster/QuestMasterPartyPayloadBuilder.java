package de.quest.questmaster;

import de.quest.network.Payloads;
import de.quest.party.QuestPartyService;
import de.quest.party.QuestShareProfiles;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.weekly.WeeklyQuestService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Party-specific questmaster payload and status presentation. */
final class QuestMasterPartyPayloadBuilder {
    private QuestMasterPartyPayloadBuilder() {}

    static Payloads.QuestMasterPartyData build(ServerPlayer player) {
        if (player == null || !enabled((ServerLevel) player.level())) {
            return new Payloads.QuestMasterPartyData(false, false, Component.empty(), List.of(), List.of());
        }
        QuestPartyService.PartySnapshot snapshot = QuestPartyService.snapshot(player);
        List<Payloads.QuestMasterPartyMemberData> members = new ArrayList<>(snapshot.members().size());
        for (QuestPartyService.PartyMemberView member : snapshot.members()) {
            members.add(new Payloads.QuestMasterPartyMemberData(
                    member.playerId(), member.name(), member.leader(), member.self()));
        }
        List<Payloads.QuestMasterPartyCandidateData> candidates = new ArrayList<>(snapshot.candidates().size());
        for (QuestPartyService.PartyInviteCandidateView candidate : snapshot.candidates()) {
            candidates.add(new Payloads.QuestMasterPartyCandidateData(
                    candidate.playerId(), candidate.name(), candidate.status(), candidate.inviteable()));
        }
        return new Payloads.QuestMasterPartyData(snapshot.hasParty(), snapshot.leader(), snapshot.summary(),
                List.copyOf(members), List.copyOf(candidates));
    }

    static Component dailyStatus(ServerLevel world, UUID playerId, DailyQuestService.DailyQuestType type) {
        if (!enabled(world)) {
            return Component.empty();
        }
        if (type == null || !QuestShareProfiles.isDailyShareable(type)) {
            return unavailable();
        }
        if (!QuestPartyService.hasParty(playerId)) {
            return solo();
        }
        int sharedMembers = QuestPartyService.dailySharedMemberCount(world, playerId, type);
        if (type == QuestPartyService.resolveSharedDailyChoice(world, playerId, null) && sharedMembers > 1) {
            return shared(sharedMembers, playerId);
        }
        return party(playerId);
    }

    static Component weeklyStatus(ServerLevel world, UUID playerId, WeeklyQuestService.WeeklyQuestType type) {
        if (!enabled(world)) {
            return Component.empty();
        }
        if (type == null || !QuestShareProfiles.isWeeklyShareable(type)) {
            return unavailable();
        }
        if (!QuestPartyService.hasParty(playerId)) {
            return solo();
        }
        int sharedMembers = QuestPartyService.weeklySharedMemberCount(world, playerId, type);
        if (type == QuestPartyService.resolveSharedWeeklyChoice(world, playerId, null) && sharedMembers > 1) {
            return shared(sharedMembers, playerId);
        }
        return party(playerId);
    }

    static Component storyStatus(ServerLevel world, UUID playerId, StoryArcType arcType, int chapterIndex) {
        if (!enabled(world)) {
            return Component.empty();
        }
        if (arcType == null || !QuestShareProfiles.isStoryShareable(arcType)) {
            return unavailable();
        }
        if (!QuestPartyService.hasParty(playerId)) {
            return solo();
        }
        int sharedMembers = QuestPartyService.storySharedMemberCount(world, playerId, arcType, chapterIndex);
        return sharedMembers > 1 ? shared(sharedMembers, playerId) : party(playerId);
    }

    static boolean enabled(ServerLevel world) {
        return world != null && QuestPartyService.isEnabled(world.getServer());
    }

    private static Component unavailable() {
        return Component.translatable("screen.village-quest.questmaster.party.status.unavailable")
                .withStyle(ChatFormatting.DARK_GRAY);
    }

    private static Component solo() {
        return Component.translatable("screen.village-quest.questmaster.party.status.solo")
                .withStyle(ChatFormatting.GRAY);
    }

    private static Component shared(int sharedMembers, UUID playerId) {
        return Component.translatable("screen.village-quest.questmaster.party.status.shared",
                sharedMembers, QuestPartyService.partySize(playerId)).withStyle(ChatFormatting.GREEN);
    }

    private static Component party(UUID playerId) {
        return Component.translatable("screen.village-quest.questmaster.party.status.party",
                QuestPartyService.partySize(playerId), QuestPartyService.MAX_PARTY_SIZE)
                .withStyle(ChatFormatting.GRAY);
    }
}
