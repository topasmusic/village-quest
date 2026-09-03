package de.quest.party;

import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** NBT codec for party membership, shared sessions, pending offers, and reconnect grace. */
final class QuestPartyPersistence {
    private QuestPartyPersistence() {}

    static void read(CompoundTag root,
                     Map<UUID, PartyRuntime> partiesById,
                     Map<UUID, UUID> partyByMember,
                     Map<UUID, PartyInvite> invitesByTarget) {
        if (root == null || root.isEmpty()) return;
        ListTag parties = root.getListOrEmpty("parties");
        for (int i = 0; i < parties.size(); i++) {
            CompoundTag partyNbt = parties.getCompoundOrEmpty(i);
            UUID partyId = parseUuid(partyNbt.getStringOr("id", ""));
            UUID leaderId = parseUuid(partyNbt.getStringOr("leader", ""));
            if (partyId == null || leaderId == null) continue;

            PartyRuntime party = new PartyRuntime(partyId, leaderId);
            ListTag members = partyNbt.getListOrEmpty("members");
            for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
                UUID memberId = parseUuid(members.getCompoundOrEmpty(memberIndex).getStringOr("id", ""));
                if (memberId == null || party.members().contains(memberId)) continue;
                party.members().add(memberId);
                partyByMember.put(memberId, partyId);
            }
            if (party.members().isEmpty()) continue;
            if (!party.members().contains(leaderId)) party.setLeaderId(party.members().iterator().next());

            readSharedSession(partyNbt.getCompoundOrEmpty("daily"), party.daily());
            readSharedSession(partyNbt.getCompoundOrEmpty("weekly"), party.weekly());
            readSharedSession(partyNbt.getCompoundOrEmpty("story"), party.story());
            readSharedSession(partyNbt.getCompoundOrEmpty("pilgrim"), party.pilgrim());
            readOfferMap(partyNbt.getListOrEmpty("dailyOffers"), party.dailyOffers());
            readOfferMap(partyNbt.getListOrEmpty("weeklyOffers"), party.weeklyOffers());
            readOfferMap(partyNbt.getListOrEmpty("storyOffers"), party.storyOffers());
            readOfferMap(partyNbt.getListOrEmpty("pilgrimOffers"), party.pilgrimOffers());
            readDisconnectMap(partyNbt.getListOrEmpty("disconnects"), party.disconnectDeadlines());
            partiesById.put(partyId, party);
        }

        ListTag invites = root.getListOrEmpty("invites");
        for (int i = 0; i < invites.size(); i++) {
            CompoundTag inviteNbt = invites.getCompoundOrEmpty(i);
            UUID targetId = parseUuid(inviteNbt.getStringOr("target", ""));
            UUID partyId = parseUuid(inviteNbt.getStringOr("party", ""));
            UUID inviterId = parseUuid(inviteNbt.getStringOr("inviter", ""));
            long expiresAt = inviteNbt.getLongOr("expiresAt", 0L);
            if (targetId != null && partyId != null && inviterId != null && expiresAt > 0L) {
                invitesByTarget.put(targetId, new PartyInvite(partyId, inviterId, expiresAt));
            }
        }
    }

    static CompoundTag write(Map<UUID, PartyRuntime> partiesById,
                             Map<UUID, PartyInvite> invitesByTarget) {
        CompoundTag root = new CompoundTag();
        ListTag parties = new ListTag();
        for (PartyRuntime party : partiesById.values()) {
            CompoundTag partyNbt = new CompoundTag();
            partyNbt.putString("id", party.id().toString());
            partyNbt.putString("leader", party.leaderId().toString());
            ListTag members = new ListTag();
            for (UUID memberId : party.members()) {
                CompoundTag memberNbt = new CompoundTag();
                memberNbt.putString("id", memberId.toString());
                members.add(memberNbt);
            }
            partyNbt.put("members", members);
            partyNbt.put("daily", writeSharedSession(party.daily()));
            partyNbt.put("weekly", writeSharedSession(party.weekly()));
            partyNbt.put("story", writeSharedSession(party.story()));
            partyNbt.put("pilgrim", writeSharedSession(party.pilgrim()));
            partyNbt.put("dailyOffers", writeOfferMap(party.dailyOffers()));
            partyNbt.put("weeklyOffers", writeOfferMap(party.weeklyOffers()));
            partyNbt.put("storyOffers", writeOfferMap(party.storyOffers()));
            partyNbt.put("pilgrimOffers", writeOfferMap(party.pilgrimOffers()));
            partyNbt.put("disconnects", writeDisconnectMap(party.disconnectDeadlines()));
            parties.add(partyNbt);
        }
        root.put("parties", parties);

        ListTag invites = new ListTag();
        for (var entry : invitesByTarget.entrySet()) {
            PartyInvite invite = entry.getValue();
            CompoundTag inviteNbt = new CompoundTag();
            inviteNbt.putString("target", entry.getKey().toString());
            inviteNbt.putString("party", invite.partyId().toString());
            inviteNbt.putString("inviter", invite.inviterId().toString());
            inviteNbt.putLong("expiresAt", invite.expiresAtMillis());
            invites.add(inviteNbt);
        }
        root.put("invites", invites);
        return root;
    }

    private static CompoundTag writeSharedSession(SharedQuestRuntime session) {
        CompoundTag sessionNbt = new CompoundTag();
        if (session == null || session.questId() == null) return sessionNbt;
        sessionNbt.putString("questId", session.questId());
        sessionNbt.putLong("revision", session.revision());
        ListTag ints = new ListTag();
        for (var entry : session.intState().entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putString("key", entry.getKey());
            item.putInt("value", entry.getValue());
            ints.add(item);
        }
        sessionNbt.put("ints", ints);
        ListTag flags = new ListTag();
        for (String flag : session.flags()) {
            CompoundTag item = new CompoundTag();
            item.putString("key", flag);
            flags.add(item);
        }
        sessionNbt.put("flags", flags);
        ListTag synced = new ListTag();
        for (UUID memberId : session.syncedMembers()) {
            CompoundTag item = new CompoundTag();
            item.putString("id", memberId.toString());
            synced.add(item);
        }
        sessionNbt.put("synced", synced);
        return sessionNbt;
    }

    private static void readSharedSession(CompoundTag sessionNbt, SharedQuestRuntime session) {
        if (session == null || sessionNbt == null || sessionNbt.isEmpty()) return;
        String questId = sessionNbt.getStringOr("questId", "");
        long revision = sessionNbt.getLongOr("revision", Long.MIN_VALUE);
        if (questId.isEmpty() || revision == Long.MIN_VALUE) return;
        session.bind(questId, revision);
        for (CompoundTag item : compounds(sessionNbt.getListOrEmpty("ints"))) {
            session.setInt(item.getStringOr("key", ""), item.getIntOr("value", 0));
        }
        for (CompoundTag item : compounds(sessionNbt.getListOrEmpty("flags"))) {
            String key = item.getStringOr("key", "");
            if (!key.isEmpty()) session.setFlag(key, true);
        }
        for (CompoundTag item : compounds(sessionNbt.getListOrEmpty("synced"))) {
            UUID memberId = parseUuid(item.getStringOr("id", ""));
            if (memberId != null) session.markSynced(memberId);
        }
    }

    private static ListTag writeOfferMap(Map<UUID, QuestJoinOffer> offers) {
        ListTag list = new ListTag();
        for (var entry : offers.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putString("target", entry.getKey().toString());
            item.putString("questId", entry.getValue().questId());
            item.putLong("revision", entry.getValue().revision());
            if (entry.getValue().sourceId() != null) item.putString("source", entry.getValue().sourceId().toString());
            list.add(item);
        }
        return list;
    }

    private static void readOfferMap(ListTag list, Map<UUID, QuestJoinOffer> offers) {
        for (CompoundTag item : compounds(list)) {
            UUID targetId = parseUuid(item.getStringOr("target", ""));
            String questId = item.getStringOr("questId", "");
            long revision = item.getLongOr("revision", Long.MIN_VALUE);
            UUID sourceId = parseUuid(item.getStringOr("source", ""));
            if (targetId != null && !questId.isEmpty() && revision != Long.MIN_VALUE) {
                offers.put(targetId, new QuestJoinOffer(questId, revision, sourceId));
            }
        }
    }

    private static ListTag writeDisconnectMap(Map<UUID, Long> disconnects) {
        ListTag list = new ListTag();
        for (var entry : disconnects.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putString("id", entry.getKey().toString());
            item.putLong("until", entry.getValue());
            list.add(item);
        }
        return list;
    }

    private static void readDisconnectMap(ListTag list, Map<UUID, Long> disconnects) {
        for (CompoundTag item : compounds(list)) {
            UUID memberId = parseUuid(item.getStringOr("id", ""));
            long until = item.getLongOr("until", 0L);
            if (memberId != null && until > 0L) disconnects.put(memberId, until);
        }
    }

    private static Iterable<CompoundTag> compounds(ListTag list) {
        java.util.List<CompoundTag> result = new java.util.ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) result.add(list.getCompoundOrEmpty(i));
        return result;
    }

    private static UUID parseUuid(String value) {
        try { return value == null || value.isEmpty() ? null : UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
