package de.quest.quest.weekly;

import de.quest.content.weekly.BakehouseStockWeeklyQuest;
import de.quest.content.weekly.HarvestForVillageWeeklyQuest;
import de.quest.content.weekly.MarketWeekWeeklyQuest;
import de.quest.content.weekly.NightWatchWeeklyQuest;
import de.quest.content.weekly.RoadWardenWeeklyQuest;
import de.quest.content.weekly.SmithWeekWeeklyQuest;
import de.quest.content.weekly.StallAndPastureWeeklyQuest;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;

public final class WeeklyQuestGenerator {
    private static final List<WeeklyQuestDefinition> DEFINITIONS = List.of(
            new HarvestForVillageWeeklyQuest(),
            new BakehouseStockWeeklyQuest(),
            new SmithWeekWeeklyQuest(),
            new StallAndPastureWeeklyQuest(),
            new MarketWeekWeeklyQuest(),
            new NightWatchWeeklyQuest(),
            new RoadWardenWeeklyQuest()
    );
    private static final List<WeeklyQuestDefinition> CORE_DEFINITIONS = DEFINITIONS.stream()
            .filter(definition -> definition.type().category() != WeeklyQuestService.WeeklyQuestCategory.COMBAT)
            .toList();

    private WeeklyQuestGenerator() {}

    public static WeeklyQuestDefinition pick(ServerWorld world) {
        return pick(world, null, null);
    }

    public static WeeklyQuestDefinition pick(ServerWorld world,
                                             WeeklyQuestService.WeeklyQuestType excludedType,
                                             WeeklyQuestService.WeeklyQuestCategory excludedCategory) {
        if (world == null || DEFINITIONS.isEmpty()) {
            return null;
        }

        List<WeeklyQuestDefinition> candidates = withoutType(CORE_DEFINITIONS, excludedType);
        candidates = withoutCategory(candidates, excludedCategory);
        return candidates.get(world.random.nextInt(candidates.size()));
    }

    public static WeeklyQuestDefinition definition(WeeklyQuestService.WeeklyQuestType type) {
        if (type == null) {
            return null;
        }
        for (WeeklyQuestDefinition definition : DEFINITIONS) {
            if (definition.type() == type) {
                return definition;
            }
        }
        return null;
    }

    public static int count() {
        return DEFINITIONS.size();
    }

    private static List<WeeklyQuestDefinition> withoutType(List<WeeklyQuestDefinition> source,
                                                           WeeklyQuestService.WeeklyQuestType excludedType) {
        if (excludedType == null || source.size() <= 1) {
            return source;
        }

        List<WeeklyQuestDefinition> filtered = new ArrayList<>();
        for (WeeklyQuestDefinition definition : source) {
            if (definition.type() != excludedType) {
                filtered.add(definition);
            }
        }
        return filtered.isEmpty() ? source : filtered;
    }

    private static List<WeeklyQuestDefinition> withoutCategory(List<WeeklyQuestDefinition> source,
                                                               WeeklyQuestService.WeeklyQuestCategory excludedCategory) {
        if (excludedCategory == null || source.size() <= 1) {
            return source;
        }

        List<WeeklyQuestDefinition> filtered = new ArrayList<>();
        for (WeeklyQuestDefinition definition : source) {
            if (definition.type().category() != excludedCategory) {
                filtered.add(definition);
            }
        }
        return filtered.isEmpty() ? source : filtered;
    }
}
