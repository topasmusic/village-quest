package de.quest.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.caravan.TradeContractHold;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class TradeContractHoldTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void settlementClosureFreezesDueDateThroughSaveReloadWithoutDoubleExtension() {
        UUID owner = UUID.fromString("6a4bba84-a50b-4276-93f1-dc57a57a8e12");
        QuestState quests = QuestState.fromNbt(new CompoundTag());
        PlayerQuestData data = quests.getPlayerData(owner);
        data.setTradeRouteInt("guild_contract_due_day", 100);

        assertTrue(TradeContractHold.begin(data, 98));
        assertFalse(TradeContractHold.begin(data, 101));
        PlayerQuestData loaded = QuestState.fromNbt(QuestState.toNbt(quests)).getPlayerData(owner);
        assertTrue(TradeContractHold.isHeld(loaded));
        assertTrue(TradeContractHold.end(loaded, 103));
        assertEquals(105, loaded.getTradeRouteInt("guild_contract_due_day"));
        assertFalse(TradeContractHold.end(loaded, 103));
        assertEquals(105, loaded.getTradeRouteInt("guild_contract_due_day"));
    }
}
