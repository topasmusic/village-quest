package de.quest.kingdom;

import java.util.LinkedHashMap;
import java.util.OptionalInt;
import java.util.UUID;

/** Personal, bounded speech pacing. Looking or clicking never extends a trade refusal. */
final class NpcRefusalDialogue {
    private record Key(UUID person,UUID player) {}
    private record Said(long tick,int variant) {}
    private final LinkedHashMap<Key,Said> last=new LinkedHashMap<>();
    OptionalInt next(UUID person,UUID player,long now) {
        if(person==null || player==null || now<0) return OptionalInt.empty();
        var key=new Key(person,player); var previous=last.get(key);
        if(previous!=null && now>=previous.tick && now-previous.tick<300) return OptionalInt.empty();
        int variant=previous==null?Math.floorMod(person.hashCode()^player.hashCode(),4):(previous.variant+1)%4;
        last.remove(key); last.put(key,new Said(now,variant));
        while(last.size()>512) last.remove(last.keySet().iterator().next());
        return OptionalInt.of(variant);
    }
}
