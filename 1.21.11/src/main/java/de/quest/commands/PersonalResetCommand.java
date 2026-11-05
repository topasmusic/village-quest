package de.quest.commands;

import static net.minecraft.commands.Commands.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import de.quest.data.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Only the requesting player can confirm their own restart. No target argument or OP requirement. */
public final class PersonalResetCommand {
    private static final PersonalResetConfirmations CONFIRMATIONS=new PersonalResetConfirmations();
    private PersonalResetCommand() {}
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return literal("reset").executes(ctx -> request(ctx.getSource().getPlayer()))
                .then(literal("confirm").executes(ctx -> confirm(ctx.getSource().getPlayer())));
    }
    private static boolean eligible(ServerPlayer player) {
        return player!=null && player.isAlive() && !player.isCreative() && !player.isSpectator()
                && player.gameMode!=null && player.gameMode.getGameModeForPlayer()==net.minecraft.world.level.GameType.SURVIVAL;
    }
    private static int request(ServerPlayer player) {
        if(!eligible(player))return 0;
        CONFIRMATIONS.request(player.level().getServer(),player.getUUID(),System.currentTimeMillis());
        player.sendSystemMessage(Component.translatable("command.village-quest.personal_reset.warning"),false);
        player.sendSystemMessage(Component.translatable("command.village-quest.personal_reset.confirm","/vq reset confirm"),false);
        return 1;
    }
    private static int confirm(ServerPlayer player) {
        if(!eligible(player))return 0;
        var server=player.level().getServer();
        // A direct, expired or already-consumed confirm only opens a fresh warning.
        if(!CONFIRMATIONS.consume(server,player.getUUID(),System.currentTimeMillis())) return request(player);
        if (!PersonalResetService.restart(player)) {
            player.sendSystemMessage(Component.translatable("command.village-quest.personal_reset.blocked"),false);return 0;
        }
        player.sendSystemMessage(Component.translatable("command.village-quest.personal_reset.done"),false);
        return 1;
    }
}
