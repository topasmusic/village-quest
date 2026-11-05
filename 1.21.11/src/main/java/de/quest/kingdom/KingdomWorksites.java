package de.quest.kingdom;

import java.util.*;
import net.minecraft.core.BlockPos;

/** Small transient cache of real Vanilla assignment/path observations. Earned recognition remains saved separately. */
public final class KingdomWorksites {
    private record Proof(long villageRevision,UUID anchor,long anchorRevision,long tick,String dimension,BlockPos position,BuildingScanner.Range range) {}
    private final LivingKingdomsState state;
    private final LinkedHashMap<UUID,Proof> proofs=new LinkedHashMap<>();
    KingdomWorksites(LivingKingdomsState state) { this.state=state; }
    boolean observe(UUID village,long villageRevision,UUID anchor,long anchorRevision,long now,NativeFoundingObserver.Evidence evidence) {
        proofs.remove(village);
        var council=state.anchors().official(village,BuildingScanner.Role.COUNCIL);
        if(state.readOnly() || now<0 || evidence==null || !evidence.fullyLoaded() || !evidence.hasJob()
                || state.village(village).filter(v -> v.revision()==villageRevision).isEmpty()
                || council.filter(a -> a.id().equals(anchor) && a.revision()==anchorRevision && state.anchors().maySpawn(a.id(),now)).isEmpty()) return false;
        var actual=council.get();
        boolean found=evidence.residents().stream().anyMatch(r -> r.job()!=null
                && state.founding().residentOwner(r.id()).filter(owner -> !owner.equals(village)).isEmpty()
                && state.resolveAt(actual.dimension(),r.bed()).filter(v -> v.id().equals(village)).isPresent()
                && state.resolveAt(actual.dimension(),r.job()).filter(v -> v.id().equals(village)).isPresent()
                && Math.abs((long)r.job().getX()-actual.position().getX())<=actual.range().horizontal
                && Math.abs((long)r.job().getY()-actual.position().getY())<=actual.range().vertical
                && Math.abs((long)r.job().getZ()-actual.position().getZ())<=actual.range().horizontal);
        if(!found) return false;
        proofs.entrySet().removeIf(entry -> now<entry.getValue().tick || now-entry.getValue().tick>100);
        if(proofs.size()>=64) proofs.remove(proofs.keySet().iterator().next());
        proofs.put(village,new Proof(villageRevision,anchor,anchorRevision,now,actual.dimension(),actual.position(),actual.range())); return true;
    }
    public boolean valid(UUID village,long now) {
        var proof=proofs.get(village);
        return !state.readOnly() && proof!=null && now>=proof.tick && now-proof.tick<=100
                && state.village(village).filter(v -> v.revision()==proof.villageRevision).isPresent()
                && state.anchors().official(village,BuildingScanner.Role.COUNCIL).filter(a -> a.id().equals(proof.anchor)
                    && a.revision()==proof.anchorRevision && state.anchors().maySpawn(a.id(),now)).isPresent();
    }
    void invalidateAt(String dimension,BlockPos position) {
        if(position==null) return;
        proofs.entrySet().removeIf(entry -> {
            var p=entry.getValue();
            return p.dimension.equals(dimension) && Math.abs((long)position.getX()-p.position.getX())<=p.range.horizontal+8
                    && Math.abs((long)position.getZ()-p.position.getZ())<=p.range.horizontal+8
                    && Math.abs((long)position.getY()-p.position.getY())<=p.range.vertical+8;
        });
    }
}
