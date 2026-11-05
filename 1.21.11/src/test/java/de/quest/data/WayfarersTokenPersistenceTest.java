package de.quest.data;

import de.quest.quest.special.FriendsQuestProgress;
import de.quest.quest.special.WayfarersTokenService;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class WayfarersTokenPersistenceTest {
    @Test void personalCooldownSurvivesActualQuestLedgerSerialization(){
        var id=UUID.randomUUID();var state=QuestState.fromNbt(new CompoundTag());
        new FriendsQuestProgress(state.getPlayerData(id)).text("simon.token.readyAt","6100");
        var restored=QuestState.fromNbt(QuestState.toNbt(state));
        assertEquals(6000,WayfarersTokenService.remaining(new FriendsQuestProgress(restored.getPlayerData(id)),100));
        assertEquals(0,WayfarersTokenService.remaining(new FriendsQuestProgress(restored.getPlayerData(UUID.randomUUID())),100));
    }
}
