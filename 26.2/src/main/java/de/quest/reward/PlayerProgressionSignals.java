package de.quest.reward;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Vanilla milestones are eligibility hints, never a farm detector or payout multiplier. */
public final class PlayerProgressionSignals {
    public static boolean advancement(ServerPlayer player, String id) {
        if (player == null) return false;
        var holder = player.level().getServer().getAdvancements().get(Identifier.withDefaultNamespace(id));
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }
    public static boolean diamonds(ServerPlayer player) { return advancement(player, "story/mine_diamond"); }
    public static String recommendation(ServerPlayer player) {
        return advancement(player, "end/enter_end_gateway") ? "work" : "supplies";
    }
    private PlayerProgressionSignals() { }
}
