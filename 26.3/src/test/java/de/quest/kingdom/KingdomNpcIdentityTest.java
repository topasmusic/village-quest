package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

final class KingdomNpcIdentityTest {
    @Test void canonicalTicketSurvivesReloadWithoutTrustingAnIndependentSavedBodyId() {
        UUID person=UUID.randomUUID(),body=UUID.randomUUID();
        var original=KingdomNpcIdentity.bound(new KingdomPersonRegistry.Ticket(person,7,body));
        var saved=original.toNbt(); saved.putString("futureRemark","retain");
        var restored=KingdomNpcIdentity.fromNbt(saved);
        assertEquals(new KingdomPersonRegistry.Ticket(person,7,body),restored.ticket(body).orElseThrow());
        assertEquals("retain",restored.toNbt().getStringOr("futureRemark",""));
    }
    @Test void futureMalformedAndInvalidIdentityStayOpaqueAndInert() {
        var future=new CompoundTag(); future.putInt("schema",2); future.putString("person",UUID.randomUUID().toString()); future.putLong("generation",2);
        var corrupt=future.copy(); corrupt.putInt("schema",1); corrupt.putLong("generation",-1);
        for(var data:java.util.List.of(future,corrupt,StringTag.valueOf("opaque"))) {
            var identity=KingdomNpcIdentity.fromNbt(data);
            assertTrue(identity.ticket(UUID.randomUUID()).isEmpty()); assertEquals(data,identity.payload());
        }
    }
    @Test void fractionalGenerationAndOverflowingSchemaAreNotCoercedIntoCurrentAuthority() {
        var valid=KingdomNpcIdentity.bound(new KingdomPersonRegistry.Ticket(UUID.randomUUID(),1,UUID.randomUUID())).toNbt();
        var fractional=valid.copy(); fractional.putDouble("generation",1.5);
        var overflow=valid.copy(); overflow.putLong("schema",4_294_967_297L);
        for(var malformed:java.util.List.of(fractional,overflow)) {
            assertTrue(KingdomNpcIdentity.fromNbt(malformed).ticket(UUID.randomUUID()).isEmpty());
            var encoded=KingdomNpcIdentity.PAYLOAD_CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,malformed).getOrThrow();
            assertEquals(malformed,KingdomNpcIdentity.PAYLOAD_CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,encoded).getOrThrow());
        }
    }
}
