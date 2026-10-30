package de.quest.quest;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.kingdom.KingdomStoryGuidance;
import de.quest.kingdom.KingdomStoryProgress;
import de.quest.kingdom.LivingKingdomsState;
import de.quest.quest.daily.FirstDailyChoiceService;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryQuestService;
import de.quest.shrine.VillageBondLevel;
import de.quest.shrine.VillageBondProgression;
import de.quest.shrine.VillageBondService;
import de.quest.shrine.VillageIntroRequestService;
import de.quest.shrine.VillageWelcomeService;
import de.quest.village.VillageLifeState;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Read-only next steps from earned server evidence. No progress, scans or chunk loads. */
public final class QuestGuidanceService {
    private static final String TEXT="quest.village-quest.guidance.";
    private QuestGuidanceService() {}
    public enum Status { LOCKED, ACTIVE, READY, RECOVERY }
    public record QuestNextStep(String id, Status status, Component action, int current, int required,
                               UUID village, String dimension, BlockPos location) {}

    public static QuestNextStep firstStory() {
        return step("story:core:failing_harvest",Status.LOCKED,"first_daily",0,1,null);
    }

    /** Same Trust step for the board, personal journey and Kingdom prerequisites. */
    public static QuestNextStep localTrust(PlayerQuestData data, int index, LivingKingdomsState state) {
        var village=canonical(data,index,state);
        if (village==null) return step("village:contact",Status.RECOVERY,"contact",0,1,null);
        var bond=VillageBondProgression.snapshot(data,index);
        if (bond.level()!=VillageBondLevel.KNOWN)
            return step("village:trust:"+village.id(),Status.READY,"mandate",bond.actualCompletions(),2,village);
        return step("village:trust:"+village.id(),Status.ACTIVE,
                bond.actualCompletions()==0?"local_help":"local_help_or_trade",bond.actualCompletions(),2,village,
                bond.qualifiedTrades());
    }

    public static QuestNextStep villageJourney(PlayerQuestData data, LivingKingdomsState state, VillageLifeState life) {
        if (FirstDailyChoiceService.canChoose(data)) return step("daily:first",Status.ACTIVE,"choose_daily",0,1,null);
        if (FirstDailyChoiceService.isActive(data)) return step("daily:first",Status.ACTIVE,"finish_daily",0,1,null);
        if (!FirstDailyChoiceService.isCompleted(data)) return firstStory();
        int index=data.getTradeRouteInt("guild_intro.welcome_village")-1;
        if (VillageIntroRequestService.lostContact(data,index,state,life))
            return step("village:contact",Status.RECOVERY,"replace_contact",0,1,null);
        var village=canonical(data,index,state);
        if (data.hasTradeRouteFlag("guild_intro.welcome_active") && !VillageWelcomeService.isCompleted(data))
            return step("village:welcome",Status.ACTIVE,"greet",Math.max(0,Math.min(3,
                    data.getTradeRouteInt("guild_intro.welcome_greetings"))),3,village);
        if (!VillageWelcomeService.isCompleted(data)) return step("village:contact",Status.ACTIVE,"contact",0,1,null);
        if (village==null) return step("village:contact",Status.RECOVERY,"check_contact",0,1,null);
        if (VillageBondService.knownBondLevel(data,village.id())==VillageBondLevel.KNOWN)
            return localHelp(data,index,state);
        return null;
    }

    private static QuestNextStep localHelp(PlayerQuestData data,int index,LivingKingdomsState state) {
        String active=data.getTradeRouteString("village_intro.active");
        var village=canonical(data,index,state);
        if (village!=null && village.id().toString().equals(data.getTradeRouteString("village_intro.village")))
            for (var type:de.quest.shrine.VillageIntroRequestType.values()) if (type.id().equals(active)) {
                boolean harvest=type==de.quest.shrine.VillageIntroRequestType.HARVEST_HELP;
                int current=harvest?Math.min(8,Math.max(0,data.getTradeRouteInt("village_intro.harvest_count"))):0;
                return step("village:intro:"+type.id(),harvest && current==8?Status.READY:Status.ACTIVE,
                        type.id(),current,type.target(),village);
            }
        return localTrust(data,index,state);
    }

    public static QuestNextStep lockedStory(ServerLevel world,ServerPlayer player,StoryArcType requested) {
        StoryArcType next=StoryQuestService.activeArcType(world,player.getUUID());
        if(next==null) next=StoryQuestService.availableArcType(world,player.getUUID());
        return next!=null && next!=requested?story(world,player,next):null;
    }

    public static QuestNextStep kingdom(PlayerQuestData data, LivingKingdomsState state, UUID owner, int chapter) {
        UUID target=KingdomStoryProgress.target(data);
        var village=target==null?canonical(data,data.getTradeRouteInt("guild_intro.welcome_village")-1,state)
                :state.village(target).orElse(null);
        if (target!=null && village==null)
            return step("story:kingdom:living_kingdoms",Status.RECOVERY,"recover_target",0,1,null);
        if (target!=null && KingdomStoryProgress.complete(data,state,chapter))
            return step("story:kingdom:living_kingdoms",Status.READY,"claim",1,1,village);
        if (village==null) return step("story:kingdom:living_kingdoms",Status.ACTIVE,"council_prepare",0,1,null);
        if (!state.canManageVillage(owner,village.id())) {
            if (VillageBondService.knownBondLevel(data,village.id())==VillageBondLevel.KNOWN) {
                int index=index(data,village.id());
                var trust=localTrust(data,index,state);
                return new QuestNextStep("story:kingdom:living_kingdoms",Status.ACTIVE,
                        Component.translatable(TEXT+"prepare_and_help",trust.action()),trust.current(),trust.required(),
                        village.id(),village.legacyKey().dimension(),village.arrival());
            }
            return step("story:kingdom:living_kingdoms",Status.ACTIVE,"mandate",0,1,village);
        }
        if (target==null) return step("story:kingdom:living_kingdoms",Status.ACTIVE,"council_bind",0,1,village);
        Component action=KingdomStoryGuidance.next(data,state,chapter);
        return new QuestNextStep("story:kingdom:living_kingdoms",Status.ACTIVE,action,0,1,target,
                village.legacyKey().dimension(),village.arrival());
    }

    /** One authored source feeds questmaster objectives and, in AP7, individual journal/trackers. */
    public static QuestNextStep story(ServerLevel world, ServerPlayer player, StoryArcType arc) {
        var data=QuestState.get(world.getServer()).getPlayerData(player.getUUID());
        String id="story:"+(arc==StoryArcType.LIVING_KINGDOMS?"kingdom:":"core:")+arc.id();
        if (VillageIntroRequestService.blocksActions(world,player.getUUID()))
            return step(id,Status.RECOVERY,"pending_delivery",0,1,null);
        if (!de.quest.questmaster.QuestMasterProgressionService.isStoryCategoryUnlocked(world,player.getUUID())) return firstStory();
        if (!StoryQuestService.isActive(world,player.getUUID(),arc))
            return new QuestNextStep(id,Status.ACTIVE,Component.translatable(TEXT+"accept_story",
                    StoryQuestService.definition(arc).title()),0,1,null,"",null);
        // Consumed party turn-ins remain claimable even if the physical target was lost afterwards.
        if (StoryQuestService.isReadyForClaim(world,player,arc)) return step(id,Status.READY,"claim",1,1,null);
        if (arc==StoryArcType.LIVING_KINGDOMS)
            return kingdom(data,LivingKingdomsState.get(world.getServer()),player.getUUID(),
                    StoryQuestService.chapterIndex(world,player.getUUID(),arc));
        var chapter=StoryQuestService.chapter(world,player.getUUID(),arc);
        if (chapter==null) return step(id,Status.RECOVERY,"questmaster",0,1,null);
        var lines=chapter.progressLines(world,player.getUUID());
        return new QuestNextStep(id,Status.ACTIVE,lines.isEmpty()?chapter.offerParagraph1():lines.getFirst(),0,1,null,"",null);
    }

    private static int index(PlayerQuestData data, UUID village) {
        for(int i=0;i<VillageBondService.historicalVillageCount(data);i++)
            if (village.toString().equals(data.getTradeRouteString(VillageBondService.villageKey(i,"canonical_id")))) return i;
        return -1;
    }
    private static LivingKingdomsState.VillageView canonical(PlayerQuestData data,int index,LivingKingdomsState state) {
        if (data==null || state==null || index<0 || index>=VillageBondService.historicalVillageCount(data)) return null;
        try { return state.village(UUID.fromString(data.getTradeRouteString(VillageBondService.villageKey(index,"canonical_id")))).orElse(null); }
        catch(IllegalArgumentException absent) { return null; }
    }
    private static QuestNextStep step(String id,Status status,String key,int current,int required,
            LivingKingdomsState.VillageView village,Object... extra) {
        Object[] args=new Object[4+extra.length];
        args[0]=village==null?Component.empty():Component.literal(village.name()); args[1]=current; args[2]=required;
        args[3]=village==null?Component.empty():Component.literal(village.legacyKey().dimension()+" · "+
                village.arrival().getX()+", "+village.arrival().getZ());
        System.arraycopy(extra,0,args,4,extra.length);
        return new QuestNextStep(id,status,Component.translatable(TEXT+key,args),current,required,
                village==null?null:village.id(),village==null?"":village.legacyKey().dimension(),village==null?null:village.arrival());
    }
}
