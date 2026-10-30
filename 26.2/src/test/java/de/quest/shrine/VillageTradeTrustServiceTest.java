package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.data.PlayerQuestData;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class VillageTradeTrustServiceTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    // Removing either cap would enable one villager or repeated receipts to grant early rights.
    @Test void boundedCreditsRequireAnActualRequestAndSurviveLargeCyclesAndReload() {
        var state=VillageBondMigrationTest.load(new net.minecraft.nbt.CompoundTag());
        var owner=UUID.randomUUID(); var id=UUID.randomUUID(); var trader=UUID.randomUUID();
        var data=state.getPlayerData(owner);
        VillageBondService.ensureCanonicalVillageRecord(data,id,"minecraft:overworld",32,64,VillageBondType.GRANARY);
        for(int i=0;i<200;i++) assertFalse(VillageTradeTrustService.recordTrade(data,0,id,trader,4294967300L,UUID.randomUUID()));
        assertEquals(VillageBondLevel.KNOWN,VillageBondService.knownBondLevel(data,id));
        data.setTradeRouteInt(key("completions"),1);
        var receipt=UUID.randomUUID();
        assertTrue(VillageTradeTrustService.recordTrade(data,0,id,trader,4294967300L,receipt));
        assertFalse(VillageTradeTrustService.recordTrade(data,0,id,trader,4294967300L,receipt));
        assertTrue(VillageTradeTrustService.recordTrade(data,0,id,trader,4294967300L,UUID.randomUUID()));
        assertFalse(VillageTradeTrustService.recordTrade(data,0,id,trader,4294967300L,UUID.randomUUID()));
        state=VillageBondMigrationTest.load(VillageBondMigrationTest.save(state)); data=state.getPlayerData(owner);
        assertFalse(VillageTradeTrustService.recordTrade(data,0,id,trader,4294967299L,UUID.randomUUID()));
        assertFalse(VillageTradeTrustService.recordTrade(data,0,id,trader,4294967300L,UUID.randomUUID()));
        assertTrue(VillageTradeTrustService.recordTrade(data,0,id,trader,4294967301L,UUID.randomUUID()));
        assertTrue(VillageTradeTrustService.recordTrade(data,0,id,trader,4294967301L,UUID.randomUUID()));
        assertEquals("4294967301",data.getTradeRouteString(key("trust_trade_villager_"+trader+"_cycle")));
        for(int i=0;i<200;i++) assertFalse(VillageTradeTrustService.recordTrade(data,0,id,UUID.randomUUID(),4294967302L,UUID.randomUUID()));
        assertEquals(4,VillageBondProgression.snapshot(data,0).qualifiedTrades());
        assertEquals(1,data.getTradeRouteInt(key("completions")));
        assertEquals(2,data.getTradeRouteInt(key("level")));
        assertEquals(VillageBondLevel.TRUSTED,VillageBondService.knownBondLevel(data,id));
        data.setTradeRouteInt(key("completions"),7);
        assertEquals(VillageBondLevel.TRUSTED,VillageBondService.knownBondLevel(data,id));
        data.setTradeRouteInt(key("completions"),8);
        assertEquals(VillageBondLevel.ALLIED,VillageBondService.knownBondLevel(data,id));
        var fresh=data.personalRestart();
        assertEquals(0,VillageBondProgression.snapshot(fresh,0).qualifiedTrades());
        assertFalse(VillageTradeTrustService.recordTrade(fresh,0,id,trader,4294967302L,receipt));
    }

    @Test void partialForeignUnknownAndUnboundedTradeRecordsCannotGrantOrBeOverwritten() {
        for(String damage : new String[]{"schema","binding","receipt","duplicate","merchant","cycle","extra","count","wrong_type"}) {
            var data=new PlayerQuestData(); var id=UUID.randomUUID(); var trader=UUID.randomUUID();
            VillageBondService.ensureCanonicalVillageRecord(data,id,"minecraft:overworld",32,64,VillageBondType.GRANARY);
            data.setTradeRouteInt(key("completions"),1);
            assertTrue(VillageTradeTrustService.recordTrade(data,0,id,trader,9,UUID.randomUUID()));
            assertTrue(VillageTradeTrustService.recordTrade(data,0,id,trader,9,UUID.randomUUID()));
            switch(damage) {
                case "schema" -> data.setTradeRouteInt(key("trust_trade_schema"),2);
                case "binding" -> data.setTradeRouteString(key("trust_trade_village"),UUID.randomUUID().toString());
                case "receipt" -> data.setTradeRouteString(key("trust_trade_receipt_0"),"");
                case "duplicate" -> data.setTradeRouteString(key("trust_trade_receipt_1"),data.getTradeRouteString(key("trust_trade_receipt_0")));
                case "merchant" -> data.setTradeRouteInt(key("trust_trade_villager_invalid_count"),1);
                case "cycle" -> data.setTradeRouteString(key("trust_trade_villager_"+trader+"_cycle"),"bad");
                case "extra" -> data.setTradeRouteString(key("trust_trade_receipt_4"),UUID.randomUUID().toString());
                case "count" -> data.setTradeRouteInt(key("trust_trade_total"),9);
                case "wrong_type" -> data.setTradeRouteInt(key("trust_trade_village"),1);
            }
            var ints=java.util.Map.copyOf(data.getTradeRouteIntState());
            var strings=java.util.Map.copyOf(data.getTradeRouteStringState());
            assertEquals(0,VillageBondProgression.snapshot(data,0).qualifiedTrades(),damage);
            assertFalse(VillageTradeTrustService.recordTrade(data,0,id,UUID.randomUUID(),10,UUID.randomUUID()),damage);
            assertEquals(ints,data.getTradeRouteIntState()); assertEquals(strings,data.getTradeRouteStringState());
            data.setTradeRouteInt(key("completions"),8);
            assertEquals(VillageBondLevel.ALLIED,VillageBondService.knownBondLevel(data,id));
        }
    }
    private static String key(String suffix) { return VillageBondService.villageKey(0,suffix); }
}
