package de.quest.reputation;

import de.quest.caravan.TradeRouteService;
import de.quest.data.QuestState;
import de.quest.entity.QuestMasterEntity;
import de.quest.network.ReputationPayloads.*;
import de.quest.quest.QuestBookHelper;
import de.quest.registry.ModBlocks;
import de.quest.reputation.ReparationService.*;
import de.quest.reputation.ReputationViewService.*;
import de.quest.reputation.SocialReputationRules.*;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** All requests resolve the sender, fresh state, physical anchor and inventory on the server thread. */
public final class ReputationInteractionService {
    private static final ReputationSessionRegistry SESSIONS = new ReputationSessionRegistry();
    private static final Map<UUID, InteractionPayload> LAST = new HashMap<>();
    private static final Map<UUID, Integer> JOURNAL_PAGES = new HashMap<>();
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    private ReputationInteractionService() {}
    private static boolean allowRequest(ServerPlayer player) {
        long tick = QuestState.get(player.level().getServer()).socialServerTick();
        Long previous = LAST_REQUEST.put(player.getUUID(), tick);
        return previous == null || tick - previous >= 4;
    }
    private static boolean validReadAnchor(ServerPlayer player, InteractionAnchor anchor) {
        if (ReparationService.validAnchor(player, anchor)) return true;
        if (!SocialReputationService.enabled() || !player.isAlive() || player.isSpectator() || anchor.kind() != AnchorKind.BOARD) return false;
        var world = player.level();
        if (!world.dimension().identifier().toString().equals(anchor.dimension()) || !world.hasChunkAt(anchor.position())
                || player.blockPosition().distSqr(anchor.position()) > 64 || !world.getBlockState(anchor.position()).is(ModBlocks.GUILD_NOTICE_POST)) return false;
        var village = QuestState.get(world.getServer()).protectedVillages().resolve(world, anchor.position()).orElse(null);
        return village != null && ReputationViewService.knownVillages(player).contains(village);
    }
    public static void open(ServerPlayer player, OpenPayload request) {
        if (!allowRequest(player) || !SocialReputationService.enabled()) return;
        var world = player.level(); InteractionAnchor anchor;
        if (request.kind() == 0) anchor = new InteractionAnchor(AnchorKind.BOARD, world.dimension().identifier().toString(), request.position(), null);
        else if (request.kind() == 1 && world.getEntity(request.entityId()) instanceof QuestMasterEntity master)
            anchor = new InteractionAnchor(AnchorKind.QUESTMASTER, world.dimension().identifier().toString(), master.blockPosition(), master.getUUID());
        else return;
        if (!validReadAnchor(player, anchor)) return;
        ReparationService.open(player, anchor);
        var session = SESSIONS.open(player.getUUID(), anchor, QuestState.get(world.getServer()).socialServerTick());
        LAST.remove(player.getUUID()); send(player, session, "");
    }
    public static void sendJournal(ServerPlayer player) {
        ServerPlayNetworking.send(player, new ViewPayload(ReputationViewService.view(player, JOURNAL_PAGES.getOrDefault(player.getUUID(), 0))));
    }
    public static void page(ServerPlayer player, PagePayload request) {
        if (!QuestBookHelper.isJournalOpen(player.getUUID()) || !allowRequest(player)) return;
        var view = ReputationViewService.view(player, request.page()); JOURNAL_PAGES.put(player.getUUID(), view.villagePage());
        ServerPlayNetworking.send(player, new ViewPayload(view));
    }
    public static void handle(ServerPlayer player, ActionPayload request) {
        var state = QuestState.get(player.level().getServer());
        var session = SESSIONS.current(player.getUUID(), request.session(), state.socialServerTick());
        if (session == null) return;
        if (request.action() == ActionPayload.CLOSE) { SESSIONS.close(player.getUUID(), session.id()); LAST.remove(player.getUUID()); return; }
        if (!allowRequest(player)) return;
        if (!validReadAnchor(player, session.anchor())) { close(player, session); return; }
        if (request.action() == ActionPayload.PAGE) {
            int page = ReputationViewService.view(player, request.villagePage()).villagePage();
            SESSIONS.page(player.getUUID(), session.id(), page); send(player, SESSIONS.current(player.getUUID(), session.id(), state.socialServerTick()), ""); return;
        }
        String feedback = "reputation.village-quest.reparation.stale";
        if (state.getPlayerData(player.getUUID()).socialReputation().revision() == request.revision()) {
            if (request.action() == ActionPayload.CHOOSE && request.material() >= 0 && request.material() < MaterialOption.values().length) {
                feedback = ReparationService.choose(player, request.subject(), request.revision(), MaterialOption.values()[request.material()], session.anchor()).reasonKey();
            } else if (request.action() == ActionPayload.SUBMIT) {
                feedback = ReparationService.submit(player, request.subject(), request.revision(), session.anchor()).reasonKey();
            } else if (request.action() == ActionPayload.SUPPORT_ACCEPT || request.action() == ActionPayload.SUPPORT_SUBMIT) {
                var offered = snapshot(player, session, "").support().stream().filter(offer -> offer.id().equals(request.subject())).findFirst().orElse(null);
                if (offered != null) {
                    if (request.action() == ActionPayload.SUPPORT_ACCEPT) feedback = ResettlementSupportService.accept(player, offered.id(), offered.village(), session.anchor().position()) ? "" : "reputation.village-quest.support.invalid";
                    else feedback = ResettlementSupportService.submit(player, offered.id(), offered.village(), session.anchor().position()).reasonKey();
                }
            }
        }
        send(player, session, feedback); QuestBookHelper.refreshQuestBook(player.level(), player);
    }
    private static InteractionPayload snapshot(ServerPlayer player, ReputationSessionRegistry.Session session, String feedback) {
        var base = ReputationViewService.view(player, session.page());
        CaseView caseView = base.reparation(); var reparation = ReparationService.view(player);
        if (caseView != null && reparation != null) {
            caseView = new CaseView(caseView.id(), caseView.major(), caseView.affectedCount(), caseView.ticksRemaining(), caseView.selected(), caseView.offence(),
                    reparation.materials().stream().map(line -> new AidView(line.material().ordinal(), line.supplied(), line.required(), line.carried())).toList());
        }
        var view = new View(base.schema(), base.revision(), base.enabled(), base.writable(), base.guildTrust(), base.probation(), base.buyLimit(), base.dispatchAllowed(),
                base.convoyAllowed(), base.rewardPercent(), base.villagePage(), base.villageTotal(), base.villages(), base.history(), caseView);
        List<SupportView> support = new ArrayList<>();
        if (session.anchor().kind() == AnchorKind.BOARD && TradeRouteService.isNearPlayerYard(player.level(), player.getUUID(), session.anchor().position(), 24)) {
            for (var village : view.villages()) {
                var offer = ResettlementSupportService.offer(player, village.key());
                if (offer != null) support.add(new SupportView(offer.id(), offer.village(), offer.accepted(), offer.supplied(), offer.barred()));
            }
        }
        return new InteractionPayload(session.id(), false, ReparationService.validAnchor(player, session.anchor()), view, support, feedback);
    }
    private static void send(ServerPlayer player, ReputationSessionRegistry.Session session, String feedback) {
        if (session == null) return;
        var payload = snapshot(player, session, feedback);
        if (!payload.equals(LAST.put(player.getUUID(), payload))) ServerPlayNetworking.send(player, payload);
    }
    private static void close(ServerPlayer player, ReputationSessionRegistry.Session session) {
        SESSIONS.close(player.getUUID(), session.id()); LAST.remove(player.getUUID()); ServerPlayNetworking.send(player, new ClosePayload(session.id()));
    }
    public static void onServerTick(MinecraftServer server) {
        var state = QuestState.get(server); if (state.socialServerTick() % 20 != 0) return;
        SESSIONS.view().forEach((id, session) -> {
            var player = server.getPlayerList().getPlayer(id);
            if (player == null) { remove(id); return; }
            if (SESSIONS.current(id, session.id(), state.socialServerTick()) == null || !validReadAnchor(player, session.anchor())) close(player, session);
            else send(player, session, "");
        });
    }
    public static void remove(UUID player) { ReputationDamageAdapter.forgetPlayer(player); SESSIONS.remove(player); LAST.remove(player); JOURNAL_PAGES.remove(player); LAST_REQUEST.remove(player); }
    public static void resetRuntime() { SESSIONS.clear(); LAST.clear(); JOURNAL_PAGES.clear(); LAST_REQUEST.clear(); }
}
