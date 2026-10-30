package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.data.QuestState;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.*;

final class VillageBondMigrationTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    // A completion-only consumer would lose the legitimate C=1/T=4 alternative.
    @Test void canonicalTradeAlternativeSurvivesRepeatedNbtLoadsWithoutInventingCompletions() {
        var state = load(new CompoundTag());
        var owner = UUID.randomUUID(); var village = UUID.randomUUID();
        var data = state.getPlayerData(owner);
        VillageBondService.ensureCanonicalVillageRecord(data,village,"minecraft:overworld",32,64,VillageBondType.GRANARY);
        data.setTradeRouteInt(key("completions"),1);
        data.setTradeRouteInt(key("level"),1);
        data.setTradeRouteInt(key("trust_trade_schema"),1);
        data.setTradeRouteInt(key("trust_trade_total"),4);
        data.setTradeRouteString(key("trust_trade_village"),village.toString());
        for(int i=0;i<4;i++) {
            data.setTradeRouteString(key("trust_trade_receipt_"+i),UUID.randomUUID().toString());
            String merchant="trust_trade_villager_"+UUID.randomUUID();
            data.setTradeRouteString(key(merchant+"_cycle"),"4294967300");
            data.setTradeRouteInt(key(merchant+"_count"),1);
        }
        for(int reload=0;reload<3;reload++) {
            assertEquals(VillageBondLevel.TRUSTED,VillageBondService.knownBondLevel(data,village));
            assertEquals(1,data.getTradeRouteInt(key("completions")));
            assertEquals(1,data.getTradeRouteInt(key("level")),"read must not rewrite a cache");
            state=load(save(state)); data=state.getPlayerData(owner);
        }
        data.setTradeRouteString(key("trust_trade_village"),UUID.randomUUID().toString());
        assertEquals(VillageBondLevel.KNOWN,VillageBondService.knownBondLevel(data,village));
        data.setTradeRouteInt(key("completions"),8);
        assertEquals(VillageBondLevel.ALLIED,VillageBondService.knownBondLevel(data,village));
    }

    @Test void legacyCountsAndEvenContradictoryCachesArePreserved() {
        for(int c : new int[]{0,1,2,7,8,9}) {
            var state=load(new CompoundTag()); var owner=UUID.randomUUID(); var id=UUID.randomUUID();
            var data=state.getPlayerData(owner);
            VillageBondService.ensureVillageRecord(data,32,64,VillageBondType.GRANARY);
            data.setTradeRouteInt(key("completions"),c); data.setTradeRouteInt(key("level"),3);
            assertEquals(0,VillageBondService.ensureCanonicalVillageRecord(data,id,"minecraft:overworld",32,64,VillageBondType.GRANARY));
            for(int reload=0;reload<3;reload++) {
                assertEquals(c>=8?VillageBondLevel.ALLIED:c>=2?VillageBondLevel.TRUSTED:VillageBondLevel.KNOWN,
                        VillageBondService.knownBondLevel(data,id));
                assertEquals(c,data.getTradeRouteInt(key("completions"))); assertEquals(3,data.getTradeRouteInt(key("level")));
                state=load(save(state)); data=state.getPlayerData(owner);
            }
        }
    }
    @Test void verifiedLegacyAliasReusesTheOriginalPersonalIndexAndTradeProof() {
        var kingdoms=new de.quest.kingdom.LivingKingdomsState();
        var key=new de.quest.village.VillageLifeState.VillageKey("minecraft:overworld",32,64);
        var village=kingdoms.registerGenerated(key,new net.minecraft.core.BlockPos(32,64,64),"Village").orElseThrow();
        var data=new de.quest.data.PlayerQuestData();
        int index=VillageContactService.establish(data,village,VillageBondType.GRANARY).contact().villageIndex();
        data.setTradeRouteInt(key("completions"),1);
        for(int i=0;i<4;i++) assertTrue(VillageTradeTrustService.recordTrade(data,index,village.id(),UUID.randomUUID(),9,UUID.randomUUID()));
        assertTrue(kingdoms.recognizeLegacyAlias(village.id(),new de.quest.village.VillageLifeState.VillageKey("minecraft:overworld",40,68)));
        var alias=new de.quest.content.story.ShadowsTradeRoadEncounterService.VillageMarker("alias",40,68,16,48,48,80);
        var resolved=VillageContactService.bindCanonical(kingdoms,data,"minecraft:overworld",alias,
                new net.minecraft.core.BlockPos(40,64,68),VillageBondType.GRANARY);
        assertTrue(resolved.accepted()); assertEquals(index,resolved.contact().villageIndex());
        assertEquals(1,VillageBondService.historicalVillageCount(data));
        assertEquals(VillageBondLevel.TRUSTED,VillageBondService.knownBondLevel(data,village.id()));
        assertEquals(1,data.getTradeRouteInt(key("completions")));
    }
    private static String key(String suffix) { return VillageBondService.villageKey(0,suffix); }
    static QuestState load(CompoundTag tag) {
        try {
            var method=QuestState.class.getDeclaredMethod("fromNbt",CompoundTag.class);
            method.setAccessible(true); return (QuestState)method.invoke(null,tag);
        } catch(ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    static CompoundTag save(QuestState state) {
        try {
            var method=QuestState.class.getDeclaredMethod("toNbt",QuestState.class);
            method.setAccessible(true); return (CompoundTag)method.invoke(null,state);
        } catch(ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
