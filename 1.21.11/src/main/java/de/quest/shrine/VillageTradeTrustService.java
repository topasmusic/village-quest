package de.quest.shrine;

import de.quest.content.story.ShadowsTradeRoadEncounterService;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.kingdom.KingdomVillageService;
import de.quest.kingdom.LivingKingdomsState;
import de.quest.reputation.SocialReputationRules;
import de.quest.util.QuestCalendarService;
import de.quest.village.VillageLifeState;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;

/** Bounded personal proof of committed native trades; no network action can submit a receipt. */
public final class VillageTradeTrustService {
    private VillageTradeTrustService() {}

    public static void onCommittedTrade(ServerLevel world, ServerPlayer player, Villager trader, UUID receipt) {
        if(world==null || player==null || trader==null || receipt==null || player.level()!=world
                || trader.level()!=world || !player.isAlive() || player.isCreative() || player.isSpectator()
                || !trader.isAlive() || trader.isBaby()
                || player.blockPosition().distSqr(trader.blockPosition())>64
                || !world.hasChunkAt(trader.blockPosition())) return;
        var merchantVillage=ShadowsTradeRoadEncounterService.currentVillage(world,trader.blockPosition());
        var visitorVillage=ShadowsTradeRoadEncounterService.currentVillage(world,player.blockPosition());
        if(merchantVillage==null || visitorVillage==null) return;
        String dimension=VillageBondService.dimensionKey(world);
        var kingdoms=LivingKingdomsState.get(world.getServer());
        var key=new VillageLifeState.VillageKey(dimension,merchantVillage.centerX(),merchantVillage.centerZ());
        var canonical=kingdoms.byLegacy(key);
        if(canonical.isEmpty() || !KingdomVillageService.sameVillage(kingdoms,dimension,
                merchantVillage.centerX(),merchantVillage.centerZ(),visitorVillage.centerX(),visitorVillage.centerZ())
                || !KingdomVillageService.withinConfirmedArea(KingdomVillageService.confirmedZones(world,merchantVillage),trader.blockPosition())
                || trader.blockPosition().getX()<merchantVillage.minX() || trader.blockPosition().getX()>merchantVillage.maxX()
                || trader.blockPosition().getZ()<merchantVillage.minZ() || trader.blockPosition().getZ()>merchantVillage.maxZ()
                || VillageLifeState.get(world.getServer()).status(key)!=VillageLifeState.Status.ACTIVE) return;
        var data=VillageBondService.data(world,player.getUUID());
        if(!SocialReputationRules.decision(SocialReputationRules.ServiceKind.NOTICE_ACCEPT,data.socialReputation(),key).allowed()) return;
        // Only existing personal history can earn trade trust. This path never creates contacts or network profiles.
        UUID id=canonical.orElseThrow().id();
        for(int index=0;index<VillageBondService.historicalVillageCount(data);index++) {
            if(id.toString().equals(data.getTradeRouteString(key(index,"canonical_id")))
                    && VillageBondService.matchesStoredDimension(data,key(index,"dimension"),dimension)) {
                if(recordTrade(data,index,id,trader.getUUID(),QuestCalendarService.currentDay(),receipt))
                    QuestState.get(world.getServer()).setDirty();
                return;
            }
        }
    }

    static int qualifiedTrades(PlayerQuestData data,int index) {
        var proof=read(data,index);
        return proof==null ? 0 : proof.total();
    }

    /** Server-thread mutation after world/merchant validation. No inventory or payout mutation. */
    static boolean recordTrade(PlayerQuestData data,int index,UUID village,UUID merchant,long cycle,UUID receipt) {
        if(data==null || village==null || merchant==null || receipt==null || cycle<0 || index<0
                || index>=VillageBondService.historicalVillageCount(data)
                || data.getTradeRouteInt(key(index,"completions"))<1
                || !village.toString().equals(data.getTradeRouteString(key(index,"canonical_id")))) return false;
        var proof=read(data,index);
        if(proof==null || proof.total()>=4 || proof.receipts().contains(receipt)) return false;
        var previous=proof.merchants().get(merchant);
        if(previous!=null && (cycle<previous.cycle() || (cycle==previous.cycle() && previous.count()>=2))) return false;
        int count=previous!=null && cycle==previous.cycle() ? previous.count()+1 : 1;
        data.setTradeRouteInt(key(index,"trust_trade_schema"),1);
        data.setTradeRouteInt(key(index,"trust_trade_total"),proof.total()+1);
        data.setTradeRouteString(key(index,"trust_trade_village"),village.toString());
        data.setTradeRouteString(key(index,"trust_trade_receipt_"+proof.total()),receipt.toString());
        data.setTradeRouteString(key(index,"trust_trade_villager_"+merchant+"_cycle"),Long.toString(cycle));
        data.setTradeRouteInt(key(index,"trust_trade_villager_"+merchant+"_count"),count);
        data.setTradeRouteInt(key(index,"level"),VillageBondProgression.snapshot(data,index).level().id()+1);
        return true;
    }

    /** Unknown/partial proofs stay untouched and earn zero rights. Reads never migrate data. */
    private static Proof read(PlayerQuestData data,int index) {
        if(data==null || index<0 || index>=VillageBondService.historicalVillageCount(data)) return null;
        String prefix=key(index,"trust_trade_");
        var ints=data.getTradeRouteIntState(); var strings=data.getTradeRouteStringState();
        boolean present=ints.keySet().stream().anyMatch(k->k.startsWith(prefix))
                || strings.keySet().stream().anyMatch(k->k.startsWith(prefix))
                || data.getTradeRouteFlags().stream().anyMatch(k->k.startsWith(prefix));
        if(!present) return new Proof(0,Set.of(),Map.of());
        if(data.getTradeRouteFlags().stream().anyMatch(k->k.startsWith(prefix))
                || data.getTradeRouteInt(key(index,"trust_trade_schema"))!=1) return null;
        int total=data.getTradeRouteInt(key(index,"trust_trade_total"));
        if(total<1 || total>4) return null;
        String canonical=data.getTradeRouteString(key(index,"canonical_id"));
        if(uuid(canonical)==null || !canonical.equals(data.getTradeRouteString(key(index,"trust_trade_village")))) return null;
        Set<String> expectedInts=new HashSet<>(Set.of(prefix+"schema",prefix+"total"));
        Set<String> expectedStrings=new HashSet<>(Set.of(prefix+"village"));
        Set<UUID> receipts=new HashSet<>();
        for(int i=0;i<total;i++) {
            String name=prefix+"receipt_"+i; expectedStrings.add(name);
            UUID receipt=uuid(data.getTradeRouteString(name));
            if(receipt==null || !receipts.add(receipt)) return null;
        }
        Map<UUID,MerchantCycle> merchants=new HashMap<>();
        int summed=0;
        for(var entry:ints.entrySet()) {
            String name=entry.getKey();
            if(!name.startsWith(prefix+"villager_") || !name.endsWith("_count")) continue;
            UUID merchant=uuid(name.substring((prefix+"villager_").length(),name.length()-"_count".length()));
            if(merchant==null || merchants.size()>=4 || entry.getValue()<1 || entry.getValue()>2) return null;
            String cycleKey=prefix+"villager_"+merchant+"_cycle";
            long cycle;
            try { cycle=Long.parseLong(data.getTradeRouteString(cycleKey)); }
            catch(NumberFormatException invalid) { return null; }
            if(cycle<0 || !Long.toString(cycle).equals(data.getTradeRouteString(cycleKey))) return null;
            merchants.put(merchant,new MerchantCycle(cycle,entry.getValue())); summed+=entry.getValue();
            expectedInts.add(name); expectedStrings.add(cycleKey);
        }
        if(merchants.isEmpty() || summed>total || merchants.size()>total
                || ints.keySet().stream().anyMatch(k->k.startsWith(prefix) && !expectedInts.contains(k))
                || strings.keySet().stream().anyMatch(k->k.startsWith(prefix) && !expectedStrings.contains(k))) return null;
        return new Proof(total,receipts,merchants);
    }
    private static UUID uuid(String value) {
        try { var id=UUID.fromString(value); return id.toString().equals(value) ? id : null; }
        catch(IllegalArgumentException invalid) { return null; }
    }
    private static String key(int index,String suffix) { return VillageBondService.villageKey(index,suffix); }
    private record MerchantCycle(long cycle,int count) {}
    private record Proof(int total,Set<UUID> receipts,Map<UUID,MerchantCycle> merchants) {}
}
