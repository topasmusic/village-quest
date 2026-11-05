package de.quest.kingdom;

import de.quest.data.PlayerQuestData;
import java.util.*;
import net.minecraft.core.BlockPos;

/** Personal rehearsal progress, independent of the old acceptance checklist and normal story slots. */
public final class KingdomGuidedProgress {
    public static final String PREFIX="kingdoms.guided-test.";
    public enum Stage {
        WELCOME, COUNCIL(KingdomAdminTestProgress.Event.FOUND), INN(KingdomAdminTestProgress.Event.INN),
        MARKET(KingdomAdminTestProgress.Event.MARKET), STOREHOUSE(KingdomAdminTestProgress.Event.STOREHOUSE),
        VOICES(KingdomAdminTestProgress.Event.LEADER,KingdomAdminTestProgress.Event.INNKEEPER,KingdomAdminTestProgress.Event.STOREKEEPER),
        BUY(KingdomAdminTestProgress.Event.BUY), SELL(KingdomAdminTestProgress.Event.SELL),
        PROJECTS, RELATIONS(KingdomAdminTestProgress.Event.OFFER_TRADE), CHECK(KingdomAdminTestProgress.Event.CHECK), TOOLS, ARTIFACTS, JOURNAL, MAP, FINISH;
        final Set<KingdomAdminTestProgress.Event> events;
        Stage(KingdomAdminTestProgress.Event... events) { this.events=Set.of(events); }
        public String key() { return name().toLowerCase(Locale.ROOT); }
        public Set<KingdomAdminTestProgress.Event> events() { return events; }
        public boolean inspection() { return events.isEmpty(); }
    }
    public record View(UUID owner,UUID session,int revision,Stage stage,BlockPos camp,String dimension,
                       BlockPos origin,UUID village,boolean active,int done,int skipped,boolean equipped,boolean visited,
                       Set<KingdomAdminTestProgress.Event> observed) {
        public boolean ready() { return stage.inspection()?visited:observed.containsAll(stage.events); }
    }
    private KingdomGuidedProgress() {}
    private static String k(String key) { return PREFIX+key; }
    public static boolean exists(PlayerQuestData d) { return d.getTradeRouteIntState().keySet().stream().anyMatch(k -> k.startsWith(PREFIX))
            || d.getTradeRouteStringState().keySet().stream().anyMatch(k -> k.startsWith(PREFIX)); }
    public static Optional<View> view(PlayerQuestData d,UUID owner) {
        if(d==null || owner==null || d.getTradeRouteInt(k("schema"))!=1) return Optional.empty();
        try {
            if(!owner.toString().equals(d.getTradeRouteString(k("owner")))) return Optional.empty();
            UUID session=UUID.fromString(d.getTradeRouteString(k("session")));
            int rev=d.getTradeRouteInt(k("revision")),step=d.getTradeRouteInt(k("step")),active=d.getTradeRouteInt(k("active"));
            int done=d.getTradeRouteInt(k("done")),skip=d.getTradeRouteInt(k("skipped"));
            if(session.equals(BuildingSessionPolicy.NONE) || rev<0 || step<0 || step>=Stage.values().length || active<0 || active>1
                    || done<0 || skip<0 || ((done|skip)>>>Stage.values().length)!=0 || (done&skip)!=0) return Optional.empty();
            String dim=d.getTradeRouteString(k("dimension")); if(net.minecraft.resources.Identifier.tryParse(dim)==null) return Optional.empty();
            String town=d.getTradeRouteString(k("village")); UUID village=town.isEmpty()?null:UUID.fromString(town);
            var events=EnumSet.noneOf(KingdomAdminTestProgress.Event.class);
            for(var e:KingdomAdminTestProgress.Event.values()) if(d.getTradeRouteInt(k("event."+e.name()))==1) events.add(e);
            return Optional.of(new View(owner,session,rev,Stage.values()[step],pos(d,"camp"),dim,pos(d,"origin"),village,active==1,done,skip,
                    (d.getTradeRouteInt(k("kits"))&(1<<step))!=0,d.getTradeRouteInt(k("visited"))==step+1,Set.copyOf(events)));
        } catch(RuntimeException invalid) { return Optional.empty(); }
    }
    private static BlockPos pos(PlayerQuestData d,String field) { return new BlockPos(d.getTradeRouteInt(k(field+"x")),d.getTradeRouteInt(k(field+"y")),d.getTradeRouteInt(k(field+"z"))); }
    private static void position(PlayerQuestData d,String field,BlockPos p) { d.setTradeRouteInt(k(field+"x"),p.getX()); d.setTradeRouteInt(k(field+"y"),p.getY()); d.setTradeRouteInt(k(field+"z"),p.getZ()); }
    public static boolean start(PlayerQuestData d,UUID owner,BlockPos camp,String dim,BlockPos origin,boolean allowed) {
        if(!allowed || d==null || owner==null || camp==null || origin==null || exists(d) || net.minecraft.resources.Identifier.tryParse(dim)==null) return false;
        d.setTradeRouteInt(k("schema"),1); d.setTradeRouteInt(k("active"),1);
        d.setTradeRouteString(k("owner"),owner.toString()); d.setTradeRouteString(k("session"),UUID.randomUUID().toString());
        d.setTradeRouteString(k("dimension"),dim); position(d,"camp",camp); position(d,"origin",origin); return true;
    }
    public static boolean matches(View v,UUID session,int revision,boolean allowed) { return allowed && v!=null && v.active && v.session.equals(session) && v.revision==revision && revision<Integer.MAX_VALUE; }
    private static void bump(PlayerQuestData d,View v) { d.setTradeRouteInt(k("revision"),v.revision+1); }
    public static boolean visit(PlayerQuestData d,UUID owner,UUID session,int revision,boolean allowed) {
        var v=view(d,owner).orElse(null); if(!matches(v,session,revision,allowed)) return false;
        d.setTradeRouteInt(k("visited"),v.stage.ordinal()+1); bump(d,v); return true;
    }
    public static boolean kit(PlayerQuestData d,UUID owner,UUID session,int revision,boolean allowed) {
        var v=view(d,owner).orElse(null); if(!matches(v,session,revision,allowed) || v.equipped) return false;
        d.setTradeRouteInt(k("kits"),d.getTradeRouteInt(k("kits"))|1<<v.stage.ordinal()); bump(d,v); return true;
    }
    public static boolean advance(PlayerQuestData d,UUID owner,UUID session,int revision,boolean skip,boolean allowed) {
        var v=view(d,owner).orElse(null); if(!matches(v,session,revision,allowed) || !skip && !v.ready()) return false;
        d.setTradeRouteInt(k(skip?"skipped":"done"),(skip?v.skipped:v.done)|1<<v.stage.ordinal());
        if(v.stage==Stage.FINISH) d.setTradeRouteInt(k("active"),0);
        else d.setTradeRouteInt(k("step"),v.stage.ordinal()+1);
        bump(d,v); return true;
    }
    public static boolean record(PlayerQuestData d,UUID owner,UUID village,KingdomAdminTestProgress.Event event,boolean allowed) {
        var v=view(d,owner).orElse(null);
        if(!allowed || v==null || !v.active || v.revision==Integer.MAX_VALUE || village==null || event==null
                || v.village==null && event!=KingdomAdminTestProgress.Event.FOUND || v.village!=null && !v.village.equals(village) || v.observed.contains(event)) return false;
        if(v.village==null) d.setTradeRouteString(k("village"),village.toString());
        d.setTradeRouteInt(k("event."+event.name()),1); bump(d,v); return true;
    }
    /** Explicit admin rehearsal shortcut; never records real gameplay events or grants kits. */
    public static boolean jumpToTrader(PlayerQuestData d,UUID owner,UUID session,int revision,boolean allowed) {
        var v=view(d,owner).orElse(null);
        if(!matches(v,session,revision,allowed) || v.stage.ordinal()>=Stage.BUY.ordinal() || v.village==null) return false;
        int previous=(1<<Stage.BUY.ordinal())-1;
        d.setTradeRouteInt(k("done"),v.done|previous);
        d.setTradeRouteInt(k("skipped"),v.skipped&~previous);
        d.setTradeRouteInt(k("step"),Stage.BUY.ordinal());
        d.setTradeRouteInt(k("waiting"),0);d.setTradeRouteInt(k("tries"),0);
        bump(d,v);return true;
    }
    public static boolean stop(PlayerQuestData d,UUID owner,UUID session,int revision,boolean allowed) {
        var v=view(d,owner).orElse(null); if(!matches(v,session,revision,allowed)) return false;
        d.setTradeRouteInt(k("active"),0); bump(d,v); return true;
    }
    /** Explicit restart resets only this owner's walkthrough; one-time grants remain consumed. */
    public static boolean restart(PlayerQuestData d,UUID owner,UUID session,int revision,UUID nextSession,BlockPos camp,boolean allowed) {
        var v=view(d,owner).orElse(null);
        if(!allowed || v==null || !v.session.equals(session) || v.revision!=revision || nextSession==null
                || nextSession.equals(BuildingSessionPolicy.NONE) || nextSession.equals(session) || camp==null || camp.equals(v.camp)) return false;
        for(String field:List.of("step","done","skipped","visited","waiting","tries","trader_jump")) d.setTradeRouteInt(k(field),0);
        for(var event:KingdomAdminTestProgress.Event.values()) d.setTradeRouteInt(k("event."+event.name()),0);
        d.setTradeRouteString(k("village"),""); d.setTradeRouteString(k("session"),nextSession.toString());
        d.setTradeRouteInt(k("revision"),0); d.setTradeRouteInt(k("active"),1); position(d,"camp",camp);
        return true;
    }
    public static boolean resume(PlayerQuestData d,UUID owner,boolean allowed) {
        var v=view(d,owner).orElse(null); if(!allowed || v==null || v.active || v.revision==Integer.MAX_VALUE || (v.done&1<<Stage.FINISH.ordinal())!=0) return false;
        d.setTradeRouteInt(k("active"),1); bump(d,v); return true;
    }
}
