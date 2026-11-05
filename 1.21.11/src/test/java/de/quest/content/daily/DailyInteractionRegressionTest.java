package de.quest.content.daily;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.quest.quest.daily.DailyQuestKeys;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class DailyInteractionRegressionTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void autumnFruitCountsWithoutInspectingAnAdjacentStem() {
        assertEquals(DailyQuestKeys.AUTUMN_PUMPKIN_PROGRESS,
                AutumnHarvestDailyQuest.progressKey(Blocks.PUMPKIN.defaultBlockState()));
        assertEquals(DailyQuestKeys.AUTUMN_MELON_PROGRESS,
                AutumnHarvestDailyQuest.progressKey(Blocks.MELON.defaultBlockState()));
        assertNull(AutumnHarvestDailyQuest.progressKey(Blocks.PUMPKIN_STEM.defaultBlockState()));
        assertNull(AutumnHarvestDailyQuest.progressKey(Blocks.AIR.defaultBlockState()));
    }

    @Test
    void onlySuccessfulFullHiveToolsClassifyAsHoneyHarvests() {
        BlockState fullNest = Blocks.BEE_NEST.defaultBlockState()
                .setValue(BeehiveBlock.HONEY_LEVEL, 5);
        BlockState fullHive = Blocks.BEEHIVE.defaultBlockState()
                .setValue(BeehiveBlock.HONEY_LEVEL, 5);
        BlockState unreadyHive = Blocks.BEEHIVE.defaultBlockState()
                .setValue(BeehiveBlock.HONEY_LEVEL, 4);

        assertEquals(HoneyDailyQuest.HarvestKind.HONEY_BOTTLE,
                HoneyDailyQuest.harvestKind(fullNest, Items.GLASS_BOTTLE));
        assertEquals(HoneyDailyQuest.HarvestKind.HONEYCOMB,
                HoneyDailyQuest.harvestKind(fullHive, Items.SHEARS));
        assertEquals(HoneyDailyQuest.HarvestKind.NONE,
                HoneyDailyQuest.harvestKind(unreadyHive, Items.GLASS_BOTTLE));
        assertEquals(HoneyDailyQuest.HarvestKind.NONE,
                HoneyDailyQuest.harvestKind(fullHive, Items.STICK));
        assertEquals(HoneyDailyQuest.HarvestKind.NONE,
                HoneyDailyQuest.harvestKind(Blocks.STONE.defaultBlockState(), Items.SHEARS));
    }
}
