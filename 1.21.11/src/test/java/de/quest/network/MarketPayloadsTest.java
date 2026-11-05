package de.quest.network;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class MarketPayloadsTest {
    private static final UUID SESSION=new UUID(1,1),QUOTE=new UUID(2,2);
    private static MarketPayloads.Row row() { return new MarketPayloads.Row("wheat",Identifier.withDefaultNamespace("wheat"),32,2,4,new de.quest.kingdom.MarketPricePolicy.Adjustment(3,3,6,-4,8,6),QUOTE,4,MarketPayloads.NONE,0); }
    @Test void privateSnapshotRoundTripsDisplayedQuoteIdentityAndBalancesWithoutClientEvidence() {
        var rows=new java.util.ArrayList<>(List.of(row()));
        var view=new MarketPayloads.View(SESSION,7,true,true,"Mara","Auenhof","Falkensteg",1,99,16,-4,10,rows,MarketPayloads.Feedback.UPDATED,1200);
        rows.clear(); assertEquals(1,view.rows().size());
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { MarketPayloads.View.CODEC.encode(buf,view); assertEquals(view,MarketPayloads.View.CODEC.decode(buf)); }
        finally { buf.release(); }
        assertThrows(UnsupportedOperationException.class,() -> view.rows().clear());
    }
    @Test void tradeActionCarriesOnlySessionRevisionAndServerQuoteId() {
        var action=new MarketPayloads.Action(SESSION,7,MarketPayloads.Kind.TRADE,QUOTE,1);
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { MarketPayloads.Action.CODEC.encode(buf,action); assertEquals(action,MarketPayloads.Action.CODEC.decode(buf)); }
        finally { buf.release(); }
        assertThrows(IllegalArgumentException.class,() -> new MarketPayloads.Action(SESSION,7,MarketPayloads.Kind.TRADE,MarketPayloads.NONE,1));
        assertThrows(IllegalArgumentException.class,() -> new MarketPayloads.Action(SESSION,7,MarketPayloads.Kind.REFRESH,QUOTE,1));
        assertThrows(IllegalArgumentException.class,() -> new MarketPayloads.Action(SESSION,-1,MarketPayloads.Kind.LOTS,MarketPayloads.NONE,2));
    }
    @Test void oversizedMalformedOrNonVanillaDisplayGoodsCannotAllocateAnUnboundedScreen() {
        assertThrows(IllegalArgumentException.class,() -> new MarketPayloads.View(SESSION,0,true,true,"Mara","Home","Market",1,0,16,0,0,
                java.util.Collections.nCopies(9,row()),MarketPayloads.Feedback.UPDATED));
        assertThrows(IllegalArgumentException.class,() -> new MarketPayloads.Row("wheat",Identifier.parse("other:copy"),32,2,4,new de.quest.kingdom.MarketPricePolicy.Adjustment(3,3,6,-4,8,6),QUOTE,4,MarketPayloads.NONE,0));
        assertThrows(IllegalArgumentException.class,() -> new MarketPayloads.Row("wheat",Identifier.withDefaultNamespace("wheat"),32,2,4,new de.quest.kingdom.MarketPricePolicy.Adjustment(0,0,0,0,0),MarketPayloads.NONE,4,MarketPayloads.NONE,0));
        assertThrows(IllegalArgumentException.class,() -> new MarketPayloads.View(SESSION,0,true,true,"x".repeat(97),"Home","Market",1,0,16,0,0,List.of(),MarketPayloads.Feedback.UPDATED));
    }
    @Test void oversizedWireRowCountIsRejectedBeforeReadingAnyRows() {
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            buf.writeUUID(SESSION); buf.writeVarLong(0); buf.writeBoolean(false); buf.writeBoolean(true);
            buf.writeUtf("Mara"); buf.writeUtf("Home"); buf.writeUtf("Market"); buf.writeVarInt(1);
            buf.writeVarLong(0); buf.writeVarInt(16); buf.writeVarInt(0); buf.writeVarInt(0); buf.writeVarInt(9);
            assertThrows(IllegalArgumentException.class,() -> MarketPayloads.View.CODEC.decode(buf));
        } finally { buf.release(); }
    }
}
