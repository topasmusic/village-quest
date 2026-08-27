package de.quest.quest;

import de.quest.archive.GuildArchiveService;
import de.quest.content.item.PeaceArmorHandler;
import de.quest.config.ClientPreferenceService;
import de.quest.caravan.TradeRouteService;
import de.quest.content.story.ShadowsTradeRoadEncounterService;
import de.quest.content.story.EmptyCaravanStoryService;
import de.quest.content.story.ShrinesBetweenRoadsStoryArc;
import de.quest.data.QuestState;
import de.quest.party.QuestPartyService;
import de.quest.painting.PaintingNameService;
import de.quest.pilgrim.PilgrimContractService;
import de.quest.pilgrim.PilgrimService;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.special.MerchantSealQuestService;
import de.quest.quest.special.SpecialQuestService;
import de.quest.quest.special.SurveyorCompassQuestService;
import de.quest.quest.story.StoryQuestService;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.reputation.ReputationService;
import de.quest.recipe.VillageQuestRecipeBookService;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;

public final class QuestService {
    private QuestService() {}

    public static void registerEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            resetTransientRuntimeState();
            TradeRouteService.despawnAll(server.overworld());
            EmptyCaravanStoryService.despawnAll(server.overworld());
            QuestState.get(server).applyToRuntime();
            QuestPartyService.loadPersistentState(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            TradeRouteService.despawnAll(server.overworld());
            EmptyCaravanStoryService.despawnAll(server.overworld());
            QuestPartyService.persistRuntimeState(server);
            QuestState state = QuestState.get(server);
            state.updateFromRuntime();
            server.overworld().getDataStorage().saveAndJoin();
            resetTransientRuntimeState();
        });

        ServerTickEvents.END_SERVER_TICK.register(QuestPartyService::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(QuestDropTracker::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(QuestHarvestTracker::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(DailyQuestService::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(WeeklyQuestService::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(StoryQuestService::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(QuestAvailabilityNotifier::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(TradeRouteService::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(SpecialQuestService::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(PilgrimContractService::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(PaintingNameService::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(QuestBookHelper::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(QuestTrackerService::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(PilgrimService::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(VillageQuestRecipeBookService::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(GuildArchiveService::onServerTick);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> {
                    QuestPartyService.handleJoin(handler.player);
                    TradeRouteService.backfillUnlockedLedger(server.overworld(), handler.player);
                    ReputationService.backfillRoadwardenHorn(server.overworld(), handler.player);
                    GuildArchiveService.migrateInventoryOnJoin(server.overworld(), handler.player);
                    VillageQuestRecipeBookService.unlockEligibleRecipes(handler.player);
                }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                server.execute(() -> handleDisconnect(handler.player)));

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
                if (PeaceArmorHandler.tryNegateAttack(player, source.getEntity())) {
                    return false;
                }
            }
            return true;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world instanceof ServerLevel sw && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                InteractionResult specialResult = SpecialQuestService.onUseBlock(sw, sp, hit.getBlockPos());
                if (specialResult != InteractionResult.PASS) {
                    return specialResult;
                }
                var pos = hit.getBlockPos();
                var state = world.getBlockState(pos);
                var stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof BlockItem) {
                    QuestState stateData = QuestState.get(sw.getServer());
                    stateData.markTerrainModified(pos);
                    stateData.markTerrainModified(pos.relative(hit.getDirection()));
                }
                QuestHarvestTracker.onUseBlock(sw, sp, pos, state);
                StoryQuestService.onUseBlock(sw, sp, pos, state, stack);
            }
            return InteractionResult.PASS;
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerLevel sw && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                boolean allowed = de.quest.shrine.VillageBondService.canBreakWayshrine(sw, sp, pos, state)
                        && ShrinesBetweenRoadsStoryArc.canBreakActiveRuinMilestone(sw, sp, pos, state)
                        && SpecialQuestService.allowBlockBreak(sw, sp, pos);
                if (allowed) {
                    QuestHarvestTracker.onBlockBreakStart(sw, sp, pos, state);
                    QuestDropTracker.onBlockBreakStart(sw, sp, pos, state, blockEntity);
                }
                return allowed;
            }
            return true;
        });

        PlayerBlockBreakEvents.CANCELED.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerLevel sw && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                QuestHarvestTracker.onBlockBreakCanceled(sw, sp, pos);
                QuestDropTracker.onBlockBreakCanceled(sw, sp, pos);
            }
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerLevel sw && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                QuestState.get(sw.getServer()).markTerrainModified(pos);
                DailyQuestService.onBlockBreak(sw, sp, pos, state);
                StoryQuestService.onBlockBreak(sw, sp, pos, state);
                SpecialQuestService.onBlockBreak(sw, sp, pos, state);
                QuestHarvestTracker.onBlockBreakFinished(sw, sp, pos);
                QuestDropTracker.onBlockBreakFinished(sw, sp, pos);
            }
        });

        ServerEntityEvents.ENTITY_LOAD.register(QuestDropTracker::onEntityLoad);

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world instanceof ServerLevel sw && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                var stack = player.getItemInHand(hand);
                InteractionResult specialResult = SpecialQuestService.onUseEntity(sw, sp, hand, entity, stack);
                if (specialResult != InteractionResult.PASS) {
                    return specialResult;
                }
                QuestDropTracker.onEntityUse(sw, sp, entity, stack);
                InteractionResult routeResult = TradeRouteService.onEntityUse(sw, sp, entity);
                if (routeResult != InteractionResult.PASS) {
                    return routeResult;
                }
                DailyQuestService.onEntityUse(sw, sp, entity, stack);
                StoryQuestService.onEntityUse(sw, sp, entity, stack);
                PilgrimContractService.onEntityUse(sw, sp, entity, stack);
            }
            return InteractionResult.PASS;
        });

        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killedEntity, damageSource) -> {
            if (world instanceof ServerLevel sw && entity instanceof net.minecraft.server.level.ServerPlayer sp) {
                QuestDropTracker.onKilledOtherEntity(sw, sp, killedEntity);
                DailyQuestService.onMonsterKill(sw, sp, killedEntity);
                StoryQuestService.onMonsterKill(sw, sp, killedEntity);
                TradeRouteService.onMonsterKill(sw, sp, killedEntity);
                SpecialQuestService.onMonsterKill(sw, sp, killedEntity);
                PilgrimContractService.onMonsterKill(sw, sp, killedEntity);
            }
        });
    }

    private static void handleDisconnect(ServerPlayer player) {
        if (player == null) {
            return;
        }
        var playerId = player.getUUID();
        QuestBookHelper.handleDisconnect(playerId);
        QuestTrackerService.handleDisconnect(playerId);
        QuestMasterUiService.handleDisconnect(player);
        MerchantSealQuestService.handleDisconnect(playerId);
        SurveyorCompassQuestService.handleDisconnect(playerId);
        TradeRouteService.handleDisconnect(playerId);
        ClientPreferenceService.handleDisconnect(playerId);
        QuestPartyService.handleDisconnect(player);
    }

    private static void resetTransientRuntimeState() {
        QuestDropTracker.clear();
        QuestHarvestTracker.clear();
        QuestBookHelper.resetAllSessions();
        QuestTrackerService.resetAllRuntimeState();
        QuestMasterUiService.resetAllSessions();
        MerchantSealQuestService.resetRuntimeState();
        SurveyorCompassQuestService.resetRuntimeState();
        ShadowsTradeRoadEncounterService.resetRuntimeState();
        EmptyCaravanStoryService.resetRuntimeState();
        TradeRouteService.resetRuntimeState();
        GuildArchiveService.resetTransientState();
        ClientPreferenceService.reset();
    }
}
