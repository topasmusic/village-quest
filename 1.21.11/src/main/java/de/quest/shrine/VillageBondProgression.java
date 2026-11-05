package de.quest.shrine;

import de.quest.data.PlayerQuestData;

/** The sole rank formula. Stored ranks are compatibility caches, never authority. */
public final class VillageBondProgression {
    public static final int TRUSTED_REQUESTS=2;
    public static final int ALLIED_REQUESTS=8;
    public static final int MAX_TRADES=4;
    private VillageBondProgression() {}

    public static VillageBondLevel level(int actualCompletions, int qualifiedTrades) {
        int c=Math.max(0,actualCompletions);
        if(c>=ALLIED_REQUESTS) return VillageBondLevel.ALLIED;
        if(c>=TRUSTED_REQUESTS || (c>=1 && qualifiedTrades==MAX_TRADES)) return VillageBondLevel.TRUSTED;
        return VillageBondLevel.KNOWN;
    }

    public static VillageBondProgress snapshot(PlayerQuestData data,int index) {
        int c=data==null || index<0 || index>=VillageBondService.historicalVillageCount(data) ? 0
                : Math.max(0,data.getTradeRouteInt(VillageBondService.villageKey(index,"completions")));
        int t=VillageTradeTrustService.qualifiedTrades(data,index);
        return new VillageBondProgress(c,t,level(c,t));
    }
}
