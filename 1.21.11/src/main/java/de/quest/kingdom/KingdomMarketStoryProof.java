package de.quest.kingdom;

import de.quest.data.QuestState;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** One bounded prepared context per wallet. Replay never derives recipients from the current party or target. */
final class KingdomMarketStoryProof {
    static final String KEY="kingdoms.market.story.proof";
    private record Proof(UUID actor,UUID receipt,UUID host,Map<UUID,UUID> recipients) {}
    private KingdomMarketStoryProof() {}
    static boolean prepare(QuestState quests,UUID actor,UUID receipt,UUID host,List<UUID> recipients) {
        if(quests==null || !valid(actor) || !valid(receipt) || !valid(host) || recipients==null || recipients.size()>4
                || recipients.stream().anyMatch(id -> !valid(id)) || new HashSet<>(recipients).size()!=recipients.size()) return false;
        var data=quests.getPlayersView().get(actor); if(data==null) return false;
        String previous=data.getTradeRouteString(KEY);
        var old=parse(previous);
        if(!previous.isEmpty() && (old.isEmpty() || !old.get().actor.equals(actor))) return false;
        if(old.isPresent() && old.get().receipt.equals(receipt)) return old.get().host.equals(host);
        var eligible=new TreeMap<UUID,UUID>();
        // The acting player's target was wrong or unbound: this trade can never become a story deed later.
        if(host.equals(KingdomStoryProgress.target(data)) && recipients.contains(actor)) {
            for(var id:recipients) {
                var member=quests.getPlayersView().get(id);
                if(member==null || !host.equals(KingdomStoryProgress.target(member))) continue;
                var epoch=KingdomStoryProgress.targetEpoch(member,true); if(epoch!=null) eligible.put(id,epoch);
            }
        }
        if(!eligible.containsKey(actor)) eligible.clear();
        String rows=eligible.entrySet().stream().map(row -> row.getKey()+"@"+row.getValue()).collect(java.util.stream.Collectors.joining(","));
        data.setTradeRouteString(KEY,"1|"+actor+"|"+receipt+"|"+host+"|"+rows); quests.setDirty(); return true;
    }
    static Set<UUID> completeNative(QuestState quests,KingdomMarketLedger ledger,UUID actor,UUID receipt) {
        if(ledger==null || ledger.readOnly()) return Set.of();
        var actual=ledger.receipt(receipt);
        if(actual.isEmpty() || actual.get().phase()!=KingdomMarketLedger.Phase.COMMITTED || !ledger.nativeAcknowledged(receipt)
                || !actual.get().quote().actor().equals(actor)) return Set.of();
        var visit=ledger.visitById(actual.get().quote().visit());
        if(visit.isEmpty() || quests==null) return Set.of();
        var changed=new HashSet<>(complete(quests,actor,receipt,visit.get().market(),true));
        if(KingdomStoryProgress.recordProject(quests.getPlayersView().get(actor),visit.get().market(),
                KingdomStoryProgress.Deed.MARKET_TRADE,"market:"+receipt,true)) { quests.setDirty();changed.add(actor); }
        return Set.copyOf(changed);
    }
    static Set<UUID> complete(QuestState quests,UUID actor,UUID receipt,UUID host,boolean committedAndAcknowledged) {
        if(!committedAndAcknowledged || quests==null || !valid(actor) || !valid(receipt) || !valid(host)) return Set.of();
        var data=quests.getPlayersView().get(actor); if(data==null) return Set.of();
        var parsed=parse(data.getTradeRouteString(KEY));
        if(parsed.isEmpty()) return Set.of(); var proof=parsed.get();
        if(!proof.actor.equals(actor) || !proof.receipt.equals(receipt) || !proof.host.equals(host)) return Set.of();
        var changed=new HashSet<UUID>();
        proof.recipients.forEach((id,epoch) -> {
            var member=quests.getPlayersView().get(id);
            if(member!=null && epoch.equals(KingdomStoryProgress.targetEpoch(member,false))
                    && KingdomStoryProgress.record(member,host,KingdomStoryProgress.Deed.MARKET_TRADE,"market:"+receipt,true)) changed.add(id);
        });
        if(!changed.isEmpty()) quests.setDirty(); return Set.copyOf(changed);
    }
    private static Optional<Proof> parse(String raw) {
        if(raw==null || raw.isEmpty() || raw.length()>512) return Optional.empty();
        var fields=raw.split("\\|",-1); if(fields.length!=5 || !fields[0].equals("1")) return Optional.empty();
        try {
            UUID actor=id(fields[1]),receipt=id(fields[2]),host=id(fields[3]); var recipients=new TreeMap<UUID,UUID>();
            if(!fields[4].isEmpty()) {
                var rows=fields[4].split(",",-1); if(rows.length>4) return Optional.empty();
                for(var row:rows) {
                    var pair=row.split("@",-1); if(pair.length!=2) return Optional.empty();
                    if(recipients.putIfAbsent(id(pair[0]),id(pair[1]))!=null) return Optional.empty();
                }
            }
            if(!recipients.isEmpty() && !recipients.containsKey(actor)) return Optional.empty();
            return Optional.of(new Proof(actor,receipt,host,Map.copyOf(recipients)));
        } catch(IllegalArgumentException invalid) { return Optional.empty(); }
    }
    private static UUID id(String raw) {
        var id=UUID.fromString(raw); if(!valid(id) || !id.toString().equals(raw)) throw new IllegalArgumentException("Invalid identity"); return id;
    }
    private static boolean valid(UUID id) { return id!=null && !id.equals(BuildingSessionPolicy.NONE); }
}
