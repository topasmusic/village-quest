package de.quest.kingdom;

import de.quest.data.QuestState;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

/** Native local evidence and the explicit council acknowledgement; no generated project successes. */
public final class KingdomRecognitionService {
    private KingdomRecognitionService() {}
    static Set<KingdomRecognition.Evidence> facts(LivingKingdomsState state,de.quest.data.PlayerQuestData data,UUID village,long now) {
        return facts(state,data,village,now,List.of());
    }
    static Set<KingdomRecognition.Evidence> facts(LivingKingdomsState state,de.quest.data.PlayerQuestData data,UUID village,long now,
                                                 List<de.quest.caravan.KingdomRouteConnections.Connection> connections) {
        migrateStaffMemories(state,data,village);
        var found=EnumSet.noneOf(KingdomRecognition.Evidence.class);
        if(state.founding().residents(village).size()==3) found.add(KingdomRecognition.Evidence.POPULATION);
        for(var role:BuildingScanner.Role.values()) state.anchors().official(village,role)
                .filter(a -> state.anchors().mayContinue(a.id(),now)).ifPresent(a -> found.add(KingdomRecognition.Evidence.valueOf(role.name())));
        if(met(state,data,village,KingdomPersonRegistry.Role.LEADER,KingdomStoryProgress.Deed.MEET_LEADER)
                && met(state,data,village,KingdomPersonRegistry.Role.INNKEEPER,KingdomStoryProgress.Deed.MEET_INNKEEPER)
                && met(state,data,village,KingdomPersonRegistry.Role.STOREKEEPER,KingdomStoryProgress.Deed.MEET_STOREKEEPER)) found.add(KingdomRecognition.Evidence.VOICES);
        if(KingdomStoryProgress.hasProject(data,village,KingdomStoryProgress.Deed.MARKET_TRADE)) found.add(KingdomRecognition.Evidence.TRADE);
        if(KingdomStoryProgress.hasProject(data,village,KingdomStoryProgress.Deed.STORE_DELIVERY)) found.add(KingdomRecognition.Evidence.FREIGHT);
        state.anchors().official(village,BuildingScanner.Role.MARKET).ifPresent(a -> {
            int count=state.marketPlaces().usable(a.id(),now).size();
            if(count>=2) found.add(KingdomRecognition.Evidence.TWO_STANDS);
            if(count>=3) found.add(KingdomRecognition.Evidence.THREE_STANDS);
            if(count>=4) found.add(KingdomRecognition.Evidence.FOUR_STANDS);
            if(count>=5) found.add(KingdomRecognition.Evidence.FIVE_STANDS);
        });
        if(state.markets().visit(village).filter(v -> v.open() && v.traders().stream().anyMatch(t ->
                state.marketDays().place(village,t.person(),now).isPresent() && state.people().person(t.person()).filter(p ->
                        (p.presence()==KingdomPersonRegistry.Presence.PRESENT || p.presence()==KingdomPersonRegistry.Presence.VISITING) && p.body()!=null).isPresent())).isPresent())
            found.add(KingdomRecognition.Evidence.ACTIVE_VISIT);
        var partners=new HashSet<UUID>();
        for(var deal:state.diplomacy().contracts(village)) if(deal.status()==KingdomDiplomacy.Status.SIGNED) {
            UUID other=deal.left().equals(village)?deal.right():deal.left(); partners.add(other);
            if(deal.type()==KingdomDiplomacy.Type.FRIENDSHIP && state.diplomacy().met(deal)) found.add(KingdomRecognition.Evidence.MEETING);
            if(deal.type()!=KingdomDiplomacy.Type.FRIENDSHIP && connections.stream().anyMatch(c -> c.active() && c.joins(village,other)))
                found.add(KingdomRecognition.Evidence.ROUTE_AGREEMENT);
        }
        if(partners.size()>=2) found.add(KingdomRecognition.Evidence.TWO_PAIRS);
        if(state.relations().hasAid(village)) found.add(KingdomRecognition.Evidence.AID);
        if(state.worksites().valid(village,now)) found.add(KingdomRecognition.Evidence.WORKSITE);
        return Set.copyOf(found);
    }
    private static boolean met(LivingKingdomsState state,de.quest.data.PlayerQuestData data,UUID village,
                               KingdomPersonRegistry.Role role,KingdomStoryProgress.Deed deed) {
        return KingdomStoryProgress.hasProject(data,village,deed);
    }
    private static boolean migrateStaffMemories(LivingKingdomsState state,de.quest.data.PlayerQuestData data,UUID village) {
        if(data==null) return false;
        boolean changed=KingdomStaffDialogue.snapshotLegacy(data);
        for(var role:List.of(KingdomPersonRegistry.Role.LEADER,KingdomPersonRegistry.Role.INNKEEPER,KingdomPersonRegistry.Role.STOREKEEPER)) {
            var person=state.people().staff(village,role);
            if(person.isPresent() && KingdomStaffDialogue.legacySeen(data,person.get().id()))
                changed|=KingdomStoryProgress.recordProject(data,village,KingdomStoryProgress.Deed.valueOf("MEET_"+role.name()),"legacy-dialogue:"+person.get().id(),true);
        }
        return changed;
    }
    static List<String> missingConversations(de.quest.data.PlayerQuestData data,UUID village) {
        return java.util.stream.Stream.of(KingdomStoryProgress.Deed.MEET_LEADER,KingdomStoryProgress.Deed.MEET_INNKEEPER,KingdomStoryProgress.Deed.MEET_STOREKEEPER)
                .filter(deed -> !KingdomStoryProgress.hasProject(data,village,deed))
                .map(deed -> deed.name().substring(5).toLowerCase(Locale.ROOT)).toList();
    }
    public static de.quest.network.BuildingPayloads.Recognition view(ServerPlayer player,UUID village) {
        var state=LivingKingdomsState.get(player.level().getServer()); var data=QuestState.get(player.level().getServer()).getPlayerData(player.getUUID());
        if(migrateStaffMemories(state,data,village)) QuestState.get(player.level().getServer()).setDirty();
        int level=state.recognition().level(village); var facts=facts(state,data,village,player.level().getServer().overworld().getGameTime(),
                de.quest.caravan.KingdomRouteConnections.read(player.level().getServer()));
        var missing=KingdomRecognition.requirements(level).stream().filter(e -> !facts.contains(e)).map(e -> e.name().toLowerCase(Locale.ROOT)).sorted().toList();
        return new de.quest.network.BuildingPayloads.Recognition(state.recognition().revision(village),level,missing,state.recognition().readOnly(),missingConversations(data,village));
    }
    public static boolean complete(ServerPlayer player,UUID village,long villageRevision,long recognitionRevision,int project) {
        var server=player.level().getServer(); if(!server.isSameThread()) return false;
        var state=LivingKingdomsState.get(server); var quests=QuestState.get(server); var data=quests.getPlayerData(player.getUUID());
        if(migrateStaffMemories(state,data,village)) quests.setDirty();
        if(!state.recognition().complete(player.getUUID(),village,villageRevision,recognitionRevision,project,
                facts(state,data,village,server.overworld().getGameTime(),de.quest.caravan.KingdomRouteConnections.read(server)))) return false;
        if(project==0) KingdomStoryService.recordConfirmed(player.level(),player,village,KingdomStoryProgress.Deed.FIRST_PROJECT,"recognition:"+village+":1");
        if(project>=2) state.people().ensure(village,KingdomPersonRegistry.Role.LEADER,0).ifPresent(p -> state.people().promote(p.id(),p.revision()));
        player.sendSystemMessage(Component.translatable("message.village-quest.recognition.earned",state.village(village).orElseThrow().name(),
                Component.translatable("screen.village-quest.building.recognition_level."+(project+1)),project+1));
        return true;
    }
}
