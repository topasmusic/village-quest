package de.quest.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.List;

final class QuestHarvestTrackerTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Bootstrap.validate();
        for (var item : java.util.List.of(Items.WHEAT, Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO,
                Items.BEETROOT, Items.BEETROOT_SEEDS, Items.STICK)) {
            item.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        }
    }

    @Test
    void everyGuildTownHarvestTargetIsTrackedForRightClickReplantFlows() {
        assertTrue(QuestHarvestTracker.isTrackedCropDrop(new ItemStack(Items.WHEAT)));
        assertTrue(QuestHarvestTracker.isTrackedCropDrop(new ItemStack(Items.WHEAT_SEEDS)));
        assertTrue(QuestHarvestTracker.isTrackedCropDrop(new ItemStack(Items.CARROT)));
        assertTrue(QuestHarvestTracker.isTrackedCropDrop(new ItemStack(Items.POTATO)));
        assertTrue(QuestHarvestTracker.isTrackedCropDrop(new ItemStack(Items.BEETROOT)));
        assertTrue(QuestHarvestTracker.isTrackedCropDrop(new ItemStack(Items.BEETROOT_SEEDS)));
        assertFalse(QuestHarvestTracker.isTrackedCropDrop(new ItemStack(Items.STICK)));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void clearRemovesPendingDelayedHarvestsBeforeTheirVerificationTick() throws Exception {
        Field field = QuestHarvestTracker.class.getDeclaredField("PENDING_HARVESTS");
        field.setAccessible(true);
        List pending = (List) field.get(null);
        pending.add(null);
        assertTrue(QuestHarvestTracker.pendingCount() > 0);
        QuestHarvestTracker.clear();
        assertTrue(QuestHarvestTracker.pendingCount() == 0);
    }
}
