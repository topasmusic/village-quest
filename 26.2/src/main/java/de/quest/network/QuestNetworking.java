package de.quest.network;

import de.quest.caravan.TradeRouteService;
import de.quest.caravan.RegionalDispatchService;
import de.quest.config.ClientPreferenceService;
import de.quest.economy.ProsperityService;
import de.quest.pilgrim.PilgrimService;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.shrine.VillageBondService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class QuestNetworking {
    private QuestNetworking() {}

    private static void executeAction(ServerPlayer player, Runnable action) {
        player.level().getServer().execute(() -> {
            if (player.isRemoved() || player.connection != null && !player.connection.isAcceptingMessages()) return;
            if (!ProgressionProtocol.require(player)) return;
            if (de.quest.shrine.VillageIntroRequestService.blocksActions(player.level(), player.getUUID())
                    && !de.quest.shrine.VillageIntroRequestService.recoverBeforeActions(player)) return;
            action.run();
        });
    }
    public static void register() {
        Payloads.register();
        ServerPlayNetworking.registerGlobalReceiver(ExactVersionPayload.ID,(payload,context) -> {});
        ServerPlayNetworking.registerGlobalReceiver(EncounterQuestPayloads.Action.ID,(payload,context)->
                executeAction(context.player(),()->de.quest.ambient.EncounterQuestService.handle(context.player(),payload)));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->de.quest.quest.special.FriendsQuestUiService.disconnected(handler.player.getUUID()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(de.quest.quest.special.FriendsQuestUiService::stopped);
        ServerPlayNetworking.registerGlobalReceiver(FriendsQuestPayloads.Action.ID,(payload,context) ->
                executeAction(context.player(), () -> de.quest.quest.special.FriendsQuestUiService.handle(context.player(),payload)));
        ServerPlayNetworking.registerGlobalReceiver(QuestSetupPayloads.Open.ID, (payload, context) ->
                executeAction(context.player(), () -> de.quest.config.QuestSetupService.open(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(QuestSetupPayloads.Save.ID, (payload, context) ->
                executeAction(context.player(), () -> de.quest.config.QuestSetupService.save(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(MarketPayloads.Action.ID, (payload, context) ->
                executeAction(context.player(), () -> de.quest.kingdom.KingdomMarketUiService.handle(context.server(),context.player(),payload)));
        ServerPlayNetworking.registerGlobalReceiver(BuildingPayloads.Action.ID, (payload, context) ->
                executeAction(context.player(), () -> de.quest.kingdom.KingdomBuildingService.handle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(RewardPayloads.Action.ID, (payload, context) ->
                executeAction(context.player(), () -> de.quest.reward.VanillaRewardService.handle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ReputationPayloads.PagePayload.ID, (payload, context) ->
                executeAction(context.player(), () -> de.quest.reputation.ReputationInteractionService.page(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ReputationPayloads.OpenPayload.ID, (payload, context) ->
                executeAction(context.player(), () -> de.quest.reputation.ReputationInteractionService.open(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ReputationPayloads.ActionPayload.ID, (payload, context) ->
                executeAction(context.player(), () -> de.quest.reputation.ReputationInteractionService.handle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.JournalActionPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            executeAction(context.player(), () -> {
                ServerLevel world = (ServerLevel) player.level();
                if (payload.action() == 2) {
                    de.quest.ambient.EncounterQuestService.cancel(player);
                } else if (payload.action() == Payloads.JournalActionPayload.ACTION_CANCEL_DAILY) {
                    if (DailyQuestService.isDailyActive(world, player.getUUID())) {
                        DailyQuestService.cancelToday(world, player.getUUID());
                        QuestBookHelper.refreshQuestBook(world, player);
                    }
                } else if (payload.action() == Payloads.JournalActionPayload.ACTION_CANCEL_WEEKLY) {
                    if (WeeklyQuestService.isWeeklyActive(world, player.getUUID())) {
                        WeeklyQuestService.cancelThisWeek(world, player.getUUID());
                        QuestBookHelper.refreshQuestBook(world, player);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(Payloads.PilgrimTradeActionPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            executeAction(context.player(), () -> PilgrimService.handleTradeAction(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(Payloads.PilgrimTradeSessionPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            executeAction(context.player(), () -> PilgrimService.handleTradeSession(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(Payloads.EconomyActionPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            executeAction(context.player(), () -> ProsperityService.handleAction(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(Payloads.QuestMasterActionPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            executeAction(context.player(), () -> QuestMasterUiService.handleAction(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(Payloads.QuestMasterPartyActionPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            executeAction(context.player(), () -> QuestMasterUiService.handlePartyAction(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(Payloads.QuestMasterSessionPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            executeAction(context.player(), () -> QuestMasterUiService.handleSession(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(Payloads.TradeRouteActionPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            executeAction(context.player(), () -> TradeRouteService.handleMapAction(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(VillageNetworkPayloads.WayshrineTravelPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            executeAction(context.player(), () -> VillageBondService.handleTravel(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(VillageNetworkPayloads.WayshrineRenamePayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            executeAction(context.player(), () -> VillageBondService.handleRename(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(VillageNetworkPayloads.NoticeBoardActionPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            executeAction(context.player(), () -> VillageBondService.handleNoticeBoardAction(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(VillageNetworkPayloads.NoticeJourneyActionPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            executeAction(context.player(), () -> VillageBondService.handleNoticeJourneyAction(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(VillageNetworkPayloads.RegionalDispatchActionPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            executeAction(context.player(), () -> RegionalDispatchService.handleAction(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(VillageNetworkPayloads.CaravanMasterActionPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            executeAction(context.player(), () -> TradeRouteService.handleMasterAction(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(Payloads.ClientPreferencesPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            executeAction(context.player(), () -> {
                ClientPreferenceService.update(player, payload);
                QuestTrackerService.applyClientDefault(
                        context.server().overworld(), player, payload.questTrackerEnabledByDefault());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(Payloads.QuestTrackerActionPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            executeAction(context.player(), () -> QuestTrackerService.toggle(context.server().overworld(), player));
        });
    }
}
