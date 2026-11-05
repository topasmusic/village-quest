package de.quest.party;

import static de.quest.party.QuestPartySessions.*;
import static de.quest.party.QuestPartyMessaging.*;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.pilgrim.PilgrimContractService;
import de.quest.pilgrim.PilgrimContractType;
import de.quest.pilgrim.PilgrimService;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestGenerator;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.daily.FirstDailyChoiceService;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryLane;
import de.quest.quest.story.StoryChapterDefinition;
import de.quest.quest.story.StoryQuestKeys;
import de.quest.quest.story.StoryQuestService;
import de.quest.quest.weekly.WeeklyQuestDefinition;
import de.quest.quest.weekly.WeeklyQuestGenerator;
import de.quest.quest.weekly.WeeklyQuestKeys;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.util.Texts;
import de.quest.util.TimeUtil;
import de.quest.util.QuestCalendarService;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
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
        // Mod-hosted sessions may admit guests without vanilla's LAN publication flag.
        return server != null && (server.isDedicatedServer() || server.isPublished() || server.getPlayerCount() > 1);
    }

    public static void loadPersistentState(MinecraftServer server) {
        resetRuntimeState();
        if (server == null || !isEnabled(server)) {
            // Integrated worlds start private; defer loading until multiplayer opens.
            return;
        }

        CompoundTag root = QuestState.get(server).getQuestPartyState();
        if (!QuestPartyPersistence.storyLanesWritable(root)) { loaded=true; return; }
        if (root == null || root.isEmpty()) {
            loaded = true;
            return;
        }

        QuestPartyPersistence.read(root, PARTIES, MEMBER_TO_PARTY, INVITES);

        loaded = true;
        cleanupInvalidState(server);
        migrateNightBellsVillageBindings(server);
        storePersistentState(server);
    }

    public static void onServerTick(MinecraftServer server) {
        if (!isEnabled(server)) {
            return;
        }
        ensureLoaded(server);
        long now = System.currentTimeMillis();
        expireInvites(server, now);
        expireDisconnectedMembers(server.overworld(), now);
        cleanupStaleOffers(server.overworld());
    }

    public static void resetRuntimeState() {
        PARTIES.clear();
        MEMBER_TO_PARTY.clear();
        INVITES.clear();
        loaded = false;
    }

    public static void persistRuntimeState(MinecraftServer server) {
        ensureLoaded(server);
        if (server != null && loaded) {
            storePersistentState(server);
        }
        resetRuntimeState();
    }

    public static void handleJoin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        ensureLoaded(server);
        if (!isEnabled(server)) {
            return;
        }

        PartyRuntime party = partyFor(player.getUUID());
        if (party == null) {
            return;
        }

        Long deadline = party.disconnectDeadlines().remove(player.getUUID());
        QuestPartyMessaging.rememberName(party,player);
        if (deadline != null) {
            broadcast((ServerLevel) player.level(), party,
                    Component.translatable("message.village-quest.party.member_returned", player.getDisplayName()).withStyle(ChatFormatting.GRAY));
        }
        boolean hadPendingOffer = party.dailyOffers().containsKey(player.getUUID())
                || party.weeklyOffers().containsKey(player.getUUID())
                || party.storyOffers().containsKey(player.getUUID())
                || party.kingdomStoryOffers().containsKey(player.getUUID())
                || party.pilgrimOffers().containsKey(player.getUUID());
        queueActiveQuestOffersForJoiner((ServerLevel) player.level(), party, player);
        if (hadPendingOffer) {
            resendPendingQuestOffers((ServerLevel) player.level(), party, player);
        }
        storePersistentState(server);
        refreshPlayer((ServerLevel) player.level(), player);
        refreshPartyUi((ServerLevel) player.level(), party);
    }

    public static void handleDisconnect(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ServerLevel world = (ServerLevel) player.level();
        MinecraftServer server = world.getServer();
        ensureLoaded(server);
        removePendingInviteFor(player.getUUID());

        PartyRuntime party = partyFor(player.getUUID());
        // The last mod-hosted guest can leave after the player count drops to one.
        if (party == null) {
            return;
        }
        QuestPartyMessaging.rememberName(party,player);
        if (party.disconnectDeadlines().containsKey(player.getUUID())) {
            return;
        }

        long removeAt = System.currentTimeMillis() + DISCONNECT_GRACE_MILLIS;
        party.disconnectDeadlines().put(player.getUUID(), removeAt);
        broadcast(world, party, Component.translatable(
                "message.village-quest.party.member_disconnected",
                nameOf(world, player.getUUID()),
                formatRemainingMinutes(removeAt - System.currentTimeMillis())
        ).withStyle(ChatFormatting.GRAY));
        storePersistentState(server);
        refreshPartyUi(world, party);
    }

    public static boolean invite(ServerLevel world, ServerPlayer inviter, ServerPlayer target) {
        if (world == null || inviter == null || target == null) {
            return false;
        }
        ensureLoaded(world.getServer());
        if (!ensureEnabled(world.getServer(), inviter)) {
            return false;
        }
        if (inviter.getUUID().equals(target.getUUID())) {
            inviter.sendSystemMessage(Component.translatable("message.village-quest.party.invite.self").withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (hasParty(target.getUUID())) {
            inviter.sendSystemMessage(Component.translatable("message.village-quest.party.invite.target_busy", target.getDisplayName()).withStyle(ChatFormatting.RED), false);
            return false;
        }

        PartyRuntime party = getOrCreateParty(world, inviter);
        if (party == null) {
            inviter.sendSystemMessage(Component.translatable("message.village-quest.party.invite.failed").withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (!party.leaderId().equals(inviter.getUUID())) {
            inviter.sendSystemMessage(Component.translatable("message.village-quest.party.invite.only_leader").withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (party.members().contains(target.getUUID())) {
            inviter.sendSystemMessage(Component.translatable("message.village-quest.party.invite.same_party", target.getDisplayName()).withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (party.members().size() >= MAX_PARTY_SIZE) {
            inviter.sendSystemMessage(Component.translatable("message.village-quest.party.invite.full").withStyle(ChatFormatting.RED), false);
            return false;
        }

        INVITES.put(target.getUUID(), new PartyInvite(party.id(), inviter.getUUID(), System.currentTimeMillis() + INVITE_TIMEOUT_MILLIS));
        inviter.sendSystemMessage(Component.translatable("message.village-quest.party.invite.sent", target.getDisplayName()).withStyle(ChatFormatting.GREEN), false);
        target.sendSystemMessage(buildInviteMessage(inviter), false);
        storePersistentState(world.getServer());
        refreshPartyUi(world, party);
        refreshOpenUi(target);
        return true;
    }

    public static boolean acceptInvite(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }
        ensureLoaded(world.getServer());
        if (!ensureEnabled(world.getServer(), player)) {
            return false;
        }
        PartyInvite invite = INVITES.remove(player.getUUID());
        if (invite == null || invite.expiresAtMillis() <= System.currentTimeMillis()) {
            player.sendSystemMessage(Component.translatable("message.village-quest.party.accept.none").withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (hasParty(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.village-quest.party.accept.already_in_party").withStyle(ChatFormatting.RED), false);
            return false;
        }

        PartyRuntime party = PARTIES.get(invite.partyId());
        if (party == null || !party.members().contains(invite.inviterId())) {
            player.sendSystemMessage(Component.translatable("message.village-quest.party.accept.invalid").withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (party.members().size() >= MAX_PARTY_SIZE) {
            player.sendSystemMessage(Component.translatable("message.village-quest.party.invite.full").withStyle(ChatFormatting.RED), false);
            return false;
        }

        party.members().add(player.getUUID());
        QuestPartyMessaging.rememberName(party,player);
        MEMBER_TO_PARTY.put(player.getUUID(), party.id());
        party.disconnectDeadlines().remove(player.getUUID());
        broadcast(world, party, Component.translatable("message.village-quest.party.member_joined", player.getDisplayName()).withStyle(ChatFormatting.GREEN));
        queueActiveQuestOffersForJoiner(world, party, player);
        storePersistentState(world.getServer());
        refreshPartyUi(world, party);
        return true;
    }

    public static boolean declineInvite(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) {
            return false;
        }
        ensureLoaded(server);
        if (!ensureEnabled(server, player)) {
            return false;
        }
        PartyInvite invite = INVITES.remove(player.getUUID());
        if (invite == null) {
            player.sendSystemMessage(Component.translatable("message.village-quest.party.decline.none").withStyle(ChatFormatting.RED), false);
            return false;
        }
        ServerPlayer inviter = server.getPlayerList().getPlayer(invite.inviterId());
        if (inviter != null) {
            inviter.sendSystemMessage(Component.translatable("message.village-quest.party.decline.notify", player.getDisplayName()).withStyle(ChatFormatting.GRAY), false);
            refreshOpenUi(inviter);
        }
        player.sendSystemMessage(Component.translatable("message.village-quest.party.decline.self").withStyle(ChatFormatting.GRAY), false);
        storePersistentState(server);
        refreshOpenUi(player);
        return true;
    }

    public static boolean leave(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }
        ensureLoaded(world.getServer());
        if (!ensureEnabled(world.getServer(), player)) {
            return false;
        }
        return leaveInternal(world, player.getUUID(), true, false);
    }

    public static boolean disband(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }
        ensureLoaded(world.getServer());
        if (!ensureEnabled(world.getServer(), player)) {
            return false;
        }
        PartyRuntime party = partyFor(player.getUUID());
        if (party == null) {
            player.sendSystemMessage(Component.translatable("message.village-quest.party.none").withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (!Objects.equals(party.leaderId(), player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.village-quest.party.disband.only_leader").withStyle(ChatFormatting.RED), false);
            return false;
        }

        List<UUID> members = new ArrayList<>(party.members());
        for (UUID memberId : members) {
            copySharedProgressToPlayer(world, party, memberId);
            MEMBER_TO_PARTY.remove(memberId);
            ServerPlayer member = world.getServer().getPlayerList().getPlayer(memberId);
            if (member != null) {
                member.sendSystemMessage(Component.translatable("message.village-quest.party.disbanded").withStyle(ChatFormatting.GRAY), false);
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

    public static PartySnapshot snapshot(ServerPlayer viewer) {
        if (viewer == null) {
            return PartySnapshot.empty();
        }
        MinecraftServer server = ((ServerLevel) viewer.level()).getServer();
        ensureLoaded(server);
        if (!isEnabled(server)) {
            return PartySnapshot.empty();
        }
        cleanupInvite(viewer.getUUID());
        PartyRuntime party = partyFor(viewer.getUUID());
        return QuestPartyViewBuilder.build(server, viewer, party, MEMBER_TO_PARTY);
    }

    public static DailyQuestService.DailyQuestType resolveSharedDailyChoice(ServerLevel world,
                                                                            UUID playerId,
                                                                            DailyQuestService.DailyQuestType fallback) {
        ensureLoaded(world == null ? null : world.getServer());
        PartyRuntime party = partyFor(playerId);
        if (world == null || party == null || !FirstDailyChoiceService.canUseSharedDaily(
                QuestState.get(world.getServer()).getPlayerData(playerId))) {
            return fallback;
        }
        DailyQuestService.DailyQuestType sessionType = activeDailyType(party);
        if (sessionType != null) clearDailySessionIfFinished(world, playerId, sessionType);
        sessionType = activeDailyType(party);
        return sessionType == null || !party.daily().canJoinAfterTurnIn(DailyQuestKeys.SHARED_TURN_IN_CONSUMED, playerId)
                ? fallback : sessionType;
    }

    public static WeeklyQuestService.WeeklyQuestType resolveSharedWeeklyChoice(ServerLevel world,
                                                                               UUID playerId,
                                                                               WeeklyQuestService.WeeklyQuestType fallback) {
        ensureLoaded(world == null ? null : world.getServer());
        PartyRuntime party = partyFor(playerId);
        if (world == null || party == null) {
            return fallback;
        }
        WeeklyQuestService.WeeklyQuestType sessionType = activeWeeklyType(party);
        if (sessionType != null) clearWeeklySessionIfFinished(world, playerId, sessionType);
        sessionType = activeWeeklyType(party);
        return sessionType == null || !party.weekly().canJoinAfterTurnIn(WeeklyQuestKeys.SHARED_TURN_IN_CONSUMED, playerId)
                ? fallback : sessionType;
    }

    public static void onDailyQuestAccepted(ServerLevel world,
                                            ServerPlayer player,
                                            DailyQuestService.DailyQuestType type,
                                            DailyQuestDefinition definition) {
        ensureLoaded(world == null ? null : world.getServer());
        if (world == null || player == null || type == null || !QuestShareProfiles.isDailyShareable(type)
                || !FirstDailyChoiceService.canUseSharedDaily(
                        QuestState.get(world.getServer()).getPlayerData(player.getUUID()))) {
            return;
        }
        PartyRuntime party = partyFor(player.getUUID());
        if (party == null) {
            return;
        }

        SharedQuestRuntime session = ensureDailySession(party, type);
        if (session == null || !session.canJoinAfterTurnIn(DailyQuestKeys.SHARED_TURN_IN_CONSUMED, player.getUUID())) return;
        mergeDailyProgressIntoSession(world, player.getUUID(), type, session);
        syncPartyMembersIntoDaily(world, party, type, definition, player.getUUID());
        clearDailyOffer(party, player.getUUID());
        storePersistentState(world.getServer());
        refreshPartyUi(world, party);
    }

    public static void onWeeklyQuestAccepted(ServerLevel world,
                                             ServerPlayer player,
                                             WeeklyQuestService.WeeklyQuestType type,
                                             WeeklyQuestDefinition definition) {
        ensureLoaded(world == null ? null : world.getServer());
        if (world == null || player == null || type == null || !QuestShareProfiles.isWeeklyShareable(type)) {
            return;
        }
        PartyRuntime party = partyFor(player.getUUID());
        if (party == null) {
            return;
        }

        SharedQuestRuntime session = ensureWeeklySession(party, type);
        if (session == null || !session.canJoinAfterTurnIn(WeeklyQuestKeys.SHARED_TURN_IN_CONSUMED, player.getUUID())) return;
        mergeWeeklyProgressIntoSession(world, player.getUUID(), type, session);
        syncPartyMembersIntoWeekly(world, party, type, definition, player.getUUID());
        clearWeeklyOffer(party, player.getUUID());
        storePersistentState(world.getServer());
        refreshPartyUi(world, party);
    }

    public static boolean usesSharedDailyInt(ServerLevel world,
                                             UUID playerId,
                                             DailyQuestService.DailyQuestType type,
                                             String key) {
        return sharedDailySession(world, playerId, type) != null && QuestShareProfiles.sharesDailyInt(type, key);
    }

    public static boolean usesSharedDailyFlag(ServerLevel world,
                                              UUID playerId,
                                              DailyQuestService.DailyQuestType type,
                                              String key) {
        return sharedDailySession(world, playerId, type) != null && QuestShareProfiles.sharesDailyFlag(type, key);
    }

    public static boolean usesSharedWeeklyInt(ServerLevel world,
                                              UUID playerId,
                                              WeeklyQuestService.WeeklyQuestType type,
                                              String key) {
        return sharedWeeklySession(world, playerId, type) != null && QuestShareProfiles.sharesWeeklyInt(type, key);
    }

    public static boolean usesSharedWeeklyFlag(ServerLevel world,
                                               UUID playerId,
                                               WeeklyQuestService.WeeklyQuestType type,
                                               String key) {
        return sharedWeeklySession(world, playerId, type) != null && QuestShareProfiles.sharesWeeklyFlag(type, key);
    }

    public static boolean isSharedDailyMember(ServerLevel world, UUID playerId, DailyQuestService.DailyQuestType type) {
        return sharedDailySession(world, playerId, type) != null;
    }

    public static boolean isSharedWeeklyMember(ServerLevel world, UUID playerId, WeeklyQuestService.WeeklyQuestType type) {
        return sharedWeeklySession(world, playerId, type) != null;
    }

    public static int getSharedDailyInt(ServerLevel world,
                                        UUID playerId,
                                        DailyQuestService.DailyQuestType type,
                                        String key) {
        SharedQuestRuntime session = sharedDailySession(world, playerId, type);
        return session == null ? 0 : session.getInt(key);
    }

    public static void setSharedDailyInt(ServerLevel world,
                                         UUID playerId,
                                         DailyQuestService.DailyQuestType type,
                                         String key,
                                         int value) {
        SharedQuestRuntime session = sharedDailySession(world, playerId, type);
        if (session != null) {
            session.setInt(key, value);
        }
    }

    public static void addSharedDailyInt(ServerLevel world,
                                         UUID playerId,
                                         DailyQuestService.DailyQuestType type,
                                         String key,
                                         int amount) {
        SharedQuestRuntime session = sharedDailySession(world, playerId, type);
        if (session != null) {
            session.addInt(key, amount);
        }
    }

    public static boolean getSharedDailyFlag(ServerLevel world,
                                             UUID playerId,
                                             DailyQuestService.DailyQuestType type,
                                             String key) {
        SharedQuestRuntime session = sharedDailySession(world, playerId, type);
        return session != null && session.hasFlag(key);
    }

    public static void setSharedDailyFlag(ServerLevel world,
                                          UUID playerId,
                                          DailyQuestService.DailyQuestType type,
                                          String key,
                                          boolean enabled) {
        SharedQuestRuntime session = sharedDailySession(world, playerId, type);
        if (session != null) {
            session.setFlag(key, enabled);
        }
    }

    public static int getSharedWeeklyInt(ServerLevel world,
                                         UUID playerId,
                                         WeeklyQuestService.WeeklyQuestType type,
                                         String key) {
        SharedQuestRuntime session = sharedWeeklySession(world, playerId, type);
        return session == null ? 0 : session.getInt(key);
    }

    public static void setSharedWeeklyInt(ServerLevel world,
                                          UUID playerId,
                                          WeeklyQuestService.WeeklyQuestType type,
                                          String key,
                                          int value) {
        SharedQuestRuntime session = sharedWeeklySession(world, playerId, type);
        if (session != null) {
            session.setInt(key, value);
        }
    }

    public static void addSharedWeeklyInt(ServerLevel world,
                                          UUID playerId,
                                          WeeklyQuestService.WeeklyQuestType type,
                                          String key,
                                          int amount) {
        SharedQuestRuntime session = sharedWeeklySession(world, playerId, type);
        if (session != null) {
            session.addInt(key, amount);
        }
    }

    public static boolean getSharedWeeklyFlag(ServerLevel world,
                                              UUID playerId,
                                              WeeklyQuestService.WeeklyQuestType type,
                                              String key) {
        SharedQuestRuntime session = sharedWeeklySession(world, playerId, type);
        return session != null && session.hasFlag(key);
    }

    public static void setSharedWeeklyFlag(ServerLevel world,
                                           UUID playerId,
                                           WeeklyQuestService.WeeklyQuestType type,
                                           String key,
                                           boolean enabled) {
        SharedQuestRuntime session = sharedWeeklySession(world, playerId, type);
        if (session != null) {
            session.setFlag(key, enabled);
        }
    }

    public static boolean dailyTurnInRequirementsConsumed(ServerLevel world,
                                                          UUID playerId,
                                                          DailyQuestService.DailyQuestType type) {
        SharedQuestRuntime session = sharedDailySession(world, playerId, type);
        return session != null && session.hasFlag(DailyQuestKeys.SHARED_TURN_IN_CONSUMED);
    }

    public static void markDailyTurnInRequirementsConsumed(ServerLevel world,
                                                           UUID playerId,
                                                           DailyQuestService.DailyQuestType type) {
        SharedQuestRuntime session = sharedDailySession(world, playerId, type);
        PartyRuntime party = partyFor(playerId);
        if (world == null || session == null || party == null) return;

        session.setFlag(DailyQuestKeys.SHARED_TURN_IN_CONSUMED, true);
        session.removeUnsyncedOffersAfterTurnIn(
                DailyQuestKeys.SHARED_TURN_IN_CONSUMED, party.dailyOffers());
        long day = QuestCalendarService.currentDay();
        for (UUID memberId : party.members()) {
            if (!session.hasSynced(memberId)) continue;
            PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
            if (data.hasActiveDaily() && data.getDailyChoice() == type) {
                data.setDailyFlag(DailyQuestKeys.SHARED_TURN_IN_CONSUMED, true);
            }
        }
        QuestState.get(world.getServer()).setDirty();
        storePersistentState(world.getServer());
    }

    public static boolean weeklyTurnInRequirementsConsumed(ServerLevel world,
                                                           UUID playerId,
                                                           WeeklyQuestService.WeeklyQuestType type) {
        SharedQuestRuntime session = sharedWeeklySession(world, playerId, type);
        return session != null && session.hasFlag(WeeklyQuestKeys.SHARED_TURN_IN_CONSUMED);
    }

    public static void markWeeklyTurnInRequirementsConsumed(ServerLevel world,
                                                            UUID playerId,
                                                            WeeklyQuestService.WeeklyQuestType type) {
        SharedQuestRuntime session = sharedWeeklySession(world, playerId, type);
        PartyRuntime party = partyFor(playerId);
        if (world == null || session == null || party == null) return;

        session.setFlag(WeeklyQuestKeys.SHARED_TURN_IN_CONSUMED, true);
        session.removeUnsyncedOffersAfterTurnIn(
                WeeklyQuestKeys.SHARED_TURN_IN_CONSUMED, party.weeklyOffers());
        long cycle = QuestCalendarService.currentWeekCycle();
        for (UUID memberId : party.members()) {
            if (!session.hasSynced(memberId)) continue;
            PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
            if (data.hasActiveWeekly() && data.getWeeklyChoice() == type) {
                data.setWeeklyFlag(WeeklyQuestKeys.SHARED_TURN_IN_CONSUMED, true);
            }
        }
        QuestState.get(world.getServer()).setDirty();
        storePersistentState(world.getServer());
    }

    public static void onStoryQuestAccepted(ServerLevel world,
                                            ServerPlayer player,
                                            StoryArcType arcType,
                                            int chapterIndex,
                                            StoryChapterDefinition chapter) {
        ensureLoaded(world == null ? null : world.getServer());
        if (world == null || player == null || arcType == null || !QuestShareProfiles.isStoryShareable(arcType)) {
            return;
        }
        PartyRuntime party = partyFor(player.getUUID());
        if (party == null) {
            return;
        }

        SharedQuestRuntime session = ensurePartyStorySession(world, party, arcType, chapterIndex);
        mergeStoryProgressIntoSession(world, player.getUUID(), arcType, chapterIndex, session);
        syncPartyMembersIntoStory(world, party, arcType, chapterIndex, chapter, player.getUUID());
        clearStoryOffer(party, player.getUUID(), StoryLane.forArc(arcType));
        storePersistentState(world.getServer());
        refreshPartyUi(world, party);
    }

    public static boolean usesSharedStoryInt(ServerLevel world,
                                             UUID playerId,
                                             StoryArcType arcType,
                                             int chapterIndex,
                                             String key) {
        return sharedStorySession(world, playerId, arcType, chapterIndex) != null
                && QuestShareProfiles.sharesStoryInt(arcType, chapterIndex, key);
    }

    public static boolean usesSharedStoryFlag(ServerLevel world,
                                              UUID playerId,
                                              StoryArcType arcType,
                                              int chapterIndex,
                                              String key) {
        return sharedStorySession(world, playerId, arcType, chapterIndex) != null
                && QuestShareProfiles.sharesStoryFlag(arcType, chapterIndex, key);
    }

    public static boolean isSharedStoryMember(ServerLevel world,
                                              UUID playerId,
                                              StoryArcType arcType,
                                              int chapterIndex) {
        return sharedStorySession(world, playerId, arcType, chapterIndex) != null;
    }

    /** An authorized council action selects one story village; only the party leader can replace a shared target. */
    public static boolean bindKingdomStoryTarget(ServerLevel world, ServerPlayer actor, UUID village, boolean explicit) {
        if (world==null || actor==null || village==null || village.equals(de.quest.kingdom.BuildingSessionPolicy.NONE)) return false;
        ensureLoaded(world.getServer());
        var data=QuestState.get(world.getServer()).getPlayerData(actor.getUUID());
        if (!data.isKingdomStoryActive())
            return de.quest.kingdom.KingdomStoryProgress.bind(data,village,explicit);
        int chapter=data.getStoryChapterProgress(StoryArcType.LIVING_KINGDOMS.id());
        var session=sharedStorySession(world,actor.getUUID(),StoryArcType.LIVING_KINGDOMS,chapter);
        var party=partyFor(actor.getUUID());
        if (session==null || party==null) return de.quest.kingdom.KingdomStoryProgress.bind(data,village,explicit);
        Map<UUID,PlayerQuestData> members=new LinkedHashMap<>();
        for(UUID member:session.syncedMembers()) if (party.members().contains(member))
            members.put(member,QuestState.get(world.getServer()).getPlayerData(member));
        if (!updateKingdomVillageBinding(members,session,actor.getUUID(),party.leaderId(),village,explicit,chapter)) return false;
        QuestState.get(world.getServer()).setDirty(); storePersistentState(world.getServer()); return true;
    }

    public static int getSharedStoryInt(ServerLevel world,
                                        UUID playerId,
                                        StoryArcType arcType,
                                        int chapterIndex,
                                        String key) {
        SharedQuestRuntime session = sharedStorySession(world, playerId, arcType, chapterIndex);
        return session == null ? 0 : session.getInt(key);
    }

    public static void setSharedStoryInt(ServerLevel world,
                                         UUID playerId,
                                         StoryArcType arcType,
                                         int chapterIndex,
                                         String key,
                                         int value) {
        SharedQuestRuntime session = sharedStorySession(world, playerId, arcType, chapterIndex);
        if (session != null) {
            session.setInt(key, value);
            if (arcType == StoryArcType.NIGHT_BELLS
                    && StoryQuestKeys.NIGHT_BELLS_VILLAGE_BOUND.equals(key) && value != 0) {
                storePersistentState(world.getServer());
            }
        }
    }

    public static boolean getSharedStoryFlag(ServerLevel world,
                                             UUID playerId,
                                             StoryArcType arcType,
                                             int chapterIndex,
                                             String key) {
        SharedQuestRuntime session = sharedStorySession(world, playerId, arcType, chapterIndex);
        return session != null && session.hasFlag(key);
    }

    public static void setSharedStoryFlag(ServerLevel world,
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

    public static boolean storyTurnInRequirementsConsumed(ServerLevel world,
                                                          UUID playerId,
                                                          StoryArcType arcType,
                                                          int chapterIndex) {
        SharedQuestRuntime session = sharedStorySession(world, playerId, arcType, chapterIndex);
        if (session != null && session.hasFlag(StoryQuestKeys.SHARED_TURN_IN_CONSUMED)) return true;
        return world != null && playerId != null && arcType != null && chapterIndex >= 0
                && QuestState.get(world.getServer()).getPlayerData(playerId).hasStoryFlag(
                        StoryQuestKeys.sharedTurnInConsumed(arcType, chapterIndex));
    }

    public static void markStoryTurnInRequirementsConsumed(ServerLevel world,
                                                           UUID playerId,
                                                           StoryArcType arcType,
                                                           int chapterIndex) {
        SharedQuestRuntime session = sharedStorySession(world, playerId, arcType, chapterIndex);
        PartyRuntime party = partyFor(playerId);
        if (world == null || session == null || party == null) return;

        session.setFlag(StoryQuestKeys.SHARED_TURN_IN_CONSUMED, true);
        session.removeUnsyncedOffersAfterTurnIn(
                StoryQuestKeys.SHARED_TURN_IN_CONSUMED, offersFor(party,arcType));
        for (UUID memberId : party.members()) {
            if (!session.hasSynced(memberId)) continue;
            PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
            if (data.getActiveStoryArc(StoryLane.forArc(arcType)) == arcType
                    && data.getStoryChapterProgress(arcType.id()) == chapterIndex
                    && (arcType!=StoryArcType.LIVING_KINGDOMS || matchesKingdomVillageBinding(data,session))) {
                data.setStoryFlag(StoryQuestKeys.sharedTurnInConsumed(arcType, chapterIndex), true);
            }
        }
        QuestState.get(world.getServer()).setDirty();
        storePersistentState(world.getServer());
    }

    public static List<UUID> activeStoryMembers(ServerLevel world,
                                                UUID playerId,
                                                StoryArcType arcType,
                                                int chapterIndex) {
        return storyMembers(world,playerId,arcType,chapterIndex,false);
    }

    /** Progress evidence includes admitted offline partners; inventory/reward delivery stays online. */
    public static List<UUID> storyProgressMembers(ServerLevel world, UUID playerId, StoryArcType arcType, int chapterIndex) {
        return storyMembers(world,playerId,arcType,chapterIndex,true);
    }

    private static List<UUID> storyMembers(ServerLevel world, UUID playerId, StoryArcType arcType,
                                          int chapterIndex, boolean includeOffline) {
        PartyRuntime party = partyFor(playerId);
        SharedQuestRuntime session = sharedStorySessionForType(world, playerId, arcType, chapterIndex);
        if (world == null || party == null || arcType == null || session == null
                || sharedStorySession(world,playerId,arcType,chapterIndex)==null) {
            return List.of(playerId);
        }

        List<UUID> members = new ArrayList<>();
        for (UUID memberId : party.members()) {
            if (!session.hasSynced(memberId)) {
                continue;
            }
            PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
            if (data.getActiveStoryArc(StoryLane.forArc(arcType)) != arcType) {
                continue;
            }
            if (data.getStoryChapterProgress(arcType.id()) != chapterIndex) {
                continue;
            }
            if (arcType==StoryArcType.LIVING_KINGDOMS && !matchesKingdomVillageBinding(data,session)) continue;
            if (includeOffline || world.getServer().getPlayerList().getPlayer(memberId) != null) {
                members.add(memberId);
            }
        }
        return members.isEmpty() ? List.of(playerId) : List.copyOf(members);
    }

    public static int storySharedMemberCount(ServerLevel world,
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
            if (data.getActiveStoryArc(StoryLane.forArc(arcType)) == arcType && data.getStoryChapterProgress(arcType.id()) == chapterIndex
                    && (arcType!=StoryArcType.LIVING_KINGDOMS || matchesKingdomVillageBinding(data,session))) {
                count++;
            }
        }
        return count;
    }

    public static int countStoryTurnInItem(ServerLevel world,
                                           UUID playerId,
                                           StoryArcType arcType,
                                           int chapterIndex,
                                           Item item) {
        return QuestPartyInventory.countItem(
                onlineMembers(world, activeStoryMembers(world, playerId, arcType, chapterIndex)), item);
    }

    public static boolean consumeStoryTurnInItem(ServerLevel world,
                                                 UUID playerId,
                                                 StoryArcType arcType,
                                                 int chapterIndex,
                                                 Item item,
                                                 int amount) {
        return QuestPartyInventory.consumeItem(
                onlineMembers(world, activeStoryMembers(world, playerId, arcType, chapterIndex)), item, amount);
    }

    public static int countStoryTurnInItems(ServerLevel world,
                                            UUID playerId,
                                            StoryArcType arcType,
                                            int chapterIndex,
                                            java.util.function.Predicate<ItemStack> matcher) {
        return QuestPartyInventory.countMatching(
                onlineMembers(world, activeStoryMembers(world, playerId, arcType, chapterIndex)), matcher);
    }

    public static boolean consumeStoryTurnInItems(ServerLevel world,
                                                  UUID playerId,
                                                  StoryArcType arcType,
                                                  int chapterIndex,
                                                  java.util.function.Predicate<ItemStack> matcher,
                                                  int amount) {
        return QuestPartyInventory.consumeMatching(
                onlineMembers(world, activeStoryMembers(world, playerId, arcType, chapterIndex)), matcher, amount);
    }

    public static void clearStorySessionIfFinished(ServerLevel world,
                                                   UUID playerId,
                                                   StoryArcType arcType,
                                                   int chapterIndex) {
        PartyRuntime party = partyFor(playerId);
        if (party == null || !matchesStorySession(party, arcType, chapterIndex)) {
            return;
        }
        if (storySharedMemberCount(world, playerId, arcType, chapterIndex) <= 0) {
            sessionFor(party,arcType).clear();
            offersFor(party,arcType).clear();
            storePersistentState(world.getServer());
        }
    }

    public static void onPilgrimContractAccepted(ServerLevel world,
                                                 ServerPlayer player,
                                                 PilgrimContractType type) {
        ensureLoaded(world == null ? null : world.getServer());
        if (world == null || player == null || type == null || !QuestShareProfiles.isPilgrimShareable(type)) {
            return;
        }
        PartyRuntime party = partyFor(player.getUUID());
        if (party == null) {
            return;
        }

        SharedQuestRuntime session = ensurePilgrimSession(party, type);
        mergePilgrimProgressIntoSession(world, player.getUUID(), type, session);
        syncPartyMembersIntoPilgrim(world, party, type, player.getUUID());
        clearPilgrimOffer(party, player.getUUID());
        storePersistentState(world.getServer());
        refreshPartyUi(world, party);
    }

    public static boolean usesSharedPilgrimInt(ServerLevel world,
                                               UUID playerId,
                                               PilgrimContractType type,
                                               String key) {
        return sharedPilgrimSession(world, playerId, type) != null && QuestShareProfiles.sharesPilgrimInt(type, key);
    }

    public static boolean usesSharedPilgrimFlag(ServerLevel world,
                                                UUID playerId,
                                                PilgrimContractType type,
                                                String key) {
        return sharedPilgrimSession(world, playerId, type) != null && QuestShareProfiles.sharesPilgrimFlag(type, key);
    }

    public static boolean isSharedPilgrimMember(ServerLevel world, UUID playerId, PilgrimContractType type) {
        return sharedPilgrimSession(world, playerId, type) != null;
    }

    public static int getSharedPilgrimInt(ServerLevel world,
                                          UUID playerId,
                                          PilgrimContractType type,
                                          String key) {
        SharedQuestRuntime session = sharedPilgrimSession(world, playerId, type);
        return session == null ? 0 : session.getInt(key);
    }

    public static void setSharedPilgrimInt(ServerLevel world,
                                           UUID playerId,
                                           PilgrimContractType type,
                                           String key,
                                           int value) {
        SharedQuestRuntime session = sharedPilgrimSession(world, playerId, type);
        if (session != null) {
            session.setInt(key, value);
        }
    }

    public static boolean getSharedPilgrimFlag(ServerLevel world,
                                               UUID playerId,
                                               PilgrimContractType type,
                                               String key) {
        SharedQuestRuntime session = sharedPilgrimSession(world, playerId, type);
        return session != null && session.hasFlag(key);
    }

    public static void setSharedPilgrimFlag(ServerLevel world,
                                            UUID playerId,
                                            PilgrimContractType type,
                                            String key,
                                            boolean enabled) {
        SharedQuestRuntime session = sharedPilgrimSession(world, playerId, type);
        if (session != null) {
            session.setFlag(key, enabled);
        }
    }

    public static List<UUID> activePilgrimMembers(ServerLevel world,
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
            if (world.getServer().getPlayerList().getPlayer(memberId) != null) {
                members.add(memberId);
            }
        }
        return members.isEmpty() ? List.of(playerId) : List.copyOf(members);
    }

    public static int pilgrimSharedMemberCount(ServerLevel world, UUID playerId, PilgrimContractType type) {
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

    public static void clearPilgrimSessionIfFinished(ServerLevel world, UUID playerId, PilgrimContractType type) {
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

    public static List<UUID> activeDailyMembers(ServerLevel world,
                                                UUID playerId,
                                                DailyQuestService.DailyQuestType type,
                                                boolean includeCompleted) {
        PartyRuntime party = partyFor(playerId);
        SharedQuestRuntime session = sharedDailySessionForType(world, playerId, type);
        if (world == null || party == null || type == null || session == null) {
            return List.of(playerId);
        }

        long day = QuestCalendarService.currentDay();
        List<UUID> members = new ArrayList<>();
        for (UUID memberId : party.members()) {
            if (!session.hasSynced(memberId)) {
                continue;
            }
            PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
            if (data.getAcceptedDay() == PlayerQuestData.UNSET_DAY) {
                continue;
            }
            if (data.getDailyChoice() != type) {
                continue;
            }
            if (!includeCompleted && !data.hasActiveDaily()) {
                continue;
            }
            if (world.getServer().getPlayerList().getPlayer(memberId) != null) {
                members.add(memberId);
            }
        }
        return members.isEmpty() ? List.of(playerId) : List.copyOf(members);
    }

    public static List<UUID> activeWeeklyMembers(ServerLevel world,
                                                 UUID playerId,
                                                 WeeklyQuestService.WeeklyQuestType type,
                                                 boolean includeCompleted) {
        PartyRuntime party = partyFor(playerId);
        SharedQuestRuntime session = sharedWeeklySessionForType(world, playerId, type);
        if (world == null || party == null || type == null || session == null) {
            return List.of(playerId);
        }

        long cycle = QuestCalendarService.currentWeekCycle();
        List<UUID> members = new ArrayList<>();
        for (UUID memberId : party.members()) {
            if (!session.hasSynced(memberId)) {
                continue;
            }
            PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
            if (data.getWeeklyAcceptedCycle() == PlayerQuestData.UNSET_DAY) {
                continue;
            }
            if (data.getWeeklyChoice() != type) {
                continue;
            }
            if (!includeCompleted && !data.hasActiveWeekly()) {
                continue;
            }
            if (world.getServer().getPlayerList().getPlayer(memberId) != null) {
                members.add(memberId);
            }
        }
        return members.isEmpty() ? List.of(playerId) : List.copyOf(members);
    }

    public static int dailySharedMemberCount(ServerLevel world, UUID playerId, DailyQuestService.DailyQuestType type) {
        SharedQuestRuntime session = sharedDailySessionForType(world, playerId, type);
        if (world == null || session == null) {
            return 0;
        }
        int count = 0;
        long day = QuestCalendarService.currentDay();
        PartyRuntime party = partyFor(playerId);
        if (party == null) {
            return 0;
        }
        for (UUID memberId : party.members()) {
            if (!session.hasSynced(memberId)) {
                continue;
            }
            PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
            if (data.hasActiveDaily() && data.getDailyChoice() == type) {
                count++;
            }
        }
        return count;
    }

    public static int weeklySharedMemberCount(ServerLevel world, UUID playerId, WeeklyQuestService.WeeklyQuestType type) {
        SharedQuestRuntime session = sharedWeeklySessionForType(world, playerId, type);
        if (world == null || session == null) {
            return 0;
        }
        int count = 0;
        long cycle = QuestCalendarService.currentWeekCycle();
        PartyRuntime party = partyFor(playerId);
        if (party == null) {
            return 0;
        }
        for (UUID memberId : party.members()) {
            if (!session.hasSynced(memberId)) {
                continue;
            }
            PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
            if (data.hasActiveWeekly() && data.getWeeklyChoice() == type) {
                count++;
            }
        }
        return count;
    }

    public static int partySize(UUID playerId) {
        PartyRuntime party = partyFor(playerId);
        return party == null ? 1 : party.members().size();
    }

    public static de.quest.quest.repeatable.RepeatableTargetProfile rewardDailyProfile(ServerLevel world, UUID player,
            DailyQuestService.DailyQuestType type, de.quest.quest.repeatable.RepeatableTargetProfile fallback) {
        PartyRuntime party = partyFor(player);
        return party != null && activeDailyType(party) == type && !party.daily().syncedMembers().isEmpty()
                ? sharedDailyTargetProfile(world, party, type) : fallback;
    }
    public static de.quest.quest.repeatable.RepeatableTargetProfile rewardWeeklyProfile(ServerLevel world, UUID player,
            WeeklyQuestService.WeeklyQuestType type, de.quest.quest.repeatable.RepeatableTargetProfile fallback) {
        PartyRuntime party = partyFor(player);
        return party != null && activeWeeklyType(party) == type && !party.weekly().syncedMembers().isEmpty()
                ? sharedWeeklyTargetProfile(world, party, type) : fallback;
    }
    private static boolean modernShared(ServerLevel world, SharedQuestRuntime session, String slot) {
        for (UUID owner : session.syncedMembers()) {
            var ownerData = QuestState.get(world.getServer()).getPlayerData(owner);
            var state = ownerData.vanillaRewards();
            var snapshot = state.snapshot(slot, state.currentIdentity(slot));
            String expectedKey = slot + ":" + session.questId();
            if ((slot.equals("story") || slot.equals("kingdom_story")) && session.questId() != null) expectedKey = "story:" + session.questId().split("#")[0];
            if (snapshot.getStringOr("key", "").equals(expectedKey) && snapshot.getIntOr("version", 0) >= 1) {
                if (slot.equals("daily") && !state.currentIdentity(slot).equals(session.questId() + ":" + ownerData.getAcceptedDay())) continue;
                if (slot.equals("weekly") && !state.currentIdentity(slot).equals(session.questId() + ":" + ownerData.getWeeklyAcceptedCycle())) continue;
                return true;
            }
        }
        return session.syncedMembers().isEmpty();
    }
    public static String rewardDailyKey(ServerLevel world, UUID player, DailyQuestService.DailyQuestType type) {
        PartyRuntime party = partyFor(player);
        return party != null && activeDailyType(party) == type && !modernShared(world, party.daily(), "daily") ? "" : "daily:" + type.name();
    }
    public static String rewardWeeklyKey(ServerLevel world, UUID player, WeeklyQuestService.WeeklyQuestType type) {
        PartyRuntime party = partyFor(player);
        return party != null && activeWeeklyType(party) == type && !modernShared(world, party.weekly(), "weekly") ? "" : "weekly:" + type.name();
    }
    public static String rewardStoryKey(ServerLevel world, UUID player, StoryArcType type, int chapter) {
        PartyRuntime party = partyFor(player);
        return party != null && matchesStorySession(party,type,chapter)
                && !modernShared(world, sessionFor(party,type), StoryLane.forArc(type)==StoryLane.KINGDOM ? "kingdom_story" : "story") ? "" : "story:" + type.id();
    }
    public static String rewardPilgrimKey(ServerLevel world, UUID player, PilgrimContractType type) {
        PartyRuntime party = partyFor(player);
        return party != null && activePilgrimType(party) == type && !modernShared(world, party.pilgrim(), "pilgrim") ? "" : "pilgrim:" + type.id();
    }

    public static boolean acceptDailyOffer(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }
        ensureLoaded(world.getServer());
        PartyRuntime party = partyFor(player.getUUID());
        if (party == null) {
            player.sendSystemMessage(Component.translatable("message.village-quest.party.none").withStyle(ChatFormatting.RED), false);
            return false;
        }
        QuestJoinOffer offer = party.dailyOffers().get(player.getUUID());
        if (offer == null) {
            player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.daily.none").withStyle(ChatFormatting.RED), false);
            return false;
        }
        DailyQuestService.DailyQuestType type = parseDailyType(offer.questId());
        SharedQuestRuntime session = sharedDailySessionForType(world, player.getUUID(), type);
        if (type == null || session == null || !session.matches(offer.questId(), offer.revision())) {
            clearDailyOffer(party, player.getUUID());
            storePersistentState(world.getServer());
            player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.daily.expired").withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (!canJoinDailyShare(world, player.getUUID(), type, true)) {
            clearDailyOffer(party, player.getUUID());
            storePersistentState(world.getServer());
            return false;
        }

        PlayerQuestData joining = QuestState.get(world.getServer()).getPlayerData(player.getUUID());
        String rewardIdentity = joining.hasActiveDaily() && joining.getDailyChoice() == type
                ? type.name() + ":" + joining.getAcceptedDay()
                : de.quest.reward.VanillaRewardService.dailyIdentity(type.name());
        if (!de.quest.reward.VanillaRewardService.prepare(world, player, "daily", rewardDailyKey(world, player.getUUID(), type),
                rewardIdentity, sharedDailyTargetProfile(world, party, type),
                () -> acceptDailyOffer(world, player))) return false;
        syncDailyMember(world, party, player, type, DailyQuestGenerator.definition(type), offer.sourceId());
        storePersistentState(world.getServer());
        refreshPartyUi(world, party);
        return true;
    }

    public static boolean declineDailyOffer(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }
        ensureLoaded(world.getServer());
        PartyRuntime party = partyFor(player.getUUID());
        if (party == null || clearDailyOffer(party, player.getUUID()) == null) {
            player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.daily.none").withStyle(ChatFormatting.RED), false);
            return false;
        }
        player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.daily.declined").withStyle(ChatFormatting.GRAY), false);
        storePersistentState(world.getServer());
        return true;
    }

    public static boolean acceptWeeklyOffer(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }
        ensureLoaded(world.getServer());
        PartyRuntime party = partyFor(player.getUUID());
        if (party == null) {
            player.sendSystemMessage(Component.translatable("message.village-quest.party.none").withStyle(ChatFormatting.RED), false);
            return false;
        }
        QuestJoinOffer offer = party.weeklyOffers().get(player.getUUID());
        if (offer == null) {
            player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.weekly.none").withStyle(ChatFormatting.RED), false);
            return false;
        }
        WeeklyQuestService.WeeklyQuestType type = parseWeeklyType(offer.questId());
        SharedQuestRuntime session = sharedWeeklySessionForType(world, player.getUUID(), type);
        if (type == null || session == null || !session.matches(offer.questId(), offer.revision())) {
            clearWeeklyOffer(party, player.getUUID());
            storePersistentState(world.getServer());
            player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.weekly.expired").withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (!canJoinWeeklyShare(world, player.getUUID(), type, true)) {
            clearWeeklyOffer(party, player.getUUID());
            storePersistentState(world.getServer());
            return false;
        }

        PlayerQuestData joining = QuestState.get(world.getServer()).getPlayerData(player.getUUID());
        String rewardIdentity = joining.hasActiveWeekly() && joining.getWeeklyChoice() == type
                ? type.name() + ":" + joining.getWeeklyAcceptedCycle()
                : de.quest.reward.VanillaRewardService.weeklyIdentity(type.name());
        if (!de.quest.reward.VanillaRewardService.prepare(world, player, "weekly", rewardWeeklyKey(world, player.getUUID(), type),
                rewardIdentity, sharedWeeklyTargetProfile(world, party, type),
                () -> acceptWeeklyOffer(world, player))) return false;
        syncWeeklyMember(world, party, player, type, WeeklyQuestGenerator.definition(type), offer.sourceId());
        storePersistentState(world.getServer());
        refreshPartyUi(world, party);
        return true;
    }

    public static boolean declineWeeklyOffer(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }
        ensureLoaded(world.getServer());
        PartyRuntime party = partyFor(player.getUUID());
        if (party == null || clearWeeklyOffer(party, player.getUUID()) == null) {
            player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.weekly.none").withStyle(ChatFormatting.RED), false);
            return false;
        }
        player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.weekly.declined").withStyle(ChatFormatting.GRAY), false);
        storePersistentState(world.getServer());
        return true;
    }

    public static boolean acceptStoryOffer(ServerLevel world, ServerPlayer player) {
        return acceptStoryOffer(world, player, null);
    }

    public static boolean acceptStoryOffer(ServerLevel world, ServerPlayer player, StoryLane selectedLane) {
        if (world == null || player == null) {
            return false;
        }
        if (de.quest.shrine.VillageIntroRequestService.blocksActions(world,player.getUUID())) return false;
        ensureLoaded(world.getServer());
        PartyRuntime party = partyFor(player.getUUID());
        if (party == null) {
            player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.story.none").withStyle(ChatFormatting.RED), false);
            return false;
        }

        StoryLane lane = selectStoryOffer(world, player, party, selectedLane);
        if (lane == null) return false;
        QuestJoinOffer offer = offersFor(party,lane).get(player.getUUID());
        StoryArcType arcType = offer == null ? null : parseStoryType(offer.questId());
        int chapterIndex = offer == null ? -1 : parseStoryChapter(offer.questId());
        if (offer == null || arcType == null || chapterIndex < 0 || !isOfferStillValid(world, party, player.getUUID(), offer, arcType, chapterIndex)) {
            clearStoryOffer(party, player.getUUID(), lane);
            player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.story.expired").withStyle(ChatFormatting.RED), false);
            storePersistentState(world.getServer());
            return false;
        }
        if (!canJoinStoryShare(world, player.getUUID(), arcType, chapterIndex, true)) {
            clearStoryOffer(party, player.getUUID(), lane);
            storePersistentState(world.getServer());
            return false;
        }

        StoryChapterDefinition chapter = StoryQuestService.chapter(world, player.getUUID(), arcType);
        if (chapter == null) {
            chapter = StoryQuestService.definition(arcType) == null ? null : StoryQuestService.definition(arcType).chapter(chapterIndex);
        }
        if (chapterIndex == arcType.chapterCount() - 1 && !de.quest.data.PersonalResetReceipts.storyPaid(QuestState.get(world.getServer()).getPlayerData(player.getUUID()),arcType,chapterIndex)
                && !de.quest.reward.VanillaRewardService.prepare(world, player, lane==StoryLane.KINGDOM ? "kingdom_story" : "story",
                rewardStoryKey(world, player.getUUID(), arcType, chapterIndex), arcType.id() + ":" + chapterIndex,
                de.quest.quest.repeatable.RepeatableTargetProfile.NORMAL, () -> acceptStoryOffer(world, player, lane))) return false;
        syncStoryMember(world, party, player, arcType, chapterIndex, chapter, offer.sourceId());
        storePersistentState(world.getServer());
        refreshPartyUi(world, party);
        return true;
    }

    public static boolean declineStoryOffer(ServerLevel world, ServerPlayer player) {
        return declineStoryOffer(world, player, null);
    }

    public static boolean declineStoryOffer(ServerLevel world, ServerPlayer player, StoryLane selectedLane) {
        if (world == null || player == null) {
            return false;
        }
        ensureLoaded(world.getServer());
        PartyRuntime party = partyFor(player.getUUID());
        StoryLane lane = selectStoryOffer(world, player, party, selectedLane);
        if (lane == null) return false;
        if (party == null || clearStoryOffer(party, player.getUUID(), lane) == null) {
            player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.story.none").withStyle(ChatFormatting.RED), false);
            return false;
        }
        player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.story.declined").withStyle(ChatFormatting.GRAY), false);
        storePersistentState(world.getServer());
        return true;
    }

    public static boolean acceptPilgrimOffer(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }
        ensureLoaded(world.getServer());
        PartyRuntime party = partyFor(player.getUUID());
        if (party == null) {
            player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.pilgrim.none").withStyle(ChatFormatting.RED), false);
            return false;
        }

        QuestJoinOffer offer = party.pilgrimOffers().get(player.getUUID());
        PilgrimContractType type = offer == null ? null : PilgrimContractType.fromId(offer.questId());
        if (offer == null || type == null || !isOfferStillValid(world, party, player.getUUID(), offer, type)) {
            clearPilgrimOffer(party, player.getUUID());
            player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.pilgrim.expired").withStyle(ChatFormatting.RED), false);
            storePersistentState(world.getServer());
            return false;
        }
        if (!canJoinPilgrimShare(world, player.getUUID(), type, true)) {
            clearPilgrimOffer(party, player.getUUID());
            storePersistentState(world.getServer());
            return false;
        }

        var rewardState = QuestState.get(world.getServer()).getPlayerData(player.getUUID()).vanillaRewards();
        if (!de.quest.reward.VanillaRewardService.prepare(world, player, "pilgrim", rewardPilgrimKey(world, player.getUUID(), type),
                rewardState.nextIdentity("pilgrim", type.id()), sharedPilgrimTargetProfile(world,party,type),
                () -> acceptPilgrimOffer(world, player))) return false;
        syncPilgrimMember(world, party, player, type, offer.sourceId());
        storePersistentState(world.getServer());
        refreshPartyUi(world, party);
        return true;
    }

    public static boolean declinePilgrimOffer(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }
        ensureLoaded(world.getServer());
        PartyRuntime party = partyFor(player.getUUID());
        if (party == null || clearPilgrimOffer(party, player.getUUID()) == null) {
            player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.pilgrim.none").withStyle(ChatFormatting.RED), false);
            return false;
        }
        player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.pilgrim.declined").withStyle(ChatFormatting.GRAY), false);
        storePersistentState(world.getServer());
        return true;
    }

    private static void ensureLoaded(MinecraftServer server) {
        if (!loaded && isEnabled(server)) {
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
            ServerPlayer inviter = server.getPlayerList().getPlayer(invite.inviterId());
            if (inviter != null) {
                inviter.sendSystemMessage(Component.translatable("message.village-quest.party.invite.expired").withStyle(ChatFormatting.GRAY), false);
                refreshOpenUi(inviter);
            }
            ServerPlayer target = server.getPlayerList().getPlayer(targetId);
            if (target != null) {
                target.sendSystemMessage(Component.translatable("message.village-quest.party.invite.expired_target").withStyle(ChatFormatting.GRAY), false);
                refreshOpenUi(target);
            }
        }
        if (!expired.isEmpty()) {
            storePersistentState(server);
        }
    }

    private static void expireDisconnectedMembers(ServerLevel world, long now) {
        if (world == null) {
            return;
        }
        List<ExpiryTarget> expired = new ArrayList<>();
        for (PartyRuntime party : PARTIES.values()) {
            for (var entry : party.disconnectDeadlines().entrySet()) {
                if (entry.getValue() <= now && world.getServer().getPlayerList().getPlayer(entry.getKey()) == null
                        && !de.quest.data.PersonalResetService.blocksActions(world,entry.getKey())) {
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

    private static void cleanupStaleOffers(ServerLevel world) {
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

    private static boolean cleanupDailyOffers(ServerLevel world, PartyRuntime party) {
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

    private static boolean cleanupWeeklyOffers(ServerLevel world, PartyRuntime party) {
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

    private static boolean cleanupStoryOffers(ServerLevel world, PartyRuntime party) {
        boolean core = cleanupStoryOffers(world,party,StoryLane.CORE);
        return cleanupStoryOffers(world,party,StoryLane.KINGDOM) || core;
    }

    private static boolean cleanupStoryOffers(ServerLevel world, PartyRuntime party, StoryLane lane) {
        StoryArcType activeType = activeStoryType(party,lane);
        int chapterIndex = activeStoryChapterIndex(party,lane);
        if (activeType == null || chapterIndex < 0) {
            if (!offersFor(party,lane).isEmpty()) {
                offersFor(party,lane).clear();
                return true;
            }
            return false;
        }
        boolean changed = false;
        List<UUID> remove = new ArrayList<>();
        for (var entry : offersFor(party,lane).entrySet()) {
            if (!isOfferStillValid(world, party, entry.getKey(), entry.getValue(), activeType, chapterIndex)) {
                remove.add(entry.getKey());
            }
        }
        for (UUID memberId : remove) {
            offersFor(party,lane).remove(memberId);
            changed = true;
        }
        return changed;
    }

    private static boolean cleanupPilgrimOffers(ServerLevel world, PartyRuntime party) {
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

    private static boolean ensureEnabled(MinecraftServer server, ServerPlayer actor) {
        if (isEnabled(server)) {
            return true;
        }
        if (actor != null) {
            actor.sendSystemMessage(Component.translatable("message.village-quest.party.unavailable").withStyle(ChatFormatting.GRAY), false);
        }
        return false;
    }

    public static int countPartyInventoryItem(ServerLevel world, UUID playerId, Item item) {
        return QuestPartyInventory.countItem(orderedOnlineMembers(world, playerId), item);
    }

    public static boolean consumePartyInventoryItem(ServerLevel world, UUID playerId, Item item, int amount) {
        return QuestPartyInventory.consumeItem(orderedOnlineMembers(world, playerId), item, amount);
    }

    public static int countDailyTurnInItem(ServerLevel world,
                                           UUID playerId,
                                           DailyQuestService.DailyQuestType type,
                                           Item item) {
        return QuestPartyInventory.countItem(
                onlineMembers(world, activeDailyMembers(world, playerId, type, false)), item);
    }

    public static boolean consumeDailyTurnInItem(ServerLevel world,
                                                 UUID playerId,
                                                 DailyQuestService.DailyQuestType type,
                                                 Item item,
                                                 int amount) {
        return QuestPartyInventory.consumeItem(
                onlineMembers(world, activeDailyMembers(world, playerId, type, false)), item, amount);
    }

    public static int countWeeklyTurnInItem(ServerLevel world,
                                            UUID playerId,
                                            WeeklyQuestService.WeeklyQuestType type,
                                            Item item) {
        return QuestPartyInventory.countItem(
                onlineMembers(world, activeWeeklyMembers(world, playerId, type, false)), item);
    }

    public static boolean consumeWeeklyTurnInItem(ServerLevel world,
                                                  UUID playerId,
                                                  WeeklyQuestService.WeeklyQuestType type,
                                                  Item item,
                                                  int amount) {
        return QuestPartyInventory.consumeItem(
                onlineMembers(world, activeWeeklyMembers(world, playerId, type, false)), item, amount);
    }

    public static void clearDailySessionIfFinished(ServerLevel world, UUID playerId, DailyQuestService.DailyQuestType type) {
        PartyRuntime party = partyFor(playerId);
        if (party == null || activeDailyType(party) != type) {
            return;
        }
        // Completed members must not rejoin yesterday's consumed session for a second payout.
        party.daily().syncedMembers().removeIf(id -> {
            PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(id);
            return !data.hasActiveDaily() || data.getDailyChoice() != type;
        });
        if (dailySharedMemberCount(world, playerId, type) <= 0) {
            party.daily().clear();
            party.dailyOffers().clear();
        }
        storePersistentState(world.getServer());
    }

    public static void clearWeeklySessionIfFinished(ServerLevel world, UUID playerId, WeeklyQuestService.WeeklyQuestType type) {
        PartyRuntime party = partyFor(playerId);
        if (party == null || activeWeeklyType(party) != type) {
            return;
        }
        party.weekly().syncedMembers().removeIf(id -> {
            PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(id);
            return !data.hasActiveWeekly() || data.getWeeklyChoice() != type;
        });
        if (weeklySharedMemberCount(world, playerId, type) <= 0) {
            party.weekly().clear();
            party.weeklyOffers().clear();
        }
        storePersistentState(world.getServer());
    }

    /** Preserve every other member and session, including a remaining single member. */
    public static void detachForPersonalRestart(ServerLevel world, UUID owner) {
        loadForPersonalRestart(world.getServer());
        removePendingInviteFor(owner);
        PartyRuntime party = partyFor(owner);
        if (party == null) return;
        for (var session : List.of(party.daily(), party.weekly(), party.story(), party.kingdomStory(), party.pilgrim())) {
            session.unmarkSynced(owner);
            session.setFlag("personal_reset.excluded." + owner, true);
        }
        party.members().remove(owner); party.memberNames().remove(owner); party.disconnectDeadlines().remove(owner);
        clearDailyOffer(party, owner); clearWeeklyOffer(party, owner); clearStoryOffer(party, owner); clearPilgrimOffer(party, owner);
        MEMBER_TO_PARTY.remove(owner);
        if (party.members().isEmpty()) { PARTIES.remove(party.id()); removeInvitesForParty(party.id()); }
        else if (owner.equals(party.leaderId())) party.setLeaderId(party.members().iterator().next());
        storePersistentState(world.getServer());
    }

    /** Private integrated worlds also retain party documents. Do not silently overwrite them. */
    private static void loadForPersonalRestart(MinecraftServer server) {
        var state=QuestState.get(server);
        if(!state.storyLanesWritable())throw new IllegalStateException("Unknown party state");
        if(!loaded) {
            QuestPartyPersistence.read(state.getQuestPartyState(),PARTIES,MEMBER_TO_PARTY,INVITES);
            loaded=true;
        }
    }
    public static boolean personalRestartDetached(ServerLevel world,UUID owner) {
        loadForPersonalRestart(world.getServer());
        return !MEMBER_TO_PARTY.containsKey(owner) && !INVITES.containsKey(owner)
                && PARTIES.values().stream().noneMatch(p -> p.members().contains(owner));
    }

    private static boolean leaveInternal(ServerLevel world, UUID playerId, boolean notifyPlayer, boolean disconnecting) {
        PartyRuntime party = partyFor(playerId);
        if (world == null || party == null) {
            return false;
        }
        if (de.quest.data.PersonalResetService.blocksActions(world,playerId)) return false;

        ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
        Component departingName=QuestPartyMessaging.nameOf(world,party,playerId);
        copySharedProgressToPlayer(world, party, playerId);
        party.members().remove(playerId);
        party.memberNames().remove(playerId);
        party.disconnectDeadlines().remove(playerId);
        clearDailyOffer(party, playerId);
        clearWeeklyOffer(party, playerId);
        clearStoryOffer(party, playerId);
        clearPilgrimOffer(party, playerId);
        party.daily().unmarkSynced(playerId);
        party.weekly().unmarkSynced(playerId);
        party.story().unmarkSynced(playerId);
        party.kingdomStory().unmarkSynced(playerId);
        party.pilgrim().unmarkSynced(playerId);
        MEMBER_TO_PARTY.remove(playerId);
        removePendingInviteFor(playerId);

        if (player != null && notifyPlayer) {
            player.sendSystemMessage(Component.translatable("message.village-quest.party.left").withStyle(ChatFormatting.GRAY), false);
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
            if (de.quest.data.PersonalResetService.blocksActions(world,remainingId)) {
                storePersistentState(world.getServer());return true;
            }
            copySharedProgressToPlayer(world, party, remainingId);
            MEMBER_TO_PARTY.remove(remainingId);
            ServerPlayer remaining = world.getServer().getPlayerList().getPlayer(remainingId);
            if (remaining != null) {
                remaining.sendSystemMessage(Component.translatable("message.village-quest.party.disbanded").withStyle(ChatFormatting.GRAY), false);
                refreshPlayer(world, remaining);
            }
            PARTIES.remove(party.id());
            removeInvitesForParty(party.id());
            storePersistentState(world.getServer());
            return true;
        }

        if (!disconnecting) {
            broadcast(world, party, Component.translatable("message.village-quest.party.member_left", departingName).withStyle(ChatFormatting.GRAY));
        }
        storePersistentState(world.getServer());
        refreshPartyUi(world, party);
        return true;
    }

    private static PartyRuntime getOrCreateParty(ServerLevel world, ServerPlayer leader) {
        PartyRuntime existing = partyFor(leader.getUUID());
        if (existing != null) {
            return existing;
        }
        PartyRuntime created = new PartyRuntime(UUID.randomUUID(), leader.getUUID());
        created.members().add(leader.getUUID());
        QuestPartyMessaging.rememberName(created,leader);
        PARTIES.put(created.id(), created);
        MEMBER_TO_PARTY.put(leader.getUUID(), created.id());
        seedSessionsFromMember(world, created, leader.getUUID());
        return created;
    }

    static PartyRuntime partyFor(UUID memberId) {
        if (memberId == null) {
            return null;
        }
        UUID partyId = MEMBER_TO_PARTY.get(memberId);
        return partyId == null ? null : PARTIES.get(partyId);
    }

    /** Retire a shared chapter into each still-admitted personal claim before reusing its lane. */
    private static SharedQuestRuntime ensurePartyStorySession(ServerLevel world, PartyRuntime party,
                                                               StoryArcType arc, int chapter) {
        var previous=sessionFor(party,arc);
        if (previous!=null && previous.questId()!=null
                && !previous.matches(storySessionId(arc,chapter),chapter)) {
            var oldArc=parseStoryType(previous.questId());
            int oldChapter=parseStoryChapter(previous.questId());
            if (oldArc!=null && oldChapter>=0 && oldChapter<oldArc.chapterCount()) {
                for (UUID owner:previous.syncedMembers()) {
                    if (!party.members().contains(owner)) continue;
                    var data=QuestState.get(world.getServer()).getPlayerData(owner);
                    if (data.getActiveStoryArc(StoryLane.forArc(oldArc))!=oldArc
                            || data.getStoryChapterProgress(oldArc.id())!=oldChapter
                            || (oldArc==StoryArcType.LIVING_KINGDOMS && !matchesKingdomVillageBinding(data,previous))) continue;
                    applySharedStoryProgress(data,previous,oldArc,oldChapter);
                }
                QuestState.get(world.getServer()).setDirty();
            }
        }
        return QuestPartySessions.ensureStorySession(party,arc,chapter);
    }

    private static void seedSessionsFromMember(ServerLevel world, PartyRuntime party, UUID memberId) {
        if (world == null || party == null || memberId == null) {
            return;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
        long day = QuestCalendarService.currentDay();
        if (data.hasActiveDaily()
                && QuestShareProfiles.isDailyShareable(data.getDailyChoice())) {
            mergeDailyProgressIntoSession(world, memberId, data.getDailyChoice(), ensureDailySession(party, data.getDailyChoice()));
        }

        long cycle = QuestCalendarService.currentWeekCycle();
        if (data.hasActiveWeekly()
                && QuestShareProfiles.isWeeklyShareable(data.getWeeklyChoice())) {
            mergeWeeklyProgressIntoSession(world, memberId, data.getWeeklyChoice(), ensureWeeklySession(party, data.getWeeklyChoice()));
        }

        for (StoryLane lane : StoryLane.values()) {
            StoryArcType activeStoryArc = data.getActiveStoryArc(lane);
            if (activeStoryArc != null && QuestShareProfiles.isStoryShareable(activeStoryArc)) {
                int chapterIndex = data.getStoryChapterProgress(activeStoryArc.id());
                mergeStoryProgressIntoSession(world, memberId, activeStoryArc, chapterIndex, ensurePartyStorySession(world, party, activeStoryArc, chapterIndex));
            }
        }

        PilgrimContractType activePilgrimType = PilgrimContractType.fromId(data.getActivePilgrimContractId());
        if (activePilgrimType != null && QuestShareProfiles.isPilgrimShareable(activePilgrimType)) {
            mergePilgrimProgressIntoSession(world, memberId, activePilgrimType, ensurePilgrimSession(party, activePilgrimType));
        }
    }

    private static void queueActiveQuestOffersForJoiner(ServerLevel world, PartyRuntime party, ServerPlayer player) {
        if (world == null || party == null || player == null) {
            return;
        }
        DailyQuestService.DailyQuestType dailyType = activeDailyType(party);
        if (dailyType != null) {
            queueDailyQuestOffer(world, party, player.getUUID(), dailyType, dailyOfferSourceId(world, party, player.getUUID()), true);
        }
        WeeklyQuestService.WeeklyQuestType weeklyType = activeWeeklyType(party);
        if (weeklyType != null) {
            queueWeeklyQuestOffer(world, party, player.getUUID(), weeklyType, weeklyOfferSourceId(world, party, player.getUUID()), true);
        }
        for (StoryLane lane : StoryLane.values()) {
            StoryArcType storyType = activeStoryType(party,lane);
            int storyChapterIndex = activeStoryChapterIndex(party,lane);
            if (storyType != null && storyChapterIndex >= 0) {
                queueStoryQuestOffer(world, party, player.getUUID(), storyType, storyChapterIndex, storyOfferSourceId(world, party, player.getUUID(),lane), true);
            }
        }
        PilgrimContractType pilgrimType = activePilgrimType(party);
        if (pilgrimType != null) {
            queuePilgrimQuestOffer(world, party, player.getUUID(), pilgrimType, pilgrimOfferSourceId(world, party, player.getUUID()), true);
        }
    }

    private static void syncPartyMembersIntoDaily(ServerLevel world,
                                                  PartyRuntime party,
                                                  DailyQuestService.DailyQuestType type,
                                                  DailyQuestDefinition definition,
                                                  UUID sourceId) {
        for (UUID memberId : party.members()) {
            ServerPlayer member = world.getServer().getPlayerList().getPlayer(memberId);
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

    private static void syncPartyMembersIntoWeekly(ServerLevel world,
                                                   PartyRuntime party,
                                                   WeeklyQuestService.WeeklyQuestType type,
                                                   WeeklyQuestDefinition definition,
                                                   UUID sourceId) {
        for (UUID memberId : party.members()) {
            ServerPlayer member = world.getServer().getPlayerList().getPlayer(memberId);
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

    private static void syncDailyMember(ServerLevel world,
                                        PartyRuntime party,
                                        ServerPlayer member,
                                        DailyQuestService.DailyQuestType type,
                                        DailyQuestDefinition definition,
                                        UUID sourceId) {
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(member.getUUID());
        if (!FirstDailyChoiceService.canUseSharedDaily(data)) {
            clearDailyOffer(party, member.getUUID());
            return;
        }
        long day = QuestCalendarService.currentDay();
        SharedQuestRuntime session = ensureDailySession(party, type);
        if (session == null) {
            return;
        }

        boolean sameQuestActive = data.hasActiveDaily() && data.getDailyChoice() == type;
        if (sameQuestActive) {
            if (sourceId != null) {
                var source = QuestState.get(world.getServer()).getPlayerData(sourceId);
                if (data.getDailyTargetProfile() != source.getDailyTargetProfile()) return;
                if (type == DailyQuestService.DailyQuestType.SMITH_SMELTING) {
                    boolean ownModern = data.vanillaRewards().snapshot("daily",type.name() + ":" + data.getAcceptedDay()).getIntOr("version",0) >= 1;
                    boolean sourceModern = source.vanillaRewards().snapshot("daily",type.name() + ":" + source.getAcceptedDay()).getIntOr("version",0) >= 1;
                    if (ownModern != sourceModern) return;
                }
            }
            mergeDailyProgressIntoSession(world, member.getUUID(), type, session);
            if (!session.hasSynced(member.getUUID())) return;
            clearDailyOffer(party, member.getUUID());
            data.setDailyTargetProfile(sharedDailyTargetProfile(world, party, type));
            if (sourceId != null && !sourceId.equals(member.getUUID())) {
                member.sendSystemMessage(Component.translatable("message.village-quest.party.shared_daily", nameOf(world, sourceId)).withStyle(ChatFormatting.GRAY), false);
            }
            refreshPlayer(world, member);
            return;
        }

        if (data.getLastRewardDay() == day || data.hasActiveDailySlot()) {
            if (sourceId != null && !sourceId.equals(member.getUUID())) {
                member.sendSystemMessage(Component.translatable("message.village-quest.party.sync_skipped_daily").withStyle(ChatFormatting.GRAY), false);
            }
            return;
        }

        clearDailyOffer(party, member.getUUID());
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
        QuestState.get(world.getServer()).setDirty();
        session.markSynced(member.getUUID());
        if (definition != null) {
            definition.onAccepted(world, member);
        }
        member.sendSystemMessage(Component.translatable("message.village-quest.party.shared_daily", nameOf(world, sourceId == null ? member.getUUID() : sourceId)).withStyle(ChatFormatting.GRAY), false);
        member.sendSystemMessage(Texts.acceptedTitle(definition == null ? DailyQuestService.displayKey(type) : definition.title(), ChatFormatting.GREEN), false);
        QuestTrackerService.enableForAcceptedQuest(world, member);
        DailyQuestService.sendCurrentProgressActionbar(world, member);
        refreshPlayer(world, member);
    }

    private static void syncWeeklyMember(ServerLevel world,
                                         PartyRuntime party,
                                         ServerPlayer member,
                                         WeeklyQuestService.WeeklyQuestType type,
                                         WeeklyQuestDefinition definition,
                                         UUID sourceId) {
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(member.getUUID());
        long cycle = QuestCalendarService.currentWeekCycle();
        SharedQuestRuntime session = ensureWeeklySession(party, type);
        if (session == null) {
            return;
        }

        boolean sameQuestActive = data.hasActiveWeekly() && data.getWeeklyChoice() == type;
        if (sameQuestActive) {
            mergeWeeklyProgressIntoSession(world, member.getUUID(), type, session);
            if (!session.hasSynced(member.getUUID())) return;
            clearWeeklyOffer(party, member.getUUID());
            data.setWeeklyTargetProfile(sharedWeeklyTargetProfile(world, party, type));
            if (sourceId != null && !sourceId.equals(member.getUUID())) {
                member.sendSystemMessage(Component.translatable("message.village-quest.party.shared_weekly", nameOf(world, sourceId)).withStyle(ChatFormatting.GRAY), false);
            }
            refreshPlayer(world, member);
            return;
        }

        if (data.getWeeklyRewardCycle() == cycle || data.hasActiveWeekly()) {
            if (sourceId != null && !sourceId.equals(member.getUUID())) {
                member.sendSystemMessage(Component.translatable("message.village-quest.party.sync_skipped_weekly").withStyle(ChatFormatting.GRAY), false);
            }
            return;
        }

        clearWeeklyOffer(party, member.getUUID());
        data.setWeeklyChoice(type);
        data.setWeeklyChoiceCycle(cycle);
        data.setWeeklyTargetProfile(sharedWeeklyTargetProfile(world, party, type));
        data.clearWeeklyProgress();
        data.setWeeklyProgressCycle(cycle);
        data.setWeeklyAcceptedCycle(cycle);
        data.markWeeklyDiscovered(type.name());
        QuestState.get(world.getServer()).setDirty();
        session.markSynced(member.getUUID());
        if (definition != null) {
            definition.onAccepted(world, member);
        }
        member.sendSystemMessage(Component.translatable("message.village-quest.party.shared_weekly", nameOf(world, sourceId == null ? member.getUUID() : sourceId)).withStyle(ChatFormatting.GRAY), false);
        member.sendSystemMessage(Texts.acceptedTitle(definition == null ? WeeklyQuestService.displayName(type) : definition.title(), ChatFormatting.GOLD), false);
        QuestTrackerService.enableForAcceptedQuest(world, member);
        refreshPlayer(world, member);
    }

    private static void syncPartyMembersIntoStory(ServerLevel world,
                                                  PartyRuntime party,
                                                  StoryArcType arcType,
                                                  int chapterIndex,
                                                  StoryChapterDefinition chapter,
                                                  UUID sourceId) {
        for (UUID memberId : party.members()) {
            ServerPlayer member = world.getServer().getPlayerList().getPlayer(memberId);
            if (memberId.equals(sourceId)) {
                continue;
            }
            if (member != null && sessionFor(party,arcType).hasSynced(memberId)
                    && memberHasActiveStory(world, memberId, arcType, chapterIndex)) {
                syncStoryMember(world, party, member, arcType, chapterIndex, chapter, sourceId);
                continue;
            }
            queueStoryQuestOffer(world, party, memberId, arcType, chapterIndex, sourceId, false);
        }
    }

    private static void syncPartyMembersIntoPilgrim(ServerLevel world,
                                                    PartyRuntime party,
                                                    PilgrimContractType type,
                                                    UUID sourceId) {
        for (UUID memberId : party.members()) {
            ServerPlayer member = world.getServer().getPlayerList().getPlayer(memberId);
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

    private static void syncStoryMember(ServerLevel world,
                                        PartyRuntime party,
                                        ServerPlayer member,
                                        StoryArcType arcType,
                                        int chapterIndex,
                                        StoryChapterDefinition chapter,
                                        UUID sourceId) {
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(member.getUUID());
        SharedQuestRuntime session = ensurePartyStorySession(world, party, arcType, chapterIndex);
        if (session == null) {
            return;
        }

        boolean sameChapterActive = data.getActiveStoryArc(StoryLane.forArc(arcType)) == arcType && data.getStoryChapterProgress(arcType.id()) == chapterIndex;
        if (sameChapterActive) {
            mergeStoryProgressIntoSession(world, member.getUUID(), arcType, chapterIndex, session);
            if (!session.hasSynced(member.getUUID())) return;
            clearStoryOffer(party, member.getUUID(), StoryLane.forArc(arcType));
            if (sourceId != null && !sourceId.equals(member.getUUID())) {
                member.sendSystemMessage(Component.translatable("message.village-quest.party.shared_story", nameOf(world, sourceId)).withStyle(ChatFormatting.GRAY), false);
            }
            refreshPlayer(world, member);
            return;
        }

        if (data.getActiveStoryArc(StoryLane.forArc(arcType)) != null && data.getActiveStoryArc(StoryLane.forArc(arcType)) != arcType) {
            if (sourceId != null && !sourceId.equals(member.getUUID())) {
                member.sendSystemMessage(Component.translatable("message.village-quest.party.sync_skipped_story").withStyle(ChatFormatting.GRAY), false);
            }
            return;
        }

        clearStoryOffer(party, member.getUUID(), StoryLane.forArc(arcType));
        if (StoryLane.forArc(arcType)==StoryLane.CORE) data.clearStoryProgress();
        data.setActiveStoryArc(StoryLane.forArc(arcType),arcType);
        data.setStoryDiscovered(arcType.id(), true);
        data.setStoryChapterProgress(arcType.id(), chapterIndex);
        if (arcType==StoryArcType.LIVING_KINGDOMS) {
            UUID village=kingdomVillageBinding(session);
            if (village!=null) de.quest.kingdom.KingdomStoryProgress.bind(data,village,true);
        }
        QuestState.get(world.getServer()).setDirty();
        session.markSynced(member.getUUID());
        if (chapter != null) {
            chapter.onAccepted(world, member);
        }
        member.sendSystemMessage(Component.translatable("message.village-quest.party.shared_story", nameOf(world, sourceId == null ? member.getUUID() : sourceId)).withStyle(ChatFormatting.GRAY), false);
        member.sendSystemMessage(Texts.acceptedTitle(chapter == null ? StoryQuestService.definition(arcType).title() : chapter.title(), ChatFormatting.GOLD), false);
        QuestTrackerService.enableForAcceptedQuest(world, member);
        refreshPlayer(world, member);
    }

    private static void syncPilgrimMember(ServerLevel world,
                                          PartyRuntime party,
                                          ServerPlayer member,
                                          PilgrimContractType type,
                                          UUID sourceId) {
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(member.getUUID());
        SharedQuestRuntime session = ensurePilgrimSession(party, type);
        if (session == null) {
            return;
        }

        boolean sameContractActive = memberHasActivePilgrim(world, member.getUUID(), type);
        if (sameContractActive) {
            mergePilgrimProgressIntoSession(world, member.getUUID(), type, session);
            if (!session.hasSynced(member.getUUID())) return;
            clearPilgrimOffer(party, member.getUUID());
            data.setActivePilgrimTargetProfile(sharedPilgrimTargetProfile(world, party, type));
            if (sourceId != null && !sourceId.equals(member.getUUID())) {
                member.sendSystemMessage(Component.translatable("message.village-quest.party.shared_pilgrim", nameOf(world, sourceId)).withStyle(ChatFormatting.GRAY), false);
            }
            refreshPlayer(world, member);
            return;
        }

        if (PilgrimContractType.fromId(data.getActivePilgrimContractId()) != null) {
            if (sourceId != null && !sourceId.equals(member.getUUID())) {
                member.sendSystemMessage(Component.translatable("message.village-quest.party.sync_skipped_pilgrim").withStyle(ChatFormatting.GRAY), false);
            }
            return;
        }

        clearPilgrimOffer(party, member.getUUID());
        data.clearPilgrimProgress();
        data.setPilgrimFlag("pilgrim_contract_ready", false);
        data.setPilgrimFlag("pilgrim_contract_suppress_offer", false);
        data.setActivePilgrimContractId(type.id());
        data.setActivePilgrimTargetProfile(sharedPilgrimTargetProfile(world, party, type));
        data.setPilgrimOfferDay(TimeUtil.currentDay());
        QuestState.get(world.getServer()).setDirty();
        session.markSynced(member.getUUID());
        member.sendSystemMessage(Component.translatable("message.village-quest.party.shared_pilgrim", nameOf(world, sourceId == null ? member.getUUID() : sourceId)).withStyle(ChatFormatting.GRAY), false);
        member.sendSystemMessage(Texts.acceptedTitle(PilgrimContractService.title(type), ChatFormatting.GOLD), false);
        QuestTrackerService.enableForAcceptedQuest(world, member);
        refreshPlayer(world, member);
    }

    private static void mergeStoryProgressIntoSession(ServerLevel world,
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
        if (arcType==StoryArcType.LIVING_KINGDOMS && !mergeKingdomVillageBinding(data,session)) return;
        if (arcType == StoryArcType.NIGHT_BELLS) {
            if(!de.quest.content.story.NightBellsMarkerProgress.supported(data::getStoryInt,chapterIndex)
                    || !de.quest.content.story.NightBellsMarkerProgress.supported(session::getInt,chapterIndex))return;
            mergeNightBellsVillageBinding(data, session);
            mergeNightBellsMarkers(data,session,chapterIndex);
        }
        for (String key : profile.intKeys()) {
            if (arcType == StoryArcType.NIGHT_BELLS && (isNightBellsVillageKey(key)
                    || de.quest.content.story.NightBellsMarkerProgress.isMarkerKey(key,chapterIndex))) {
                continue;
            }
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

    static UUID kingdomVillageBinding(SharedQuestRuntime session) {
        if (session==null) return null;
        var bindings=session.flags().stream().filter(flag -> flag.startsWith("kingdoms.bound.")).toList();
        if (bindings.size()!=1) return null;
        try {
            UUID id=UUID.fromString(bindings.getFirst().substring("kingdoms.bound.".length()));
            return id.equals(de.quest.kingdom.BuildingSessionPolicy.NONE)?null:id;
        } catch(IllegalArgumentException ignored) { return null; }
    }
    static boolean matchesKingdomVillageBinding(PlayerQuestData data, SharedQuestRuntime session) {
        if (data==null || session==null) return false;
        UUID target=kingdomVillageBinding(session);
        return target!=null ? target.equals(de.quest.kingdom.KingdomStoryProgress.target(data))
                : session.flags().stream().noneMatch(flag -> flag.startsWith("kingdoms.bound."));
    }
    static boolean mergeKingdomVillageBinding(PlayerQuestData data, SharedQuestRuntime session) {
        if (data==null || session==null) return false;
        UUID own=de.quest.kingdom.KingdomStoryProgress.target(data), shared=kingdomVillageBinding(session);
        if (shared==null && session.flags().stream().anyMatch(flag -> flag.startsWith("kingdoms.bound."))) return false;
        if (shared!=null) return own==null ? de.quest.kingdom.KingdomStoryProgress.bind(data,shared,false) : own.equals(shared);
        if (own!=null) session.setFlag("kingdoms.bound."+own,true);
        return true;
    }
    static boolean updateKingdomVillageBinding(Map<UUID,PlayerQuestData> members,SharedQuestRuntime session,
                                               UUID actor,UUID leader,UUID village,boolean explicit,int chapter) {
        if (members==null || members.size()>64 || session==null || actor==null || village==null
                || village.equals(de.quest.kingdom.BuildingSessionPolicy.NONE) || chapter<0 || chapter>=StoryArcType.LIVING_KINGDOMS.chapterCount()) return false;
        var source=members.get(actor);
        if (source==null || !session.hasSynced(actor) || !source.isKingdomStoryActive()
                || source.getStoryChapterProgress(StoryArcType.LIVING_KINGDOMS.id())!=chapter || !matchesKingdomVillageBinding(source,session)) return false;
        UUID old=kingdomVillageBinding(session), personal=de.quest.kingdom.KingdomStoryProgress.target(source);
        if (!village.equals(old) && session.hasFlag(StoryQuestKeys.SHARED_TURN_IN_CONSUMED)) return false;
        if (old!=null && !old.equals(village) && (!explicit || !actor.equals(leader))
                || personal!=null && !personal.equals(village) && !explicit) return false;
        var admitted=members.entrySet().stream().filter(entry -> session.hasSynced(entry.getKey()) && entry.getValue()!=null
                && entry.getValue().isKingdomStoryActive()
                && entry.getValue().getStoryChapterProgress(StoryArcType.LIVING_KINGDOMS.id())==chapter
                && matchesKingdomVillageBinding(entry.getValue(),session)).map(Map.Entry::getValue).toList();
        if (admitted.stream().anyMatch(data -> !village.equals(de.quest.kingdom.KingdomStoryProgress.target(data))
                && data.hasStoryFlag(StoryQuestKeys.sharedTurnInConsumed(StoryArcType.LIVING_KINGDOMS,chapter)))) return false;
        if (old==null || !old.equals(village)) {
            session.flags().removeIf(flag -> flag.startsWith("kingdoms.bound.")); session.setFlag("kingdoms.bound."+village,true);
        }
        for(var member:admitted) de.quest.kingdom.KingdomStoryProgress.bind(member,village,explicit);
        return true;
    }

    private static void migrateNightBellsVillageBindings(MinecraftServer server) {
        for (PartyRuntime party : PARTIES.values()) {
            if (QuestPartySessions.activeStoryType(party) != StoryArcType.NIGHT_BELLS
                    || party.story().getInt(StoryQuestKeys.NIGHT_BELLS_VILLAGE_BOUND) != 0) {
                continue;
            }
            UUID leaderId = party.leaderId();
            if (party.story().hasSynced(leaderId)) {
                mergeNightBellsVillageBinding(QuestState.get(server).getPlayerData(leaderId), party.story());
            }
            for (UUID memberId : party.members()) {
                if (party.story().getInt(StoryQuestKeys.NIGHT_BELLS_VILLAGE_BOUND) != 0) break;
                if (party.story().hasSynced(memberId)) {
                    mergeNightBellsVillageBinding(QuestState.get(server).getPlayerData(memberId), party.story());
                }
            }
        }
    }

    static void mergeNightBellsVillageBinding(PlayerQuestData data, SharedQuestRuntime session) {
        if (data == null || session == null
                || session.getInt(StoryQuestKeys.NIGHT_BELLS_VILLAGE_BOUND) != 0
                || data.getStoryInt(StoryQuestKeys.NIGHT_BELLS_VILLAGE_BOUND) == 0) {
            return;
        }
        session.setInt(StoryQuestKeys.NIGHT_BELLS_VILLAGE_X,
                data.getStoryInt(StoryQuestKeys.NIGHT_BELLS_VILLAGE_X));
        session.setInt(StoryQuestKeys.NIGHT_BELLS_VILLAGE_Z,
                data.getStoryInt(StoryQuestKeys.NIGHT_BELLS_VILLAGE_Z));
        session.setInt(StoryQuestKeys.NIGHT_BELLS_VILLAGE_BOUND, 1);
    }

    static void mergeNightBellsMarkers(PlayerQuestData data,SharedQuestRuntime session,int chapter) {
        if(data.getStoryInt(StoryQuestKeys.NIGHT_BELLS_VILLAGE_BOUND)==0
                || session.getInt(StoryQuestKeys.NIGHT_BELLS_VILLAGE_BOUND)==0
                || data.getStoryInt(StoryQuestKeys.NIGHT_BELLS_VILLAGE_X)!=session.getInt(StoryQuestKeys.NIGHT_BELLS_VILLAGE_X)
                || data.getStoryInt(StoryQuestKeys.NIGHT_BELLS_VILLAGE_Z)!=session.getInt(StoryQuestKeys.NIGHT_BELLS_VILLAGE_Z))return;
        de.quest.content.story.NightBellsMarkerProgress.merge(data::getStoryInt,session::getInt,session::setInt,chapter,chapter==3?4:3);
    }

    private static boolean isNightBellsVillageKey(String key) {
        return StoryQuestKeys.NIGHT_BELLS_VILLAGE_BOUND.equals(key)
                || StoryQuestKeys.NIGHT_BELLS_VILLAGE_X.equals(key)
                || StoryQuestKeys.NIGHT_BELLS_VILLAGE_Z.equals(key);
    }

    private static void mergePilgrimProgressIntoSession(ServerLevel world,
                                                        UUID memberId,
                                                        PilgrimContractType type,
                                                        SharedQuestRuntime session) {
        if (world == null || memberId == null || session == null || session.hasSynced(memberId)) {
            return;
        }
        if (!canMergeRewardContract(world,memberId,session,"pilgrim")) return;
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

    private static boolean canMergeRewardContract(ServerLevel world, UUID member, SharedQuestRuntime session, String slot) {
        var data = QuestState.get(world.getServer()).getPlayerData(member);
        for (UUID owner : session.syncedMembers()) {
            var other = QuestState.get(world.getServer()).getPlayerData(owner);
            if (slot.equals("daily") && data.getDailyTargetProfile() != other.getDailyTargetProfile()) return false;
            if (slot.equals("weekly") && data.getWeeklyTargetProfile() != other.getWeeklyTargetProfile()) return false;
            if (slot.equals("pilgrim") && data.getActivePilgrimTargetProfile() != other.getActivePilgrimTargetProfile()) return false;
            if ((slot.equals("daily") && "SMITH_SMELTING".equals(session.questId()))
                    || (slot.equals("pilgrim") && "stillness_beyond_the_gate".equals(session.questId()))) {
                var own = data.vanillaRewards(); var theirs = other.vanillaRewards();
                boolean ownModern = own.snapshot(slot,own.currentIdentity(slot)).getIntOr("version",0) >= 1;
                boolean otherModern = theirs.snapshot(slot,theirs.currentIdentity(slot)).getIntOr("version",0) >= 1;
                if (ownModern != otherModern) return false;
            }
        }
        return true;
    }

    private static void mergeDailyProgressIntoSession(ServerLevel world,
                                                      UUID memberId,
                                                      DailyQuestService.DailyQuestType type,
                                                      SharedQuestRuntime session) {
        if (world == null || memberId == null || session == null || session.hasSynced(memberId)) {
            return;
        }
        if (!canMergeRewardContract(world,memberId,session,"daily")) return;
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
        if (data.hasDailyFlag(DailyQuestKeys.SHARED_TURN_IN_CONSUMED)) {
            session.setFlag(DailyQuestKeys.SHARED_TURN_IN_CONSUMED, true);
        }
        session.markSynced(memberId);
    }

    private static void mergeWeeklyProgressIntoSession(ServerLevel world,
                                                       UUID memberId,
                                                       WeeklyQuestService.WeeklyQuestType type,
                                                       SharedQuestRuntime session) {
        if (world == null || memberId == null || session == null || session.hasSynced(memberId)) {
            return;
        }
        if (!canMergeRewardContract(world,memberId,session,"weekly")) return;
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
        if (data.hasWeeklyFlag(WeeklyQuestKeys.SHARED_TURN_IN_CONSUMED)) {
            session.setFlag(WeeklyQuestKeys.SHARED_TURN_IN_CONSUMED, true);
        }
        session.markSynced(memberId);
    }

    private static void copySharedProgressToPlayer(ServerLevel world, PartyRuntime party, UUID memberId) {
        if (world == null || party == null || memberId == null) {
            return;
        }
        if (de.quest.data.PersonalResetService.blocksActions(world,memberId)) return;
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
        DailyQuestService.DailyQuestType dailyType = activeDailyType(party);
        if (dailyType != null && party.daily().hasSynced(memberId)
                && data.hasActiveDaily() && data.getDailyChoice() == dailyType) {
            applySharedDailyProgress(data, party.daily(), dailyType);
            data.setProgressDay(QuestCalendarService.currentDay());
            QuestState.get(world.getServer()).setDirty();
        }

        WeeklyQuestService.WeeklyQuestType weeklyType = activeWeeklyType(party);
        if (weeklyType != null && party.weekly().hasSynced(memberId)
                && data.hasActiveWeekly() && data.getWeeklyChoice() == weeklyType) {
            applySharedWeeklyProgress(data, party.weekly(), weeklyType);
            data.setWeeklyProgressCycle(QuestCalendarService.currentWeekCycle());
            QuestState.get(world.getServer()).setDirty();
        }

        for (StoryLane lane : StoryLane.values()) {
            StoryArcType storyType = activeStoryType(party,lane);
            int storyChapterIndex = activeStoryChapterIndex(party,lane);
            var session=sessionFor(party,lane);
            if (storyType != null && storyChapterIndex >= 0 && session.hasSynced(memberId)
                    && data.getActiveStoryArc(lane) == storyType
                    && data.getStoryChapterProgress(storyType.id()) == storyChapterIndex
                    && (lane!=StoryLane.KINGDOM || matchesKingdomVillageBinding(data,session))) {
                applySharedStoryProgress(data, session, storyType, storyChapterIndex);
                QuestState.get(world.getServer()).setDirty();
            }
        }

        PilgrimContractType pilgrimType = activePilgrimType(party);
        if (pilgrimType != null && party.pilgrim().hasSynced(memberId)
                && pilgrimType == PilgrimContractType.fromId(data.getActivePilgrimContractId())) {
            applySharedPilgrimProgress(data, party.pilgrim(), pilgrimType);
            QuestState.get(world.getServer()).setDirty();
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
        data.setDailyFlag(DailyQuestKeys.SHARED_TURN_IN_CONSUMED,
                session.hasFlag(DailyQuestKeys.SHARED_TURN_IN_CONSUMED));
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
        data.setWeeklyFlag(WeeklyQuestKeys.SHARED_TURN_IN_CONSUMED,
                session.hasFlag(WeeklyQuestKeys.SHARED_TURN_IN_CONSUMED));
    }

    private static void applySharedStoryProgress(PlayerQuestData data,
                                                 SharedQuestRuntime session,
                                                 StoryArcType arcType,
                                                 int chapterIndex) {
        QuestShareProfiles.SharedQuestProfile profile = QuestShareProfiles.storyProfile(arcType, chapterIndex);
        if (data == null || session == null || profile == null) {
            return;
        }
        if(arcType==StoryArcType.NIGHT_BELLS && (!de.quest.content.story.NightBellsMarkerProgress.supported(data::getStoryInt,chapterIndex)
                || !de.quest.content.story.NightBellsMarkerProgress.supported(session::getInt,chapterIndex)))return;
        if (session.hasFlag(StoryQuestKeys.SHARED_TURN_IN_CONSUMED))
            data.setStoryFlag(StoryQuestKeys.sharedTurnInConsumed(arcType,chapterIndex),true);
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

    private static void queueDailyQuestOffer(ServerLevel world,
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
            ServerPlayer online = world.getServer().getPlayerList().getPlayer(memberId);
            if (online != null) {
                syncDailyMember(world, party, online, type, DailyQuestGenerator.definition(type), sourceId);
            }
            return;
        }

        QuestJoinOffer offer = new QuestJoinOffer(type.name(), session.revision(), sourceId);
        QuestJoinOffer previous = party.dailyOffers().put(memberId, offer);
        if (!offer.equals(previous)) {
            ServerPlayer online = world.getServer().getPlayerList().getPlayer(memberId);
            if (online != null) {
                sendDailyJoinOffer(world, online, type, offer.sourceId());
            }
        }
    }

    private static void queueWeeklyQuestOffer(ServerLevel world,
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
            ServerPlayer online = world.getServer().getPlayerList().getPlayer(memberId);
            if (online != null) {
                syncWeeklyMember(world, party, online, type, WeeklyQuestGenerator.definition(type), sourceId);
            }
            return;
        }

        QuestJoinOffer offer = new QuestJoinOffer(type.name(), session.revision(), sourceId);
        QuestJoinOffer previous = party.weeklyOffers().put(memberId, offer);
        if (!offer.equals(previous)) {
            ServerPlayer online = world.getServer().getPlayerList().getPlayer(memberId);
            if (online != null) {
                sendWeeklyJoinOffer(world, online, type, offer.sourceId());
            }
        }
    }

    private static void queueStoryQuestOffer(ServerLevel world,
                                             PartyRuntime party,
                                             UUID memberId,
                                             StoryArcType arcType,
                                             int chapterIndex,
                                             UUID sourceId,
                                             boolean fromPartyJoin) {
        if (world == null || party == null || memberId == null || arcType == null || chapterIndex < 0) {
            return;
        }
        SharedQuestRuntime session = ensurePartyStorySession(world, party, arcType, chapterIndex);
        if (session == null || session.hasSynced(memberId)) {
            clearStoryOffer(party, memberId, StoryLane.forArc(arcType));
            return;
        }
        if (!canJoinStoryShare(world, memberId, arcType, chapterIndex, false)) {
            clearStoryOffer(party, memberId, StoryLane.forArc(arcType));
            return;
        }

        QuestJoinOffer offer = new QuestJoinOffer(storySessionId(arcType, chapterIndex), session.revision(), sourceId);
        QuestJoinOffer previous = offersFor(party,arcType).put(memberId, offer);
        if (!offer.equals(previous)) {
            ServerPlayer online = world.getServer().getPlayerList().getPlayer(memberId);
            if (online != null) {
                sendStoryJoinOffer(world, online, arcType, chapterIndex, offer.sourceId());
            }
        }
    }

    private static void queuePilgrimQuestOffer(ServerLevel world,
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
            ServerPlayer online = world.getServer().getPlayerList().getPlayer(memberId);
            if (online != null) {
                syncPilgrimMember(world, party, online, type, sourceId);
            }
            return;
        }

        QuestJoinOffer offer = new QuestJoinOffer(type.id(), session.revision(), sourceId);
        QuestJoinOffer previous = party.pilgrimOffers().put(memberId, offer);
        if (!offer.equals(previous)) {
            ServerPlayer online = world.getServer().getPlayerList().getPlayer(memberId);
            if (online != null) {
                sendPilgrimJoinOffer(world, online, type, offer.sourceId());
            }
        }
    }

    private static void resendPendingQuestOffers(ServerLevel world, PartyRuntime party, ServerPlayer player) {
        if (world == null || party == null || player == null) {
            return;
        }
        QuestJoinOffer dailyOffer = party.dailyOffers().get(player.getUUID());
        DailyQuestService.DailyQuestType dailyType = dailyOffer == null ? null : parseDailyType(dailyOffer.questId());
        if (dailyOffer != null && dailyType != null && isOfferStillValid(world, party, player.getUUID(), dailyOffer, dailyType)) {
            sendDailyJoinOffer(world, player, dailyType, dailyOffer.sourceId());
        }

        QuestJoinOffer weeklyOffer = party.weeklyOffers().get(player.getUUID());
        WeeklyQuestService.WeeklyQuestType weeklyType = weeklyOffer == null ? null : parseWeeklyType(weeklyOffer.questId());
        if (weeklyOffer != null && weeklyType != null && isOfferStillValid(world, party, player.getUUID(), weeklyOffer, weeklyType)) {
            sendWeeklyJoinOffer(world, player, weeklyType, weeklyOffer.sourceId());
        }

        for (StoryLane lane : StoryLane.values()) {
        QuestJoinOffer storyOffer = offersFor(party,lane).get(player.getUUID());
        StoryArcType storyType = storyOffer == null ? null : parseStoryType(storyOffer.questId());
        int storyChapterIndex = storyOffer == null ? -1 : parseStoryChapter(storyOffer.questId());
        if (storyOffer != null && storyType != null && storyChapterIndex >= 0
                && isOfferStillValid(world, party, player.getUUID(), storyOffer, storyType, storyChapterIndex)) {
            sendStoryJoinOffer(world, player, storyType, storyChapterIndex, storyOffer.sourceId());
        }

        }

        QuestJoinOffer pilgrimOffer = party.pilgrimOffers().get(player.getUUID());
        PilgrimContractType pilgrimType = pilgrimOffer == null ? null : PilgrimContractType.fromId(pilgrimOffer.questId());
        if (pilgrimOffer != null && pilgrimType != null && isOfferStillValid(world, party, player.getUUID(), pilgrimOffer, pilgrimType)) {
            sendPilgrimJoinOffer(world, player, pilgrimType, pilgrimOffer.sourceId());
        }
    }

    private static void sendDailyJoinOffer(ServerLevel world,
                                           ServerPlayer player,
                                           DailyQuestService.DailyQuestType type,
                                           UUID sourceId) {
        DailyQuestDefinition definition = DailyQuestGenerator.definition(type);
        Component title = definition == null ? DailyQuestService.displayKey(type) : definition.title();
        Component progress = definition == null
                ? Component.empty()
                : definition.progressLine(world, sourceId != null ? sourceId : player.getUUID()).copy().withStyle(ChatFormatting.GRAY);
        player.sendSystemMessage(Component.translatable(
                "message.village-quest.party.offer.daily.prompt",
                title,
                nameOf(world, sourceId == null ? player.getUUID() : sourceId)
        ).withStyle(ChatFormatting.GRAY), false);
        if (!progress.getString().isBlank()) {
            player.sendSystemMessage(progress, false);
        }
        player.sendSystemMessage(joinOfferButtons("/vq party share daily accept", "/vq party share daily decline"), false);
    }

    private static void sendWeeklyJoinOffer(ServerLevel world,
                                            ServerPlayer player,
                                            WeeklyQuestService.WeeklyQuestType type,
                                            UUID sourceId) {
        WeeklyQuestDefinition definition = WeeklyQuestGenerator.definition(type);
        Component title = definition == null ? WeeklyQuestService.displayName(type) : definition.title();
        Component progress = Component.empty();
        if (definition != null) {
            List<Component> lines = definition.progressLines(world, sourceId != null ? sourceId : player.getUUID());
            if (!lines.isEmpty()) {
                progress = lines.getFirst().copy().withStyle(ChatFormatting.GRAY);
            }
        }
        player.sendSystemMessage(Component.translatable(
                "message.village-quest.party.offer.weekly.prompt",
                title,
                nameOf(world, sourceId == null ? player.getUUID() : sourceId)
        ).withStyle(ChatFormatting.GRAY), false);
        if (!progress.getString().isBlank()) {
            player.sendSystemMessage(progress, false);
        }
        player.sendSystemMessage(joinOfferButtons("/vq party share weekly accept", "/vq party share weekly decline"), false);
    }

    private static void sendStoryJoinOffer(ServerLevel world,
                                           ServerPlayer player,
                                           StoryArcType arcType,
                                           int chapterIndex,
                                           UUID sourceId) {
        StoryChapterDefinition chapter = StoryQuestService.definition(arcType) == null ? null : StoryQuestService.definition(arcType).chapter(chapterIndex);
        Component title = chapter == null ? StoryQuestService.definition(arcType).title() : chapter.title();
        Component progress = Component.empty();
        if (chapter != null) {
            List<Component> lines = chapter.progressLines(world, sourceId != null ? sourceId : player.getUUID());
            if (!lines.isEmpty()) {
                progress = lines.getFirst().copy().withStyle(ChatFormatting.GRAY);
            }
        }
        player.sendSystemMessage(Component.translatable(
                "message.village-quest.party.offer.story.prompt",
                title,
                nameOf(world, sourceId == null ? player.getUUID() : sourceId)
        ).withStyle(ChatFormatting.GRAY), false);
        if (!progress.getString().isBlank()) {
            player.sendSystemMessage(progress, false);
        }
        String lane=StoryLane.forArc(arcType)==StoryLane.KINGDOM ? "kingdom" : "core";
        player.sendSystemMessage(joinOfferButtons("/vq party share story accept "+lane, "/vq party share story decline "+lane), false);
    }

    private static void sendPilgrimJoinOffer(ServerLevel world,
                                             ServerPlayer player,
                                             PilgrimContractType type,
                                             UUID sourceId) {
        Component progress = PilgrimContractService.previewProgressLine(world, sourceId != null ? sourceId : player.getUUID(), type).copy().withStyle(ChatFormatting.GRAY);
        player.sendSystemMessage(Component.translatable(
                "message.village-quest.party.offer.pilgrim.prompt",
                PilgrimContractService.title(type),
                nameOf(world, sourceId == null ? player.getUUID() : sourceId)
        ).withStyle(ChatFormatting.GRAY), false);
        if (!progress.getString().isBlank()) {
            player.sendSystemMessage(progress, false);
        }
        player.sendSystemMessage(joinOfferButtons("/vq party share pilgrim accept", "/vq party share pilgrim decline"), false);
    }

    private static MutableComponent joinOfferButtons(String acceptCommand, String declineCommand) {
        MutableComponent accept = Component.translatable("message.village-quest.party.offer.accept")
                .withStyle(style -> style.withColor(0x57A550).withClickEvent(new ClickEvent.RunCommand(acceptCommand)));
        MutableComponent decline = Component.translatable("message.village-quest.party.offer.decline")
                .withStyle(style -> style.withColor(0xA55252).withClickEvent(new ClickEvent.RunCommand(declineCommand)));
        return accept.append(Component.literal(" ")).append(decline);
    }

    private static boolean memberHasActiveDaily(ServerLevel world, UUID memberId, DailyQuestService.DailyQuestType type) {
        if (world == null || memberId == null || type == null) {
            return false;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
        long day = QuestCalendarService.currentDay();
        return data.hasActiveDaily() && data.getDailyChoice() == type;
    }

    private static boolean memberHasActiveWeekly(ServerLevel world, UUID memberId, WeeklyQuestService.WeeklyQuestType type) {
        if (world == null || memberId == null || type == null) {
            return false;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
        long cycle = QuestCalendarService.currentWeekCycle();
        return data.hasActiveWeekly() && data.getWeeklyChoice() == type;
    }

    private static boolean memberHasActiveStory(ServerLevel world, UUID memberId, StoryArcType arcType, int chapterIndex) {
        if (world == null || memberId == null || arcType == null || chapterIndex < 0) {
            return false;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
        return data.getActiveStoryArc(StoryLane.forArc(arcType)) == arcType && data.getStoryChapterProgress(arcType.id()) == chapterIndex;
    }

    private static boolean memberHasActivePilgrim(ServerLevel world, UUID memberId, PilgrimContractType type) {
        if (world == null || memberId == null || type == null) {
            return false;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
        return type == PilgrimContractType.fromId(data.getActivePilgrimContractId());
    }

    private static boolean canJoinDailyShare(ServerLevel world, UUID memberId, DailyQuestService.DailyQuestType type, boolean notify) {
        if (world == null || memberId == null || type == null) {
            return false;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
        long day = QuestCalendarService.currentDay();
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(memberId);
        if (!FirstDailyChoiceService.canUseSharedDaily(data)) {
            return false;
        }
        SharedQuestRuntime session = sharedDailySessionForType(world, memberId, type);
        if (session != null && !session.canJoinAfterTurnIn(DailyQuestKeys.SHARED_TURN_IN_CONSUMED, memberId)) {
            if (notify && player != null) {
                player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.daily.expired")
                        .withStyle(ChatFormatting.RED), false);
            }
            return false;
        }
        if (data.getLastRewardDay() == day) {
            if (notify && player != null) {
                player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.daily.blocked.completed").withStyle(ChatFormatting.RED), false);
            }
            return false;
        }
        if (data.hasActiveBonusDaily() || (data.hasActiveDaily() && data.getDailyChoice() != type)) {
            if (notify && player != null) {
                player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.daily.blocked.active").withStyle(ChatFormatting.RED), false);
            }
            return false;
        }
        return true;
    }

    private static boolean canJoinWeeklyShare(ServerLevel world, UUID memberId, WeeklyQuestService.WeeklyQuestType type, boolean notify) {
        if (world == null || memberId == null || type == null) {
            return false;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
        long cycle = QuestCalendarService.currentWeekCycle();
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(memberId);
        SharedQuestRuntime session = sharedWeeklySessionForType(world, memberId, type);
        if (session != null && !session.canJoinAfterTurnIn(WeeklyQuestKeys.SHARED_TURN_IN_CONSUMED, memberId)) {
            if (notify && player != null) {
                player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.weekly.expired")
                        .withStyle(ChatFormatting.RED), false);
            }
            return false;
        }
        if (data.getWeeklyRewardCycle() == cycle) {
            if (notify && player != null) {
                player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.weekly.blocked.completed").withStyle(ChatFormatting.RED), false);
            }
            return false;
        }
        if (data.hasActiveWeekly() && data.getWeeklyChoice() != type) {
            if (notify && player != null) {
                player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.weekly.blocked.active").withStyle(ChatFormatting.RED), false);
            }
            return false;
        }
        return true;
    }

    private static boolean canJoinStoryShare(ServerLevel world,
                                             UUID memberId,
                                             StoryArcType arcType,
                                             int chapterIndex,
                                             boolean notify) {
        if (world == null || memberId == null || arcType == null || chapterIndex < 0) {
            return false;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(memberId);
        SharedQuestRuntime session = sharedStorySessionForType(world, memberId, arcType, chapterIndex);
        if (arcType==StoryArcType.LIVING_KINGDOMS && session!=null) {
            UUID shared=kingdomVillageBinding(session), own=de.quest.kingdom.KingdomStoryProgress.target(data);
            if ((shared==null && session.flags().stream().anyMatch(flag->flag.startsWith("kingdoms.bound.")))
                    || (shared!=null && own!=null && !shared.equals(own))) return false;
        }
        if (session != null && !session.canJoinAfterTurnIn(
                StoryQuestKeys.SHARED_TURN_IN_CONSUMED, memberId)) {
            if (notify && player != null) {
                player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.story.expired")
                        .withStyle(ChatFormatting.RED), false);
            }
            return false;
        }
        if (data.hasStoryCompleted(arcType.id())) {
            if (notify && player != null) {
                player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.story.blocked.completed").withStyle(ChatFormatting.RED), false);
            }
            return false;
        }
        StoryArcType activeArc = data.getActiveStoryArc(StoryLane.forArc(arcType));
        if (activeArc != null && (activeArc != arcType || data.getStoryChapterProgress(arcType.id()) != chapterIndex)) {
            if (notify && player != null) {
                player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.story.blocked.active").withStyle(ChatFormatting.RED), false);
            }
            return false;
        }
        if (activeArc == null && StoryLane.forArc(arcType)==StoryLane.CORE && StoryQuestService.isStoryCooldownActive(world, memberId)) {
            if (notify && player != null) {
                player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.story.blocked.cooldown").withStyle(ChatFormatting.RED), false);
            }
            return false;
        }
        StoryArcType available = StoryLane.forArc(arcType)==StoryLane.KINGDOM
                ? (StoryQuestService.definition(arcType).isUnlocked(world,memberId) ? arcType : null)
                : StoryQuestService.availableArcTypeIgnoringCooldown(world, memberId);
        if (activeArc == null && available != arcType) {
            if (notify && player != null) {
                player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.story.blocked.sequence").withStyle(ChatFormatting.RED), false);
            }
            return false;
        }
        if (data.getStoryChapterProgress(arcType.id()) != chapterIndex) {
            if (notify && player != null) {
                player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.story.blocked.sequence").withStyle(ChatFormatting.RED), false);
            }
            return false;
        }
        return true;
    }

    private static boolean canJoinPilgrimShare(ServerLevel world,
                                               UUID memberId,
                                               PilgrimContractType type,
                                               boolean notify) {
        if (world == null || memberId == null || type == null) {
            return false;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(memberId);
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(memberId);
        var resetParty = partyFor(memberId);
        if (resetParty != null && resetParty.pilgrim().hasFlag("personal_reset.excluded." + memberId)) return false;
        PilgrimContractType activeType = PilgrimContractType.fromId(data.getActivePilgrimContractId());
        if (activeType != null && activeType != type) {
            if (notify && player != null) {
                player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.pilgrim.blocked.active").withStyle(ChatFormatting.RED), false);
            }
            return false;
        }
        if (activeType == null && data.hasPilgrimFlag("pilgrim_contract_suppress_offer")) {
            if (notify && player != null) {
                player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.pilgrim.blocked.completed").withStyle(ChatFormatting.RED), false);
            }
            return false;
        }
        return true;
    }

    private static UUID dailyOfferSourceId(ServerLevel world, PartyRuntime party, UUID fallbackMemberId) {
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

    private static UUID weeklyOfferSourceId(ServerLevel world, PartyRuntime party, UUID fallbackMemberId) {
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

    private static de.quest.quest.repeatable.RepeatableTargetProfile sharedDailyTargetProfile(ServerLevel world,
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

    private static de.quest.quest.repeatable.RepeatableTargetProfile sharedWeeklyTargetProfile(ServerLevel world,
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

    private static de.quest.quest.repeatable.RepeatableTargetProfile sharedPilgrimTargetProfile(ServerLevel world,
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

    private static UUID storyOfferSourceId(ServerLevel world, PartyRuntime party, UUID fallbackMemberId, StoryLane lane) {
        if (party == null) {
            return fallbackMemberId;
        }
        StoryArcType activeType = activeStoryType(party,lane);
        int chapterIndex = activeStoryChapterIndex(party,lane);
        for (UUID memberId : sessionFor(party,lane).syncedMembers()) {
            if (!memberId.equals(fallbackMemberId) && memberHasActiveStory(world, memberId, activeType, chapterIndex)) {
                return memberId;
            }
        }
        return party.leaderId();
    }

    private static UUID pilgrimOfferSourceId(ServerLevel world, PartyRuntime party, UUID fallbackMemberId) {
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

    private static QuestJoinOffer clearStoryOffer(PartyRuntime party, UUID memberId, StoryLane lane) {
        return party == null || memberId == null ? null : offersFor(party,lane).remove(memberId);
    }

    private static void clearStoryOffer(PartyRuntime party, UUID memberId) {
        if (party!=null && memberId!=null) for (StoryLane lane:StoryLane.values()) clearStoryOffer(party,memberId,lane);
    }

    private static StoryLane selectStoryOffer(ServerLevel world, ServerPlayer player, PartyRuntime party, StoryLane selected) {
        if (selected!=null) return selected;
        boolean core=party!=null && party.storyOffers().containsKey(player.getUUID());
        boolean kingdom=party!=null && party.kingdomStoryOffers().containsKey(player.getUUID());
        if (core && kingdom) {
            player.sendSystemMessage(Component.translatable("message.village-quest.party.offer.story.select").withStyle(ChatFormatting.GOLD),false);
            return null;
        }
        return kingdom ? StoryLane.KINGDOM : StoryLane.CORE;
    }

    private static QuestJoinOffer clearPilgrimOffer(PartyRuntime party, UUID memberId) {
        return party == null || memberId == null ? null : party.pilgrimOffers().remove(memberId);
    }

    private static boolean isOfferStillValid(ServerLevel world,
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

    private static boolean isOfferStillValid(ServerLevel world,
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

    private static boolean isOfferStillValid(ServerLevel world,
                                             PartyRuntime party,
                                             UUID memberId,
                                             QuestJoinOffer offer,
                                             StoryArcType arcType,
                                             int chapterIndex) {
        SharedQuestRuntime session = sessionFor(party,arcType);
        return offer != null
                && session != null
                && session.matches(storySessionId(arcType, chapterIndex), offer.revision())
                && matchesStorySession(party, arcType, chapterIndex)
                && !session.hasSynced(memberId)
                && canJoinStoryShare(world, memberId, arcType, chapterIndex, false);
    }

    private static boolean isOfferStillValid(ServerLevel world,
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

    private static void removeDisconnectedMember(ServerLevel world, PartyRuntime party, UUID memberId) {
        if (world == null || party == null || memberId == null) {
            return;
        }
        Component departedName=QuestPartyMessaging.nameOf(world,party,memberId);
        if(!leaveInternal(world, memberId, false, true)) return;
        PartyRuntime updated = PARTIES.get(party.id());
        if (updated != null) {
            broadcast(world, updated, Component.translatable("message.village-quest.party.member_removed_timeout", departedName).withStyle(ChatFormatting.GRAY));
        }
    }

    private static void storePersistentState(MinecraftServer server) {
        // Save a loaded session even if LAN publication has already ended at shutdown.
        // An unopened private world must not overwrite its inactive saved party data.
        if (server == null || !loaded || !QuestState.get(server).storyLanesWritable()) {
            return;
        }
        QuestState.get(server).setQuestPartyState(QuestPartyPersistence.write(PARTIES, INVITES));
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

    public record PartyMemberView(String playerId, Component name, boolean leader, boolean self) {}

    public record PartyInviteCandidateView(String playerId, Component name, Component status, boolean inviteable) {}

    public record PartySnapshot(boolean hasParty,
                                boolean leader,
                                Component summary,
                                List<PartyMemberView> members,
                                List<PartyInviteCandidateView> candidates) {
        private static PartySnapshot empty() {
            return new PartySnapshot(false, false, Component.empty(), List.of(), List.of());
        }
    }

}
