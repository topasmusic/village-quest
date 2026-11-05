package de.quest.reputation;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.reputation.SocialReputationRules.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.*;

final class ReputationBudgetTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void largeHistoriesKeepReplayProtectionWhileOneHundredOnlineCasesUseOnlyNearbySpatialBuckets() {
        var index = new ProtectedVillageIndex();
        for (int i = 0; i < 10_000; i++) index.upsert(VillageKey.overworld((i % 100) * 256, (i / 100) * 256), 70);
        List<SocialReputationData> players = new ArrayList<>(); List<UUID> firstReceipts = new ArrayList<>();
        List<VillageKey> known = new ArrayList<>(); for (int i = 0; i < 100; i++) known.add(VillageKey.overworld(i * 256, 0));
        for (int p = 0; p < 100; p++) {
            var data = new SocialReputationData(); UUID owner = UUID.randomUUID(); UUID first = null;
            for (int i = 0; i < 1_000; i++) {
                UUID receipt = UUID.randomUUID(); if (i == 0) first = receipt;
                SocialReputationService.recordBenefit(data, new BenefitEvent(receipt, BenefitKind.STORY, owner, List.of(), 1), i);
            }
            assertEquals(32, data.history().size()); firstReceipts.add(first);
            data.openCase(UUID.randomUUID(), Offence.ASSAULT, known.get(p), 1_000); players.add(data);
        }
        for (int warmup = 0; warmup < 200; warmup++) for (var data : players) ReparationService.tick(data, 1, true, 1_001 + warmup);
        long start = System.nanoTime(); int ticks = 1_000;
        for (int tick = 0; tick < ticks; tick++) for (int p = 0; p < players.size(); p++) {
            ReparationService.tick(players.get(p), 1, true, 1_201 + tick);
            assertEquals(known.get(p), index.resolve("minecraft:overworld", new BlockPos(p * 256, 70, 0)).orElseThrow());
            assertTrue(index.lastCandidatesExamined() <= 1);
            if (tick % 20 == 0) assertEquals(8, ReputationViewService.build(players.get(p), known, 0, true).villages().size());
        }
        double averageMs = (System.nanoTime() - start) / 1_000_000.0 / ticks;
        System.out.printf(java.util.Locale.ROOT, "REPUTATION_CORE_BUDGET: %.4f ms/tick; 100 online cases, 1000 receipts/player, 10000 anchors%n", averageMs);
        assertTrue(averageMs < 1.0, "Core simulation exceeded 1 ms/tick: " + averageMs);
        for (int p = 0; p < players.size(); p++) {
            var loaded = SocialReputationData.fromNbt(players.get(p).toNbt());
            assertTrue(loaded.writable()); assertFalse(loaded.markReceipt(BenefitKind.STORY, firstReceipts.get(p)));
            assertEquals(10_800, loaded.activeCase().onlineTicksRemaining());
        }
    }
}
