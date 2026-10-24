package de.quest.network;

import de.quest.kingdom.BuildingScanner;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Private, bounded diagnostic snapshot from the existing local scan cache. */
public record VillageCheckPayload(String dimension,List<Marker> markers,java.util.UUID session,boolean initial) implements CustomPacketPayload {
    public VillageCheckPayload(String dimension,List<Marker> markers) { this(dimension,markers,new java.util.UUID(0,0),true); }
    public record Marker(BlockPos position,BuildingScanner.Status status,BuildingScanner.Role role,String village,List<BuildingPayloads.Row> rows,boolean conflict) {
        public Marker(BlockPos position,BuildingScanner.Status status) { this(position,status,BuildingScanner.Role.COUNCIL,"",List.of(),false); }
        public Marker { if(position==null || status==null || role==null || village==null || village.length()>96 || rows==null || rows.size()>13) throw new IllegalArgumentException("Invalid marker"); position=position.immutable(); rows=List.copyOf(rows); }
    }
    public VillageCheckPayload {
        if(session==null || dimension==null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") || markers==null || markers.size()>8)
            throw new IllegalArgumentException("Invalid village check");
        markers=List.copyOf(markers);
    }
    public static final Type<VillageCheckPayload> ID=new Type<>(Identifier.fromNamespaceAndPath("village-quest","village_check"));
    public static final StreamCodec<RegistryFriendlyByteBuf,VillageCheckPayload> CODEC=StreamCodec.of((buf,value) -> {
        buf.writeUtf(value.dimension,128); buf.writeUUID(value.session); buf.writeBoolean(value.initial); buf.writeVarInt(value.markers.size());
        for(var marker:value.markers) {
            buf.writeBlockPos(marker.position); buf.writeEnum(marker.status); buf.writeEnum(marker.role); buf.writeUtf(marker.village,96); buf.writeBoolean(marker.conflict);
            buf.writeVarInt(marker.rows.size());
            for(var row:marker.rows) {
                buf.writeEnum(row.check()); buf.writeBoolean(row.passed()); buf.writeVarInt(row.found()); buf.writeVarInt(row.required());
                buf.writeVarInt(row.targets().size()); for(var pos:row.targets()) buf.writeBlockPos(pos);
            }
        }
    },buf -> {
        String dimension=buf.readUtf(128); var session=buf.readUUID(); boolean initial=buf.readBoolean(); int count=buf.readVarInt();
        if(count<0 || count>8) throw new IllegalArgumentException("Too many village check markers");
        List<Marker> markers=new ArrayList<>();
        for(int i=0;i<count;i++) {
            var pos=buf.readBlockPos(); var status=buf.readEnum(BuildingScanner.Status.class); var role=buf.readEnum(BuildingScanner.Role.class);
            String village=buf.readUtf(96); boolean conflict=buf.readBoolean(); int size=buf.readVarInt();
            if(size<0 || size>13) throw new IllegalArgumentException("Too many check rows");
            List<BuildingPayloads.Row> rows=new ArrayList<>();
            for(int j=0;j<size;j++) {
                var check=buf.readEnum(BuildingScanner.Check.class); boolean passed=buf.readBoolean(); int found=buf.readVarInt(),required=buf.readVarInt(),targets=buf.readVarInt();
                if(targets<0 || targets>64) throw new IllegalArgumentException("Too many check targets");
                List<BlockPos> positions=new ArrayList<>(); for(int k=0;k<targets;k++) positions.add(buf.readBlockPos());
                rows.add(new BuildingPayloads.Row(check,passed,found,required,positions));
            }
            markers.add(new Marker(pos,status,role,village,rows,conflict));
        }
        return new VillageCheckPayload(dimension,markers,session,initial);
    });
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
