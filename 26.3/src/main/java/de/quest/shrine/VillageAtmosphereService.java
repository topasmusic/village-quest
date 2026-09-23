package de.quest.shrine;

import de.quest.content.story.VillagerDialogueService;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.village.VillageCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.villager.Villager;

/** Non-destructive world feedback for village condition and earned trust. */
final class VillageAtmosphereService {
    private VillageAtmosphereService() {}

    static void reactToVillager(ServerLevel world, ServerPlayer player, Villager villager,
                                VillageBondService.VillageBondView view) {
        if (world == null || player == null || villager == null || view == null) {
            return;
        }
        PlayerQuestData data = VillageBondService.data(world, player.getUUID());
        int day = VillageBondService.currentRequestDay();
        String prefix = VillageBondService.villageKey(view.index(), "reaction_");
        int revision = view.network().revision();
        if (data.getTradeRouteInt(prefix + "day") == day
                && data.getTradeRouteInt(prefix + "revision") == revision + 1) {
            return;
        }
        data.setTradeRouteInt(prefix + "day", day);
        data.setTradeRouteInt(prefix + "revision", revision + 1);
        QuestState.get(world.getServer()).setDirty();
        VillagerDialogueService.sendDialogue(player, villager, Component.translatable(
                dialogueKey(view.network().condition()), view.network().need().label(), view.level().label()));
        emit(world, villager.blockPosition().above(), view.network().condition(), 5);
    }

    static void showBoardState(ServerLevel world, BlockPos pos, VillageBondService.VillageBondView view) {
        if (world == null || pos == null || view == null) {
            return;
        }
        emit(world, pos.above(), view.network().condition(), 3);
    }

    static void celebrateRecovery(ServerLevel world, BlockPos pos) {
        if (world == null || pos == null) {
            return;
        }
        world.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.15,
                pos.getZ() + 0.5, 12, 0.55, 0.35, 0.55, 0.02);
        world.playSound(null, pos, SoundEvents.VILLAGER_CELEBRATE, SoundSource.BLOCKS, 0.7f, 1.1f);
    }

    static String dialogueKey(VillageCondition condition) {
        VillageCondition safe = condition == null ? VillageCondition.STABLE : condition;
        return "message.village-quest.village_network.reaction." + safe.key();
    }

    static void previewAll(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) return;
        VillageCondition[] conditions = VillageCondition.values();
        int start = -(conditions.length - 1) * 2;
        for (int i = 0; i < conditions.length; i++) {
            BlockPos pos = player.blockPosition().offset(start + i * 4, 1, 3);
            emit(world, pos, conditions[i], 18);
            player.sendSystemMessage(Component.literal((i + 1) + ". ")
                    .append(conditions[i].label()).append(Component.literal(" @ "
                            + pos.getX() + ", " + pos.getY() + ", " + pos.getZ())), false);
        }
        celebrateRecovery(world, player.blockPosition().above());
    }

    private static void emit(ServerLevel world, BlockPos pos, VillageCondition condition, int count) {
        ParticleOptions particle = switch (condition) {
            case CRISIS -> ParticleTypes.ANGRY_VILLAGER;
            case STRAINED -> ParticleTypes.SMOKE;
            case STABLE -> ParticleTypes.COMPOSTER;
            case RECOVERING -> ParticleTypes.WAX_ON;
            case THRIVING -> ParticleTypes.HAPPY_VILLAGER;
        };
        world.sendParticles(particle, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5,
                Math.max(1, count), 0.45, 0.25, 0.45, 0.01);
    }
}
