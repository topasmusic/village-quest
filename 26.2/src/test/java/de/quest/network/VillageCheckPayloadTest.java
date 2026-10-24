package de.quest.network;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.kingdom.BuildingScanner;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

final class VillageCheckPayloadTest {
    @Test void diagnosisKeepsRoleMissingCountsAndExclusiveAssignmentConflict() {
        var row=new BuildingPayloads.Row(BuildingScanner.Check.CONTAINERS,false,2,4,List.of(new BlockPos(1,64,0)));
        var value=new VillageCheckPayload("minecraft:overworld",List.of(new VillageCheckPayload.Marker(
                BlockPos.ZERO,BuildingScanner.Status.INVALID,BuildingScanner.Role.STOREHOUSE,"Oak Village",List.of(row),true)),java.util.UUID.randomUUID(),false);
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { VillageCheckPayload.CODEC.encode(buf,value); assertEquals(value,VillageCheckPayload.CODEC.decode(buf)); assertEquals(0,buf.readableBytes()); }
        finally { buf.release(); }
    }
    @Test void oversizedDiagnosticRowsAreRejectedBeforeReadingTheirContents() {
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            buf.writeUtf("minecraft:overworld",128); buf.writeUUID(java.util.UUID.randomUUID()); buf.writeBoolean(false); buf.writeVarInt(1); buf.writeBlockPos(BlockPos.ZERO);
            buf.writeEnum(BuildingScanner.Status.UNKNOWN); buf.writeEnum(BuildingScanner.Role.INN); buf.writeUtf("Inn",96); buf.writeBoolean(false); buf.writeVarInt(Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class,() -> VillageCheckPayload.CODEC.decode(buf));
        } finally { buf.release(); }
    }
    @Test void privateSnapshotRoundTripsAllThreeEvidenceStates() {
        var value=new VillageCheckPayload("minecraft:overworld",List.of(
                new VillageCheckPayload.Marker(BlockPos.ZERO,BuildingScanner.Status.VALID),
                new VillageCheckPayload.Marker(new BlockPos(3,64,2),BuildingScanner.Status.INVALID),
                new VillageCheckPayload.Marker(new BlockPos(5,64,2),BuildingScanner.Status.UNKNOWN)));
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { VillageCheckPayload.CODEC.encode(buf,value); assertEquals(value,VillageCheckPayload.CODEC.decode(buf)); }
        finally { buf.release(); }
    }
    @Test void malformedPacketCannotAllocateAnUnboundedMarkerList() {
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { buf.writeUtf("minecraft:overworld",128); buf.writeUUID(java.util.UUID.randomUUID()); buf.writeBoolean(false); buf.writeVarInt(Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class,() -> VillageCheckPayload.CODEC.decode(buf)); }
        finally { buf.release(); }
    }
}
