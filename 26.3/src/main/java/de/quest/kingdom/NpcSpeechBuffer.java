package de.quest.kingdom;

import de.quest.network.NpcSpeechPayload;
import java.util.*;

/** Small client presentation model, independently testable without a renderer or a world. */
public final class NpcSpeechBuffer {
    public record Entry(NpcSpeechPayload speech,long until) {}
    private final LinkedHashMap<UUID,Entry> entries=new LinkedHashMap<>();
    private final LinkedHashMap<UUID,Long> sequences=new LinkedHashMap<>();
    public boolean offer(NpcSpeechPayload speech,String dimension,long now) {
        if(speech==null || now<0 || now>Long.MAX_VALUE-240 || !speech.dimension().equals(dimension)
                || speech.sequence()<=sequences.getOrDefault(speech.body(),-1L)) return false;
        sequences.put(speech.body(),speech.sequence()); entries.remove(speech.body()); entries.put(speech.body(),new Entry(speech,now+speech.ticks()));
        while(entries.size()>16) entries.remove(entries.keySet().iterator().next());
        while(sequences.size()>64) sequences.remove(sequences.keySet().iterator().next());
        return true;
    }
    public List<Entry> active(String dimension,long now) {
        entries.values().removeIf(e -> now<0 || e.until<=now || !e.speech.dimension().equals(dimension));
        return List.copyOf(entries.values());
    }
    public void clear() { entries.clear(); sequences.clear(); }
    public static int duration(int characters) { return Math.max(80,Math.min(240,60+Math.max(0,Math.min(1024,characters))*2)); }
    public static boolean recipient(NpcSpeechPayload.Scope scope,UUID intended,UUID player,boolean sameWorld,double distanceSquared) {
        return sameWorld && Double.isFinite(distanceSquared) && distanceSquared>=0 && distanceSquared<=32*32
                && (scope==NpcSpeechPayload.Scope.NEARBY || scope==NpcSpeechPayload.Scope.PRIVATE && Objects.equals(intended,player));
    }
}
