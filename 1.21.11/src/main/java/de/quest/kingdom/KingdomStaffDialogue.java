package de.quest.kingdom;

import de.quest.data.PlayerQuestData;
import de.quest.village.VillageLifeState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

/** Private bounded conversational memory; no story, authority, reward or world mutation. */
final class KingdomStaffDialogue {
    private static final String MEMORY="kingdoms.staff.familiar";
    private static final String LEGACY="kingdoms.staff.legacy";
    private KingdomStaffDialogue() {}
    static boolean seen(PlayerQuestData data,UUID person) {
        return Arrays.asList(data.getTradeRouteString(MEMORY).split(",")).contains(person.toString());
    }
    static boolean remember(PlayerQuestData data,UUID person) {
        boolean adopted=snapshotLegacy(data);
        if(seen(data,person)) return adopted;
        var remembered=new ArrayList<>(Arrays.asList(data.getTradeRouteString(MEMORY).split(",")));
        remembered.removeIf(String::isBlank); remembered.addFirst(person.toString());
        data.setTradeRouteString(MEMORY,String.join(",",remembered.stream().limit(16).toList())); return true;
    }
    /** Snapshot only pre-upgrade memory. New familiarity is never retrospective project evidence. */
    static boolean snapshotLegacy(PlayerQuestData data) {
        if(data==null || data.getTradeRouteInt(LEGACY+".schema")!=0) return false;
        data.setTradeRouteString(LEGACY,data.getTradeRouteString(MEMORY));
        data.setTradeRouteInt(LEGACY+".schema",1); return true;
    }
    static boolean legacySeen(PlayerQuestData data,UUID person) {
        return data!=null && data.getTradeRouteInt(LEGACY+".schema")==1
                && Arrays.stream(data.getTradeRouteString(LEGACY).split(",")).limit(16).anyMatch(person.toString()::equals);
    }
    static String key(KingdomPersonRegistry.View person,VillageLifeState.Status status,boolean again) {
        if(person.role()==KingdomPersonRegistry.Role.MARKET_TRADER) throw new IllegalArgumentException("Market greetings have their own travel context");
        String role=person.role().name().toLowerCase(java.util.Locale.ROOT);
        String scene=person.visit()!=null?"visiting":switch(status) {
            case ABANDONED -> "abandoned";
            case RECOVERING -> "recovering";
            case ACTIVE -> (again?"again.":"hello.")+person.personality();
        };
        return "message.village-quest.kingdom.staff."+role+"."+scene;
    }
}
