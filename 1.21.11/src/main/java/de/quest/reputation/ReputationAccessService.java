package de.quest.reputation;

import de.quest.data.QuestState;
import de.quest.reputation.SocialReputationRules.*;
import de.quest.village.VillageLifeState.VillageKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Every action checks current personal standing on the server, including an already opened dialog. */
public final class ReputationAccessService {
    private ReputationAccessService() {}
    public static boolean require(ServerLevel world, ServerPlayer player, ServiceKind kind, VillageKey village) {
        ServiceDecision decision = SocialReputationRules.decision(kind,
                QuestState.get(world.getServer()).getPlayerData(player.getUUID()).socialReputation(), village);
        if (!decision.allowed()) player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(decision.reasonKey()), false);
        return decision.allowed();
    }
}
