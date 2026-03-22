package de.quest.quest.daily;

import de.quest.content.daily.CoalMiningDailyQuest;
import de.quest.content.daily.CreeperWatchDailyQuest;
import de.quest.content.daily.HoneyDailyQuest;
import de.quest.content.daily.AutumnHarvestDailyQuest;
import de.quest.content.daily.PetCollarDailyQuest;
import de.quest.content.daily.PotatoHarvestDailyQuest;
import de.quest.content.daily.RiverMealDailyQuest;
import de.quest.content.daily.SkeletonPatrolDailyQuest;
import de.quest.content.daily.SmithSmeltingDailyQuest;
import de.quest.content.daily.SpiderSweepDailyQuest;
import de.quest.content.daily.StallNewLifeDailyQuest;
import de.quest.content.daily.VillageTradingDailyQuest;
import de.quest.content.daily.WheatHarvestDailyQuest;
import de.quest.content.daily.WoolWeaverDailyQuest;
import de.quest.content.daily.WoodcuttingDailyQuest;
import de.quest.content.daily.ZombieCullDailyQuest;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;

public final class DailyQuestGenerator {
    private static final List<DailyQuestDefinition> DEFINITIONS = List.of(
            new HoneyDailyQuest(),
            new PetCollarDailyQuest(),
            new WheatHarvestDailyQuest(),
            new PotatoHarvestDailyQuest(),
            new WoodcuttingDailyQuest(),
            new CoalMiningDailyQuest(),
            new WoolWeaverDailyQuest(),
            new RiverMealDailyQuest(),
            new AutumnHarvestDailyQuest(),
            new SmithSmeltingDailyQuest(),
            new StallNewLifeDailyQuest(),
            new VillageTradingDailyQuest(),
            new ZombieCullDailyQuest(),
            new SkeletonPatrolDailyQuest(),
            new SpiderSweepDailyQuest(),
            new CreeperWatchDailyQuest()
    );

    private DailyQuestGenerator() {}

    public static DailyQuestDefinition pick(ServerWorld world) {
        return pick(world, null);
    }

    public static DailyQuestDefinition pick(ServerWorld world, DailyQuestService.DailyQuestType excludedType) {
        return pick(world, excludedType, null);
    }

    public static DailyQuestDefinition pick(ServerWorld world,
                                            DailyQuestService.DailyQuestType excludedType,
                                            DailyQuestService.DailyQuestCategory excludedCategory) {
        if (world == null || DEFINITIONS.isEmpty()) {
            return null;
        }

        List<DailyQuestDefinition> candidates = withoutType(DEFINITIONS, excludedType);
        candidates = withoutCategory(candidates, excludedCategory);

        return candidates.get(world.random.nextInt(candidates.size()));
    }

    private static List<DailyQuestDefinition> withoutType(List<DailyQuestDefinition> source,
                                                          DailyQuestService.DailyQuestType excludedType) {
        if (excludedType == null || source.size() <= 1) {
            return source;
        }

        List<DailyQuestDefinition> filtered = new ArrayList<>();
        for (DailyQuestDefinition definition : source) {
            if (definition.type() != excludedType) {
                filtered.add(definition);
            }
        }
        return filtered.isEmpty() ? source : filtered;
    }

    private static List<DailyQuestDefinition> withoutCategory(List<DailyQuestDefinition> source,
                                                              DailyQuestService.DailyQuestCategory excludedCategory) {
        if (excludedCategory == null || source.size() <= 1) {
            return source;
        }

        List<DailyQuestDefinition> filtered = new ArrayList<>();
        for (DailyQuestDefinition definition : source) {
            if (definition.type().category() != excludedCategory) {
                filtered.add(definition);
            }
        }
        return filtered.isEmpty() ? source : filtered;
    }

    public static DailyQuestDefinition definition(DailyQuestService.DailyQuestType type) {
        if (type == null) {
            return null;
        }
        for (DailyQuestDefinition definition : DEFINITIONS) {
            if (definition.type() == type) {
                return definition;
            }
        }
        return null;
    }

    public static int count() {
        return DEFINITIONS.size();
    }
}
