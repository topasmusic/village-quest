package de.quest.kingdom;

import de.quest.caravan.KingdomRouteConnections;
import de.quest.data.QuestState;
import de.quest.village.VillageLifeState;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;

/** Called only after the original freight service committed a supplied contract. No personal story gate. */
public final class KingdomFreightService {
    private KingdomFreightService() {}
    public static void delivered(ServerLevel world,UUID owner,int route,int sequence,Item item) {
        if(world==null || owner==null || route<0 || sequence<=0 || item==null || !world.getServer().isSameThread()
                || !world.dimension().identifier().toString().equals("minecraft:overworld")) return;
        var state=LivingKingdomsState.get(world.getServer());
        var data=QuestState.get(world.getServer()).getPlayersView().get(owner); if(data==null) return;
        KingdomRouteConnections.route(state,VillageLifeState.get(world.getServer()),owner,data,route)
                .filter(KingdomRouteConnections.Connection::active).ifPresent(connection -> {
                    // Preserve the existing participating-endpoint rule, using canonical IDs rather than legacy coordinates.
                    boolean progress=KingdomStoryProgress.recordProject(data,connection.home(),KingdomStoryProgress.Deed.STORE_DELIVERY,"freight:"+owner+":"+sequence,true);
                    progress|=KingdomStoryProgress.recordProject(data,connection.destination(),KingdomStoryProgress.Deed.STORE_DELIVERY,"freight:"+owner+":"+sequence,true);
                    if(progress) QuestState.get(world.getServer()).setDirty();
                    sharedDemand(state,QuestState.get(world.getServer()),de.quest.village.LivingVillageNetworkState.get(world.getServer()),
                            owner,connection.destination(),sequence,item,de.quest.util.TimeUtil.currentDay());
                    if(state.relations().delivered(connection.home(),connection.destination(),owner,connection.id(),sequence)) {
                        state.diplomacy().delivered(connection.home(),connection.destination(),item);
                        state.crises().delivered(owner,connection.home(),connection.destination(),sequence,item);
                        if(state.relations().repairAttackFreight(owner,connection.home(),connection.destination(),sequence,item)) {
                            var actor=world.getServer().getPlayerList().getPlayer(owner);
                            if(actor!=null) actor.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.village-quest.kingdom.attack_repaired",
                                    state.village(connection.destination()).orElseThrow().name()));
                        }
                    }
                });
    }
    /** Exact existing owner/index binding; missing/future sources stay neutral and no row is created. */
    static boolean sharedDemand(LivingKingdomsState state,QuestState quests,de.quest.village.LivingVillageNetworkState network,
                                UUID carrier,UUID village,int sequence,Item item,long day) {
        if(state==null || quests==null || network==null || sequence<=0 || item==null
                || !KingdomMarketDemandService.observe(state,quests,network,carrier,village)) return false;
        var source=state.markets().homeNeedSource(village); var town=state.village(village);
        if(source.isEmpty() || town.isEmpty()) return false;
        var current=network.marketSnapshot(source.get().owner(),source.get().index()); if(current.isEmpty()) return false;
        boolean changed=network.sharedFreight(source.get().owner(),source.get().index(),town.get().legacyKey().anchorX(),
                town.get().legacyKey().anchorZ(),current.get().type(),carrier,sequence,item,day);
        KingdomMarketDemandService.observe(state,quests,network,carrier,village); return changed;
    }
}
