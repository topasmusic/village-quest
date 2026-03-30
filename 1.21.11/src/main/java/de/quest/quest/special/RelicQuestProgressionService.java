package de.quest.quest.special;

import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryQuestService;
import de.quest.quest.story.VillageProjectType;
import de.quest.reputation.ReputationService;
import net.minecraft.server.world.ServerWorld;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public final class RelicQuestProgressionService {
    public record RelicUnlockPath(
            SpecialQuestKind kind,
            ReputationService.ReputationTrack track,
            StoryArcType requiredStoryArc,
            VillageProjectType project,
            int requiredReputation,
            String titleKey
    ) {}

    private static final Map<SpecialQuestKind, RelicUnlockPath> PATHS = buildPaths();
    private static final Map<ReputationService.ReputationTrack, RelicUnlockPath> TRACK_PATHS = buildTrackPaths();

    private RelicQuestProgressionService() {}

    private static Map<SpecialQuestKind, RelicUnlockPath> buildPaths() {
        Map<SpecialQuestKind, RelicUnlockPath> paths = new EnumMap<>(SpecialQuestKind.class);
        register(paths, new RelicUnlockPath(
                SpecialQuestKind.APIARIST_SMOKER,
                ReputationService.ReputationTrack.FARMING,
                StoryArcType.FAILING_HARVEST,
                VillageProjectType.APIARY_CHARTER,
                ApiaristSmokerQuestService.REQUIRED_FARMING_REPUTATION,
                "quest.village-quest.special.apiarist_smoker.title"
        ));
        register(paths, new RelicUnlockPath(
                SpecialQuestKind.SURVEYOR_COMPASS,
                ReputationService.ReputationTrack.CRAFTING,
                StoryArcType.SILENT_FORGE,
                VillageProjectType.FORGE_CHARTER,
                SurveyorCompassQuestService.REQUIRED_CRAFTING_REPUTATION,
                "quest.village-quest.special.surveyor_compass.title"
        ));
        register(paths, new RelicUnlockPath(
                SpecialQuestKind.MERCHANT_SEAL,
                ReputationService.ReputationTrack.TRADE,
                StoryArcType.MARKET_ROAD_TROUBLES,
                VillageProjectType.MARKET_CHARTER,
                MerchantSealQuestService.REQUIRED_TRADE_REPUTATION,
                "quest.village-quest.special.merchant.title"
        ));
        register(paths, new RelicUnlockPath(
                SpecialQuestKind.SHEPHERD_FLUTE,
                ReputationService.ReputationTrack.ANIMALS,
                StoryArcType.RESTLESS_PENS,
                VillageProjectType.PASTURE_CHARTER,
                ShepherdFluteQuestService.REQUIRED_ANIMAL_REPUTATION,
                "quest.village-quest.special.flute.title"
        ));
        return Map.copyOf(paths);
    }

    private static Map<ReputationService.ReputationTrack, RelicUnlockPath> buildTrackPaths() {
        Map<ReputationService.ReputationTrack, RelicUnlockPath> paths = new EnumMap<>(ReputationService.ReputationTrack.class);
        for (RelicUnlockPath path : PATHS.values()) {
            paths.put(path.track(), path);
        }
        return Map.copyOf(paths);
    }

    private static void register(Map<SpecialQuestKind, RelicUnlockPath> paths, RelicUnlockPath path) {
        paths.put(path.kind(), path);
    }

    public static RelicUnlockPath pathFor(SpecialQuestKind kind) {
        return kind == null ? null : PATHS.get(kind);
    }

    public static RelicUnlockPath pathFor(ReputationService.ReputationTrack track) {
        return track == null ? null : TRACK_PATHS.get(track);
    }

    public static boolean hasStoryRequirement(ServerWorld world, UUID playerId, SpecialQuestKind kind) {
        RelicUnlockPath path = pathFor(kind);
        return path == null || hasStoryRequirement(world, playerId, path.track());
    }

    public static boolean hasStoryRequirement(ServerWorld world, UUID playerId, ReputationService.ReputationTrack track) {
        RelicUnlockPath path = pathFor(track);
        return path == null
                || path.requiredStoryArc() == null
                || StoryQuestService.isCompleted(world, playerId, path.requiredStoryArc());
    }

    public static boolean hasReputationRequirement(ServerWorld world, UUID playerId, SpecialQuestKind kind) {
        RelicUnlockPath path = pathFor(kind);
        return path == null
                || path.track() == null
                || ReputationService.get(world, playerId, path.track()) >= path.requiredReputation();
    }

    public static boolean isUnlocked(ServerWorld world, UUID playerId, SpecialQuestKind kind) {
        RelicUnlockPath path = pathFor(kind);
        return path == null || (hasStoryRequirement(world, playerId, kind) && hasReputationRequirement(world, playerId, kind));
    }
}
