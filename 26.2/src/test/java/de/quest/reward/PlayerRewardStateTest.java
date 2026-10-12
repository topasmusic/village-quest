package de.quest.reward;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

final class PlayerRewardStateTest {
    private static CompoundTag packageData() {
        var tag = new CompoundTag(); tag.putLong("currency", 12); tag.putString("option", "coins");
        return tag;
    }
    @Test void snapshotIsFrozenAndCannotBeReplacedForSameInstance() {
        var state = new PlayerRewardState(); var input = packageData();
        assertTrue(state.accept("daily", "SMITH:10", input));
        input.putLong("currency", 999);
        assertFalse(state.accept("daily", "SMITH:10", input));
        var output = state.snapshot("daily", "SMITH:10"); output.putLong("currency", 1);
        assertEquals(12, state.snapshot("daily", "SMITH:10").getLongOr("currency", 0));
        assertTrue(state.snapshot("daily", "SMITH:11").isEmpty());
    }
    @Test void redemptionSurvivesRestartAndRejectsDuplicates() {
        var state = new PlayerRewardState(); state.accept("weekly", "SMITH:4", packageData());
        assertEquals(12, state.redeem("weekly", "SMITH:4").getLongOr("currency", 0));
        var loaded = PlayerRewardState.fromNbt(state.toNbt());
        assertTrue(loaded.redeem("weekly", "SMITH:4").isEmpty());
        assertFalse(loaded.accept("weekly", "SMITH:4", packageData()));
        assertTrue(loaded.accept("weekly", "SMITH:5", packageData()));
    }
    @Test void pendingItemComponentsAndFirstTypeReceiptSurviveReload() {
        var state = new PlayerRewardState(); var pending = new ListTag(); var item = new CompoundTag();
        item.putString("id", "minecraft:potion"); var components = new CompoundTag();
        components.putString("minecraft:potion_contents", "minecraft:strong_healing"); item.put("components", components);
        pending.add(item); state.pending(pending); pending.clear();
        assertTrue(state.first("commission:ploughs"));
        var loaded = PlayerRewardState.fromNbt(state.toNbt());
        assertEquals(1, loaded.pending().size()); assertEquals(item, loaded.pending().getFirst());
        assertFalse(loaded.first("commission:ploughs"));
    }
    @Test void unknownSchemaIsPreservedReadOnly() {
        var original = packageData(); original.putInt("schema", 999);
        var state = PlayerRewardState.fromNbt(original);
        assertFalse(state.writable()); assertFalse(state.accept("daily", "1", packageData()));
        assertEquals(original, state.toNbt());
    }
    @Test void activeSnapshotsReserveDeliveryCapacity() {
        var state = new PlayerRewardState(); var pending = new ListTag();
        for (int i=0;i<239;i++) pending.add(packageData()); state.pending(pending);
        var reward = packageData(); var items = new ListTag();
        for (int i=0;i<10;i++) items.add(packageData()); reward.put("items",items);
        assertTrue(state.accept("daily", "1", reward));
        assertFalse(state.accept("weekly", "1", reward));
    }
    @Test void completedChoiceSurvivesNewQuestResetAndReloadAndCanOnlyBeTakenOnce() {
        var state = new PlayerRewardState(); var offer = packageData();
        var choices = new ListTag(); choices.add(packageData()); offer.put("choices", choices);
        assertTrue(state.accept("daily", "old", offer));
        String instance = state.snapshot("daily", "old").getStringOr("instance", "");
        assertTrue(state.earn("daily", "old")); assertFalse(state.earn("daily", "old"));
        assertTrue(state.accept("daily", "new", offer)); state.resetSlots();
        var restored = PlayerRewardState.fromNbt(state.toNbt());
        assertEquals(1, restored.earned().size());
        assertEquals(12, restored.takeChoice(instance, "coins").getLongOr("currency", 0));
        assertTrue(restored.takeChoice(instance, "coins").isEmpty());
    }
    @Test void earnedOptionsReserveLargestPackageAndRejectForgedChoiceWithoutRemovingReward() {
        var state = new PlayerRewardState(); var offer = packageData(); var choices = new ListTag();
        var supplies = packageData(); supplies.putString("option", "supplies"); var items = new ListTag();
        items.add(packageData()); items.add(packageData()); supplies.put("items", items);
        choices.add(packageData()); choices.add(supplies); offer.put("choices", choices);
        assertTrue(state.accept("daily", "1", offer)); assertEquals(2, state.reservedStacks());
        String instance = state.snapshot("daily", "1").getStringOr("instance", "");
        assertTrue(state.earn("daily", "1")); assertEquals(2, state.reservedStacks());
        assertTrue(state.takeChoice(instance, "forged").isEmpty()); assertEquals(1, state.earned().size());
    }

}
