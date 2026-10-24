package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

final class MarketPlayerProofCodecTest {
    @Test void futureCompoundAndNonCompoundAttachmentValuesRoundTripInsteadOfBecomingAnAbsentProof() {
        var future=new CompoundTag(); future.putInt("schema",99); future.putString("unknown","keep");
        var list=new ListTag(); list.add(StringTag.valueOf("future list proof"));
        for(var raw:java.util.List.of(future,StringTag.valueOf("future scalar proof"),list)) {
            var loaded=MarketPlayerProofCodec.CODEC.parse(NbtOps.INSTANCE,raw).getOrThrow();
            assertEquals(raw,MarketPlayerProofCodec.CODEC.encodeStart(NbtOps.INSTANCE,loaded).getOrThrow());
            assertTrue(MarketTransferMarker.fromNbt(loaded).isEmpty());
        }
    }
    @Test void decodedAndEncodedAttachmentDocumentsAreIndependentSnapshots() {
        var raw=new CompoundTag(); raw.putInt("schema",99); raw.putString("keep","original");
        var loaded=assertInstanceOf(CompoundTag.class,MarketPlayerProofCodec.CODEC.parse(NbtOps.INSTANCE,raw).getOrThrow());
        raw.putString("keep","source changed"); assertEquals("original",loaded.getStringOr("keep",""));
        var encoded=assertInstanceOf(CompoundTag.class,MarketPlayerProofCodec.CODEC.encodeStart(NbtOps.INSTANCE,loaded).getOrThrow());
        loaded.putString("keep","runtime changed"); assertEquals("original",encoded.getStringOr("keep",""));
    }
}
