package de.quest.commands;

import net.minecraft.commands.CommandSourceStack;

public final class AdminCommands {
    private AdminCommands() {}

    public static void register() {
    }

    public static boolean canManageRespawn(CommandSourceStack source) {
        var player = source.getPlayer();
        if (player == null) {
            return true;
        }
        return source.getServer().getPlayerList().isOp(player.nameAndId());
    }
}
