package de.quest.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.caravan.TradeContractRefundLedger;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class TradeContractRefundLedgerTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void consumedFreightSurvivesOfflineFailureAndCanOnlyBeClaimedOnce() {
        UUID owner = UUID.fromString("df7581df-46c8-4508-9fd2-812ee824ec81");
        QuestState quests = QuestState.fromNbt(new CompoundTag());
        PlayerQuestData data = quests.getPlayerData(owner);
        assertTrue(TradeContractRefundLedger.queue(data, 2, 48));

        PlayerQuestData loaded = QuestState.fromNbt(QuestState.toNbt(quests)).getPlayerData(owner);
        assertTrue(TradeContractRefundLedger.queue(loaded, 3, 32));
        assertEquals(java.util.List.of(
                new TradeContractRefundLedger.Refund(2, 48),
                new TradeContractRefundLedger.Refund(3, 32)),
                TradeContractRefundLedger.drain(loaded));
        assertTrue(TradeContractRefundLedger.drain(loaded).isEmpty());
    }

    @Test
    void repeatedOfflineFailuresCoalesceWithoutFillingTheMailbox() {
        PlayerQuestData data = QuestState.fromNbt(new CompoundTag()).getPlayerData(UUID.randomUUID());
        for (int type = 1; type <= 8; type++) {
            assertTrue(TradeContractRefundLedger.queue(data, type, 48));
            assertTrue(TradeContractRefundLedger.queue(data, type, 48));
        }
        var refunds = TradeContractRefundLedger.drain(data);
        assertEquals(8, refunds.size());
        for (int type = 1; type <= 8; type++) {
            assertTrue(refunds.contains(new TradeContractRefundLedger.Refund(type, 96)));
        }
        assertTrue(TradeContractRefundLedger.drain(data).isEmpty());
    }
}
