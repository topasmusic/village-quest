package de.quest.kingdom;

import de.quest.data.PlayerQuestData;
import java.util.UUID;

/** Personal earned history uses a stable village target and bounded confirmed service receipts. */
public final class KingdomStoryProgress {
    public static final String ARC = "living_kingdoms", TARGET = "kingdoms.story.target", CHECK_EARNED = "kingdoms.dorfcheck.earned";
    private static final String RECEIPT = "kingdoms.story.receipt.";
    private static final String EPOCH = "kingdoms.story.target_epoch";
    private KingdomStoryProgress() {}
    public enum Deed { MEET_LEADER, MEET_INNKEEPER, MEET_STOREKEEPER, MARKET_TRADE, STORE_DELIVERY, FIRST_PROJECT }
    public static UUID target(PlayerQuestData data) {
        if (data == null) return null;
        try { UUID id=UUID.fromString(data.getTradeRouteString(TARGET)); return id.equals(BuildingSessionPolicy.NONE)?null:id; }
        catch (IllegalArgumentException ignored) { return null; }
    }
    /** Caller validates actual village authority, or explicit admission to the same party story. */
    public static boolean bind(PlayerQuestData data, UUID village, boolean explicitRebind) {
        if (data == null || village == null || village.equals(BuildingSessionPolicy.NONE)) return false;
        UUID old=target(data);
        if (village.equals(old)) return true;
        if (old!=null && data.hasStoryFlag(de.quest.quest.story.StoryQuestKeys.sharedTurnInConsumed(
                de.quest.quest.story.StoryArcType.LIVING_KINGDOMS,data.getStoryChapterProgress(ARC)))) return false;
        if (old!=null && !explicitRebind) return false;
        if(old!=null) for(Deed deed:Deed.values()) if(has(data,deed))
            recordProject(data,old,deed,data.getTradeRouteString(RECEIPT+deed.name()),true);
        data.setTradeRouteString(TARGET,village.toString());
        data.setTradeRouteString(EPOCH,UUID.randomUUID().toString());
        for (Deed deed:Deed.values()) data.setTradeRouteString(RECEIPT+deed.name(),"");
        return true;
    }
    /** Lazy adoption for existing bindings. An opaque future value is retained and cannot prove a trade. */
    static UUID targetEpoch(PlayerQuestData data,boolean adopt) {
        if(target(data)==null) return null;
        String raw=data.getTradeRouteString(EPOCH);
        if(raw.isEmpty() && adopt) { raw=UUID.randomUUID().toString(); data.setTradeRouteString(EPOCH,raw); }
        try { UUID id=UUID.fromString(raw); return id.equals(BuildingSessionPolicy.NONE) || !id.toString().equals(raw)?null:id; }
        catch(IllegalArgumentException invalid) { return null; }
    }
    public static boolean record(PlayerQuestData data, UUID village, Deed deed, String receipt, boolean confirmed) {
        if (!confirmed || data == null || village == null || !village.equals(target(data)) || deed == null
                || receipt == null || receipt.isBlank() || receipt.length()>128 || receipt.codePoints().anyMatch(Character::isISOControl)) return false;
        String key=RECEIPT+deed.name();
        if (!data.getTradeRouteString(key).isEmpty()) return false;
        data.setTradeRouteString(key,receipt); return true;
    }
    public static boolean has(PlayerQuestData data, Deed deed) { return data!=null && deed!=null && !data.getTradeRouteString(RECEIPT+deed.name()).isEmpty(); }
    /** Personal project history belongs to its canonical village, independently of the active story target. */
    public static boolean recordProject(PlayerQuestData data,UUID village,Deed deed,String receipt,boolean confirmed) {
        if(!confirmed || data==null || village==null || village.equals(BuildingSessionPolicy.NONE) || deed==null || deed==Deed.FIRST_PROJECT
                || receipt==null || receipt.isBlank() || receipt.length()>128 || receipt.codePoints().anyMatch(Character::isISOControl)) return false;
        String key="kingdoms.project.receipt."+village+"."+deed.name();
        if(!data.getTradeRouteString(key).isEmpty()) return false;
        data.setTradeRouteString(key,receipt);return true;
    }
    public static boolean hasProject(PlayerQuestData data,UUID village,Deed deed) {
        return data!=null && village!=null && deed!=null &&
                (!data.getTradeRouteString("kingdoms.project.receipt."+village+"."+deed.name()).isEmpty()
                || village.equals(target(data)) && has(data,deed));
    }
    public static boolean earned(PlayerQuestData data, LivingKingdomsState state, BuildingScanner.Role role) {
        UUID target=target(data);
        return target!=null && state!=null && state.village(target).isPresent()
                && state.anchors().official(target,role).map(KingdomAnchorRegistry.AnchorView::earned).orElse(false);
    }
    public static boolean hasMarketPlace(PlayerQuestData data,LivingKingdomsState state) {
        var target=target(data);
        return target!=null && state.anchors().official(target,BuildingScanner.Role.MARKET)
                .filter(a -> !state.marketPlaces().assigned(a.id()).isEmpty()).isPresent();
    }
    public static boolean complete(PlayerQuestData data, LivingKingdomsState state, int chapter) {
        if (target(data)==null || state==null || state.village(target(data)).isEmpty()) return false;
        return switch(chapter) {
            case 0 -> earned(data,state,BuildingScanner.Role.COUNCIL);
            case 1 -> earned(data,state,BuildingScanner.Role.INN) && earned(data,state,BuildingScanner.Role.MARKET) && earned(data,state,BuildingScanner.Role.STOREHOUSE) && hasMarketPlace(data,state);
            case 2 -> has(data,Deed.MEET_LEADER) && has(data,Deed.MEET_INNKEEPER) && has(data,Deed.MEET_STOREKEEPER);
            case 3 -> has(data,Deed.MARKET_TRADE) && has(data,Deed.STORE_DELIVERY);
            case 4 -> has(data,Deed.FIRST_PROJECT);
            default -> false;
        };
    }
    public static boolean restoreUnlocks(PlayerQuestData data) {
        if (data==null || data.getStoryChapterProgress(ARC)<1 && !data.hasStoryCompleted(ARC)) return false;
        boolean changed=!data.hasMilestoneFlag(KingdomBuildingService.COUNCIL_UNLOCK) || !data.hasMilestoneFlag(KingdomBuildingService.BUILDINGS_UNLOCK);
        data.setMilestoneFlag(KingdomBuildingService.COUNCIL_UNLOCK,true); data.setMilestoneFlag(KingdomBuildingService.BUILDINGS_UNLOCK,true);
        return changed;
    }
    public static boolean hasLocalRoutePermit(PlayerQuestData data) {
        return data!=null && (data.getStoryChapterProgress(ARC)>=2 || data.hasStoryCompleted(ARC)
                || data.hasMilestoneFlag("kingdoms.route_permit.earned"));
    }
}
