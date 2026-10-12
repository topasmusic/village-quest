package de.quest.commands;

import de.quest.network.UiTestTourPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/** Starts a client-local, read-only UI tour. Admin permission is enforced by the command tree. */
final class AdminUiPreviewService {
    private AdminUiPreviewService() {}
    static int start(CommandSourceStack source) {
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.village-quest.questadmin.player_required"));
            return 0;
        }
        ServerPlayNetworking.send(player, new UiTestTourPayload());
        return 1;
    }
}
