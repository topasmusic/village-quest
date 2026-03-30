package de.quest.quest.story;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.economy.CurrencyService;
import de.quest.reputation.ReputationService;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class VillageProjectService {
    private static final int APIARY_CHARTER_FARMING_BONUS = 5;
    private static final int FORGE_CHARTER_CRAFTING_LEVEL_BONUS = 2;
    private static final long PASTURE_CHARTER_ANIMALS_CURRENCY_BONUS = CurrencyService.SILVERMARK * 2L;
    private static final int WATCH_BELL_ROADSIDE_BONUS = 5;

    private VillageProjectService() {}

    private static PlayerQuestData data(ServerWorld world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    public static boolean isUnlocked(ServerWorld world, UUID playerId, VillageProjectType project) {
        if (project == null) {
            return false;
        }
        if (project.alwaysUnlocked()) {
            return true;
        }
        return world != null && playerId != null && data(world, playerId).hasUnlockedProject(project.id());
    }

    public static boolean setUnlocked(ServerWorld world, UUID playerId, VillageProjectType project, boolean unlocked) {
        if (world == null || playerId == null || project == null || project.alwaysUnlocked()) {
            return false;
        }
        PlayerQuestData data = data(world, playerId);
        if (data.hasUnlockedProject(project.id()) == unlocked) {
            return false;
        }
        data.setUnlockedProject(project.id(), unlocked);
        QuestState.get(world.getServer()).markDirty();
        return true;
    }

    public static boolean unlock(ServerWorld world, UUID playerId, VillageProjectType project) {
        return setUnlocked(world, playerId, project, true);
    }

    public static int bonusReputation(ServerWorld world, UUID playerId, ReputationService.ReputationTrack track) {
        if (track == null) {
            return 0;
        }
        return switch (track) {
            case FARMING -> isUnlocked(world, playerId, VillageProjectType.APIARY_CHARTER) ? APIARY_CHARTER_FARMING_BONUS : 0;
            case MONSTER_HUNTING -> isUnlocked(world, playerId, VillageProjectType.WATCH_BELL) ? WATCH_BELL_ROADSIDE_BONUS : 0;
            case CRAFTING, ANIMALS, TRADE -> 0;
        };
    }

    public static int bonusLevels(ServerWorld world, UUID playerId, ReputationService.ReputationTrack track) {
        if (track != ReputationService.ReputationTrack.CRAFTING) {
            return 0;
        }
        return isUnlocked(world, playerId, VillageProjectType.FORGE_CHARTER) ? FORGE_CHARTER_CRAFTING_LEVEL_BONUS : 0;
    }

    public static long bonusCurrency(ServerWorld world, UUID playerId, ReputationService.ReputationTrack track) {
        if (track != ReputationService.ReputationTrack.ANIMALS) {
            return 0L;
        }
        return isUnlocked(world, playerId, VillageProjectType.PASTURE_CHARTER) ? PASTURE_CHARTER_ANIMALS_CURRENCY_BONUS : 0L;
    }

    public static VillageProjectType projectForTrack(ReputationService.ReputationTrack track) {
        if (track == null) {
            return null;
        }
        return switch (track) {
            case FARMING -> VillageProjectType.APIARY_CHARTER;
            case CRAFTING -> VillageProjectType.FORGE_CHARTER;
            case ANIMALS -> VillageProjectType.PASTURE_CHARTER;
            case TRADE -> VillageProjectType.MARKET_CHARTER;
            case MONSTER_HUNTING -> VillageProjectType.WATCH_BELL;
        };
    }

    public static int applyReputationReward(ServerWorld world, UUID playerId, ReputationService.ReputationTrack track, int baseAmount) {
        if (world == null || playerId == null || track == null || baseAmount <= 0) {
            return 0;
        }
        int totalAmount = baseAmount + bonusReputation(world, playerId, track);
        ReputationService.add(world, playerId, track, totalAmount);
        return totalAmount;
    }

    public static Text formatBonusRewardLine(ServerWorld world, UUID playerId, ReputationService.ReputationTrack track) {
        if (world == null || playerId == null || track == null) {
            return Text.empty();
        }
        VillageProjectType project = unlockedTrackProject(world, playerId, track);
        if (project == null) {
            return Text.empty();
        }

        int reputationBonus = bonusReputation(world, playerId, track);
        if (reputationBonus > 0) {
            return Text.translatable(
                    "message.village-quest.project.reputation_bonus",
                    Text.translatable("quest.village-quest.project." + project.id() + ".title"),
                    Text.literal("+" + reputationBonus).formatted(track.color())
            ).formatted(Formatting.GRAY);
        }

        int levelBonus = bonusLevels(world, playerId, track);
        if (levelBonus > 0) {
            return Text.translatable(
                    "message.village-quest.project.level_bonus",
                    Text.translatable("quest.village-quest.project." + project.id() + ".title"),
                    Text.literal("+" + levelBonus).formatted(Formatting.GREEN)
            ).formatted(Formatting.GRAY);
        }

        long currencyBonus = bonusCurrency(world, playerId, track);
        if (currencyBonus <= 0L) {
            return Text.empty();
        }

        return Text.translatable(
                "message.village-quest.project.currency_bonus",
                Text.translatable("quest.village-quest.project." + project.id() + ".title"),
                CurrencyService.formatDelta(currencyBonus)
        ).formatted(Formatting.GRAY);
    }

    public static Text formatQuestEchoLine(ServerWorld world, UUID playerId, ReputationService.ReputationTrack track) {
        VillageProjectType project = unlockedTrackProject(world, playerId, track);
        if (project == null) {
            return Text.empty();
        }
        return Text.translatable("message.village-quest.project.quest_echo." + project.id()).formatted(Formatting.GRAY);
    }

    public static Text formatRewardEchoLine(ServerWorld world, UUID playerId, ReputationService.ReputationTrack track) {
        VillageProjectType project = unlockedTrackProject(world, playerId, track);
        if (project == null) {
            return Text.empty();
        }
        return Text.translatable("message.village-quest.project.reward_echo." + project.id()).formatted(Formatting.GRAY);
    }

    private static VillageProjectType unlockedTrackProject(ServerWorld world, UUID playerId, ReputationService.ReputationTrack track) {
        if (world == null || playerId == null || track == null) {
            return null;
        }
        VillageProjectType project = projectForTrack(track);
        if (project == null || !isUnlocked(world, playerId, project)) {
            return null;
        }
        return project;
    }

    public static List<Text> buildOverview(ServerWorld world, UUID playerId) {
        List<Text> lines = new ArrayList<>();
        for (VillageProjectType project : VillageProjectType.values()) {
            boolean unlocked = isUnlocked(world, playerId, project);
            lines.add(Text.translatable(
                    unlocked
                            ? "command.village-quest.questadmin.project.line.unlocked"
                            : "command.village-quest.questadmin.project.line.locked",
                    Text.translatable("quest.village-quest.project." + project.id() + ".title")
            ).formatted(unlocked ? Formatting.GREEN : Formatting.GRAY));
        }
        return lines;
    }
}
