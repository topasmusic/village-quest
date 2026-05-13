package de.quest.party;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.pilgrim.PilgrimContractService;
import de.quest.pilgrim.PilgrimContractType;
import de.quest.pilgrim.PilgrimService;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestGenerator;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryChapterDefinition;
import de.quest.quest.story.StoryQuestService;
import de.quest.quest.weekly.WeeklyQuestDefinition;
import de.quest.quest.weekly.WeeklyQuestGenerator;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.util.Texts;
import de.quest.util.TimeUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class QuestPartyService {
    public static final int MAX_PARTY_SIZE = 4;
    private static final long INVITE_TIMEOUT_MILLIS = 60_000L;
    private static final long DISCONNECT_GRACE_MILLIS = 10L * 60_000L;

    private static final ConcurrentMap<UUID, PartyRuntime> PARTIES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, UUID> MEMBER_TO_PARTY = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, PartyInvite> INVITES = new ConcurrentHashMap<>();
    private static volatile boolean loaded;

    private QuestPartyService() {}

    public static boolean isEnabled(MinecraftServer server) {
        return server != null && server.isDedicated();
    }

    public static void loadPersistentState(MinecraftServer server) {
        resetRuntimeState();
        if (server == null || !isEnabled(server)) {
            loaded = true;
            return;
        }

        NbtCompound root = QuestState.get(server).getQuestPartyState();
        if (root == null || root.isEmpty()) {
            loaded = true;
            return;
        }

        NbtList parties = root.getListOrEmpty("parties");
        for (int i = 0; i < parties.size(); i++) {
            NbtCompound partyNbt = parties.getCompoundOrEmpty(i);
            UUID partyId = parseUuid(partyNbt.getString("id", ""));
            UUID leaderId = parseUuid(partyNbt.getString("leader", ""));
            if (partyId == null || leaderId == null) {
                continue;
            }

            PartyRuntime party = new PartyRuntime(partyId, leaderId);
            NbtList members = partyNbt.getListOrEmpty("members");
            for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
                UUID memberId = parseUuid(members.getCompoundOrEmpty(memberIndex).getString("id", ""));
                if (memberId == null || party.members().contains(memberId)) {
                    continue;
                }
                party.members().add(memberId);
                MEMBER_TO_PARTY.put(memberId, partyId);
            }
            if (party.members().isEmpty()) {
                continue;
            }
            if (!party.members().contains(leaderId)) {
                party.setLeaderId(party.members().iterator().next());
            }

            readSharedSession(partyNbt.getCompoundOrEmpty("daily"), party.daily());
            readSharedSession(partyNbt.getCompoundOrEmpty("weekly"), party.weekly());
            readSharedSession(partyNbt.getCompoundOrEmpty("story"), party.story());
            readSharedSession(partyNbt.getCompoundOrEmpty("pilgrim"), party.pilgrim());
            readOfferMap(partyNbt.getListOrEmpty("dailyOffers"), party.dailyOffers());
            readOfferMap(partyNbt.getListOrEmpty("weeklyOffers"), party.weeklyOffers());
            readOfferMap(partyNbt.getListOrEmpty("storyOffers"), party.storyOffers());
            readOfferMap(partyNbt.getListOrEmpty("pilgrimOffers"), party.pilgrimOffers());
            readDisconnectMap(partyNbt.getListOrEmpty("disconnects"), party.disconnectDeadlines());
            PARTIES.put(partyId, party);
        }

        NbtList invites = root.getListOrEmpty("invites");
        for (int i = 0; i < invites.size(); i++) {
            NbtCompound inviteNbt = invites.getCompoundOrEmpty(i);
            UUID targetId = parseUuid(inviteNbt.getString("target", ""));
            UUID partyId = parseUuid(inviteNbt.getString("party", ""));
            UUID inviterId = parseUuid(inviteNbt.getString("inviter", ""));
            long expiresAt = inviteNbt.getLong("expiresAt", 0L);
            if (targetId == null || partyId == null || inviterId == null || expiresAt <= 0L) {
                continue;
            }
            INVITES.put(targetId, new PartyInvite(partyId, inviterId, expiresAt));
        }

        loaded = true;
        cleanupInvalidState(server);
        storePersistentState(server);
    }

    public static void onServerTick(MinecraftServer server) {
        ensureLoaded(server);
        if (server == null) {
            return;
        }
        long now = System.currentTimeMillis();
        expireInvites(server, now);
        expireDisconnectedMembers(server.getOverworld(), now);
        cleanupStaleOffers(server.getOverworld());
    }

    public static void resetRuntimeState() {
        PARTIES.clear();
        MEMBER_TO_PARTY.clear();
        INVITES.clear();
        loaded = false;
    }

    public static void persistRuntimeState(MinecraftServer server) {
        ensureLoaded(server);
        if (server != null && isEnabled(server)) {
            storePersistentState(server);
        }
        resetRuntimeState();
    }

    public static void handleJoin(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        MinecraftServer server = ((ServerWorld) player.getEntityWorld()).getServer();
        ensureLoaded(server);
        if (!isEnabled(server)) {
            return;
        }

        PartyRuntime party = partyFor(player.getUuid());
        if (party == null) {
            return;
        }

        Long deadline = party.disconnectDeadlines().remove(player.getUuid());
        if (deadline != null) {
            broadcast((ServerWorld) player.getEntityWorld(), party,
                    Text.translatable("message.village-quest.party.member_returned", player.getDisplayName()).formatted(Formatting.GRAY));
        }
        boolean hadPendingOffer = party.dailyOffers().containsKey(player.getUuid())
                || party.weeklyOffers().containsKey(player.getUuid())
                || party.storyOffers().containsKey(player.getUuid())
                || party.pilgrimOffers().containsKey(player.getUuid());
        queueActiveQuestOffersForJoiner((ServerWorld) player.getEntityWorld(), party, player);
        if (hadPendingOffer) {
            resendPendingQuestOffers((ServerWorld) player.getEntityWorld(), party, player);
        }
        storePersistentState(server);
        refreshPlayer((ServerWorld) player.getEntityWorld(), player);
        refreshPartyUi((ServerWorld) player.getEntityWorld(), party);
    }

    public static void handleDisconnect(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        MinecraftServer server = world.getServer();
        ensureLoaded(server);
        removePendingInviteFor(player.getUuid());

        PartyRuntime party = partyFor(player.getUuid());
        if (party == null || !isEnabled(server)) {
            return;
        }
        if (party.disconnectDeadlines().containsKey(player.getUuid())) {
            return;
        }

        long removeAt = System.currentTimeMillis() + DISCONNECT_GRACE_MILLIS;
        party.disconnectDeadlines().put(player.getUuid(), removeAt);
        broadcast(world, party, Text.translatable(
                "message.village-quest.party.member_disconnected",
                nameOf(world, player.getUuid()),
                formatRemainingMinutes(removeAt - System.currentTimeMillis())
        ).formatted(Formatting.GRAY));
        storePersistentState(server);
        refreshPartyUi(world, party);
    }

    public static boolean invite(ServerWorld world, ServerPlayerEntity inviter, ServerPlayerEntity target) {
        if (world == null || inviter == null || target == null) {
            return false;
        }
        ensureLoaded(world.getServer());
        if (!ensureEnabled(world.getServer(), inviter)) {
            return false;
        }
        if (inviter.getUuid().equals(target.getUuid())) {
            inviter.sendMessage(Text.translatable("message.village-quest.party.invite.self").formatted(Formatting.RED), false);
            return false;
        }
        if (hasParty(target.getUuid())) {
            inviter.sendMessage(Text.translatable("message.village-quest.party.invite.target_busy", target.getDisplayName()).formatted(Formatting.RED), false);
            return false;
        }

        PartyRuntime party = getOrCreateParty(world, inviter);
        if (party == null) {
            inviter.sendMessage(Text.translatable("message.village-quest.party.invite.failed").formatted(Formatting.RED), false);
            return false;
        }
        if (!party.leaderId().equals(inviter.getUuid())) {
            inviter.sendMessage(Text.translatable("message.village-quest.party.invite.only_leader").formatted(Formatting.RED), false);
            return false;
        }
        if (party.members().contains(target.getUuid())) {
            inviter.sendMessage(Text.translatable("message.village-quest.party.invite.same_party", target.getDisplayName()).formatted(Formatting.RED), false);
            return false;
        }
        if (party.members().size() >= MAX_PARTY_SIZE) {
            inviter.sendMessage(Text.translatable("message.village-quest.party.invite.full").formatted(Formatting.RED), false);
            return false;
        }

        INVITES.put(target.getUuid(), new PartyInvite(party.id(), inviter.getUuid(), System.currentTimeMillis() + INVITE_TIMEOUT_MILLIS));
        inviter.sendMessage(Text.translatable("message.village-quest.party.invite.sent", target.getDisplayName()).formatted(Formatting.GREEN), false);
        target.sendMessage(buildInviteMessage(inviter), false);
        storePersistentState(world.getServer());
        refreshPartyUi(world, party);
        refreshOpenUi(target);
        return true;
    }

    public static boolean acceptInvite(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }
        ensureLoaded(world.getServer());
        if (!ensureEnabled(world.getServer(), player)) {
            return false;
        }
        PartyInvite invite = INVITES.remove(player.getUuid());
        if (invite == null || invite.expiresAtMillis() <= System.currentTimeMillis()) {
            player.sendMessage(Text.translatable("message.village-quest.party.accept.none").formatted(Formatting.RED), false);
            return false;
        }
        if (hasParty(player.getUuid())) {
            player.sendMessage(Text.translatable("message.village-quest.party.accept.already_in_party").formatted(Formatting.RED), false);
            return false;
        }

        PartyRuntime party = PARTIES.get(invite.partyId());
        if (party == null || !party.members().contains(invite.inviterId())) {
            player.sendMessage(Text.translatable("message.village-quest.party.accept.invalid").formatted(Formatting.RED), false);
            return false;
        }
        if (party.members().size() >= MAX_PARTY_SIZE) {
            player.sendMessage(Text.translatable("message.village-quest.party.invite.full").formatted(Formatting.RED), false);
            return false;
        }

        party.members().add(player.getUuid());
        MEMBER_TO_PARTY.put(player.getUuid(), party.id());
        party.disconnectDeadlines().remove(player.getUuid());
        broadcast(world, party, Text.translatable("message.village-quest.party.member_joined", player.getDisplayName()).formatted(Formatting.GREEN));
        queueActiveQuestOffersForJoiner(world, party, player);
        storePersistentState(world.getServer());
        refreshPartyUi(world, party);
        return true;
    }

    public static boolean declineInvite(MinecraftServer server, ServerPlayerEntity player) {
        if (server == null || player == null) {
            return false;
        }
        ensureLoaded(server);
        if (!ensureEnabled(server, player)) {
            return false;
        }
        PartyInvite invite = INVITES.remove(player.getUuid());
        if (invite == null) {
            player.sendMessage(Text.translatable("message.village-quest.party.decline.none").formatted(Formatting.RED), false);
            return false;
        }
        ServerPlayerEntity inviter = server.getPlayerManager().getPlayer(invite.inviterId());
        if (inviter != null) {
            inviter.sendMessage(Text.translatable("message.village-quest.party.decline.notify", player.getDisplayName()).formatted(Formatting.GRAY), false);
            refreshOpenUi(inviter);
        }
        player.sendMessage(Text.translatable("message.village-quest.party.decline.self").formatted(Formatting.GRAY), false);
        storePersistentState(server);
        refreshOpenUi(player);
        return true;
    }

    public static boolean leave(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }
        ensureLoaded(world.getServer());
        if (!ensureEnabled(world.getServer(), player)) {
            return false;
        }
        return leaveInternal(world, player.getUuid(), true, false);
    }

    public static boolean disband(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }
        ensureLoaded(world.getServer());
        if (!ensureEnabled(world.getServer(), player)) {
            return false;
        }
        PartyRuntime party = partyFor(player.getUuid());
        if (party == null) {
            player.sendMessage(Text.translatable("message.village-quest.party.none").formatted(Formatting.RED), false);
            return false;
        }
        if (!Objects.equals(party.leaderId(), player.getUuid())) {
            player.sendMessage(Text.translatable("message.village-quest.party.disband.only_leader").formatted(Formatting.RED), false);
            return false;
        }

        List<UUID> members = new ArrayList<>(party.members());
        for (UUID memberId : members) {
            copySharedProgressToPlayer(world, party, memberId);
            MEMBER_TO_PARTY.remove(memberId);
            ServerPlayerEntity member = world.getServer().getPlayerManager().getPlayer(memberId);
            if (member != null) {
                member.sendMessage(Text.translatable("message.village-quest.party.disbanded").formatted(Formatting.GRAY), false);
                refreshPlayer(world, member);
            }
        }
        removeInvitesForParty(party.id());
        PARTIES.remove(party.id());
        storePersistentState(world.getServer());
        return true;
    }

    public static boolean hasParty(UUID playerId) {
        return playerId != null && MEMBER_TO_PARTY.containsKey(playerId);
    }

    public static boolean isLeader(UUID playerId) {
        PartyRuntime party = partyFor(playerId);
        return party != null && Objects.equals(party.leaderId(), playerId);
    }

    public static PartySnapshot snapshot(ServerPlayerEntity viewer) {
        if (viewer == null) {
            return PartySnapshot.empty();
        }
        MinecraftServer server = ((ServerWorld) viewer.getEntityWorld()).getServer();
        ensureLoaded(server);
        if (!isEnabled(server)) {
            return PartySnapshot.empty();
        }
        cleanupInvite(viewer.getUuid());
        PartyRuntime party = partyFor(viewer.getUuid());
        List<PartyMemberView> members = new ArrayList<>();
        if (party != null) {
            for (UUID memberId : party.members()) {
                ServerPlayerEntity member = server.getPlayerManager().getPlayer(memberId);
                Text name = member == null ? Text.literal(shortId(memberId)).formatted(Formatting.GRAY) : member.getDisplayName().copy();
                Long disconnectUntil = party.disconnectDeadlines().get(memberId);
                if (disconnectUntil != null && disconnectUntil > System.currentTimeMillis()) {
                    name = name.copy().append(Text.translatable(
                            "screen.village-quest.questmaster.party.member.offline",
                            formatRemainingMinutes(disconnectUntil - System.currentTimeMillis())
                    ).formatted(Formatting.DARK_GRAY));
                }
                members.add(new PartyMemberView(
                        memberId.toString(),
                        name,
                        Objects.equals(party.leaderId(), memberId),
                        Objects.equals(viewer.getUuid(), memberId)
                ));
            }
        }

        List<PartyInviteCandidateView> candidates = new ArrayList<>();
        boolean inviteEnabled = party == null || Objects.equals(party.leaderId(), viewer.getUuid());
        int currentSize = party == null ? 1 : party.members().size();
        for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
            if (other.getUuid().equals(viewer.getUuid())) {
                continue;
            }
            boolean sameParty = party != null && party.members().contains(other.getUuid());
            Text status;
            boolean inviteable;
            if (sameParty) {
                status = Text.translatable("screen.village-quest.questmaster.party.candidate.in_party").formatted(Formatting.GRAY);
                inviteable = false;
            } else if (hasParty(other.getUuid())) {
                status = Text.translatable("screen.village-quest.questmaster.party.candidate.busy").formatted(Formatting.DARK_GRAY);
                inviteable = false;
            } else if (!inviteEnabled) {
                status = Text.translatable("screen.village-quest.questmaster.party.candidate.only_leader").formatted(Formatting.DARK_GRAY);
                inviteable = false;
            } else if (currentSize >= MAX_PARTY_SIZE) {
                status = Text.translatable("screen.village-quest.questmaster.party.candidate.full").formatted(Formatting.DARK_GRAY);
                inviteable = false;
            } else {
                status = Text.translatable("screen.village-quest.questmaster.party.candidate.invite").formatted(Formatting.GREEN);
                inviteable = true;
            }
            candidates.add(new PartyInviteCandidateView(other.getUuid().toString(), other.getDisplayName().copy(), status, inviteable));
        }
        candidates.sort(Comparator.comparing(candidate -> candidate.name().getString(), String.CASE_INSENSITIVE_ORDER));

        Text summary;
        if (party == null) {
            summary = Text.translatable("screen.village-quest.questmaster.party.summary.solo").formatted(Formatting.GRAY);
        } else if (Objects.equals(party.leaderId(), viewer.getUuid())) {
            summary = Text.translatable("screen.village-quest.questmaster.party.summary.leader", party.members().size(), MAX_PARTY_SIZE).formatted(Formatting.GOLD);
        } else {
            summary = Text.translatable("screen.village-quest.questmaster.party.summary.member", party.members().size(), MAX_PARTY_SIZE).formatted(Formatting.GRAY);
        }

        return new PartySnapshot(
                party != null,
                party != null && Objects.equals(party.leaderId(), viewer.getUuid()),
                summary,
                List.copyOf(members),
                List.copyOf(candidates)
        );
    }

    public static DailyQuestService.DailyQuestType resolveSharedDailyChoice(ServerWorld world,
                                                                            UUID playerId,
                                                                            DailyQuestService.DailyQuestType fallback) {
        ensureLoaded(world == null ? null : world.getServer());
        PartyRuntime party = partyFor(playerId);
        if (world == null || party == null) {
            return fallback;
        }
        DailyQuestService.DailyQuestType sessionType = activeDailyType(party);
        return sessionType == null ? fallback : sessionType;
    }

    public static WeeklyQuestService.WeeklyQuestType resolveSharedWeeklyChoice(ServerWorld world,
                                                                               UUID playerId,
                                                                               WeeklyQuestService.WeeklyQuestType fallback) {
        ensureLoaded(world == null ? null : world.getServer());
        PartyRuntime party = partyFor(playerId);
        if (world == null || party == null) {
            return fallback;
        }
        WeeklyQuestService.WeeklyQuestType sessionType = activeWeeklyType(party);
        return sessionType == null ? fallback : sessionType;
    }

    public static void onDailyQuestAccepted(ServerWorld world,
                                            ServerPlayerEntity player,
                                            DailyQuestService.DailyQuestType type,
                                            DailyQuestDefinition definition) {
        ensureLoaded(world == null ? null : world.getServer());
        if (world == null || player == null || type == null || !QuestShareProfiles.isDailyShareable(type)) {
            return;
        }
        PartyRuntime party = partyFor(player.getUuid());
        if (party == null) {
            return;
        }

        SharedQuestRuntime session = ensureDailySession(party, type);
        mergeDailyProgressIntoSession(world, player.getUuid(), type, session);
        syncPartyMembersIntoDaily(world, party, type, definition, player.getUuid());
        clearDailyOffer(party, player.getUuid());
        storePersistentState(world.getServer());
        refreshPartyUi(world, party);
    }

    public static void onWeeklyQuestAccepted(ServerWorld world,
                                             ServerPlayerEntity player,
                                             WeeklyQuestService.WeeklyQuestType type,
                                             WeeklyQuestDefinition definition) {
        ensureLoaded(world == null ? null : world.getServer());
        if (world == null || player == null || type == null || !QuestShareProfiles.isWeeklyShareable(type)) {
            return;
        }
        PartyRuntime party = partyFor(player.getUuid());
        if (party == null) {
            return;
        }

        SharedQuestRuntime session = ensureWeeklySession(party, type);
        mergeWeeklyProgressIntoSession(world, player.getUuid(), type, session);
        syncPartyMembersIntoWeekly(world, party, type, definition, player.getUuid());
        clearWeeklyOffer(party, player.getUuid());
        storePersistentState(world.getServer());
        refreshPartyUi(world, party);
    }

    public static boolean usesSharedDailyInt(ServerWorld world,
                                             UUID playerId,
                                             DailyQuestService.DailyQuestType type,
                                             String key) {
        return sharedDailySession(world, playerId, type) != null && QuestShareProfiles.sharesDailyInt(type, key);
    }

    public static boolean usesSharedDailyFlag(ServerWorld world,
                                              UUID playerId,
                                              DailyQuestService.DailyQuestType type,
                                              String key) {
        return sharedDailySession(world, playerId, type) != null && QuestShareProfiles.sharesDailyFlag(type, key);
    }

    public static boolean usesSharedWeeklyInt(ServerWorld world,
                                              UUID playerId,
                                              WeeklyQuestService.WeeklyQuestType type,
                                              String key) {
        return sharedWeeklySession(world, playerId, type) != null && QuestShareProfiles.sharesWeeklyInt(type, key);
    }

    public static boolean usesSharedWeeklyFlag(ServerWorld world,
                                               UUID playerId,
                                               WeeklyQuestService.WeeklyQuestType type,
                                               String key) {
        return sharedWeeklySession(world, playerId, type) != null && QuestShareProfiles.sharesWeeklyFlag(type, key);
    }

    public static boolean isSharedDailyMember(ServerWorld world, UUID playerId, DailyQuestService.DailyQuestType type) {
        return sharedDailySession(world, playerId, type) != null;
    }

    public static boolean isSharedWeeklyMember(ServerWorld world, UUID playerId, WeeklyQuestService.WeeklyQuestType type) {
        return sharedWeeklySession(world, playerId, type) != null;
    }

    public static int getSharedDailyInt(ServerWorld world,
                                        UUID playerId,
                                        DailyQuestService.DailyQuestType type,
                                        String key) {
        SharedQuestRuntime session = sharedDailySession(world, playerId, type);
        return session == null ? 0 : session.getInt(key);
    }

    public static void setSharedDailyInt(ServerWorld world,
                                         UUID playerId,
                                         DailyQuestService.DailyQuestType type,
                                         String key,
                                         int value) {
        SharedQuestRuntime session = sharedDailySession(world, playerId, type);
        if (session != null) {
            session.setInt(key, value);
        }
    }

    public static void addSharedDailyInt(ServerWorld world,
                                         UUID playerId,
                                         DailyQuestService.DailyQuestType type,
                                         String key,
                                         int amount) {
        SharedQuestRuntime session = sharedDailySession(world, playerId, type);
        if (session != null) {
            session.addInt(key, amount);
        }
    }

    public static boolean getSharedDailyFlag(ServerWorld world,
                                             UUID playerId,
                                             DailyQuestService.DailyQuestType type,
                                             String key) {
        SharedQuestRuntime session = sharedDailySession(world, playerId, type);
        return session != null && session.hasFlag(key);
    }

    public static void setSharedDailyFlag(ServerWorld world,
                                          UUID playerId,
                                          DailyQuestService.DailyQuestType type,
                                          String key,
                                          boolean enabled) {
        SharedQuestRuntime session = sharedDailySession(world, playerId, type);
        if (session != null) {
            session.setFlag(key, enabled);
        }
    }

    public static int getSharedWeeklyInt(ServerWorld world,
                                         UUID playerId,
                                         WeeklyQuestService.WeeklyQuestType type,
                                         String key) {
        SharedQuestRuntime session = sharedWeeklySession(world, playerId, type);
        return session == null ? 0 : session.getInt(key);
    }

    public static void setSharedWeeklyInt(ServerWorld world,
                                          UUID playerId,
                                          WeeklyQuestService.WeeklyQuestType type,
                                          String key,
                                          int value) {
        SharedQuestRuntime session = sharedWeeklySession(world, playerId, type);
        if (session != null) {
            session.setInt(key, value);
        }
    }

    public static void addSharedWeeklyInt(ServerWorld world,
                                          UUID playerId,
                                          WeeklyQuestService.WeeklyQuestType type,
                                          String key,
                                          int amount) {
        SharedQuestRuntime session = sharedWeeklySession(world, playerId, type);
        if (session != null) {
            session.addInt(key, amount);
        }
    }

    public static boolean getSharedWeeklyFlag(ServerWorld world,
                                              UUID playerId,
                                              WeeklyQuestService.WeeklyQuestType type,
                                              String key) {
        SharedQuestRuntime session = sharedWeeklySession(world, playerId, type);
        return session != null && session.hasFlag(key);
    }

    public static void setSharedWeeklyFlag(ServerWorld world,
                                           UUID playerId,
                                           WeeklyQuestService.WeeklyQuestType type,
                                           String key,
                                           boolean enabled) {
        SharedQuestRuntime session = sharedWeeklySession(world, playerId, type);
        if (session != null) {
            session.setFlag(key, enabled);
        }
    }

    public static void onStoryQuestAccepted(ServerWorld world,
                                            ServerPlayerEntity player,
                                            StoryArcType arcType,
                                            int chapterIndex,
                                            StoryChapterDefinition chapter) {
        ensureLoaded(world == null ? null : world.getServer());
        if (world == null || player == null || arcType == null || !QuestShareProfiles.isStoryShareable(arcType)) {
            return;
        }
        PartyRuntime party = partyFor(player.getUuid());
        if (party == null) {
            return;
        }

        SharedQuestRuntime session = ensureStorySession(party, arcType, chapterIndex);
        mergeStoryProgressIntoSession(world, player.getUuid(), arcType, chapterIndex, session);
        syncPartyMembersIntoStory(world, party, arcType, chapterIndex, chapter, player.getUuid());
        clearStoryOffer(party, player.getUuid());
        storePersistentState(world.getServer());
        refreshPartyUi(world, party);
    }

    public static boolean usesSharedStoryInt(ServerWorld world,
                                             UUID playerId,
                                             StoryArcType arcType,
                                             int chapterIndex,
                                             String key) {
        return sharedStorySession(world, playerId, arcType, chapterIndex) != null
                && QuestShareProfiles.sharesStoryInt(arcType, chapterIndex, key);
    }

    public static boolean usesSharedStoryFlag(ServerWorld world,
                                              UUID playerId,
                                              StoryArcType arcType,
                                              int chapterIndex,
                                              String key) {
        return sharedStorySession(world, playerId, arcType, chapterIndex) != null
                && QuestShareProfiles.sharesStoryFlag(arcType, chapterIndex, key);
    }

    public static boolean isSharedStoryMember(ServerWorld world,
                                              UUID playerId,
                                              StoryArcType arcType,
                                              int chapterIndex) {
        return sharedStorySession(world, playerId, arcType, chapterIndex) != null;
    }

    public static int getSharedStoryInt(ServerWorld world,
                                        UUID playerId,
                                        StoryArcType arcType,
                                        int chapterIndex,
                                        String key) {
        SharedQuestRuntime session = sharedStorySession(world, playerId, arcType, chapterIndex);
        return session == null ? 0 : session.getInt(key);
    }

    public static void setSharedStoryInt(ServerWorld world,
                                         UUID playerId,
                                         StoryArcType arcType,
                                         int chapterIndex,
                                         String key,
                                         int value) {
        SharedQuestRuntime session = sharedStorySession(world, playerId, arcType, chapterIndex);
        if (session != null) {
            session.setInt(key, value);
        }
    }

    public static boolean getSharedStoryFlag(ServerWorld world,
                                             UUID playerId,
                                             StoryArcType arcType,
                                             int chapterIndex,
                                             String key) {
        SharedQuestRuntime session = sharedStorySession(world, playerId, arcType, chapterIndex);
        return session != null && session.hasFlag(key);
    }

    public static void setSharedStoryFlag(ServerWorld world,
                                          UUID playerId,
                                          StoryArcType arcType,
                                          int chapterIndex,
                                          String key,
                                          boolean enabled) {
        SharedQuestRuntime session = sharedStorySession(world, playerId, arcType, chapterIndex);
        if (session != null) {
            session.setFlag(key, enabled);
        }
    }

    public static List<UUID> activeStoryMembers(ServerWorld world,
                                                UUID playerId,
                                                StoryArcType arcType,
                                                int chapterIndex) {
        PartyRuntime party = partyFor(playerId);
        SharedQuestRuntime session = sharedStorySessionForType(world, playerId, arcType, chapterIndex);
        if (world == null || party == null || arcType == null || session == null) {
            return List.of(playerId);
        }

        List<UUID> members = new ArrayList<>();
        for (UUID memberId : party.members()) {
            if (!session.hasSynced(memberId)) {
                continue;
            }
            PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
            if (data.getActiveStoryArc() != arcType) {
                continue;
            }
            if (data.getStoryChapterProgress(arcType.id()) != chapterIndex) {
                continue;
            }
            if (world.getServer().getPlayerManager().getPlayer(memberId) != null) {
                members.add(memberId);
            }
        }
        return members.isEmpty() ? List.of(playerId) : List.copyOf(members);
    }

    public static int storySharedMemberCount(ServerWorld world,
                                             UUID playerId,
                                             StoryArcType arcType,
                                             int chapterIndex) {
        SharedQuestRuntime session = sharedStorySessionForType(world, playerId, arcType, chapterIndex);
        PartyRuntime party = partyFor(playerId);
        if (world == null || session == null || party == null) {
            return 0;
        }
        int count = 0;
        for (UUID memberId : party.members()) {
            if (!session.hasSynced(memberId)) {
                continue;
            }
            PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
            if (data.getActiveStoryArc() == arcType && data.getStoryChapterProgress(arcType.id()) == chapterIndex) {
                count++;
            }
        }
        return count;
    }

    public static int countStoryTurnInItem(ServerWorld world,
                                           UUID playerId,
                                           StoryArcType arcType,
                                           int chapterIndex,
                                           Item item) {
        int total = 0;
        for (ServerPlayerEntity member : onlineMembers(world, activeStoryMembers(world, playerId, arcType, chapterIndex))) {
            total += DailyQuestService.countInventoryItem(member, item);
        }
        return total;
    }

    public static boolean consumeStoryTurnInItem(ServerWorld world,
                                                 UUID playerId,
                                                 StoryArcType arcType,
                                                 int chapterIndex,
                                                 Item item,
                                                 int amount) {
        if (countStoryTurnInItem(world, playerId, arcType, chapterIndex, item) < amount) {
            return false;
        }
        int remaining = amount;
        for (ServerPlayerEntity member : onlineMembers(world, activeStoryMembers(world, playerId, arcType, chapterIndex))) {
            if (remaining <= 0) {
                break;
            }
            int available = DailyQuestService.countInventoryItem(member, item);
            if (available <= 0) {
                continue;
            }
            int toConsume = Math.min(remaining, available);
            if (!DailyQuestService.consumeInventoryItem(member, item, toConsume)) {
                return false;
            }
            remaining -= toConsume;
        }
        return remaining <= 0;
    }

    public static int countStoryTurnInItems(ServerWorld world,
                                            UUID playerId,
                                            StoryArcType arcType,
                                            int chapterIndex,
                                            java.util.function.Predicate<ItemStack> matcher) {
        int total = 0;
        for (ServerPlayerEntity member : onlineMembers(world, activeStoryMembers(world, playerId, arcType, chapterIndex))) {
            total += countInventoryItems(member, matcher);
        }
        return total;
    }

    public static boolean consumeStoryTurnInItems(ServerWorld world,
                                                  UUID playerId,
                                                  StoryArcType arcType,
                                                  int chapterIndex,
                                                  java.util.function.Predicate<ItemStack> matcher,
                                                  int amount) {
        if (countStoryTurnInItems(world, playerId, arcType, chapterIndex, matcher) < amount) {
            return false;
        }
        int remaining = amount;
        for (ServerPlayerEntity member : onlineMembers(world, activeStoryMembers(world, playerId, arcType, chapterIndex))) {
            if (remaining <= 0) {
                break;
            }
            int available = countInventoryItems(member, matcher);
            if (available <= 0) {
                continue;
            }
            int toConsume = Math.min(remaining, available);
            if (!consumeInventoryItems(member, matcher, toConsume)) {
                return false;
            }
            remaining -= toConsume;
        }
        return remaining <= 0;
    }

    public static void clearStorySessionIfFinished(ServerWorld world,
                                                   UUID playerId,
                                                   StoryArcType arcType,
                                                   int chapterIndex) {
        PartyRuntime party = partyFor(playerId);
        if (party == null || !matchesStorySession(party, arcType, chapterIndex)) {
            return;
        }
        if (storySharedMemberCount(world, playerId, arcType, chapterIndex) <= 0) {
            party.story().clear();
            party.storyOffers().clear();
            storePersistentState(world.getServer());
        }
    }

    public static void onPilgrimContractAccepted(ServerWorld world,
                                                 ServerPlayerEntity player,
                                                 PilgrimContractType type) {
        ensureLoaded(world == null ? null : world.getServer());
        if (world == null || player == null || type == null || !QuestShareProfiles.isPilgrimShareable(type)) {
            return;
        }
        PartyRuntime party = partyFor(player.getUuid());
        if (party == null) {
            return;
        }

        SharedQuestRuntime session = ensurePilgrimSession(party, type);
        mergePilgrimProgressIntoSession(world, player.getUuid(), type, session);
        syncPartyMembersIntoPilgrim(world, party, type, player.getUuid());
        clearPilgrimOffer(party, player.getUuid());
        storePersistentState(world.getServer());
        refreshPartyUi(world, party);
    }

    public static boolean usesSharedPilgrimInt(ServerWorld world,
                                               UUID playerId,
                                               PilgrimContractType type,
                                               String key) {
        return sharedPilgrimSession(world, playerId, type) != null && QuestShareProfiles.sharesPilgrimInt(type, key);
    }

    public static boolean usesSharedPilgrimFlag(ServerWorld world,
                                                UUID playerId,
                                                PilgrimContractType type,
                                                String key) {
        return sharedPilgrimSession(world, playerId, type) != null && QuestShareProfiles.sharesPilgrimFlag(type, key);
    }

    public static boolean isSharedPilgrimMember(ServerWorld world, UUID playerId, PilgrimContractType type) {
        return sharedPilgrimSession(world, playerId, type) != null;
    }

    public static int getSharedPilgrimInt(ServerWorld world,
                                          UUID playerId,
                                          PilgrimContractType type,
                                          String key) {
        SharedQuestRuntime session = sharedPilgrimSession(world, playerId, type);
        return session == null ? 0 : session.getInt(key);
    }

    public static void setSharedPilgrimInt(ServerWorld world,
                                           UUID playerId,
                                           PilgrimContractType type,
                                           String key,
                                           int value) {
        SharedQuestRuntime session = sharedPilgrimSession(world, playerId, type);
        if (session != null) {
            session.setInt(key, value);
        }
    }

    public static boolean getSharedPilgrimFlag(ServerWorld world,
                                               UUID playerId,
                                               PilgrimContractType type,
                                               String key) {
        SharedQuestRuntime session = sharedPilgrimSession(world, playerId, type);
        return session != null && session.hasFlag(key);
    }

    public static void setSharedPilgrimFlag(ServerWorld world,
                                            UUID playerId,
                                            PilgrimContractType type,
                                            String key,
                                            boolean enabled) {
        SharedQuestRuntime session = sharedPilgrimSession(world, playerId, type);
        if (session != null) {
            session.setFlag(key, enabled);
        }
    }

    public static List<UUID> activePilgrimMembers(ServerWorld world,
                                                  UUID playerId,
                                                  PilgrimContractType type,
                                                  boolean includeCompleted) {
        PartyRuntime party = partyFor(playerId);
        SharedQuestRuntime session = sharedPilgrimSessionForType(world, playerId, type);
        if (world == null || party == null || type == null || session == null) {
            return List.of(playerId);
        }

        List<UUID> members = new ArrayList<>();
        for (UUID memberId : party.members()) {
            if (!session.hasSynced(memberId)) {
                continue;
            }
            PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
            if (type != PilgrimContractType.fromId(data.getActivePilgrimContractId())) {
                continue;
            }
            if (world.getServer().getPlayerManager().getPlayer(memberId) != null) {
                members.add(memberId);
            }
        }
        return members.isEmpty() ? List.of(playerId) : List.copyOf(members);
    }

    public static int pilgrimSharedMemberCount(ServerWorld world, UUID playerId, PilgrimContractType type) {
        SharedQuestRuntime session = sharedPilgrimSessionForType(world, playerId, type);
        PartyRuntime party = partyFor(playerId);
        if (world == null || session == null || party == null) {
            return 0;
        }
        int count = 0;
        for (UUID memberId : party.members()) {
            if (!session.hasSynced(memberId)) {
                continue;
            }
            if (type == PilgrimContractType.fromId(QuestState.get(world.getServer()).getPlayerData(memberId).getActivePilgrimContractId())) {
                count++;
            }
        }
        return count;
    }

    public static void clearPilgrimSessionIfFinished(ServerWorld world, UUID playerId, PilgrimContractType type) {
        PartyRuntime party = partyFor(playerId);
        if (party == null || activePilgrimType(party) != type) {
            return;
        }
        if (pilgrimSharedMemberCount(world, playerId, type) <= 0) {
            party.pilgrim().clear();
            party.pilgrimOffers().clear();
            storePersistentState(world.getServer());
        }
    }

    public static List<UUID> activeDailyMembers(ServerWorld world,
                                                UUID playerId,
                                                DailyQuestService.DailyQuestType type,
                                                boolean includeCompleted) {
        PartyRuntime party = partyFor(playerId);
        SharedQuestRuntime session = sharedDailySessionForType(world, playerId, type);
        if (world == null || party == null || type == null || session == null) {
            return List.of(playerId);
        }

        long day = TimeUtil.currentDay();
        List<UUID> members = new ArrayList<>();
        for (UUID memberId : party.members()) {
            if (!session.hasSynced(memberId)) {
                continue;
            }
            PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
            if (data.getAcceptedDay() != day) {
                continue;
            }
            if (data.getDailyChoice() != type || data.getDailyChoiceDay() != day) {
                continue;
            }
            if (!includeCompleted && data.getLastRewardDay() == day) {
                continue;
            }
            if (world.getServer().getPlayerManager().getPlayer(memberId) != null) {
                members.add(memberId);
            }
        }
        return members.isEmpty() ? List.of(playerId) : List.copyOf(members);
    }

    public static List<UUID> activeWeeklyMembers(ServerWorld world,
                                                 UUID playerId,
                                                 WeeklyQuestService.WeeklyQuestType type,
                                                 boolean includeCompleted) {
        PartyRuntime party = partyFor(playerId);
        SharedQuestRuntime session = sharedWeeklySessionForType(world, playerId, type);
        if (world == null || party == null || type == null || session == null) {
            return List.of(playerId);
        }

        long cycle = TimeUtil.currentWeekCycle();
        List<UUID> members = new ArrayList<>();
        for (UUID memberId : party.members()) {
            if (!session.hasSynced(memberId)) {
                continue;
            }
            PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
            if (data.getWeeklyAcceptedCycle() != cycle) {
                continue;
            }
            if (data.getWeeklyChoice() != type || data.getWeeklyChoiceCycle() != cycle) {
                continue;
            }
            if (!includeCompleted && data.getWeeklyRewardCycle() == cycle) {
                continue;
            }
            if (world.getServer().getPlayerManager().getPlayer(memberId) != null) {
                members.add(memberId);
            }
        }
        return members.isEmpty() ? List.of(playerId) : List.copyOf(members);
    }

    public static int dailySharedMemberCount(ServerWorld world, UUID playerId, DailyQuestService.DailyQuestType type) {
        SharedQuestRuntime session = sharedDailySessionForType(world, playerId, type);
        if (world == null || session == null) {
            return 0;
        }
        int count = 0;
        long day = TimeUtil.currentDay();
        PartyRuntime party = partyFor(playerId);
        if (party == null) {
            return 0;
        }
        for (UUID memberId : party.members()) {
            if (!session.hasSynced(memberId)) {
                continue;
            }
            PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
            if (data.getAcceptedDay() == day && data.getLastRewardDay() != day && data.getDailyChoice() == type && data.getDailyChoiceDay() == day) {
                count++;
            }
        }
        return count;
    }

    public static int weeklySharedMemberCount(ServerWorld world, UUID playerId, WeeklyQuestService.WeeklyQuestType type) {
        SharedQuestRuntime session = sharedWeeklySessionForType(world, playerId, type);
        if (world == null || session == null) {
            return 0;
        }
        int count = 0;
        long cycle = TimeUtil.currentWeekCycle();
        PartyRuntime party = partyFor(playerId);
        if (party == null) {
            return 0;
        }
        for (UUID memberId : party.members()) {
            if (!session.hasSynced(memberId)) {
                continue;
            }
            PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
            if (data.getWeeklyAcceptedCycle() == cycle && data.getWeeklyRewardCycle() != cycle && data.getWeeklyChoice() == type && data.getWeeklyChoiceCycle() == cycle) {
                count++;
            }
        }
        return count;
    }

    public static int partySize(UUID playerId) {
        PartyRuntime party = partyFor(playerId);
        return party == null ? 1 : party.members().size();
    }

    public static boolean acceptDailyOffer(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }
        ensureLoaded(world.getServer());
        PartyRuntime party = partyFor(player.getUuid());
        if (party == null) {
            player.sendMessage(Text.translatable("message.village-quest.party.none").formatted(Formatting.RED), false);
            return false;
        }
        QuestJoinOffer offer = party.dailyOffers().get(player.getUuid());
        if (offer == null) {
            player.sendMessage(Text.translatable("message.village-quest.party.offer.daily.none").formatted(Formatting.RED), false);
            return false;
        }
        DailyQuestService.DailyQuestType type = parseDailyType(offer.questId());
        SharedQuestRuntime session = sharedDailySessionForType(world, player.getUuid(), type);
        if (type == null || session == null || !session.matches(offer.questId(), offer.revision())) {
            clearDailyOffer(party, player.getUuid());
            storePersistentState(world.getServer());
            player.sendMessage(Text.translatable("message.village-quest.party.offer.daily.expired").formatted(Formatting.RED), false);
            return false;
        }
        if (!canJoinDailyShare(world, player.getUuid(), type, true)) {
            clearDailyOffer(party, player.getUuid());
            storePersistentState(world.getServer());
            return false;
        }

        syncDailyMember(world, party, player, type, DailyQuestGenerator.definition(type), offer.sourceId());
        storePersistentState(world.getServer());
        refreshPartyUi(world, party);
        return true;
    }

    public static boolean declineDailyOffer(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }
        ensureLoaded(world.getServer());
        PartyRuntime party = partyFor(player.getUuid());
        if (party == null || clearDailyOffer(party, player.getUuid()) == null) {
            player.sendMessage(Text.translatable("message.village-quest.party.offer.daily.none").formatted(Formatting.RED), false);
            return false;
        }
        player.sendMessage(Text.translatable("message.village-quest.party.offer.daily.declined").formatted(Formatting.GRAY), false);
        storePersistentState(world.getServer());
        return true;
    }

    public static boolean acceptWeeklyOffer(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }
        ensureLoaded(world.getServer());
        PartyRuntime party = partyFor(player.getUuid());
        if (party == null) {
            player.sendMessage(Text.translatable("message.village-quest.party.none").formatted(Formatting.RED), false);
            return false;
        }
        QuestJoinOffer offer = party.weeklyOffers().get(player.getUuid());
        if (offer == null) {
            player.sendMessage(Text.translatable("message.village-quest.party.offer.weekly.none").formatted(Formatting.RED), false);
            return false;
        }
        WeeklyQuestService.WeeklyQuestType type = parseWeeklyType(offer.questId());
        SharedQuestRuntime session = sharedWeeklySessionForType(world, player.getUuid(), type);
        if (type == null || session == null || !session.matches(offer.questId(), offer.revision())) {
            clearWeeklyOffer(party, player.getUuid());
            storePersistentState(world.getServer());
            player.sendMessage(Text.translatable("message.village-quest.party.offer.weekly.expired").formatted(Formatting.RED), false);
            return false;
        }
        if (!canJoinWeeklyShare(world, player.getUuid(), type, true)) {
            clearWeeklyOffer(party, player.getUuid());
            storePersistentState(world.getServer());
            return false;
        }

        syncWeeklyMember(world, party, player, type, WeeklyQuestGenerator.definition(type), offer.sourceId());
        storePersistentState(world.getServer());
        refreshPartyUi(world, party);
        return true;
    }

    public static boolean declineWeeklyOffer(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }
        ensureLoaded(world.getServer());
        PartyRuntime party = partyFor(player.getUuid());
        if (party == null || clearWeeklyOffer(party, player.getUuid()) == null) {
            player.sendMessage(Text.translatable("message.village-quest.party.offer.weekly.none").formatted(Formatting.RED), false);
            return false;
        }
        player.sendMessage(Text.translatable("message.village-quest.party.offer.weekly.declined").formatted(Formatting.GRAY), false);
        storePersistentState(world.getServer());
        return true;
    }

    public static boolean acceptStoryOffer(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }
        ensureLoaded(world.getServer());
        PartyRuntime party = partyFor(player.getUuid());
        if (party == null) {
            player.sendMessage(Text.translatable("message.village-quest.party.offer.story.none").formatted(Formatting.RED), false);
            return false;
        }

        QuestJoinOffer offer = party.storyOffers().get(player.getUuid());
        StoryArcType arcType = offer == null ? null : parseStoryType(offer.questId());
        int chapterIndex = offer == null ? -1 : parseStoryChapter(offer.questId());
        if (offer == null || arcType == null || chapterIndex < 0 || !isOfferStillValid(world, party, player.getUuid(), offer, arcType, chapterIndex)) {
            clearStoryOffer(party, player.getUuid());
            player.sendMessage(Text.translatable("message.village-quest.party.offer.story.expired").formatted(Formatting.RED), false);
            storePersistentState(world.getServer());
            return false;
        }
        if (!canJoinStoryShare(world, player.getUuid(), arcType, chapterIndex, true)) {
            clearStoryOffer(party, player.getUuid());
            storePersistentState(world.getServer());
            return false;
        }

        StoryChapterDefinition chapter = StoryQuestService.chapter(world, player.getUuid(), arcType);
        if (chapter == null) {
            chapter = StoryQuestService.definition(arcType) == null ? null : StoryQuestService.definition(arcType).chapter(chapterIndex);
        }
        syncStoryMember(world, party, player, arcType, chapterIndex, chapter, offer.sourceId());
        storePersistentState(world.getServer());
        refreshPartyUi(world, party);
        return true;
    }

    public static boolean declineStoryOffer(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }
        ensureLoaded(world.getServer());
        PartyRuntime party = partyFor(player.getUuid());
        if (party == null || clearStoryOffer(party, player.getUuid()) == null) {
            player.sendMessage(Text.translatable("message.village-quest.party.offer.story.none").formatted(Formatting.RED), false);
            return false;
        }
        player.sendMessage(Text.translatable("message.village-quest.party.offer.story.declined").formatted(Formatting.GRAY), false);
        storePersistentState(world.getServer());
        return true;
    }

    public static boolean acceptPilgrimOffer(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }
        ensureLoaded(world.getServer());
        PartyRuntime party = partyFor(player.getUuid());
        if (party == null) {
            player.sendMessage(Text.translatable("message.village-quest.party.offer.pilgrim.none").formatted(Formatting.RED), false);
            return false;
        }

        QuestJoinOffer offer = party.pilgrimOffers().get(player.getUuid());
        PilgrimContractType type = offer == null ? null : PilgrimContractType.fromId(offer.questId());
        if (offer == null || type == null || !isOfferStillValid(world, party, player.getUuid(), offer, type)) {
            clearPilgrimOffer(party, player.getUuid());
            player.sendMessage(Text.translatable("message.village-quest.party.offer.pilgrim.expired").formatted(Formatting.RED), false);
            storePersistentState(world.getServer());
            return false;
        }
        if (!canJoinPilgrimShare(world, player.getUuid(), type, true)) {
            clearPilgrimOffer(party, player.getUuid());
            storePersistentState(world.getServer());
            return false;
        }

        syncPilgrimMember(world, party, player, type, offer.sourceId());
        storePersistentState(world.getServer());
        refreshPartyUi(world, party);
        return true;
    }

    public static boolean declinePilgrimOffer(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }
        ensureLoaded(world.getServer());
        PartyRuntime party = partyFor(player.getUuid());
        if (party == null || clearPilgrimOffer(party, player.getUuid()) == null) {
            player.sendMessage(Text.translatable("message.village-quest.party.offer.pilgrim.none").formatted(Formatting.RED), false);
            return false;
        }
        player.sendMessage(Text.translatable("message.village-quest.party.offer.pilgrim.declined").formatted(Formatting.GRAY), false);
        storePersistentState(world.getServer());
        return true;
    }

    private static void ensureLoaded(MinecraftServer server) {
        if (!loaded) {
            loadPersistentState(server);
        }
    }

    private static void cleanupInvalidState(MinecraftServer server) {
        if (server == null) {
            return;
        }
        List<UUID> invalidPartyIds = new ArrayList<>();
        for (PartyRuntime party : PARTIES.values()) {
            if (party.members().isEmpty()) {
                invalidPartyIds.add(party.id());
                continue;
            }
            if (!party.members().contains(party.leaderId())) {
                party.setLeaderId(party.members().iterator().next());
            }
        }
        for (UUID partyId : invalidPartyIds) {
            PARTIES.remove(partyId);
        }
    }

    private static void expireInvites(MinecraftServer server, long now) {
        if (server == null || INVITES.isEmpty()) {
            return;
        }
        List<UUID> expired = new ArrayList<>();
        for (var entry : INVITES.entrySet()) {
            if (entry.getValue().expiresAtMillis() <= now) {
                expired.add(entry.getKey());
            }
        }
        for (UUID targetId : expired) {
            PartyInvite invite = INVITES.remove(targetId);
            if (invite == null) {
                continue;
            }
            ServerPlayerEntity inviter = server.getPlayerManager().getPlayer(invite.inviterId());
            if (inviter != null) {
                inviter.sendMessage(Text.translatable("message.village-quest.party.invite.expired").formatted(Formatting.GRAY), false);
                refreshOpenUi(inviter);
            }
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetId);
            if (target != null) {
                target.sendMessage(Text.translatable("message.village-quest.party.invite.expired_target").formatted(Formatting.GRAY), false);
                refreshOpenUi(target);
            }
        }
        if (!expired.isEmpty()) {
            storePersistentState(server);
        }
    }

    private static void expireDisconnectedMembers(ServerWorld world, long now) {
        if (world == null) {
            return;
        }
        List<ExpiryTarget> expired = new ArrayList<>();
        for (PartyRuntime party : PARTIES.values()) {
            for (var entry : party.disconnectDeadlines().entrySet()) {
                if (entry.getValue() <= now && world.getServer().getPlayerManager().getPlayer(entry.getKey()) == null) {
                    expired.add(new ExpiryTarget(party.id(), entry.getKey()));
                }
            }
        }
        for (ExpiryTarget target : expired) {
            PartyRuntime party = PARTIES.get(target.partyId());
            if (party != null) {
                removeDisconnectedMember(world, party, target.memberId());
            }
        }
        if (!expired.isEmpty()) {
            storePersistentState(world.getServer());
        }
    }

    private static void cleanupStaleOffers(ServerWorld world) {
        if (world == null) {
            return;
        }
        boolean changed = false;
        for (PartyRuntime party : PARTIES.values()) {
            changed |= cleanupDailyOffers(world, party);
            changed |= cleanupWeeklyOffers(world, party);
            changed |= cleanupStoryOffers(world, party);
            changed |= cleanupPilgrimOffers(world, party);
        }
        if (changed) {
            storePersistentState(world.getServer());
        }
    }

    private static boolean cleanupDailyOffers(ServerWorld world, PartyRuntime party) {
        DailyQuestService.DailyQuestType activeType = activeDailyType(party);
        if (activeType == null) {
            if (!party.dailyOffers().isEmpty()) {
                party.dailyOffers().clear();
                return true;
            }
            return false;
        }
        boolean changed = false;
        List<UUID> remove = new ArrayList<>();
        for (var entry : party.dailyOffers().entrySet()) {
            if (!isOfferStillValid(world, party, entry.getKey(), entry.getValue(), activeType)) {
                remove.add(entry.getKey());
            }
        }
        for (UUID memberId : remove) {
            party.dailyOffers().remove(memberId);
            changed = true;
        }
        return changed;
    }

    private static boolean cleanupWeeklyOffers(ServerWorld world, PartyRuntime party) {
        WeeklyQuestService.WeeklyQuestType activeType = activeWeeklyType(party);
        if (activeType == null) {
            if (!party.weeklyOffers().isEmpty()) {
                party.weeklyOffers().clear();
                return true;
            }
            return false;
        }
        boolean changed = false;
        List<UUID> remove = new ArrayList<>();
        for (var entry : party.weeklyOffers().entrySet()) {
            if (!isOfferStillValid(world, party, entry.getKey(), entry.getValue(), activeType)) {
                remove.add(entry.getKey());
            }
        }
        for (UUID memberId : remove) {
            party.weeklyOffers().remove(memberId);
            changed = true;
        }
        return changed;
    }

    private static boolean cleanupStoryOffers(ServerWorld world, PartyRuntime party) {
        StoryArcType activeType = activeStoryType(party);
        int chapterIndex = activeStoryChapterIndex(party);
        if (activeType == null || chapterIndex < 0) {
            if (!party.storyOffers().isEmpty()) {
                party.storyOffers().clear();
                return true;
            }
            return false;
        }
        boolean changed = false;
        List<UUID> remove = new ArrayList<>();
        for (var entry : party.storyOffers().entrySet()) {
            if (!isOfferStillValid(world, party, entry.getKey(), entry.getValue(), activeType, chapterIndex)) {
                remove.add(entry.getKey());
            }
        }
        for (UUID memberId : remove) {
            party.storyOffers().remove(memberId);
            changed = true;
        }
        return changed;
    }

    private static boolean cleanupPilgrimOffers(ServerWorld world, PartyRuntime party) {
        PilgrimContractType activeType = activePilgrimType(party);
        if (activeType == null) {
            if (!party.pilgrimOffers().isEmpty()) {
                party.pilgrimOffers().clear();
                return true;
            }
            return false;
        }
        boolean changed = false;
        List<UUID> remove = new ArrayList<>();
        for (var entry : party.pilgrimOffers().entrySet()) {
            if (!isOfferStillValid(world, party, entry.getKey(), entry.getValue(), activeType)) {
                remove.add(entry.getKey());
            }
        }
        for (UUID memberId : remove) {
            party.pilgrimOffers().remove(memberId);
            changed = true;
        }
        return changed;
    }

    private static boolean ensureEnabled(MinecraftServer server, ServerPlayerEntity actor) {
        if (isEnabled(server)) {
            return true;
        }
        if (actor != null) {
            actor.sendMessage(Text.translatable("message.village-quest.party.unavailable").formatted(Formatting.GRAY), false);
        }
        return false;
    }

    public static int countPartyInventoryItem(ServerWorld world, UUID playerId, Item item) {
        int total = 0;
        for (ServerPlayerEntity member : orderedOnlineMembers(world, playerId)) {
            total += DailyQuestService.countInventoryItem(member, item);
        }
        return total;
    }

    public static boolean consumePartyInventoryItem(ServerWorld world, UUID playerId, Item item, int amount) {
        if (world == null || item == null || amount <= 0) {
            return false;
        }
        if (countPartyInventoryItem(world, playerId, item) < amount) {
            return false;
        }

        int remaining = amount;
        for (ServerPlayerEntity member : orderedOnlineMembers(world, playerId)) {
            if (remaining <= 0) {
                break;
            }
            int available = DailyQuestService.countInventoryItem(member, item);
            if (available <= 0) {
                continue;
            }
            int toConsume = Math.min(remaining, available);
            if (!DailyQuestService.consumeInventoryItem(member, item, toConsume)) {
                return false;
            }
            remaining -= toConsume;
        }
        return remaining <= 0;
    }

    private static int countInventoryItems(ServerPlayerEntity player, java.util.function.Predicate<ItemStack> matcher) {
        if (player == null || matcher == null) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (matcher.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean consumeInventoryItems(ServerPlayerEntity player,
                                                 java.util.function.Predicate<ItemStack> matcher,
                                                 int amount) {
        if (player == null || matcher == null || amount <= 0 || countInventoryItems(player, matcher) < amount) {
            return false;
        }
        int remaining = amount;
        for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!matcher.test(stack)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.decrement(removed);
            remaining -= removed;
        }
        player.currentScreenHandler.sendContentUpdates();
        return remaining <= 0;
    }

    public static int countDailyTurnInItem(ServerWorld world,
                                           UUID playerId,
                                           DailyQuestService.DailyQuestType type,
                                           Item item) {
        int total = 0;
        for (ServerPlayerEntity member : onlineMembers(world, activeDailyMembers(world, playerId, type, false))) {
            total += DailyQuestService.countInventoryItem(member, item);
        }
        return total;
    }

    public static boolean consumeDailyTurnInItem(ServerWorld world,
                                                 UUID playerId,
                                                 DailyQuestService.DailyQuestType type,
                                                 Item item,
                                                 int amount) {
        if (countDailyTurnInItem(world, playerId, type, item) < amount) {
            return false;
        }
        int remaining = amount;
        for (ServerPlayerEntity member : onlineMembers(world, activeDailyMembers(world, playerId, type, false))) {
            if (remaining <= 0) {
                break;
            }
            int available = DailyQuestService.countInventoryItem(member, item);
            if (available <= 0) {
                continue;
            }
            int toConsume = Math.min(remaining, available);
            if (!DailyQuestService.consumeInventoryItem(member, item, toConsume)) {
                return false;
            }
            remaining -= toConsume;
        }
        return remaining <= 0;
    }

    public static int countWeeklyTurnInItem(ServerWorld world,
                                            UUID playerId,
                                            WeeklyQuestService.WeeklyQuestType type,
                                            Item item) {
        int total = 0;
        for (ServerPlayerEntity member : onlineMembers(world, activeWeeklyMembers(world, playerId, type, false))) {
            total += DailyQuestService.countInventoryItem(member, item);
        }
        return total;
    }

    public static boolean consumeWeeklyTurnInItem(ServerWorld world,
                                                  UUID playerId,
                                                  WeeklyQuestService.WeeklyQuestType type,
                                                  Item item,
                                                  int amount) {
        if (countWeeklyTurnInItem(world, playerId, type, item) < amount) {
            return false;
        }
        int remaining = amount;
        for (ServerPlayerEntity member : onlineMembers(world, activeWeeklyMembers(world, playerId, type, false))) {
            if (remaining <= 0) {
                break;
            }
            int available = DailyQuestService.countInventoryItem(member, item);
            if (available <= 0) {
                continue;
            }
            int toConsume = Math.min(remaining, available);
            if (!DailyQuestService.consumeInventoryItem(member, item, toConsume)) {
                return false;
            }
            remaining -= toConsume;
        }
        return remaining <= 0;
    }

    public static void clearDailySessionIfFinished(ServerWorld world, UUID playerId, DailyQuestService.DailyQuestType type) {
        PartyRuntime party = partyFor(playerId);
        if (party == null || activeDailyType(party) != type) {
            return;
        }
        if (dailySharedMemberCount(world, playerId, type) <= 0) {
            party.daily().clear();
            party.dailyOffers().clear();
            storePersistentState(world.getServer());
        }
    }

    public static void clearWeeklySessionIfFinished(ServerWorld world, UUID playerId, WeeklyQuestService.WeeklyQuestType type) {
        PartyRuntime party = partyFor(playerId);
        if (party == null || activeWeeklyType(party) != type) {
            return;
        }
        if (weeklySharedMemberCount(world, playerId, type) <= 0) {
            party.weekly().clear();
            party.weeklyOffers().clear();
            storePersistentState(world.getServer());
        }
    }

    private static boolean leaveInternal(ServerWorld world, UUID playerId, boolean notifyPlayer, boolean disconnecting) {
        PartyRuntime party = partyFor(playerId);
        if (world == null || party == null) {
            return false;
        }

        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
        copySharedProgressToPlayer(world, party, playerId);
        party.members().remove(playerId);
        party.disconnectDeadlines().remove(playerId);
        clearDailyOffer(party, playerId);
        clearWeeklyOffer(party, playerId);
        clearStoryOffer(party, playerId);
        clearPilgrimOffer(party, playerId);
        party.daily().unmarkSynced(playerId);
        party.weekly().unmarkSynced(playerId);
        party.story().unmarkSynced(playerId);
        party.pilgrim().unmarkSynced(playerId);
        MEMBER_TO_PARTY.remove(playerId);
        removePendingInviteFor(playerId);

        if (player != null && notifyPlayer) {
            player.sendMessage(Text.translatable("message.village-quest.party.left").formatted(Formatting.GRAY), false);
            refreshPlayer(world, player);
        }

        if (party.members().isEmpty()) {
            PARTIES.remove(party.id());
            removeInvitesForParty(party.id());
            storePersistentState(world.getServer());
            return true;
        }

        if (Objects.equals(party.leaderId(), playerId)) {
            party.setLeaderId(party.members().iterator().next());
        }

        if (party.members().size() == 1) {
            UUID remainingId = party.members().iterator().next();
            copySharedProgressToPlayer(world, party, remainingId);
            MEMBER_TO_PARTY.remove(remainingId);
            ServerPlayerEntity remaining = world.getServer().getPlayerManager().getPlayer(remainingId);
            if (remaining != null) {
                remaining.sendMessage(Text.translatable("message.village-quest.party.disbanded").formatted(Formatting.GRAY), false);
                refreshPlayer(world, remaining);
            }
            PARTIES.remove(party.id());
            removeInvitesForParty(party.id());
            storePersistentState(world.getServer());
            return true;
        }

        if (!disconnecting) {
            broadcast(world, party, Text.translatable("message.village-quest.party.member_left", nameOf(world, playerId)).formatted(Formatting.GRAY));
        }
        storePersistentState(world.getServer());
        refreshPartyUi(world, party);
        return true;
    }

    private static PartyRuntime getOrCreateParty(ServerWorld world, ServerPlayerEntity leader) {
        PartyRuntime existing = partyFor(leader.getUuid());
        if (existing != null) {
            return existing;
        }
        PartyRuntime created = new PartyRuntime(UUID.randomUUID(), leader.getUuid());
        created.members().add(leader.getUuid());
        PARTIES.put(created.id(), created);
        MEMBER_TO_PARTY.put(leader.getUuid(), created.id());
        seedSessionsFromMember(world, created, leader.getUuid());
        return created;
    }

    private static PartyRuntime partyFor(UUID memberId) {
        if (memberId == null) {
            return null;
        }
        UUID partyId = MEMBER_TO_PARTY.get(memberId);
        return partyId == null ? null : PARTIES.get(partyId);
    }

    private static void seedSessionsFromMember(ServerWorld world, PartyRuntime party, UUID memberId) {
        if (world == null || party == null || memberId == null) {
            return;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
        long day = TimeUtil.currentDay();
        if (data.getAcceptedDay() == day && data.getLastRewardDay() != day
                && data.getDailyChoice() != null
                && data.getDailyChoiceDay() == day
                && QuestShareProfiles.isDailyShareable(data.getDailyChoice())) {
            mergeDailyProgressIntoSession(world, memberId, data.getDailyChoice(), ensureDailySession(party, data.getDailyChoice()));
        }

        long cycle = TimeUtil.currentWeekCycle();
        if (data.getWeeklyAcceptedCycle() == cycle && data.getWeeklyRewardCycle() != cycle
                && data.getWeeklyChoice() != null
                && data.getWeeklyChoiceCycle() == cycle
                && QuestShareProfiles.isWeeklyShareable(data.getWeeklyChoice())) {
            mergeWeeklyProgressIntoSession(world, memberId, data.getWeeklyChoice(), ensureWeeklySession(party, data.getWeeklyChoice()));
        }

        StoryArcType activeStoryArc = data.getActiveStoryArc();
        if (activeStoryArc != null && QuestShareProfiles.isStoryShareable(activeStoryArc)) {
            int chapterIndex = data.getStoryChapterProgress(activeStoryArc.id());
            mergeStoryProgressIntoSession(world, memberId, activeStoryArc, chapterIndex, ensureStorySession(party, activeStoryArc, chapterIndex));
        }

        PilgrimContractType activePilgrimType = PilgrimContractType.fromId(data.getActivePilgrimContractId());
        if (activePilgrimType != null && QuestShareProfiles.isPilgrimShareable(activePilgrimType)) {
            mergePilgrimProgressIntoSession(world, memberId, activePilgrimType, ensurePilgrimSession(party, activePilgrimType));
        }
    }

    private static void queueActiveQuestOffersForJoiner(ServerWorld world, PartyRuntime party, ServerPlayerEntity player) {
        if (world == null || party == null || player == null) {
            return;
        }
        DailyQuestService.DailyQuestType dailyType = activeDailyType(party);
        if (dailyType != null) {
            queueDailyQuestOffer(world, party, player.getUuid(), dailyType, dailyOfferSourceId(world, party, player.getUuid()), true);
        }
        WeeklyQuestService.WeeklyQuestType weeklyType = activeWeeklyType(party);
        if (weeklyType != null) {
            queueWeeklyQuestOffer(world, party, player.getUuid(), weeklyType, weeklyOfferSourceId(world, party, player.getUuid()), true);
        }
        StoryArcType storyType = activeStoryType(party);
        int storyChapterIndex = activeStoryChapterIndex(party);
        if (storyType != null && storyChapterIndex >= 0) {
            queueStoryQuestOffer(world, party, player.getUuid(), storyType, storyChapterIndex, storyOfferSourceId(world, party, player.getUuid()), true);
        }
        PilgrimContractType pilgrimType = activePilgrimType(party);
        if (pilgrimType != null) {
            queuePilgrimQuestOffer(world, party, player.getUuid(), pilgrimType, pilgrimOfferSourceId(world, party, player.getUuid()), true);
        }
    }

    private static void syncPartyMembersIntoDaily(ServerWorld world,
                                                  PartyRuntime party,
                                                  DailyQuestService.DailyQuestType type,
                                                  DailyQuestDefinition definition,
                                                  UUID sourceId) {
        for (UUID memberId : party.members()) {
            ServerPlayerEntity member = world.getServer().getPlayerManager().getPlayer(memberId);
            if (memberId.equals(sourceId)) {
                continue;
            }
            if (member != null && memberHasActiveDaily(world, memberId, type)) {
                syncDailyMember(world, party, member, type, definition, sourceId);
                continue;
            }
            queueDailyQuestOffer(world, party, memberId, type, sourceId, false);
        }
    }

    private static void syncPartyMembersIntoWeekly(ServerWorld world,
                                                   PartyRuntime party,
                                                   WeeklyQuestService.WeeklyQuestType type,
                                                   WeeklyQuestDefinition definition,
                                                   UUID sourceId) {
        for (UUID memberId : party.members()) {
            ServerPlayerEntity member = world.getServer().getPlayerManager().getPlayer(memberId);
            if (memberId.equals(sourceId)) {
                continue;
            }
            if (member != null && memberHasActiveWeekly(world, memberId, type)) {
                syncWeeklyMember(world, party, member, type, definition, sourceId);
                continue;
            }
            queueWeeklyQuestOffer(world, party, memberId, type, sourceId, false);
        }
    }

    private static void syncDailyMember(ServerWorld world,
                                        PartyRuntime party,
                                        ServerPlayerEntity member,
                                        DailyQuestService.DailyQuestType type,
                                        DailyQuestDefinition definition,
                                        UUID sourceId) {
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(member.getUuid());
        long day = TimeUtil.currentDay();
        SharedQuestRuntime session = ensureDailySession(party, type);
        if (session == null) {
            return;
        }

        boolean sameQuestActive = data.getAcceptedDay() == day
                && data.getLastRewardDay() != day
                && data.getDailyChoice() == type
                && data.getDailyChoiceDay() == day;
        if (sameQuestActive) {
            mergeDailyProgressIntoSession(world, member.getUuid(), type, session);
            clearDailyOffer(party, member.getUuid());
            data.setDailyTargetProfile(sharedDailyTargetProfile(world, party, type));
            if (sourceId != null && !sourceId.equals(member.getUuid())) {
                member.sendMessage(Text.translatable("message.village-quest.party.shared_daily", nameOf(world, sourceId)).formatted(Formatting.GRAY), false);
            }
            refreshPlayer(world, member);
            return;
        }

        if (data.getLastRewardDay() == day || data.getAcceptedDay() == day) {
            if (sourceId != null && !sourceId.equals(member.getUuid())) {
                member.sendMessage(Text.translatable("message.village-quest.party.sync_skipped_daily").formatted(Formatting.GRAY), false);
            }
            return;
        }

        clearDailyOffer(party, member.getUuid());
        data.setDailyDiscovered(true);
        data.setPendingDailyOffer(false);
        data.setPendingShardOffer(false);
        data.setPendingBonusOffer(false);
        data.setDailyChoice(type);
        data.setDailyChoiceDay(day);
        data.setDailyTargetProfile(sharedDailyTargetProfile(world, party, type));
        data.clearDailyProgress();
        data.setProgressDay(day);
        data.setAcceptedDay(day);
        QuestState.get(world.getServer()).markDirty();
        session.markSynced(member.getUuid());
        if (definition != null) {
            definition.onAccepted(world, member);
        }
        member.sendMessage(Text.translatable("message.village-quest.party.shared_daily", nameOf(world, sourceId == null ? member.getUuid() : sourceId)).formatted(Formatting.GRAY), false);
        member.sendMessage(Texts.acceptedTitle(definition == null ? DailyQuestService.displayKey(type) : definition.title(), Formatting.GREEN), false);
        QuestTrackerService.enableForAcceptedQuest(world, member);
        DailyQuestService.sendCurrentProgressActionbar(world, member);
        refreshPlayer(world, member);
    }

    private static void syncWeeklyMember(ServerWorld world,
                                         PartyRuntime party,
                                         ServerPlayerEntity member,
                                         WeeklyQuestService.WeeklyQuestType type,
                                         WeeklyQuestDefinition definition,
                                         UUID sourceId) {
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(member.getUuid());
        long cycle = TimeUtil.currentWeekCycle();
        SharedQuestRuntime session = ensureWeeklySession(party, type);
        if (session == null) {
            return;
        }

        boolean sameQuestActive = data.getWeeklyAcceptedCycle() == cycle
                && data.getWeeklyRewardCycle() != cycle
                && data.getWeeklyChoice() == type
                && data.getWeeklyChoiceCycle() == cycle;
        if (sameQuestActive) {
            mergeWeeklyProgressIntoSession(world, member.getUuid(), type, session);
            clearWeeklyOffer(party, member.getUuid());
            data.setWeeklyTargetProfile(sharedWeeklyTargetProfile(world, party, type));
            if (sourceId != null && !sourceId.equals(member.getUuid())) {
                member.sendMessage(Text.translatable("message.village-quest.party.shared_weekly", nameOf(world, sourceId)).formatted(Formatting.GRAY), false);
            }
            refreshPlayer(world, member);
            return;
        }

        if (data.getWeeklyRewardCycle() == cycle || data.getWeeklyAcceptedCycle() == cycle) {
            if (sourceId != null && !sourceId.equals(member.getUuid())) {
                member.sendMessage(Text.translatable("message.village-quest.party.sync_skipped_weekly").formatted(Formatting.GRAY), false);
            }
            return;
        }

        clearWeeklyOffer(party, member.getUuid());
        data.setWeeklyChoice(type);
        data.setWeeklyChoiceCycle(cycle);
        data.setWeeklyTargetProfile(sharedWeeklyTargetProfile(world, party, type));
        data.clearWeeklyProgress();
        data.setWeeklyProgressCycle(cycle);
        data.setWeeklyAcceptedCycle(cycle);
        data.markWeeklyDiscovered(type.name());
        QuestState.get(world.getServer()).markDirty();
        session.markSynced(member.getUuid());
        if (definition != null) {
            definition.onAccepted(world, member);
        }
        member.sendMessage(Text.translatable("message.village-quest.party.shared_weekly", nameOf(world, sourceId == null ? member.getUuid() : sourceId)).formatted(Formatting.GRAY), false);
        member.sendMessage(Texts.acceptedTitle(definition == null ? WeeklyQuestService.displayName(type) : definition.title(), Formatting.GOLD), false);
        QuestTrackerService.enableForAcceptedQuest(world, member);
        refreshPlayer(world, member);
    }

    private static void syncPartyMembersIntoStory(ServerWorld world,
                                                  PartyRuntime party,
                                                  StoryArcType arcType,
                                                  int chapterIndex,
                                                  StoryChapterDefinition chapter,
                                                  UUID sourceId) {
        for (UUID memberId : party.members()) {
            ServerPlayerEntity member = world.getServer().getPlayerManager().getPlayer(memberId);
            if (memberId.equals(sourceId)) {
                continue;
            }
            if (member != null && memberHasActiveStory(world, memberId, arcType, chapterIndex)) {
                syncStoryMember(world, party, member, arcType, chapterIndex, chapter, sourceId);
                continue;
            }
            queueStoryQuestOffer(world, party, memberId, arcType, chapterIndex, sourceId, false);
        }
    }

    private static void syncPartyMembersIntoPilgrim(ServerWorld world,
                                                    PartyRuntime party,
                                                    PilgrimContractType type,
                                                    UUID sourceId) {
        for (UUID memberId : party.members()) {
            ServerPlayerEntity member = world.getServer().getPlayerManager().getPlayer(memberId);
            if (memberId.equals(sourceId)) {
                continue;
            }
            if (member != null && memberHasActivePilgrim(world, memberId, type)) {
                syncPilgrimMember(world, party, member, type, sourceId);
                continue;
            }
            queuePilgrimQuestOffer(world, party, memberId, type, sourceId, false);
        }
    }

    private static void syncStoryMember(ServerWorld world,
                                        PartyRuntime party,
                                        ServerPlayerEntity member,
                                        StoryArcType arcType,
                                        int chapterIndex,
                                        StoryChapterDefinition chapter,
                                        UUID sourceId) {
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(member.getUuid());
        SharedQuestRuntime session = ensureStorySession(party, arcType, chapterIndex);
        if (session == null) {
            return;
        }

        boolean sameChapterActive = data.getActiveStoryArc() == arcType && data.getStoryChapterProgress(arcType.id()) == chapterIndex;
        if (sameChapterActive) {
            mergeStoryProgressIntoSession(world, member.getUuid(), arcType, chapterIndex, session);
            clearStoryOffer(party, member.getUuid());
            if (sourceId != null && !sourceId.equals(member.getUuid())) {
                member.sendMessage(Text.translatable("message.village-quest.party.shared_story", nameOf(world, sourceId)).formatted(Formatting.GRAY), false);
            }
            refreshPlayer(world, member);
            return;
        }

        if (data.getActiveStoryArc() != null && data.getActiveStoryArc() != arcType) {
            if (sourceId != null && !sourceId.equals(member.getUuid())) {
                member.sendMessage(Text.translatable("message.village-quest.party.sync_skipped_story").formatted(Formatting.GRAY), false);
            }
            return;
        }

        clearStoryOffer(party, member.getUuid());
        data.clearStoryProgress();
        data.setActiveStoryArc(arcType);
        data.setStoryDiscovered(arcType.id(), true);
        data.setStoryChapterProgress(arcType.id(), chapterIndex);
        QuestState.get(world.getServer()).markDirty();
        session.markSynced(member.getUuid());
        if (chapter != null) {
            chapter.onAccepted(world, member);
        }
        member.sendMessage(Text.translatable("message.village-quest.party.shared_story", nameOf(world, sourceId == null ? member.getUuid() : sourceId)).formatted(Formatting.GRAY), false);
        member.sendMessage(Texts.acceptedTitle(chapter == null ? StoryQuestService.definition(arcType).title() : chapter.title(), Formatting.GOLD), false);
        QuestTrackerService.enableForAcceptedQuest(world, member);
        refreshPlayer(world, member);
    }

    private static void syncPilgrimMember(ServerWorld world,
                                          PartyRuntime party,
                                          ServerPlayerEntity member,
                                          PilgrimContractType type,
                                          UUID sourceId) {
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(member.getUuid());
        SharedQuestRuntime session = ensurePilgrimSession(party, type);
        if (session == null) {
            return;
        }

        boolean sameContractActive = memberHasActivePilgrim(world, member.getUuid(), type);
        if (sameContractActive) {
            mergePilgrimProgressIntoSession(world, member.getUuid(), type, session);
            clearPilgrimOffer(party, member.getUuid());
            data.setActivePilgrimTargetProfile(sharedPilgrimTargetProfile(world, party, type));
            if (sourceId != null && !sourceId.equals(member.getUuid())) {
                member.sendMessage(Text.translatable("message.village-quest.party.shared_pilgrim", nameOf(world, sourceId)).formatted(Formatting.GRAY), false);
            }
            refreshPlayer(world, member);
            return;
        }

        if (PilgrimContractType.fromId(data.getActivePilgrimContractId()) != null) {
            if (sourceId != null && !sourceId.equals(member.getUuid())) {
                member.sendMessage(Text.translatable("message.village-quest.party.sync_skipped_pilgrim").formatted(Formatting.GRAY), false);
            }
            return;
        }

        clearPilgrimOffer(party, member.getUuid());
        data.clearPilgrimProgress();
        data.setPilgrimFlag("pilgrim_contract_ready", false);
        data.setPilgrimFlag("pilgrim_contract_suppress_offer", false);
        data.setActivePilgrimContractId(type.id());
        data.setActivePilgrimTargetProfile(sharedPilgrimTargetProfile(world, party, type));
        data.setPilgrimOfferDay(TimeUtil.currentDay());
        QuestState.get(world.getServer()).markDirty();
        session.markSynced(member.getUuid());
        member.sendMessage(Text.translatable("message.village-quest.party.shared_pilgrim", nameOf(world, sourceId == null ? member.getUuid() : sourceId)).formatted(Formatting.GRAY), false);
        member.sendMessage(Texts.acceptedTitle(PilgrimContractService.title(type), Formatting.GOLD), false);
        QuestTrackerService.enableForAcceptedQuest(world, member);
        refreshPlayer(world, member);
    }

    private static void mergeStoryProgressIntoSession(ServerWorld world,
                                                      UUID memberId,
                                                      StoryArcType arcType,
                                                      int chapterIndex,
                                                      SharedQuestRuntime session) {
        if (world == null || memberId == null || session == null || session.hasSynced(memberId)) {
            return;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
        QuestShareProfiles.SharedQuestProfile profile = QuestShareProfiles.storyProfile(arcType, chapterIndex);
        if (profile == null) {
            return;
        }
        for (String key : profile.intKeys()) {
            int value = data.getStoryInt(key);
            if (value > 0) {
                session.addInt(key, value);
            }
        }
        for (String flag : data.getStoryFlags()) {
            if (profile.matchesFlag(flag)) {
                session.setFlag(flag, true);
            }
        }
        session.markSynced(memberId);
    }

    private static void mergePilgrimProgressIntoSession(ServerWorld world,
                                                        UUID memberId,
                                                        PilgrimContractType type,
                                                        SharedQuestRuntime session) {
        if (world == null || memberId == null || session == null || session.hasSynced(memberId)) {
            return;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
        QuestShareProfiles.SharedQuestProfile profile = QuestShareProfiles.pilgrimProfile(type);
        if (profile == null) {
            return;
        }
        for (String key : profile.intKeys()) {
            int value = data.getPilgrimInt(key);
            if (value > 0) {
                session.addInt(key, value);
            }
        }
        for (String flag : data.getPilgrimFlags()) {
            if (profile.matchesFlag(flag)) {
                session.setFlag(flag, true);
            }
        }
        session.markSynced(memberId);
    }

    private static void mergeDailyProgressIntoSession(ServerWorld world,
                                                      UUID memberId,
                                                      DailyQuestService.DailyQuestType type,
                                                      SharedQuestRuntime session) {
        if (world == null || memberId == null || session == null || session.hasSynced(memberId)) {
            return;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
        QuestShareProfiles.SharedQuestProfile profile = QuestShareProfiles.dailyProfile(type);
        if (profile == null) {
            return;
        }
        for (String key : profile.intKeys()) {
            int value = data.getDailyInt(key);
            if (value > 0) {
                session.addInt(key, value);
            }
        }
        for (String flag : data.getDailyFlags()) {
            if (profile.matchesFlag(flag)) {
                session.setFlag(flag, true);
            }
        }
        session.markSynced(memberId);
    }

    private static void mergeWeeklyProgressIntoSession(ServerWorld world,
                                                       UUID memberId,
                                                       WeeklyQuestService.WeeklyQuestType type,
                                                       SharedQuestRuntime session) {
        if (world == null || memberId == null || session == null || session.hasSynced(memberId)) {
            return;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
        QuestShareProfiles.SharedQuestProfile profile = QuestShareProfiles.weeklyProfile(type);
        if (profile == null) {
            return;
        }
        for (String key : profile.intKeys()) {
            int value = data.getWeeklyInt(key);
            if (value > 0) {
                session.addInt(key, value);
            }
        }
        for (String flag : data.getWeeklyFlags()) {
            if (profile.matchesFlag(flag)) {
                session.setFlag(flag, true);
            }
        }
        session.markSynced(memberId);
    }

    private static void copySharedProgressToPlayer(ServerWorld world, PartyRuntime party, UUID memberId) {
        if (world == null || party == null || memberId == null) {
            return;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
        DailyQuestService.DailyQuestType dailyType = activeDailyType(party);
        if (dailyType != null
                && data.getAcceptedDay() == TimeUtil.currentDay()
                && data.getLastRewardDay() != TimeUtil.currentDay()
                && data.getDailyChoice() == dailyType
                && data.getDailyChoiceDay() == TimeUtil.currentDay()) {
            applySharedDailyProgress(data, party.daily(), dailyType);
            data.setProgressDay(TimeUtil.currentDay());
            QuestState.get(world.getServer()).markDirty();
        }

        WeeklyQuestService.WeeklyQuestType weeklyType = activeWeeklyType(party);
        if (weeklyType != null
                && data.getWeeklyAcceptedCycle() == TimeUtil.currentWeekCycle()
                && data.getWeeklyRewardCycle() != TimeUtil.currentWeekCycle()
                && data.getWeeklyChoice() == weeklyType
                && data.getWeeklyChoiceCycle() == TimeUtil.currentWeekCycle()) {
            applySharedWeeklyProgress(data, party.weekly(), weeklyType);
            data.setWeeklyProgressCycle(TimeUtil.currentWeekCycle());
            QuestState.get(world.getServer()).markDirty();
        }

        StoryArcType storyType = activeStoryType(party);
        int storyChapterIndex = activeStoryChapterIndex(party);
        if (storyType != null
                && storyChapterIndex >= 0
                && data.getActiveStoryArc() == storyType
                && data.getStoryChapterProgress(storyType.id()) == storyChapterIndex) {
            applySharedStoryProgress(data, party.story(), storyType, storyChapterIndex);
            QuestState.get(world.getServer()).markDirty();
        }

        PilgrimContractType pilgrimType = activePilgrimType(party);
        if (pilgrimType != null
                && pilgrimType == PilgrimContractType.fromId(data.getActivePilgrimContractId())) {
            applySharedPilgrimProgress(data, party.pilgrim(), pilgrimType);
            QuestState.get(world.getServer()).markDirty();
        }
    }

    private static void applySharedDailyProgress(PlayerQuestData data,
                                                 SharedQuestRuntime session,
                                                 DailyQuestService.DailyQuestType type) {
        QuestShareProfiles.SharedQuestProfile profile = QuestShareProfiles.dailyProfile(type);
        if (data == null || session == null || profile == null) {
            return;
        }
        for (String key : profile.intKeys()) {
            data.setDailyInt(key, session.getInt(key));
        }
        Set<String> currentFlags = new HashSet<>(data.getDailyFlags());
        for (String flag : currentFlags) {
            if (profile.matchesFlag(flag)) {
                data.setDailyFlag(flag, false);
            }
        }
        for (String flag : session.flags()) {
            if (profile.matchesFlag(flag)) {
                data.setDailyFlag(flag, true);
            }
        }
    }

    private static void applySharedWeeklyProgress(PlayerQuestData data,
                                                  SharedQuestRuntime session,
                                                  WeeklyQuestService.WeeklyQuestType type) {
        QuestShareProfiles.SharedQuestProfile profile = QuestShareProfiles.weeklyProfile(type);
        if (data == null || session == null || profile == null) {
            return;
        }
        for (String key : profile.intKeys()) {
            data.setWeeklyInt(key, session.getInt(key));
        }
        Set<String> currentFlags = new HashSet<>(data.getWeeklyFlags());
        for (String flag : currentFlags) {
            if (profile.matchesFlag(flag)) {
                data.setWeeklyFlag(flag, false);
            }
        }
        for (String flag : session.flags()) {
            if (profile.matchesFlag(flag)) {
                data.setWeeklyFlag(flag, true);
            }
        }
    }

    private static void applySharedStoryProgress(PlayerQuestData data,
                                                 SharedQuestRuntime session,
                                                 StoryArcType arcType,
                                                 int chapterIndex) {
        QuestShareProfiles.SharedQuestProfile profile = QuestShareProfiles.storyProfile(arcType, chapterIndex);
        if (data == null || session == null || profile == null) {
            return;
        }
        for (String key : profile.intKeys()) {
            data.setStoryInt(key, session.getInt(key));
        }
        Set<String> currentFlags = new HashSet<>(data.getStoryFlags());
        for (String flag : currentFlags) {
            if (profile.matchesFlag(flag)) {
                data.setStoryFlag(flag, false);
            }
        }
        for (String flag : session.flags()) {
            if (profile.matchesFlag(flag)) {
                data.setStoryFlag(flag, true);
            }
        }
    }

    private static void applySharedPilgrimProgress(PlayerQuestData data,
                                                   SharedQuestRuntime session,
                                                   PilgrimContractType type) {
        QuestShareProfiles.SharedQuestProfile profile = QuestShareProfiles.pilgrimProfile(type);
        if (data == null || session == null || profile == null) {
            return;
        }
        for (String key : profile.intKeys()) {
            data.setPilgrimInt(key, session.getInt(key));
        }
        Set<String> currentFlags = new HashSet<>(data.getPilgrimFlags());
        for (String flag : currentFlags) {
            if (profile.matchesFlag(flag)) {
                data.setPilgrimFlag(flag, false);
            }
        }
        for (String flag : session.flags()) {
            if (profile.matchesFlag(flag)) {
                data.setPilgrimFlag(flag, true);
            }
        }
    }

    private static SharedQuestRuntime sharedDailySession(ServerWorld world,
                                                         UUID playerId,
                                                         DailyQuestService.DailyQuestType type) {
        PartyRuntime party = partyFor(playerId);
        if (world == null || party == null || type == null || activeDailyType(party) != type) {
            return null;
        }
        return party.daily().hasSynced(playerId) ? party.daily() : null;
    }

    private static SharedQuestRuntime sharedWeeklySession(ServerWorld world,
                                                          UUID playerId,
                                                          WeeklyQuestService.WeeklyQuestType type) {
        PartyRuntime party = partyFor(playerId);
        if (world == null || party == null || type == null || activeWeeklyType(party) != type) {
            return null;
        }
        return party.weekly().hasSynced(playerId) ? party.weekly() : null;
    }

    private static SharedQuestRuntime sharedDailySessionForType(ServerWorld world,
                                                                UUID playerId,
                                                                DailyQuestService.DailyQuestType type) {
        PartyRuntime party = partyFor(playerId);
        if (world == null || party == null || type == null || activeDailyType(party) != type) {
            return null;
        }
        return party.daily();
    }

    private static SharedQuestRuntime sharedWeeklySessionForType(ServerWorld world,
                                                                 UUID playerId,
                                                                 WeeklyQuestService.WeeklyQuestType type) {
        PartyRuntime party = partyFor(playerId);
        if (world == null || party == null || type == null || activeWeeklyType(party) != type) {
            return null;
        }
        return party.weekly();
    }

    private static SharedQuestRuntime sharedStorySession(ServerWorld world,
                                                         UUID playerId,
                                                         StoryArcType arcType,
                                                         int chapterIndex) {
        PartyRuntime party = partyFor(playerId);
        if (world == null || party == null || arcType == null || !matchesStorySession(party, arcType, chapterIndex)) {
            return null;
        }
        return party.story().hasSynced(playerId) ? party.story() : null;
    }

    private static SharedQuestRuntime sharedStorySessionForType(ServerWorld world,
                                                                UUID playerId,
                                                                StoryArcType arcType,
                                                                int chapterIndex) {
        PartyRuntime party = partyFor(playerId);
        if (world == null || party == null || arcType == null || !matchesStorySession(party, arcType, chapterIndex)) {
            return null;
        }
        return party.story();
    }

    private static SharedQuestRuntime sharedPilgrimSession(ServerWorld world,
                                                           UUID playerId,
                                                           PilgrimContractType type) {
        PartyRuntime party = partyFor(playerId);
        if (world == null || party == null || type == null || activePilgrimType(party) != type) {
            return null;
        }
        return party.pilgrim().hasSynced(playerId) ? party.pilgrim() : null;
    }

    private static SharedQuestRuntime sharedPilgrimSessionForType(ServerWorld world,
                                                                  UUID playerId,
                                                                  PilgrimContractType type) {
        PartyRuntime party = partyFor(playerId);
        if (world == null || party == null || type == null || activePilgrimType(party) != type) {
            return null;
        }
        return party.pilgrim();
    }

    private static SharedQuestRuntime ensureDailySession(PartyRuntime party, DailyQuestService.DailyQuestType type) {
        if (party == null || type == null) {
            return null;
        }
        long day = TimeUtil.currentDay();
        SharedQuestRuntime session = party.daily();
        if (!session.matches(type.name(), day)) {
            session.bind(type.name(), day);
            party.dailyOffers().clear();
        }
        return session;
    }

    private static SharedQuestRuntime ensureStorySession(PartyRuntime party, StoryArcType arcType, int chapterIndex) {
        if (party == null || arcType == null || chapterIndex < 0) {
            return null;
        }
        String questId = storySessionId(arcType, chapterIndex);
        SharedQuestRuntime session = party.story();
        if (!session.matches(questId, chapterIndex)) {
            session.bind(questId, chapterIndex);
            party.storyOffers().clear();
        }
        return session;
    }

    private static SharedQuestRuntime ensurePilgrimSession(PartyRuntime party, PilgrimContractType type) {
        if (party == null || type == null) {
            return null;
        }
        long day = TimeUtil.currentDay();
        SharedQuestRuntime session = party.pilgrim();
        if (!session.matches(type.id(), day)) {
            session.bind(type.id(), day);
            party.pilgrimOffers().clear();
        }
        return session;
    }

    private static SharedQuestRuntime ensureWeeklySession(PartyRuntime party, WeeklyQuestService.WeeklyQuestType type) {
        if (party == null || type == null) {
            return null;
        }
        long cycle = TimeUtil.currentWeekCycle();
        SharedQuestRuntime session = party.weekly();
        if (!session.matches(type.name(), cycle)) {
            session.bind(type.name(), cycle);
            party.weeklyOffers().clear();
        }
        return session;
    }

    private static DailyQuestService.DailyQuestType activeDailyType(PartyRuntime party) {
        if (party == null) {
            return null;
        }
        SharedQuestRuntime session = party.daily();
        if (!session.isCurrent(TimeUtil.currentDay())) {
            session.clear();
            return null;
        }
        try {
            return session.questId() == null ? null : DailyQuestService.DailyQuestType.valueOf(session.questId());
        } catch (IllegalArgumentException ex) {
            session.clear();
            return null;
        }
    }

    private static WeeklyQuestService.WeeklyQuestType activeWeeklyType(PartyRuntime party) {
        if (party == null) {
            return null;
        }
        SharedQuestRuntime session = party.weekly();
        if (!session.isCurrent(TimeUtil.currentWeekCycle())) {
            session.clear();
            return null;
        }
        try {
            return session.questId() == null ? null : WeeklyQuestService.WeeklyQuestType.valueOf(session.questId());
        } catch (IllegalArgumentException ex) {
            session.clear();
            return null;
        }
    }

    private static StoryArcType activeStoryType(PartyRuntime party) {
        if (party == null) {
            return null;
        }
        SharedQuestRuntime session = party.story();
        if (session.questId() == null) {
            return null;
        }
        return parseStoryType(session.questId());
    }

    private static int activeStoryChapterIndex(PartyRuntime party) {
        if (party == null) {
            return -1;
        }
        SharedQuestRuntime session = party.story();
        if (session.questId() == null) {
            return -1;
        }
        return parseStoryChapter(session.questId());
    }

    private static PilgrimContractType activePilgrimType(PartyRuntime party) {
        if (party == null) {
            return null;
        }
        SharedQuestRuntime session = party.pilgrim();
        if (!session.isCurrent(TimeUtil.currentDay())) {
            session.clear();
            return null;
        }
        return PilgrimContractType.fromId(session.questId());
    }

    private static void queueDailyQuestOffer(ServerWorld world,
                                             PartyRuntime party,
                                             UUID memberId,
                                             DailyQuestService.DailyQuestType type,
                                             UUID sourceId,
                                             boolean fromPartyJoin) {
        if (world == null || party == null || memberId == null || type == null) {
            return;
        }
        SharedQuestRuntime session = ensureDailySession(party, type);
        if (session == null || session.hasSynced(memberId)) {
            clearDailyOffer(party, memberId);
            return;
        }
        if (!canJoinDailyShare(world, memberId, type, false)) {
            clearDailyOffer(party, memberId);
            return;
        }
        if (!fromPartyJoin && memberHasActiveDaily(world, memberId, type)) {
            ServerPlayerEntity online = world.getServer().getPlayerManager().getPlayer(memberId);
            if (online != null) {
                syncDailyMember(world, party, online, type, DailyQuestGenerator.definition(type), sourceId);
            }
            return;
        }

        QuestJoinOffer offer = new QuestJoinOffer(type.name(), session.revision(), sourceId);
        QuestJoinOffer previous = party.dailyOffers().put(memberId, offer);
        if (!offer.equals(previous)) {
            ServerPlayerEntity online = world.getServer().getPlayerManager().getPlayer(memberId);
            if (online != null) {
                sendDailyJoinOffer(world, online, type, offer.sourceId());
            }
        }
    }

    private static void queueWeeklyQuestOffer(ServerWorld world,
                                              PartyRuntime party,
                                              UUID memberId,
                                              WeeklyQuestService.WeeklyQuestType type,
                                              UUID sourceId,
                                              boolean fromPartyJoin) {
        if (world == null || party == null || memberId == null || type == null) {
            return;
        }
        SharedQuestRuntime session = ensureWeeklySession(party, type);
        if (session == null || session.hasSynced(memberId)) {
            clearWeeklyOffer(party, memberId);
            return;
        }
        if (!canJoinWeeklyShare(world, memberId, type, false)) {
            clearWeeklyOffer(party, memberId);
            return;
        }
        if (!fromPartyJoin && memberHasActiveWeekly(world, memberId, type)) {
            ServerPlayerEntity online = world.getServer().getPlayerManager().getPlayer(memberId);
            if (online != null) {
                syncWeeklyMember(world, party, online, type, WeeklyQuestGenerator.definition(type), sourceId);
            }
            return;
        }

        QuestJoinOffer offer = new QuestJoinOffer(type.name(), session.revision(), sourceId);
        QuestJoinOffer previous = party.weeklyOffers().put(memberId, offer);
        if (!offer.equals(previous)) {
            ServerPlayerEntity online = world.getServer().getPlayerManager().getPlayer(memberId);
            if (online != null) {
                sendWeeklyJoinOffer(world, online, type, offer.sourceId());
            }
        }
    }

    private static void queueStoryQuestOffer(ServerWorld world,
                                             PartyRuntime party,
                                             UUID memberId,
                                             StoryArcType arcType,
                                             int chapterIndex,
                                             UUID sourceId,
                                             boolean fromPartyJoin) {
        if (world == null || party == null || memberId == null || arcType == null || chapterIndex < 0) {
            return;
        }
        SharedQuestRuntime session = ensureStorySession(party, arcType, chapterIndex);
        if (session == null || session.hasSynced(memberId)) {
            clearStoryOffer(party, memberId);
            return;
        }
        if (!canJoinStoryShare(world, memberId, arcType, chapterIndex, false)) {
            clearStoryOffer(party, memberId);
            return;
        }
        if (!fromPartyJoin && memberHasActiveStory(world, memberId, arcType, chapterIndex)) {
            ServerPlayerEntity online = world.getServer().getPlayerManager().getPlayer(memberId);
            StoryChapterDefinition chapter = StoryQuestService.definition(arcType) == null ? null : StoryQuestService.definition(arcType).chapter(chapterIndex);
            if (online != null) {
                syncStoryMember(world, party, online, arcType, chapterIndex, chapter, sourceId);
            }
            return;
        }

        QuestJoinOffer offer = new QuestJoinOffer(storySessionId(arcType, chapterIndex), session.revision(), sourceId);
        QuestJoinOffer previous = party.storyOffers().put(memberId, offer);
        if (!offer.equals(previous)) {
            ServerPlayerEntity online = world.getServer().getPlayerManager().getPlayer(memberId);
            if (online != null) {
                sendStoryJoinOffer(world, online, arcType, chapterIndex, offer.sourceId());
            }
        }
    }

    private static void queuePilgrimQuestOffer(ServerWorld world,
                                               PartyRuntime party,
                                               UUID memberId,
                                               PilgrimContractType type,
                                               UUID sourceId,
                                               boolean fromPartyJoin) {
        if (world == null || party == null || memberId == null || type == null) {
            return;
        }
        SharedQuestRuntime session = ensurePilgrimSession(party, type);
        if (session == null || session.hasSynced(memberId)) {
            clearPilgrimOffer(party, memberId);
            return;
        }
        if (!canJoinPilgrimShare(world, memberId, type, false)) {
            clearPilgrimOffer(party, memberId);
            return;
        }
        if (!fromPartyJoin && memberHasActivePilgrim(world, memberId, type)) {
            ServerPlayerEntity online = world.getServer().getPlayerManager().getPlayer(memberId);
            if (online != null) {
                syncPilgrimMember(world, party, online, type, sourceId);
            }
            return;
        }

        QuestJoinOffer offer = new QuestJoinOffer(type.id(), session.revision(), sourceId);
        QuestJoinOffer previous = party.pilgrimOffers().put(memberId, offer);
        if (!offer.equals(previous)) {
            ServerPlayerEntity online = world.getServer().getPlayerManager().getPlayer(memberId);
            if (online != null) {
                sendPilgrimJoinOffer(world, online, type, offer.sourceId());
            }
        }
    }

    private static void resendPendingQuestOffers(ServerWorld world, PartyRuntime party, ServerPlayerEntity player) {
        if (world == null || party == null || player == null) {
            return;
        }
        QuestJoinOffer dailyOffer = party.dailyOffers().get(player.getUuid());
        DailyQuestService.DailyQuestType dailyType = dailyOffer == null ? null : parseDailyType(dailyOffer.questId());
        if (dailyOffer != null && dailyType != null && isOfferStillValid(world, party, player.getUuid(), dailyOffer, dailyType)) {
            sendDailyJoinOffer(world, player, dailyType, dailyOffer.sourceId());
        }

        QuestJoinOffer weeklyOffer = party.weeklyOffers().get(player.getUuid());
        WeeklyQuestService.WeeklyQuestType weeklyType = weeklyOffer == null ? null : parseWeeklyType(weeklyOffer.questId());
        if (weeklyOffer != null && weeklyType != null && isOfferStillValid(world, party, player.getUuid(), weeklyOffer, weeklyType)) {
            sendWeeklyJoinOffer(world, player, weeklyType, weeklyOffer.sourceId());
        }

        QuestJoinOffer storyOffer = party.storyOffers().get(player.getUuid());
        StoryArcType storyType = storyOffer == null ? null : parseStoryType(storyOffer.questId());
        int storyChapterIndex = storyOffer == null ? -1 : parseStoryChapter(storyOffer.questId());
        if (storyOffer != null && storyType != null && storyChapterIndex >= 0
                && isOfferStillValid(world, party, player.getUuid(), storyOffer, storyType, storyChapterIndex)) {
            sendStoryJoinOffer(world, player, storyType, storyChapterIndex, storyOffer.sourceId());
        }

        QuestJoinOffer pilgrimOffer = party.pilgrimOffers().get(player.getUuid());
        PilgrimContractType pilgrimType = pilgrimOffer == null ? null : PilgrimContractType.fromId(pilgrimOffer.questId());
        if (pilgrimOffer != null && pilgrimType != null && isOfferStillValid(world, party, player.getUuid(), pilgrimOffer, pilgrimType)) {
            sendPilgrimJoinOffer(world, player, pilgrimType, pilgrimOffer.sourceId());
        }
    }

    private static void sendDailyJoinOffer(ServerWorld world,
                                           ServerPlayerEntity player,
                                           DailyQuestService.DailyQuestType type,
                                           UUID sourceId) {
        DailyQuestDefinition definition = DailyQuestGenerator.definition(type);
        Text title = definition == null ? DailyQuestService.displayKey(type) : definition.title();
        Text progress = definition == null
                ? Text.empty()
                : definition.progressLine(world, sourceId != null ? sourceId : player.getUuid()).copy().formatted(Formatting.GRAY);
        player.sendMessage(Text.translatable(
                "message.village-quest.party.offer.daily.prompt",
                title,
                nameOf(world, sourceId == null ? player.getUuid() : sourceId)
        ).formatted(Formatting.GRAY), false);
        if (!progress.getString().isBlank()) {
            player.sendMessage(progress, false);
        }
        player.sendMessage(joinOfferButtons("/vq party share daily accept", "/vq party share daily decline"), false);
    }

    private static void sendWeeklyJoinOffer(ServerWorld world,
                                            ServerPlayerEntity player,
                                            WeeklyQuestService.WeeklyQuestType type,
                                            UUID sourceId) {
        WeeklyQuestDefinition definition = WeeklyQuestGenerator.definition(type);
        Text title = definition == null ? WeeklyQuestService.displayName(type) : definition.title();
        Text progress = Text.empty();
        if (definition != null) {
            List<Text> lines = definition.progressLines(world, sourceId != null ? sourceId : player.getUuid());
            if (!lines.isEmpty()) {
                progress = lines.getFirst().copy().formatted(Formatting.GRAY);
            }
        }
        player.sendMessage(Text.translatable(
                "message.village-quest.party.offer.weekly.prompt",
                title,
                nameOf(world, sourceId == null ? player.getUuid() : sourceId)
        ).formatted(Formatting.GRAY), false);
        if (!progress.getString().isBlank()) {
            player.sendMessage(progress, false);
        }
        player.sendMessage(joinOfferButtons("/vq party share weekly accept", "/vq party share weekly decline"), false);
    }

    private static void sendStoryJoinOffer(ServerWorld world,
                                           ServerPlayerEntity player,
                                           StoryArcType arcType,
                                           int chapterIndex,
                                           UUID sourceId) {
        StoryChapterDefinition chapter = StoryQuestService.definition(arcType) == null ? null : StoryQuestService.definition(arcType).chapter(chapterIndex);
        Text title = chapter == null ? StoryQuestService.definition(arcType).title() : chapter.title();
        Text progress = Text.empty();
        if (chapter != null) {
            List<Text> lines = chapter.progressLines(world, sourceId != null ? sourceId : player.getUuid());
            if (!lines.isEmpty()) {
                progress = lines.getFirst().copy().formatted(Formatting.GRAY);
            }
        }
        player.sendMessage(Text.translatable(
                "message.village-quest.party.offer.story.prompt",
                title,
                nameOf(world, sourceId == null ? player.getUuid() : sourceId)
        ).formatted(Formatting.GRAY), false);
        if (!progress.getString().isBlank()) {
            player.sendMessage(progress, false);
        }
        player.sendMessage(joinOfferButtons("/vq party share story accept", "/vq party share story decline"), false);
    }

    private static void sendPilgrimJoinOffer(ServerWorld world,
                                             ServerPlayerEntity player,
                                             PilgrimContractType type,
                                             UUID sourceId) {
        Text progress = PilgrimContractService.previewProgressLine(world, sourceId != null ? sourceId : player.getUuid(), type).copy().formatted(Formatting.GRAY);
        player.sendMessage(Text.translatable(
                "message.village-quest.party.offer.pilgrim.prompt",
                PilgrimContractService.title(type),
                nameOf(world, sourceId == null ? player.getUuid() : sourceId)
        ).formatted(Formatting.GRAY), false);
        if (!progress.getString().isBlank()) {
            player.sendMessage(progress, false);
        }
        player.sendMessage(joinOfferButtons("/vq party share pilgrim accept", "/vq party share pilgrim decline"), false);
    }

    private static MutableText joinOfferButtons(String acceptCommand, String declineCommand) {
        MutableText accept = Text.translatable("message.village-quest.party.offer.accept")
                .styled(style -> style.withColor(0x57A550).withClickEvent(new ClickEvent.RunCommand(acceptCommand)));
        MutableText decline = Text.translatable("message.village-quest.party.offer.decline")
                .styled(style -> style.withColor(0xA55252).withClickEvent(new ClickEvent.RunCommand(declineCommand)));
        return accept.append(Text.literal(" ")).append(decline);
    }

    private static boolean memberHasActiveDaily(ServerWorld world, UUID memberId, DailyQuestService.DailyQuestType type) {
        if (world == null || memberId == null || type == null) {
            return false;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
        long day = TimeUtil.currentDay();
        return data.getAcceptedDay() == day && data.getLastRewardDay() != day && data.getDailyChoice() == type && data.getDailyChoiceDay() == day;
    }

    private static boolean memberHasActiveWeekly(ServerWorld world, UUID memberId, WeeklyQuestService.WeeklyQuestType type) {
        if (world == null || memberId == null || type == null) {
            return false;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
        long cycle = TimeUtil.currentWeekCycle();
        return data.getWeeklyAcceptedCycle() == cycle && data.getWeeklyRewardCycle() != cycle && data.getWeeklyChoice() == type && data.getWeeklyChoiceCycle() == cycle;
    }

    private static boolean memberHasActiveStory(ServerWorld world, UUID memberId, StoryArcType arcType, int chapterIndex) {
        if (world == null || memberId == null || arcType == null || chapterIndex < 0) {
            return false;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
        return data.getActiveStoryArc() == arcType && data.getStoryChapterProgress(arcType.id()) == chapterIndex;
    }

    private static boolean memberHasActivePilgrim(ServerWorld world, UUID memberId, PilgrimContractType type) {
        if (world == null || memberId == null || type == null) {
            return false;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
        return type == PilgrimContractType.fromId(data.getActivePilgrimContractId());
    }

    private static boolean canJoinDailyShare(ServerWorld world, UUID memberId, DailyQuestService.DailyQuestType type, boolean notify) {
        if (world == null || memberId == null || type == null) {
            return false;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
        long day = TimeUtil.currentDay();
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(memberId);
        if (data.getLastRewardDay() == day) {
            if (notify && player != null) {
                player.sendMessage(Text.translatable("message.village-quest.party.offer.daily.blocked.completed").formatted(Formatting.RED), false);
            }
            return false;
        }
        if (data.getAcceptedDay() == day && (data.getDailyChoice() != type || data.getDailyChoiceDay() != day)) {
            if (notify && player != null) {
                player.sendMessage(Text.translatable("message.village-quest.party.offer.daily.blocked.active").formatted(Formatting.RED), false);
            }
            return false;
        }
        return true;
    }

    private static boolean canJoinWeeklyShare(ServerWorld world, UUID memberId, WeeklyQuestService.WeeklyQuestType type, boolean notify) {
        if (world == null || memberId == null || type == null) {
            return false;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
        long cycle = TimeUtil.currentWeekCycle();
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(memberId);
        if (data.getWeeklyRewardCycle() == cycle) {
            if (notify && player != null) {
                player.sendMessage(Text.translatable("message.village-quest.party.offer.weekly.blocked.completed").formatted(Formatting.RED), false);
            }
            return false;
        }
        if (data.getWeeklyAcceptedCycle() == cycle && (data.getWeeklyChoice() != type || data.getWeeklyChoiceCycle() != cycle)) {
            if (notify && player != null) {
                player.sendMessage(Text.translatable("message.village-quest.party.offer.weekly.blocked.active").formatted(Formatting.RED), false);
            }
            return false;
        }
        return true;
    }

    private static boolean canJoinStoryShare(ServerWorld world,
                                             UUID memberId,
                                             StoryArcType arcType,
                                             int chapterIndex,
                                             boolean notify) {
        if (world == null || memberId == null || arcType == null || chapterIndex < 0) {
            return false;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(memberId);
        if (data.hasStoryCompleted(arcType.id())) {
            if (notify && player != null) {
                player.sendMessage(Text.translatable("message.village-quest.party.offer.story.blocked.completed").formatted(Formatting.RED), false);
            }
            return false;
        }
        StoryArcType activeArc = data.getActiveStoryArc();
        if (activeArc != null && (activeArc != arcType || data.getStoryChapterProgress(arcType.id()) != chapterIndex)) {
            if (notify && player != null) {
                player.sendMessage(Text.translatable("message.village-quest.party.offer.story.blocked.active").formatted(Formatting.RED), false);
            }
            return false;
        }
        if (activeArc == null && StoryQuestService.isStoryCooldownActive(world, memberId)) {
            if (notify && player != null) {
                player.sendMessage(Text.translatable("message.village-quest.party.offer.story.blocked.cooldown").formatted(Formatting.RED), false);
            }
            return false;
        }
        StoryArcType available = StoryQuestService.availableArcTypeIgnoringCooldown(world, memberId);
        if (activeArc == null && available != arcType) {
            if (notify && player != null) {
                player.sendMessage(Text.translatable("message.village-quest.party.offer.story.blocked.sequence").formatted(Formatting.RED), false);
            }
            return false;
        }
        if (data.getStoryChapterProgress(arcType.id()) != chapterIndex) {
            if (notify && player != null) {
                player.sendMessage(Text.translatable("message.village-quest.party.offer.story.blocked.sequence").formatted(Formatting.RED), false);
            }
            return false;
        }
        return true;
    }

    private static boolean canJoinPilgrimShare(ServerWorld world,
                                               UUID memberId,
                                               PilgrimContractType type,
                                               boolean notify) {
        if (world == null || memberId == null || type == null) {
            return false;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(memberId);
        PilgrimContractType activeType = PilgrimContractType.fromId(data.getActivePilgrimContractId());
        if (activeType != null && activeType != type) {
            if (notify && player != null) {
                player.sendMessage(Text.translatable("message.village-quest.party.offer.pilgrim.blocked.active").formatted(Formatting.RED), false);
            }
            return false;
        }
        if (activeType == null && data.hasPilgrimFlag("pilgrim_contract_suppress_offer")) {
            if (notify && player != null) {
                player.sendMessage(Text.translatable("message.village-quest.party.offer.pilgrim.blocked.completed").formatted(Formatting.RED), false);
            }
            return false;
        }
        return true;
    }

    private static UUID dailyOfferSourceId(ServerWorld world, PartyRuntime party, UUID fallbackMemberId) {
        if (party == null) {
            return fallbackMemberId;
        }
        for (UUID memberId : party.daily().syncedMembers()) {
            if (!memberId.equals(fallbackMemberId) && memberHasActiveDaily(world, memberId, activeDailyType(party))) {
                return memberId;
            }
        }
        return party.leaderId();
    }

    private static UUID weeklyOfferSourceId(ServerWorld world, PartyRuntime party, UUID fallbackMemberId) {
        if (party == null) {
            return fallbackMemberId;
        }
        for (UUID memberId : party.weekly().syncedMembers()) {
            if (!memberId.equals(fallbackMemberId) && memberHasActiveWeekly(world, memberId, activeWeeklyType(party))) {
                return memberId;
            }
        }
        return party.leaderId();
    }

    private static de.quest.quest.repeatable.RepeatableTargetProfile sharedDailyTargetProfile(ServerWorld world,
                                                                                              PartyRuntime party,
                                                                                              DailyQuestService.DailyQuestType type) {
        if (world == null || party == null || type == null) {
            return de.quest.quest.repeatable.RepeatableTargetProfile.NORMAL;
        }
        for (UUID memberId : party.daily().syncedMembers()) {
            if (!memberHasActiveDaily(world, memberId, type)) {
                continue;
            }
            return QuestState.get(world.getServer()).getPlayerData(memberId).getDailyTargetProfile();
        }
        return de.quest.quest.repeatable.RepeatableTargetProfile.NORMAL;
    }

    private static de.quest.quest.repeatable.RepeatableTargetProfile sharedWeeklyTargetProfile(ServerWorld world,
                                                                                               PartyRuntime party,
                                                                                               WeeklyQuestService.WeeklyQuestType type) {
        if (world == null || party == null || type == null) {
            return de.quest.quest.repeatable.RepeatableTargetProfile.NORMAL;
        }
        for (UUID memberId : party.weekly().syncedMembers()) {
            if (!memberHasActiveWeekly(world, memberId, type)) {
                continue;
            }
            return QuestState.get(world.getServer()).getPlayerData(memberId).getWeeklyTargetProfile();
        }
        return de.quest.quest.repeatable.RepeatableTargetProfile.NORMAL;
    }

    private static de.quest.quest.repeatable.RepeatableTargetProfile sharedPilgrimTargetProfile(ServerWorld world,
                                                                                                PartyRuntime party,
                                                                                                PilgrimContractType type) {
        if (world == null || party == null || type == null) {
            return de.quest.quest.repeatable.RepeatableTargetProfile.NORMAL;
        }
        for (UUID memberId : party.pilgrim().syncedMembers()) {
            if (!memberHasActivePilgrim(world, memberId, type)) {
                continue;
            }
            return QuestState.get(world.getServer()).getPlayerData(memberId).getActivePilgrimTargetProfile();
        }
        return de.quest.quest.repeatable.RepeatableTargetProfile.NORMAL;
    }

    private static UUID storyOfferSourceId(ServerWorld world, PartyRuntime party, UUID fallbackMemberId) {
        if (party == null) {
            return fallbackMemberId;
        }
        StoryArcType activeType = activeStoryType(party);
        int chapterIndex = activeStoryChapterIndex(party);
        for (UUID memberId : party.story().syncedMembers()) {
            if (!memberId.equals(fallbackMemberId) && memberHasActiveStory(world, memberId, activeType, chapterIndex)) {
                return memberId;
            }
        }
        return party.leaderId();
    }

    private static UUID pilgrimOfferSourceId(ServerWorld world, PartyRuntime party, UUID fallbackMemberId) {
        if (party == null) {
            return fallbackMemberId;
        }
        PilgrimContractType activeType = activePilgrimType(party);
        for (UUID memberId : party.pilgrim().syncedMembers()) {
            if (!memberId.equals(fallbackMemberId) && memberHasActivePilgrim(world, memberId, activeType)) {
                return memberId;
            }
        }
        return party.leaderId();
    }

    private static QuestJoinOffer clearDailyOffer(PartyRuntime party, UUID memberId) {
        return party == null || memberId == null ? null : party.dailyOffers().remove(memberId);
    }

    private static QuestJoinOffer clearWeeklyOffer(PartyRuntime party, UUID memberId) {
        return party == null || memberId == null ? null : party.weeklyOffers().remove(memberId);
    }

    private static QuestJoinOffer clearStoryOffer(PartyRuntime party, UUID memberId) {
        return party == null || memberId == null ? null : party.storyOffers().remove(memberId);
    }

    private static QuestJoinOffer clearPilgrimOffer(PartyRuntime party, UUID memberId) {
        return party == null || memberId == null ? null : party.pilgrimOffers().remove(memberId);
    }

    private static boolean isOfferStillValid(ServerWorld world,
                                             PartyRuntime party,
                                             UUID memberId,
                                             QuestJoinOffer offer,
                                             DailyQuestService.DailyQuestType type) {
        SharedQuestRuntime session = party == null ? null : party.daily();
        return offer != null
                && session != null
                && session.matches(offer.questId(), offer.revision())
                && activeDailyType(party) == type
                && !session.hasSynced(memberId)
                && canJoinDailyShare(world, memberId, type, false);
    }

    private static boolean isOfferStillValid(ServerWorld world,
                                             PartyRuntime party,
                                             UUID memberId,
                                             QuestJoinOffer offer,
                                             WeeklyQuestService.WeeklyQuestType type) {
        SharedQuestRuntime session = party == null ? null : party.weekly();
        return offer != null
                && session != null
                && session.matches(offer.questId(), offer.revision())
                && activeWeeklyType(party) == type
                && !session.hasSynced(memberId)
                && canJoinWeeklyShare(world, memberId, type, false);
    }

    private static boolean isOfferStillValid(ServerWorld world,
                                             PartyRuntime party,
                                             UUID memberId,
                                             QuestJoinOffer offer,
                                             StoryArcType arcType,
                                             int chapterIndex) {
        SharedQuestRuntime session = party == null ? null : party.story();
        return offer != null
                && session != null
                && session.matches(storySessionId(arcType, chapterIndex), offer.revision())
                && matchesStorySession(party, arcType, chapterIndex)
                && !session.hasSynced(memberId)
                && canJoinStoryShare(world, memberId, arcType, chapterIndex, false);
    }

    private static boolean isOfferStillValid(ServerWorld world,
                                             PartyRuntime party,
                                             UUID memberId,
                                             QuestJoinOffer offer,
                                             PilgrimContractType type) {
        SharedQuestRuntime session = party == null ? null : party.pilgrim();
        return offer != null
                && session != null
                && session.matches(offer.questId(), offer.revision())
                && activePilgrimType(party) == type
                && !session.hasSynced(memberId)
                && canJoinPilgrimShare(world, memberId, type, false);
    }

    private static void removeDisconnectedMember(ServerWorld world, PartyRuntime party, UUID memberId) {
        if (world == null || party == null || memberId == null) {
            return;
        }
        party.disconnectDeadlines().remove(memberId);
        leaveInternal(world, memberId, false, false);
        PartyRuntime updated = PARTIES.get(party.id());
        if (updated != null) {
            broadcast(world, updated, Text.translatable("message.village-quest.party.member_removed_timeout", nameOf(world, memberId)).formatted(Formatting.GRAY));
        }
    }

    private static void storePersistentState(MinecraftServer server) {
        if (server == null || !isEnabled(server)) {
            return;
        }
        QuestState.get(server).setQuestPartyState(writePersistentState());
    }

    private static NbtCompound writePersistentState() {
        NbtCompound root = new NbtCompound();
        NbtList parties = new NbtList();
        for (PartyRuntime party : PARTIES.values()) {
            NbtCompound partyNbt = new NbtCompound();
            partyNbt.putString("id", party.id().toString());
            partyNbt.putString("leader", party.leaderId().toString());
            NbtList members = new NbtList();
            for (UUID memberId : party.members()) {
                NbtCompound memberNbt = new NbtCompound();
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

        NbtList invites = new NbtList();
        for (var entry : INVITES.entrySet()) {
            PartyInvite invite = entry.getValue();
            NbtCompound inviteNbt = new NbtCompound();
            inviteNbt.putString("target", entry.getKey().toString());
            inviteNbt.putString("party", invite.partyId().toString());
            inviteNbt.putString("inviter", invite.inviterId().toString());
            inviteNbt.putLong("expiresAt", invite.expiresAtMillis());
            invites.add(inviteNbt);
        }
        root.put("invites", invites);
        return root;
    }

    private static NbtCompound writeSharedSession(SharedQuestRuntime session) {
        NbtCompound sessionNbt = new NbtCompound();
        if (session == null || session.questId() == null) {
            return sessionNbt;
        }
        sessionNbt.putString("questId", session.questId());
        sessionNbt.putLong("revision", session.revision());
        NbtList ints = new NbtList();
        for (var entry : session.intState().entrySet()) {
            NbtCompound item = new NbtCompound();
            item.putString("key", entry.getKey());
            item.putInt("value", entry.getValue());
            ints.add(item);
        }
        sessionNbt.put("ints", ints);
        NbtList flags = new NbtList();
        for (String flag : session.flags()) {
            NbtCompound item = new NbtCompound();
            item.putString("key", flag);
            flags.add(item);
        }
        sessionNbt.put("flags", flags);
        NbtList synced = new NbtList();
        for (UUID memberId : session.syncedMembers()) {
            NbtCompound item = new NbtCompound();
            item.putString("id", memberId.toString());
            synced.add(item);
        }
        sessionNbt.put("synced", synced);
        return sessionNbt;
    }

    private static void readSharedSession(NbtCompound sessionNbt, SharedQuestRuntime session) {
        if (session == null || sessionNbt == null || sessionNbt.isEmpty()) {
            return;
        }
        String questId = sessionNbt.getString("questId", "");
        long revision = sessionNbt.getLong("revision", Long.MIN_VALUE);
        if (questId.isEmpty() || revision == Long.MIN_VALUE) {
            return;
        }
        session.bind(questId, revision);
        NbtList ints = sessionNbt.getListOrEmpty("ints");
        for (int i = 0; i < ints.size(); i++) {
            NbtCompound item = ints.getCompoundOrEmpty(i);
            session.setInt(item.getString("key", ""), item.getInt("value", 0));
        }
        NbtList flags = sessionNbt.getListOrEmpty("flags");
        for (int i = 0; i < flags.size(); i++) {
            String key = flags.getCompoundOrEmpty(i).getString("key", "");
            if (!key.isEmpty()) {
                session.setFlag(key, true);
            }
        }
        NbtList synced = sessionNbt.getListOrEmpty("synced");
        for (int i = 0; i < synced.size(); i++) {
            UUID memberId = parseUuid(synced.getCompoundOrEmpty(i).getString("id", ""));
            if (memberId != null) {
                session.markSynced(memberId);
            }
        }
    }

    private static NbtList writeOfferMap(Map<UUID, QuestJoinOffer> offers) {
        NbtList list = new NbtList();
        for (var entry : offers.entrySet()) {
            NbtCompound item = new NbtCompound();
            item.putString("target", entry.getKey().toString());
            item.putString("questId", entry.getValue().questId());
            item.putLong("revision", entry.getValue().revision());
            if (entry.getValue().sourceId() != null) {
                item.putString("source", entry.getValue().sourceId().toString());
            }
            list.add(item);
        }
        return list;
    }

    private static void readOfferMap(NbtList list, Map<UUID, QuestJoinOffer> offers) {
        for (int i = 0; i < list.size(); i++) {
            NbtCompound item = list.getCompoundOrEmpty(i);
            UUID targetId = parseUuid(item.getString("target", ""));
            String questId = item.getString("questId", "");
            long revision = item.getLong("revision", Long.MIN_VALUE);
            UUID sourceId = parseUuid(item.getString("source", ""));
            if (targetId == null || questId.isEmpty() || revision == Long.MIN_VALUE) {
                continue;
            }
            offers.put(targetId, new QuestJoinOffer(questId, revision, sourceId));
        }
    }

    private static NbtList writeDisconnectMap(Map<UUID, Long> disconnects) {
        NbtList list = new NbtList();
        for (var entry : disconnects.entrySet()) {
            NbtCompound item = new NbtCompound();
            item.putString("id", entry.getKey().toString());
            item.putLong("until", entry.getValue());
            list.add(item);
        }
        return list;
    }

    private static void readDisconnectMap(NbtList list, Map<UUID, Long> disconnects) {
        for (int i = 0; i < list.size(); i++) {
            NbtCompound item = list.getCompoundOrEmpty(i);
            UUID memberId = parseUuid(item.getString("id", ""));
            long until = item.getLong("until", 0L);
            if (memberId != null && until > 0L) {
                disconnects.put(memberId, until);
            }
        }
    }

    private static DailyQuestService.DailyQuestType parseDailyType(String questId) {
        if (questId == null || questId.isEmpty()) {
            return null;
        }
        try {
            return DailyQuestService.DailyQuestType.valueOf(questId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static WeeklyQuestService.WeeklyQuestType parseWeeklyType(String questId) {
        if (questId == null || questId.isEmpty()) {
            return null;
        }
        try {
            return WeeklyQuestService.WeeklyQuestType.valueOf(questId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String storySessionId(StoryArcType arcType, int chapterIndex) {
        return arcType == null ? "" : arcType.id() + "#" + chapterIndex;
    }

    private static boolean matchesStorySession(PartyRuntime party, StoryArcType arcType, int chapterIndex) {
        if (party == null || arcType == null || chapterIndex < 0) {
            return false;
        }
        SharedQuestRuntime session = party.story();
        return session.matches(storySessionId(arcType, chapterIndex), chapterIndex);
    }

    private static StoryArcType parseStoryType(String questId) {
        if (questId == null || questId.isEmpty()) {
            return null;
        }
        int split = questId.indexOf('#');
        return StoryArcType.fromId(split >= 0 ? questId.substring(0, split) : questId);
    }

    private static int parseStoryChapter(String questId) {
        if (questId == null || questId.isEmpty()) {
            return -1;
        }
        int split = questId.indexOf('#');
        if (split < 0 || split + 1 >= questId.length()) {
            return -1;
        }
        try {
            return Integer.parseInt(questId.substring(split + 1));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String formatRemainingMinutes(long millis) {
        long minutes = Math.max(1L, (long) Math.ceil(millis / 60000.0d));
        return Long.toString(minutes);
    }

    private static List<ServerPlayerEntity> orderedOnlineMembers(ServerWorld world, UUID playerId) {
        List<ServerPlayerEntity> members = new ArrayList<>();
        if (world == null) {
            return members;
        }
        PartyRuntime party = partyFor(playerId);
        if (party == null) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
            if (player != null) {
                members.add(player);
            }
            return members;
        }

        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(playerId);
        if (owner != null) {
            members.add(owner);
        }
        for (UUID memberId : party.members()) {
            if (memberId.equals(playerId)) {
                continue;
            }
            ServerPlayerEntity member = world.getServer().getPlayerManager().getPlayer(memberId);
            if (member != null) {
                members.add(member);
            }
        }
        return members;
    }

    private static List<ServerPlayerEntity> onlineMembers(ServerWorld world, List<UUID> memberIds) {
        List<ServerPlayerEntity> members = new ArrayList<>();
        if (world == null || memberIds == null) {
            return members;
        }
        for (UUID memberId : memberIds) {
            ServerPlayerEntity member = world.getServer().getPlayerManager().getPlayer(memberId);
            if (member != null) {
                members.add(member);
            }
        }
        return members;
    }

    private static MutableText buildInviteMessage(ServerPlayerEntity inviter) {
        MutableText accept = Text.translatable("message.village-quest.party.invite.accept")
                .styled(style -> style.withColor(0x57A550).withClickEvent(new ClickEvent.RunCommand("/vq party accept")));
        MutableText decline = Text.translatable("message.village-quest.party.invite.decline")
                .styled(style -> style.withColor(0xA55252).withClickEvent(new ClickEvent.RunCommand("/vq party decline")));
        return Text.translatable("message.village-quest.party.invite.receive", inviter.getDisplayName())
                .formatted(Formatting.GRAY)
                .append(Text.literal(" "))
                .append(accept)
                .append(Text.literal(" "))
                .append(decline);
    }

    private static Text nameOf(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return Text.translatable("message.village-quest.party.unknown");
        }
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
        return player == null ? Text.literal(shortId(playerId)).formatted(Formatting.GRAY) : player.getDisplayName().copy();
    }

    private static String shortId(UUID playerId) {
        String value = playerId == null ? "player" : playerId.toString().replace("-", "");
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private static void broadcast(ServerWorld world, PartyRuntime party, Text message) {
        if (world == null || party == null || message == null) {
            return;
        }
        for (UUID memberId : party.members()) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(memberId);
            if (player != null) {
                player.sendMessage(message, false);
            }
        }
    }

    private static void refreshPartyUi(ServerWorld world, PartyRuntime party) {
        if (world == null || party == null) {
            return;
        }
        for (UUID memberId : party.members()) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(memberId);
            if (player != null) {
                refreshPlayer(world, player);
            }
        }
    }

    private static void refreshPlayer(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return;
        }
        QuestBookHelper.refreshQuestBook(world, player);
        QuestTrackerService.refresh(world, player);
        QuestMasterUiService.refreshIfOpen(world, player);
        PilgrimService.refreshIfTrading(world, player);
    }

    private static void refreshOpenUi(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        QuestMasterUiService.refreshIfOpen((ServerWorld) player.getEntityWorld(), player);
    }

    private static void removeInvitesForParty(UUID partyId) {
        if (partyId == null) {
            return;
        }
        List<UUID> remove = new ArrayList<>();
        for (var entry : INVITES.entrySet()) {
            if (entry.getValue().partyId().equals(partyId)) {
                remove.add(entry.getKey());
            }
        }
        for (UUID targetId : remove) {
            INVITES.remove(targetId);
        }
    }

    private static void removePendingInviteFor(UUID playerId) {
        if (playerId == null) {
            return;
        }
        INVITES.remove(playerId);
        List<UUID> remove = new ArrayList<>();
        for (var entry : INVITES.entrySet()) {
            if (entry.getValue().inviterId().equals(playerId)) {
                remove.add(entry.getKey());
            }
        }
        for (UUID targetId : remove) {
            INVITES.remove(targetId);
        }
    }

    private static void cleanupInvite(UUID playerId) {
        PartyInvite invite = INVITES.get(playerId);
        if (invite != null && invite.expiresAtMillis() <= System.currentTimeMillis()) {
            INVITES.remove(playerId);
        }
    }

    public record PartyMemberView(String playerId, Text name, boolean leader, boolean self) {}

    public record PartyInviteCandidateView(String playerId, Text name, Text status, boolean inviteable) {}

    public record PartySnapshot(boolean hasParty,
                                boolean leader,
                                Text summary,
                                List<PartyMemberView> members,
                                List<PartyInviteCandidateView> candidates) {
        private static PartySnapshot empty() {
            return new PartySnapshot(false, false, Text.empty(), List.of(), List.of());
        }
    }

    private record ExpiryTarget(UUID partyId, UUID memberId) {}

    private record PartyInvite(UUID partyId, UUID inviterId, long expiresAtMillis) {}

    private record QuestJoinOffer(String questId, long revision, UUID sourceId) {}

    private static final class PartyRuntime {
        private final UUID id;
        private UUID leaderId;
        private final LinkedHashSet<UUID> members = new LinkedHashSet<>();
        private final SharedQuestRuntime daily = new SharedQuestRuntime();
        private final SharedQuestRuntime weekly = new SharedQuestRuntime();
        private final SharedQuestRuntime story = new SharedQuestRuntime();
        private final SharedQuestRuntime pilgrim = new SharedQuestRuntime();
        private final Map<UUID, QuestJoinOffer> dailyOffers = new HashMap<>();
        private final Map<UUID, QuestJoinOffer> weeklyOffers = new HashMap<>();
        private final Map<UUID, QuestJoinOffer> storyOffers = new HashMap<>();
        private final Map<UUID, QuestJoinOffer> pilgrimOffers = new HashMap<>();
        private final Map<UUID, Long> disconnectDeadlines = new HashMap<>();

        private PartyRuntime(UUID id, UUID leaderId) {
            this.id = id;
            this.leaderId = leaderId;
        }

        private UUID id() {
            return id;
        }

        private UUID leaderId() {
            return leaderId;
        }

        private void setLeaderId(UUID leaderId) {
            this.leaderId = leaderId;
        }

        private LinkedHashSet<UUID> members() {
            return members;
        }

        private SharedQuestRuntime daily() {
            return daily;
        }

        private SharedQuestRuntime weekly() {
            return weekly;
        }

        private SharedQuestRuntime story() {
            return story;
        }

        private SharedQuestRuntime pilgrim() {
            return pilgrim;
        }

        private Map<UUID, QuestJoinOffer> dailyOffers() {
            return dailyOffers;
        }

        private Map<UUID, QuestJoinOffer> weeklyOffers() {
            return weeklyOffers;
        }

        private Map<UUID, QuestJoinOffer> storyOffers() {
            return storyOffers;
        }

        private Map<UUID, QuestJoinOffer> pilgrimOffers() {
            return pilgrimOffers;
        }

        private Map<UUID, Long> disconnectDeadlines() {
            return disconnectDeadlines;
        }
    }

    private static final class SharedQuestRuntime {
        private String questId;
        private long revision = Long.MIN_VALUE;
        private final Map<String, Integer> intState = new HashMap<>();
        private final Set<String> flags = new HashSet<>();
        private final Set<UUID> syncedMembers = new HashSet<>();

        private String questId() {
            return questId;
        }

        private long revision() {
            return revision;
        }

        private Map<String, Integer> intState() {
            return intState;
        }

        private Set<String> flags() {
            return flags;
        }

        private Set<UUID> syncedMembers() {
            return syncedMembers;
        }

        private boolean matches(String questId, long revision) {
            return Objects.equals(this.questId, questId) && this.revision == revision;
        }

        private boolean isCurrent(long revision) {
            return this.questId != null && this.revision == revision;
        }

        private void bind(String questId, long revision) {
            this.questId = questId;
            this.revision = revision;
            this.intState.clear();
            this.flags.clear();
            this.syncedMembers.clear();
        }

        private void clear() {
            this.questId = null;
            this.revision = Long.MIN_VALUE;
            this.intState.clear();
            this.flags.clear();
            this.syncedMembers.clear();
        }

        private int getInt(String key) {
            return key == null ? 0 : intState.getOrDefault(key, 0);
        }

        private void setInt(String key, int value) {
            if (key == null || key.isEmpty()) {
                return;
            }
            if (value == 0) {
                intState.remove(key);
            } else {
                intState.put(key, value);
            }
        }

        private void addInt(String key, int amount) {
            if (amount == 0) {
                return;
            }
            setInt(key, getInt(key) + amount);
        }

        private boolean hasFlag(String key) {
            return key != null && flags.contains(key);
        }

        private void setFlag(String key, boolean enabled) {
            if (key == null || key.isEmpty()) {
                return;
            }
            if (enabled) {
                flags.add(key);
            } else {
                flags.remove(key);
            }
        }

        private boolean hasSynced(UUID memberId) {
            return memberId != null && syncedMembers.contains(memberId);
        }

        private void markSynced(UUID memberId) {
            if (memberId != null) {
                syncedMembers.add(memberId);
            }
        }

        private void unmarkSynced(UUID memberId) {
            if (memberId != null) {
                syncedMembers.remove(memberId);
            }
        }
    }
}
