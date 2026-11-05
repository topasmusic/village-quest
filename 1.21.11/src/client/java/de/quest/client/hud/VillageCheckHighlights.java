package de.quest.client.hud;

import de.quest.network.VillageCheckPayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;

/** Ten-second loaded-only markers, discarded on dimension/world change or disconnect. */
public final class VillageCheckHighlights {
    private static ClientLevel level;
    private static List<VillageCheckPayload.Marker> markers=List.of();
    private static int ticks;
    private VillageCheckHighlights() {}
    public static void receive(Minecraft client,VillageCheckPayload value) {
        clear();
        if(client.level==null || client.player==null || !client.level.dimension().identifier().toString().equals(value.dimension())) return;
        level=client.level; ticks=200;
        markers=value.markers().stream().filter(m -> client.player.blockPosition().distSqr(m.position())<=48L*48).toList();
    }
    public static void tick(Minecraft client) {
        if(client.level==null || client.level!=level || client.player==null || ticks<=0) { clear(); return; }
        if(ticks--%10!=0) return;
        for(var marker:markers) if(level.hasChunkAt(marker.position()) && client.player.blockPosition().distSqr(marker.position())<=48L*48) {
            var pos=marker.position();
            var particle=switch(marker.status()) {
                case VALID -> ParticleTypes.HAPPY_VILLAGER;
                case INVALID -> ParticleTypes.ANGRY_VILLAGER;
                case UNKNOWN -> ParticleTypes.END_ROD;
            };
            level.addParticle(particle,pos.getX()+.5,pos.getY()+1.2,pos.getZ()+.5,0,.03,0);
        }
    }
    public static void clear() { level=null; markers=List.of(); ticks=0; }
}
