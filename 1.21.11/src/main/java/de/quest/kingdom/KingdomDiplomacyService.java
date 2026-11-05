package de.quest.kingdom;

import de.quest.data.PlayerQuestData;
import de.quest.network.BuildingPayloads;
import de.quest.shrine.VillageBondService;
import de.quest.shrine.VillageContactService;
import java.util.*;

/** Private council adapter. Knowledge and mandates come from actual canonical contact/player data. */
public final class KingdomDiplomacyService {
    private KingdomDiplomacyService() {}
    static boolean actNative(net.minecraft.server.level.ServerPlayer player,UUID host,long hostRevision,
                             BuildingPayloads.Partner snapshot,long pairRevision,int choice,boolean accept) {
        if(player==null || !player.level().getServer().isSameThread()) return false;
        var server=player.level().getServer(); var state=LivingKingdomsState.get(server);
        var data=de.quest.data.QuestState.get(server).getPlayerData(player.getUUID());
        if(!actFromView(state,data,player.getUUID(),host,hostRevision,snapshot,pairRevision,choice,accept)) return false;
        if(!accept) {
            var type=KingdomDiplomacy.Type.values()[choice/3];
            var person=state.people().ensure(snapshot.village(),KingdomPersonRegistry.Role.LEADER,0);
            var need=KingdomMarketDemandService.current(player,snapshot.village());
            var deal=state.diplomacy().between(host,snapshot.village(),type).orElseThrow();
            var response=person.isEmpty()?new KingdomNegotiation.Answer(KingdomNegotiation.Reaction.UNKNOWN,deal.terms(),null,0):
                    KingdomNegotiation.answer(person.get().personality(),type,deal.terms(),
                    need.map(de.quest.village.LivingVillageNetworkState.VillageSnapshot::need).orElse(null),
                    need.map(de.quest.village.LivingVillageNetworkState.VillageSnapshot::condition).orElse(null));
            state.diplomacy().respond(deal,response);
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "screen.village-quest.building.diplomacy_response."+response.personality()+"."+response.reaction().name().toLowerCase(Locale.ROOT),
                    net.minecraft.network.chat.Component.translatable("screen.village-quest.building.diplomacy_terms."+response.preferred().ordinal()),
                    response.need()==null?net.minecraft.network.chat.Component.empty():response.need().label()));
        }
        return true;
    }
    static List<LivingKingdomsState.VillageView> contacts(LivingKingdomsState state,PlayerQuestData data,UUID host) {
        var known=new TreeMap<UUID,LivingKingdomsState.VillageView>();
        for(int i=0;i<Math.min(1024,VillageBondService.historicalVillageCount(data));i++) {
            var contact=VillageContactService.read(data,i); if(contact==null) continue;
            var dimension=data.getTradeRouteString(VillageBondService.villageKey(i,"dimension"));
            var canonical=data.getTradeRouteString(VillageBondService.villageKey(i,"canonical_id"));
            try {
                state.byLegacy(new de.quest.village.VillageLifeState.VillageKey(dimension,contact.x(),contact.z()))
                        .filter(v -> v.id().toString().equals(canonical) && !v.id().equals(host)).ifPresent(v -> known.put(v.id(),v));
            } catch(IllegalArgumentException invalidContact) { /* Unknown contact is not global knowledge. */ }
        }
        return List.copyOf(known.values());
    }
    public static BuildingPayloads.Diplomacy view(LivingKingdomsState state,PlayerQuestData data,UUID actor,UUID host,int requestedPage) {
        return view(state,data,actor,host,requestedPage,List.of());
    }
    static boolean canRead(LivingKingdomsState state,PlayerQuestData data,UUID actor,UUID host) {
        return state!=null && data!=null && actor!=null && !actor.equals(BuildingPayloads.NONE) && state.village(host).isPresent()
                && (state.canManageVillage(actor,host) || contacts(state,data,BuildingPayloads.NONE).stream().anyMatch(v -> v.id().equals(host)));
    }
    public static BuildingPayloads.Diplomacy view(LivingKingdomsState state,PlayerQuestData data,UUID actor,UUID host,int requestedPage,
                                                 List<de.quest.caravan.KingdomRouteConnections.Connection> connections) {
        if(!canRead(state,data,actor,host)) return BuildingPayloads.Diplomacy.EMPTY;
        var contacts=contacts(state,data,host); int pages=Math.max(1,(contacts.size()+31)/32), page=Math.max(0,Math.min(pages-1,requestedPage));
        var partners=contacts.stream().skip(page*32L).limit(32).map(v -> {
            var deals=Arrays.stream(KingdomDiplomacy.Type.values()).map(type -> state.diplomacy().between(host,v.id(),type)
                    .map(d -> {
                        var answer=state.diplomacy().answer(d);
                        return new BuildingPayloads.Deal(d.revision(),d.status().ordinal(),d.terms().ordinal(),!d.from().equals(host),
                                answer.map(a -> a.reaction().name().toLowerCase(Locale.ROOT)).orElse("none"),
                                answer.map(a -> a.preferred().ordinal()).orElse(0),answer.map(KingdomNegotiation.Answer::personality).orElse(0),
                                answer.map(KingdomNegotiation.Answer::need).map(de.quest.village.VillageNeed::key).orElse("none"));
                    })
                    .orElse(new BuildingPayloads.Deal(-1,-1,0,false))).toList();
            String connection=connections.stream().filter(c -> c.joins(host,v.id())).sorted(Comparator.comparing(c -> !c.active()))
                    .map(de.quest.caravan.KingdomRouteConnections.Connection::reason).findFirst().orElse("none");
            boolean meeting=state.diplomacy().between(host,v.id(),KingdomDiplomacy.Type.FRIENDSHIP)
                    .flatMap(state.diplomacy()::meetingHost).filter(host::equals).isPresent();
            return new BuildingPayloads.Partner(v.id(),v.name(),v.revision(),deals,connection,meeting,explain(state,actor,host,v.id()));
        }).toList();
        return new BuildingPayloads.Diplomacy(page,pages,partners,state.diplomacy().readOnly() || !state.canManageVillage(actor,host));
    }
    /** Pure projection of stored direct evidence; never creates contacts, persons, receipts or progress. */
    static BuildingPayloads.Relation explain(LivingKingdomsState state,UUID actor,UUID host,UUID other) {
        if(state.relations().readOnly() || state.diplomacy().readOnly() || state.autonomy().readOnly()) return BuildingPayloads.Relation.UNKNOWN;
        var memory=state.relations().between(host,other); var personal=state.relations().personal(actor,other); UUID disappointed=other;
        if(!personal.distrust() && state.relations().personal(actor,host).distrust()) { personal=state.relations().personal(actor,host); disappointed=host; }
        var causes=new LinkedHashSet<String>();
        boolean trade=false,friendship=false,prepared=false,paused=false;
        for(var type:KingdomDiplomacy.Type.values()) {
            var deal=state.diplomacy().between(host,other,type);
            if(deal.isEmpty()) continue;
            switch(deal.get().status()) {
                case SIGNED -> { causes.add(type.name().toLowerCase(Locale.ROOT)); if(type==KingdomDiplomacy.Type.FRIENDSHIP) friendship=true; else trade=true; }
                case PREPARED -> prepared=true;
                case PAUSED -> paused=true;
                default -> { }
            }
        }
        if(prepared) causes.add("prepared"); if(paused) causes.add("paused");
        boolean loyalty=memory.filter(m -> m.loyaltyBroken() && !m.loyaltyRepaired()).isPresent();
        boolean repaired=memory.filter(m -> m.loyaltyRepaired() || !m.loyaltyBroken() && m.reconciliationRemembered()).isPresent();
        boolean aid=memory.filter(KingdomRelations.View::aidRemembered).isPresent();
        if(memory.filter(m -> m.deliveries()>0).isPresent()) causes.add("delivery");
        if(aid) causes.add("aid");
        if(memory.filter(KingdomRelations.View::loyaltyBroken).isPresent()) causes.add("loyalty");
        if(repaired) causes.add("reconciliation");
        if(personal.distrust()) causes.add("attack");
        state.autonomy().between(host,other).ifPresent(v -> {
            if(v.habit()>0) causes.add("reliable");
            if(v.request()!=null) causes.add(v.request()==KingdomDiplomacy.Type.SUPPLY?"initiative_aid":"initiative_bond");
        });
        state.diplomacy().between(host,other,KingdomDiplomacy.Type.TRADE).filter(state.diplomacy()::autonomous).ifPresent(v -> causes.add("autonomous"));
        boolean strained=loyalty || personal.distrust() || paused;
        String label=strained?"strained":friendship?"friendly":aid?"cooperating":trade?"trading":"acquainted";
        String trend=strained?"strained":repaired?"rebuilding":memory.isPresent()?"improving":"steady";
        String repair=personal.distrust()?(personal.repairNeed().equals("none")?"attack_apology":"attack_freight"):
                loyalty?"loyalty":repaired || paused || prepared?"negotiate":"none";
        return new BuildingPayloads.Relation(label,trend,List.copyOf(causes),repair,personal.distrust()?personal.repairNeed():"none",
                personal.distrust()?state.village(disappointed).orElseThrow().name():"");
    }
    static boolean actFromView(LivingKingdomsState state,PlayerQuestData data,UUID actor,UUID host,long hostRevision,
                               BuildingPayloads.Partner snapshot,long pairRevision,int choice,boolean accept) {
        if(snapshot==null || choice<0 || choice>(accept?2:8) || snapshot.deals().get(accept?choice:choice/3).revision()!=pairRevision) return false;
        return act(state,data,actor,host,hostRevision,snapshot.village(),snapshot.revision(),pairRevision,choice,accept);
    }
    static boolean act(LivingKingdomsState state,PlayerQuestData data,UUID actor,UUID host,long hostRevision,UUID other,long otherRevision,
                       long pairRevision,int choice,boolean accept) {
        if(state==null || data==null || !state.canManageVillage(actor,host) || choice<0 || choice>(accept?2:8)
                || state.village(host).filter(v -> v.revision()==hostRevision).isEmpty()
                || contacts(state,data,host).stream().noneMatch(v -> v.id().equals(other) && v.revision()==otherRevision)) return false;
        var type=KingdomDiplomacy.Type.values()[accept?choice:choice/3];
        if(accept) return state.diplomacy().between(host,other,type).filter(d -> !d.from().equals(host))
                .flatMap(d -> state.diplomacy().accept(actor,d.id(),pairRevision)).isPresent();
        return state.diplomacy().offer(actor,host,other,hostRevision,otherRevision,type,pairRevision,KingdomDiplomacy.Terms.values()[choice%3]).isPresent();
    }
}
