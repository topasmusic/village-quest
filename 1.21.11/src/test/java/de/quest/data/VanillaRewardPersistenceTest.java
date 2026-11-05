package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class VanillaRewardPersistenceTest {
    @Test void rewardsSurviveSavedDataReloadAndAdminResetKeepsFirstTypeReceipt() {
        var state = QuestState.fromNbt(new CompoundTag()); var id = UUID.randomUUID();
        var rewards = state.getPlayerData(id).vanillaRewards(); var snapshot = new CompoundTag(); snapshot.putLong("currency", 8);
        rewards.accept("daily", "10", snapshot); rewards.first("commission:ploughs");
        var loaded = QuestState.fromNbt(QuestState.toNbt(state));
        assertEquals(8, loaded.getPlayerData(id).vanillaRewards().snapshot("daily", "10").getLongOr("currency", 0));
        loaded.resetAllProgress();
        assertFalse(loaded.getPlayerData(id).vanillaRewards().first("commission:ploughs"));
    }
    @Test void malformedRewardContainersArePreservedWithoutNewPayouts() {
        var root = new CompoundTag(); root.putString("vanillaRewards","future-format");
        var loaded = QuestState.fromNbt(root);
        assertEquals(root.get("vanillaRewards"),QuestState.toNbt(loaded).get("vanillaRewards"));
        assertFalse(loaded.getPlayerData(UUID.randomUUID()).vanillaRewards().writable());
    }
}
