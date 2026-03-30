package de.quest.quest;

import de.quest.content.item.PeaceArmorHandler;
import de.quest.data.QuestState;
import de.quest.painting.PaintingNameService;
import de.quest.pilgrim.PilgrimContractService;
import de.quest.pilgrim.PilgrimService;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.special.SpecialQuestService;
import de.quest.quest.story.StoryQuestService;
import de.quest.quest.weekly.WeeklyQuestService;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;

public final class QuestService {
    private QuestService() {}

    public static void registerEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> QuestState.get(server).applyToRuntime());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            QuestDropTracker.clear();
            QuestState state = QuestState.get(server);
            state.updateFromRuntime();
            server.getOverworld().getPersistentStateManager().save();
        });

        ServerTickEvents.END_SERVER_TICK.register(QuestDropTracker::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(DailyQuestService::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(WeeklyQuestService::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(StoryQuestService::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(SpecialQuestService::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(PilgrimContractService::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(PaintingNameService::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(QuestBookHelper::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(QuestTrackerService::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(PilgrimService::onServerTick);

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof net.minecraft.server.network.ServerPlayerEntity player) {
                if (PeaceArmorHandler.tryNegateAttack(player, source.getAttacker())) {
                    return false;
                }
            }
            return true;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world instanceof ServerWorld sw && player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                ActionResult specialResult = SpecialQuestService.onUseBlock(sw, sp, hit.getBlockPos());
                if (specialResult != ActionResult.PASS) {
                    return specialResult;
                }
                var pos = hit.getBlockPos();
                var state = world.getBlockState(pos);
                var stack = player.getStackInHand(hand);
                StoryQuestService.onUseBlock(sw, sp, pos, state, stack);
                DailyQuestService.onBeeNestInteract(sw, sp, state, stack);
                StoryQuestService.onBeeNestInteract(sw, sp, state, stack);
                SpecialQuestService.onBeeNestInteract(sw, sp, state, stack);
                PilgrimContractService.onBeeNestInteract(sw, sp, state, stack);
            }
            return ActionResult.PASS;
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerWorld sw && player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                boolean allowed = SpecialQuestService.allowBlockBreak(sw, sp, pos);
                if (allowed) {
                    QuestDropTracker.onBlockBreakStart(sw, sp, pos, state, blockEntity);
                }
                return allowed;
            }
            return true;
        });

        PlayerBlockBreakEvents.CANCELED.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerWorld sw && player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                QuestDropTracker.onBlockBreakCanceled(sw, sp, pos);
            }
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerWorld sw && player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                DailyQuestService.onBlockBreak(sw, sp, pos, state);
                StoryQuestService.onBlockBreak(sw, sp, pos, state);
                SpecialQuestService.onBlockBreak(sw, sp, pos, state);
                QuestDropTracker.onBlockBreakFinished(sw, sp, pos);
            }
        });

        ServerEntityEvents.ENTITY_LOAD.register(QuestDropTracker::onEntityLoad);

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world instanceof ServerWorld sw && player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                var stack = player.getStackInHand(hand);
                ActionResult specialResult = SpecialQuestService.onUseEntity(sw, sp, hand, entity, stack);
                if (specialResult != ActionResult.PASS) {
                    return specialResult;
                }
                QuestDropTracker.onEntityUse(sw, sp, entity, stack);
                DailyQuestService.onEntityUse(sw, sp, entity, stack);
                StoryQuestService.onEntityUse(sw, sp, entity, stack);
                PilgrimContractService.onEntityUse(sw, sp, entity, stack);
            }
            return ActionResult.PASS;
        });

        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killedEntity, damageSource) -> {
            if (world instanceof ServerWorld sw && entity instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                QuestDropTracker.onKilledOtherEntity(sw, sp, killedEntity);
                DailyQuestService.onMonsterKill(sw, sp, killedEntity);
                StoryQuestService.onMonsterKill(sw, sp, killedEntity);
                SpecialQuestService.onMonsterKill(sw, sp, killedEntity);
                PilgrimContractService.onMonsterKill(sw, sp, killedEntity);
            }
        });
    }
}
