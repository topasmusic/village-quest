package de.quest.kingdom;

import java.util.LinkedHashMap;
import java.util.UUID;

/** Per-recipient speech pacing. Rejects do not extend the wait; gameplay actions are independent. */
final class NpcSpeechCooldown {
    private record Key(UUID speaker,UUID listener,boolean ambient) {}
    private record Said(long tick,String line) {}
    private final LinkedHashMap<Key,Said> last=new LinkedHashMap<>();
    private final LinkedHashMap<UUID,Long> ambient=new LinkedHashMap<>();
    boolean allow(UUID speaker,UUID listener,String line,long now,boolean nearby) {
        if(speaker==null || listener==null || line==null || line.isBlank() || now<0) return false;
        Key key=new Key(speaker,listener,nearby); Said previous=last.get(key);
        if(previous!=null && now>=previous.tick && now-previous.tick<(previous.line.equals(line)?600:160)) return false;
        Long around=ambient.get(listener);
        if(nearby && around!=null && now>=around && now-around<400) return false;
        last.remove(key); last.put(key,new Said(now,line));
        while(last.size()>512) last.remove(last.keySet().iterator().next());
        if(nearby) {
            ambient.remove(listener); ambient.put(listener,now);
            while(ambient.size()>128) ambient.remove(ambient.keySet().iterator().next());
        }
        return true;
    }
}
