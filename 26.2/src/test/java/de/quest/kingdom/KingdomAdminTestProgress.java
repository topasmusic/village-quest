package de.quest.kingdom;

import de.quest.data.PlayerQuestData;
import java.util.*;

/** Temporary personal test ledger. Actual observations and human acceptance are deliberately separate. */
public final class KingdomAdminTestProgress {
    public static final String PREFIX="kingdoms.admin-test.";
    public enum Event {
        FOUND, COUNCIL, INN, MARKET, STOREHOUSE, RENAME, RELEASE, LEADER, INNKEEPER, STOREKEEPER,
        BUY, SELL, FREIGHT, PROJECT_1, PROJECT_2, PROJECT_3, PROJECT_4, PROJECT_5,
        OFFER_TRADE, OFFER_SUPPLY, OFFER_FRIENDSHIP, COUNTER, SIGN_TRADE, SIGN_SUPPLY, PREPARE_FRIENDSHIP,
        MEETING, CRISIS_CHOICE, LOYALTY_CHOICE, RECONCILIATION, CHECK, ARCHIVE,
        TOOL_NAVIGATE, TOOL_LEDGER, TOOL_SIGIL, TOOL_FLOCK, TOOL_HIVE, TOOL_REACH, TOOL_CHECK, TOOL_SEAL, TOOL_SATCHEL, TOOL_HORN,
        ARTIFACT_1, ARTIFACT_2, ARTIFACT_3, ARTIFACT_4, ARTIFACT_5
    }
    public enum Step {
        INTRO, FOUNDATION(Event.FOUND), BUILDINGS(Event.COUNCIL,Event.INN,Event.MARKET,Event.STOREHOUSE),
        BUILDING_ERRORS, REPAIR, MOVE(Event.RELEASE), POPULATION,
        VOICES(Event.LEADER,Event.INNKEEPER,Event.STOREKEEPER), IDENTITY, SLEEP, ESCAPE,
        BUY(Event.BUY), SELL(Event.SELL), MARKET_LIMITS, STANDS, TRAVEL, FREIGHT(Event.FREIGHT),
        PROJECT_1(Event.PROJECT_1), PROJECT_2(Event.PROJECT_2), PROJECT_3(Event.PROJECT_3), PROJECT_4(Event.PROJECT_4), PROJECT_5(Event.PROJECT_5),
        TRADE(Event.OFFER_TRADE,Event.SIGN_TRADE), SUPPLY(Event.OFFER_SUPPLY,Event.SIGN_SUPPLY), COUNTER(Event.COUNTER),
        FRIENDSHIP(Event.OFFER_FRIENDSHIP,Event.PREPARE_FRIENDSHIP,Event.MEETING), CRISIS(Event.CRISIS_CHOICE),
        LOYALTY(Event.LOYALTY_CHOICE), RECONCILIATION(Event.RECONCILIATION), ATTACK, AUTONOMY,
        SPEECH_PRIVATE, SPEECH_NEARBY, SPEECH_FALLBACK, JOURNAL, MAP_NAMES(Event.RENAME),
        CHECK(Event.CHECK), ARCHIVE(Event.ARCHIVE),
        ARTIFACT_1(Event.ARTIFACT_1), ARTIFACT_2(Event.ARTIFACT_2), ARTIFACT_3(Event.ARTIFACT_3), ARTIFACT_4(Event.ARTIFACT_4), ARTIFACT_5(Event.ARTIFACT_5),
        TOOLS(Event.TOOL_NAVIGATE,Event.TOOL_LEDGER,Event.TOOL_SIGIL,Event.TOOL_FLOCK,Event.TOOL_HIVE,Event.TOOL_REACH,Event.TOOL_CHECK,Event.TOOL_SEAL,Event.TOOL_SATCHEL,Event.TOOL_HORN),
        SAVE_RELOAD, REPLAY, PERMISSIONS, MULTIPLAYER, CRASH, RESULT;
        private final Set<Event> required;
        Step(Event... events) { required=Set.of(events); }
        public Set<Event> required() { return required; }
        public boolean witness() { return this==MULTIPLAYER; }
        public String key() { return name().toLowerCase(Locale.ROOT); }
    }
    public enum Result { OPEN, PASS, FAIL, NOT_RUN }
    public record View(UUID session,UUID owner,UUID village,int revision,int cursor,int mode,Set<Event> observed,Map<Step,Result> results) {
        public Step step() { return Step.values()[cursor]; }
        public boolean ready() { return observed.containsAll(step().required()); }
    }
    private KingdomAdminTestProgress() {}
    private static String key(String field) { return PREFIX+field; }
    private static UUID uuid(String text) { var id=UUID.fromString(text); if(id.equals(BuildingSessionPolicy.NONE)) throw new IllegalArgumentException(); return id; }
    public static boolean exists(PlayerQuestData data) {
        return data.getTradeRouteIntState().keySet().stream().anyMatch(k -> k.startsWith(PREFIX))
                || data.getTradeRouteStringState().keySet().stream().anyMatch(k -> k.startsWith(PREFIX));
    }
    /** Invalid/future payload is never repaired by reading or by a normal start. */
    public static Optional<View> view(PlayerQuestData data,UUID owner) {
        if(data==null || owner==null || data.getTradeRouteInt(key("schema"))!=1) return Optional.empty();
        try {
            UUID stored=uuid(data.getTradeRouteString(key("owner"))),session=uuid(data.getTradeRouteString(key("session")));
            if(!stored.equals(owner)) return Optional.empty();
            String target=data.getTradeRouteString(key("village")); UUID village=target.isEmpty()?null:uuid(target);
            int revision=data.getTradeRouteInt(key("revision")),cursor=data.getTradeRouteInt(key("cursor")),mode=data.getTradeRouteInt(key("mode"));
            if(revision<0 || cursor<0 || cursor>=Step.values().length || mode<1 || mode>3) return Optional.empty();
            var events=EnumSet.noneOf(Event.class); var results=new EnumMap<Step,Result>(Step.class);
            var allowedInts=new HashSet<>(List.of(key("schema"),key("revision"),key("cursor"),key("mode")));
            var allowedStrings=new HashSet<>(List.of(key("owner"),key("session"),key("village"),key("market_receipt"),key("market_session")));
            for(var e:Event.values()) { String k=key("event."+e.name()); allowedStrings.add(k); String receipt=data.getTradeRouteString(k);
                if(!receipt.isEmpty()) { if(receipt.length()>160 || village==null) return Optional.empty(); events.add(e); } }
            for(var step:Step.values()) {
                String k=key("result."+step.name()); allowedInts.add(k); int value=data.getTradeRouteInt(k);
                if(value<0 || value>=Result.values().length) return Optional.empty(); results.put(step,Result.values()[value]);
                String note=key("note."+step.name()),witness=key("witness."+step.name()); allowedStrings.add(note); allowedStrings.add(witness);
                if(!validNote(data.getTradeRouteString(note))) return Optional.empty();
                if(!data.getTradeRouteString(witness).isEmpty() && (step!=Step.MULTIPLAYER || uuid(data.getTradeRouteString(witness)).equals(owner))) return Optional.empty();
                if(value==Result.PASS.ordinal() && (!events.containsAll(step.required()) || step.witness() && data.getTradeRouteString(witness).isEmpty())) return Optional.empty();
            }
            if(data.getTradeRouteIntState().keySet().stream().anyMatch(k -> k.startsWith(PREFIX) && !allowedInts.contains(k))
                    || data.getTradeRouteStringState().keySet().stream().anyMatch(k -> k.startsWith(PREFIX) && !allowedStrings.contains(k))) return Optional.empty();
            String market=data.getTradeRouteString(key("market_receipt")),marketSession=data.getTradeRouteString(key("market_session"));
            if(market.isEmpty()!=marketSession.isEmpty()) return Optional.empty();
            if(!market.isEmpty()) { uuid(market); if(!uuid(marketSession).equals(session)) return Optional.empty(); }
            return Optional.of(new View(session,owner,village,revision,cursor,mode,Set.copyOf(events),Map.copyOf(results)));
        } catch(RuntimeException invalid) { return Optional.empty(); }
    }
    public static boolean start(PlayerQuestData data,UUID owner,boolean allowed) {
        if(!allowed || data==null || owner==null || owner.equals(BuildingSessionPolicy.NONE) || exists(data)) return false;
        data.setTradeRouteInt(key("schema"),1); data.setTradeRouteInt(key("mode"),1);
        data.setTradeRouteString(key("owner"),owner.toString()); data.setTradeRouteString(key("session"),UUID.randomUUID().toString()); return true;
    }
    private static Optional<View> current(PlayerQuestData data,UUID actor,UUID session,int revision,boolean allowed) {
        return allowed?view(data,actor).filter(v -> v.session.equals(session) && v.revision==revision && revision<Integer.MAX_VALUE):Optional.empty();
    }
    private static void bump(PlayerQuestData data,View v) { data.setTradeRouteInt(key("revision"),v.revision+1); }
    /** Explicit retarget starts a new test epoch, preserving real gameplay and only clearing this ledger. */
    public static boolean target(PlayerQuestData data,UUID actor,UUID session,int revision,UUID village,boolean allowed) {
        var v=current(data,actor,session,revision,allowed); if(v.isEmpty() || village==null || village.equals(BuildingSessionPolicy.NONE)) return false;
        clear(data); start(data,actor,true); data.setTradeRouteString(key("village"),village.toString()); return true;
    }
    public static boolean mode(PlayerQuestData data,UUID actor,UUID session,int revision,boolean resume,boolean allowed) {
        var v=current(data,actor,session,revision,allowed); if(v.isEmpty() || v.get().mode==3) return false;
        data.setTradeRouteInt(key("mode"),resume?1:2); bump(data,v.get()); return true;
    }
    public static boolean move(PlayerQuestData data,UUID actor,UUID session,int revision,int delta,boolean allowed) {
        var v=current(data,actor,session,revision,allowed); if(v.isEmpty() || v.get().mode!=1 || Math.abs(delta)!=1) return false;
        int next=v.get().cursor+delta; if(next<0 || next>=Step.values().length) return false;
        data.setTradeRouteInt(key("cursor"),next); bump(data,v.get()); return true;
    }
    public static boolean result(PlayerQuestData data,UUID actor,UUID session,int revision,Result result,String note,UUID witness,boolean allowed) {
        var value=current(data,actor,session,revision,allowed); if(value.isEmpty() || result==null || result==Result.OPEN || !validNote(note)) return false;
        var v=value.get(); if(v.mode!=1 || result==Result.PASS && (!v.ready() || v.step().witness() && (witness==null || witness.equals(actor)))) return false;
        if(v.step()==Step.RESULT && result==Result.PASS && v.results.entrySet().stream().anyMatch(e -> e.getKey()!=Step.RESULT && e.getValue()==Result.OPEN)) return false;
        data.setTradeRouteInt(key("result."+v.step().name()),result.ordinal()); data.setTradeRouteString(key("note."+v.step().name()),note);
        if(v.step().witness() && witness!=null) data.setTradeRouteString(key("witness."+v.step().name()),witness.toString());
        if(v.step()==Step.RESULT && result==Result.PASS) data.setTradeRouteInt(key("mode"),3);
        bump(data,v); return true;
    }
    public static boolean record(PlayerQuestData data,UUID actor,UUID village,Event event,String receipt,boolean allowed) {
        var value=allowed?view(data,actor):Optional.<View>empty();
        if(value.isEmpty() || village==null || event==null || receipt==null || receipt.isBlank() || receipt.length()>160) return false;
        var v=value.get(); if(v.mode!=1 || v.revision==Integer.MAX_VALUE || v.village==null && event!=Event.FOUND
                || v.village!=null && !v.village.equals(village) || v.observed.contains(event)) return false;
        if(v.village==null) data.setTradeRouteString(key("village"),village.toString());
        data.setTradeRouteString(key("event."+event.name()),receipt); bump(data,v); return true;
    }
    public static boolean prepareMarket(PlayerQuestData data,UUID actor,UUID receipt,UUID village,boolean allowed) {
        var v=allowed?view(data,actor):Optional.<View>empty();
        if(v.isEmpty() || v.get().mode!=1 || receipt==null || !Objects.equals(village,v.get().village)) return false;
        data.setTradeRouteString(key("market_receipt"),receipt.toString()); data.setTradeRouteString(key("market_session"),v.get().session.toString()); return true;
    }
    public static boolean market(PlayerQuestData data,UUID actor,UUID receipt,UUID village,boolean buy,boolean committedAck,boolean allowed) {
        return receipt!=null && committedAck && receipt.toString().equals(data.getTradeRouteString(key("market_receipt")))
                && record(data,actor,village,buy?Event.BUY:Event.SELL,"market:"+receipt,allowed);
    }
    public static boolean reset(PlayerQuestData data,UUID actor,UUID session,int revision,boolean allowed) {
        if(current(data,actor,session,revision,allowed).isEmpty()) return false; clear(data); return true;
    }
    private static void clear(PlayerQuestData data) {
        for(String k:List.copyOf(data.getTradeRouteIntState().keySet())) if(k.startsWith(PREFIX)) data.setTradeRouteInt(k,0);
        for(String k:List.copyOf(data.getTradeRouteStringState().keySet())) if(k.startsWith(PREFIX)) data.setTradeRouteString(k,"");
    }
    private static boolean validNote(String note) { return note!=null && note.length()<=160 && note.chars().noneMatch(c -> c<32 || c==127); }
    public static String note(PlayerQuestData data,Step step) { return data.getTradeRouteString(key("note."+step.name())); }
}
