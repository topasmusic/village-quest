package de.quest.kingdom;

import de.quest.data.QuestState;
import de.quest.party.QuestPartyService;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryQuestService;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Trusted native services call this only after a committed trade, delivery, project or actual NPC dialogue. */
public final class KingdomStoryService {
    private KingdomStoryService() {}
    public static boolean chooseTarget(ServerLevel world,ServerPlayer player,UUID village,boolean explicit) {
        if (world==null || player==null || !LivingKingdomsState.get(world.getServer()).canManageVillage(player.getUUID(),village)) return false;
        boolean bound=QuestPartyService.bindKingdomStoryTarget(world,player,village,explicit);
        if (bound) { QuestState.get(world.getServer()).setDirty(); refresh(world,player); }
        return bound;
    }
    public static boolean recordConfirmed(ServerLevel world,ServerPlayer player,UUID village,KingdomStoryProgress.Deed deed,String receipt) {
        if (world==null || player==null || !world.getServer().isSameThread() || LivingKingdomsState.get(world.getServer()).village(village).isEmpty()) return false;
        var state=QuestState.get(world.getServer()); var actor=state.getPlayerData(player.getUUID());
        boolean project=KingdomStoryProgress.recordProject(actor,village,deed,receipt,true);
        if(project) state.setDirty();
        if (!village.equals(KingdomStoryProgress.target(actor))) return project;
        List<UUID> recipients=actor.getActiveStoryArc()==StoryArcType.LIVING_KINGDOMS
                ?QuestPartyService.activeStoryMembers(world,player.getUUID(),StoryArcType.LIVING_KINGDOMS,actor.getStoryChapterProgress(KingdomStoryProgress.ARC))
                :List.of(player.getUUID());
        boolean changed=project;
        for(UUID recipient:recipients) {
            if (KingdomStoryProgress.record(state.getPlayerData(recipient),village,deed,receipt,true)) {
                KingdomStoryProgress.recordProject(state.getPlayerData(recipient),village,deed,receipt,true);
                changed=true; ServerPlayer online=world.getServer().getPlayerList().getPlayer(recipient); if (online!=null) refresh(world,online);
            }
        }
        if (changed) state.setDirty(); return changed;
    }
    private static void refresh(ServerLevel world,ServerPlayer player) {
        if (StoryQuestService.isActive(world,player.getUUID(),StoryArcType.LIVING_KINGDOMS)) StoryQuestService.completeIfEligible(world,player);
        de.quest.questmaster.QuestMasterUiService.refreshIfOpen(world,player);
    }
    /** Called after native reservation, before the first three-document BEFORE checkpoint. */
    static boolean prepareMarketTrade(ServerLevel world,ServerPlayer player,KingdomMarketLedger.Quote quote) {
        if(world==null || player==null || quote==null || !world.getServer().isSameThread() || !player.getUUID().equals(quote.actor())) return false;
        var state=LivingKingdomsState.get(world.getServer()); var visit=state.markets().visitById(quote.visit());
        if(visit.isEmpty()) return false;
        var quests=QuestState.get(world.getServer()); var actor=quests.getPlayerData(player.getUUID());
        var recipients=actor.getActiveStoryArc()==StoryArcType.LIVING_KINGDOMS
                ?QuestPartyService.activeStoryMembers(world,player.getUUID(),StoryArcType.LIVING_KINGDOMS,actor.getStoryChapterProgress(KingdomStoryProgress.ARC))
                :List.of(player.getUUID());
        boolean prepared=KingdomMarketStoryProof.prepare(quests,player.getUUID(),quote.id(),visit.get().market(),recipients);
        return prepared;
    }
    /** Attempts and terminal-but-unacknowledged images cannot supply a story success event. */
    static void marketCommitted(ServerLevel world,ServerPlayer player,UUID receipt) {
        if(world==null || player==null || !world.getServer().isSameThread()) return;
        var server=world.getServer();
        var changed=KingdomMarketStoryProof.completeNative(QuestState.get(server),LivingKingdomsState.get(server).markets(),player.getUUID(),receipt);
        for(var id:changed) { var online=server.getPlayerList().getPlayer(id); if(online!=null) refresh(online.level(),online); }
    }
    /** Existing freight service calls after consuming the supplied contract and committing its real arrival. */
    public static void freightDelivered(ServerLevel world,UUID owner,int routeIndex,int completedContracts) {
        if (world==null || owner==null || routeIndex<0 || completedContracts<=0) return;
        var quests=QuestState.get(world.getServer()); var data=quests.getPlayerData(owner);
        UUID target=KingdomStoryProgress.target(data); if (target==null || routeIndex>=data.getTradeRouteInt("route_count")) return;
        var registry=LivingKingdomsState.get(world.getServer());
        if (registry.village(target).isEmpty()) return;
        String dimension=world.dimension().identifier().toString();
        var destination=registry.byLegacy(new de.quest.village.VillageLifeState.VillageKey(dimension,
                de.quest.caravan.TradeRouteService.routeDestinationX(world,owner,routeIndex),de.quest.caravan.TradeRouteService.routeDestinationZ(world,owner,routeIndex)));
        var home=data.hasTradeRouteFlag("home_bound") && !data.hasTradeRouteFlag("home_player_yard") ? registry.byLegacy(new de.quest.village.VillageLifeState.VillageKey(dimension,
                data.getTradeRouteInt("home_x"),data.getTradeRouteInt("home_z"))) : java.util.Optional.<LivingKingdomsState.VillageView>empty();
        if (destination.filter(village -> village.id().equals(target)).isEmpty() && home.filter(village -> village.id().equals(target)).isEmpty()) return;
        String receipt="freight:"+owner+":"+completedContracts;
        ServerPlayer online=world.getServer().getPlayerList().getPlayer(owner);
        if (online!=null) recordConfirmed(world,online,target,KingdomStoryProgress.Deed.STORE_DELIVERY,receipt);
        else if (KingdomStoryProgress.record(data,target,KingdomStoryProgress.Deed.STORE_DELIVERY,receipt,true)) quests.setDirty();
    }
}
