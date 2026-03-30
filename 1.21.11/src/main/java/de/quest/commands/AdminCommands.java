package de.quest.commands;

import net.minecraft.server.command.ServerCommandSource;

public final class AdminCommands {
    private AdminCommands() {}

    public static void register() {
        // Wolkensprung admin commands were moved to the standalone Wolkensprung mod.
    }

    public static boolean canManageRespawn(ServerCommandSource source) {
        var player = source.getPlayer();
        if (player == null) {
            return true;
        }
        return source.getServer().getPlayerManager().isOperator(player.getPlayerConfigEntry());
    }
}
