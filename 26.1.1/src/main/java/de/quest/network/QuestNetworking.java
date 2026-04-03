package de.quest.network;

import de.quest.pilgrim.PilgrimService;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.questmaster.QuestMasterUiService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class QuestNetworking {
    private QuestNetworking() {}

    public static void register() {
        Payloads.register();
        ServerPlayNetworking.registerGlobalReceiver(Payloads.JournalActionPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                ServerLevel world = (ServerLevel) player.level();
                if (payload.action() == Payloads.JournalActionPayload.ACTION_CANCEL_DAILY) {
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
            context.server().execute(() -> PilgrimService.handleTradeAction(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(Payloads.PilgrimTradeSessionPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> PilgrimService.handleTradeSession(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(Payloads.QuestMasterActionPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> QuestMasterUiService.handleAction(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(Payloads.QuestMasterSessionPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> QuestMasterUiService.handleSession(player, payload));
        });
    }
}
