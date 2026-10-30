package de.quest.network;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

final class NetworkVillageSummaryTest {
    @Test void summaryRoundTripsStableIdAndLastKnownLinesAndRejectsOversizedLists() {
        var value=new Payloads.NetworkVillageData(2,Component.literal("Home"),Component.literal("Ally"),Component.literal("Stable"),
                "stable",Component.literal("Food"),20,1,0,false,UUID.randomUUID(),List.of(Component.literal("Last known")),new Payloads.JournalVillageStats(4,2,3,5));
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { Payloads.NetworkVillageData.CODEC.encode(buf,value); assertEquals(value,Payloads.NetworkVillageData.CODEC.decode(buf)); }
        finally { buf.release(); }
        assertThrows(IllegalArgumentException.class,() -> new Payloads.JournalVillageStats(5,0,0,0));
        assertThrows(IllegalArgumentException.class,() -> new Payloads.NetworkVillageData(0,Component.empty(),Component.empty(),Component.empty(),
                "stable",Component.empty(),0,0,0,false,UUID.randomUUID(),java.util.Collections.nCopies(Payloads.NetworkVillageData.MAX_SUMMARY_LINES+1,Component.empty())));
    }
}
