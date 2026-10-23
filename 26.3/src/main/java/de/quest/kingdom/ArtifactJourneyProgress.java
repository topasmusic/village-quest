package de.quest.kingdom;

import de.quest.archive.GuildArchiveService.ArchiveItem;
import de.quest.data.PlayerQuestData;
import de.quest.guildtown.GuildTownProgress;
import de.quest.quest.story.StoryArcType;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Personal, bounded successful service receipts. Does not consume items or mutate old story claims. */
public final class ArtifactJourneyProgress {
    public static final String ARC="artifact_journey", PREFIX="kingdoms.artifacts.";
    public enum Deed {
        NAVIGATE(0), LEDGER(0), SIGIL(0), FLOCK(1), HIVE(1), REACH(2), CHECK(2),
        SEAL(3), SATCHEL(3), DELIVERY(3), HORN(4), ARRIVAL(4);
        public final int chapter;
        Deed(int chapter) { this.chapter=chapter; }
    }
    private ArtifactJourneyProgress() {}
    public static boolean unlocked(PlayerQuestData data,Set<ArchiveItem> earned) {
        return data!=null && GuildTownProgress.finaleClaimed(data) && data.hasStoryCompleted(KingdomStoryProgress.ARC)
                && earned!=null && earned.containsAll(List.of(ArchiveItem.STARREACH_RING,ArchiveItem.MERCHANT_SEAL,
                    ArchiveItem.SHEPHERD_FLUTE,ArchiveItem.APIARISTS_SMOKER,ArchiveItem.SURVEYORS_COMPASS,
                    ArchiveItem.CARAVAN_LEDGER,ArchiveItem.ROADWARDEN_HORN,ArchiveItem.WAYFARERS_SIGIL,
                    ArchiveItem.GUILD_COURIERS_SATCHEL,ArchiveItem.VILLAGE_CHECK));
    }
    private static boolean writable(PlayerQuestData data) {
        return data!=null && data.getTradeRouteInt(PREFIX+"schema")>=0 && data.getTradeRouteInt(PREFIX+"schema")<=1;
    }
    public static boolean active(PlayerQuestData data) {
        return writable(data) && data.getActiveStoryArc()==StoryArcType.ARTIFACT_JOURNEY
                && data.getStoryChapterProgress(ARC)>=0 && data.getStoryChapterProgress(ARC)<5;
    }
    public static UUID target(PlayerQuestData data) { return uuid(data,"target"); }
    public static UUID epoch(PlayerQuestData data) { return uuid(data,"epoch"); }
    private static UUID uuid(PlayerQuestData data,String key) {
        if(data==null) return null;
        try { var id=UUID.fromString(data.getTradeRouteString(PREFIX+key)); return id.equals(BuildingSessionPolicy.NONE)?null:id; }
        catch(IllegalArgumentException invalid) { return null; }
    }
    /** Called after acceptance or explicit local rebind, never by an incidental visit. */
    public static boolean begin(PlayerQuestData data,UUID target) {
        if(!active(data) || target==null || target.equals(BuildingSessionPolicy.NONE)) return false;
        data.setTradeRouteInt(PREFIX+"schema",1); data.setTradeRouteString(PREFIX+"target",target.toString());
        data.setTradeRouteString(PREFIX+"epoch",UUID.randomUUID().toString());
        data.setTradeRouteInt(PREFIX+"accepted",data.getStoryChapterProgress(ARC)+1);
        for(var deed:Deed.values()) data.setTradeRouteString(PREFIX+"proof."+deed.name(),"");
        data.setTradeRouteInt(PREFIX+"choice",0); data.setTradeRouteString(PREFIX+"cargo","");
        return true;
    }
    public static boolean select(PlayerQuestData data,UUID target) {
        if(active(data)) return begin(data,target);
        if(!writable(data) || data.getActiveStoryArc()!=null || data.hasStoryCompleted(ARC) || target==null || target.equals(BuildingSessionPolicy.NONE)) return false;
        data.setTradeRouteInt(PREFIX+"schema",1); data.setTradeRouteString(PREFIX+"target",target.toString()); return true;
    }
    /** Full real contract identity prevents canceled/reaccepted cargo from borrowing a previous packing deed. */
    public static boolean pack(PlayerQuestData data,UUID route,int sequence,UUID contract) {
        if(!active(data) || data.getStoryChapterProgress(ARC)!=3 || !has(data,Deed.SATCHEL) || route==null || contract==null || sequence<=0) return false;
        data.setTradeRouteString(PREFIX+"cargo",route+":"+sequence+":"+contract); return true;
    }
    public static boolean matchesCargo(PlayerQuestData data,UUID route,int sequence,UUID contract) {
        return active(data) && data.getStoryChapterProgress(ARC)==3 && has(data,Deed.SATCHEL) && route!=null && contract!=null && sequence>0
                && (route+":"+sequence+":"+contract).equals(data.getTradeRouteString(PREFIX+"cargo"));
    }
    public static List<Deed> required(int chapter) { return Arrays.stream(Deed.values()).filter(d -> d.chapter==chapter).toList(); }
    public static boolean record(PlayerQuestData data,UUID target,UUID epoch,Deed deed,String receipt) {
        if(!active(data) || epoch==null || !epoch.equals(epoch(data)) || target==null || !target.equals(target(data))
                || deed==null || deed.chapter!=data.getStoryChapterProgress(ARC)
                || data.getTradeRouteInt(PREFIX+"accepted")!=deed.chapter+1 || has(data,deed)
                || receipt==null || receipt.isBlank() || receipt.length()>128 || receipt.codePoints().anyMatch(Character::isISOControl)) return false;
        data.setTradeRouteString(PREFIX+"proof."+deed.name(),receipt); return true;
    }
    public static boolean has(PlayerQuestData data,Deed deed) {
        return writable(data) && deed!=null && !data.getTradeRouteString(PREFIX+"proof."+deed.name()).isEmpty();
    }
    public static boolean ready(PlayerQuestData data) {
        return active(data) && epoch(data)!=null && target(data)!=null && required(data.getStoryChapterProgress(ARC)).stream().allMatch(d -> has(data,d))
                && (data.getStoryChapterProgress(ARC)!=4 || choice(data)>0);
    }
    public static boolean choose(PlayerQuestData data,int choice) {
        if(!active(data) || data.getStoryChapterProgress(ARC)!=4 || choice<1 || choice>2
                || !required(4).stream().allMatch(d -> has(data,d))) return false;
        data.setTradeRouteInt(PREFIX+"choice",choice);
        data.vanillaRewards().preference(choice==1?"supplies":"roads"); return true;
    }
    public static int choice(PlayerQuestData data) { int value=data.getTradeRouteInt(PREFIX+"choice"); return value==1||value==2?value:0; }
}
