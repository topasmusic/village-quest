package de.quest.network;

import de.quest.pilgrim.PilgrimService;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.questmaster.QuestMasterUiService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public final class QuestNetworking {
    private QuestNetworking() {}

    public static void register() {
        Payloads.register();
        ServerPlayNetworking.registerGlobalReceiver(Payloads.JournalActionPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                ServerWorld world = (ServerWorld) player.getEntityWorld();
                if (payload.action() == Payloads.JournalActionPayload.ACTION_CANCEL_DAILY) {
                    if (DailyQuestService.isDailyActive(world, player.getUuid())) {
                        DailyQuestService.cancelToday(world, player.getUuid());
                        QuestBookHelper.refreshQuestBook(world, player);
                    }
                } else if (payload.action() == Payloads.JournalActionPayload.ACTION_CANCEL_WEEKLY) {
                    if (WeeklyQuestService.isWeeklyActive(world, player.getUuid())) {
                        WeeklyQuestService.cancelThisWeek(world, player.getUuid());
                        QuestBookHelper.refreshQuestBook(world, player);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(Payloads.PilgrimTradeActionPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> PilgrimService.handleTradeAction(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(Payloads.PilgrimTradeSessionPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> PilgrimService.handleTradeSession(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(Payloads.QuestMasterActionPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> QuestMasterUiService.handleAction(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(Payloads.QuestMasterSessionPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> QuestMasterUiService.handleSession(player, payload));
        });
    }
}
