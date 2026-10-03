package de.quest.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.reputation.ReparationService;
import de.quest.reputation.SocialReputationData;
import de.quest.reputation.SocialReputationRules;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Explicit operator tools; UI test previews are added with the non-persistent view layer. */
public final class ReputationCommands {
    private ReputationCommands() {}
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return literal("reputation").requires(AdminCommands::canManageRespawn)
                .then(previewCommands())
                .then(literal("inspect").then(argument("player", EntityArgument.player()).executes(ctx -> inspect(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(literal("set")
                    .then(literal("guild").then(argument("player", EntityArgument.player()).then(argument("value", IntegerArgumentType.integer())
                            .executes(ctx -> set(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), null, IntegerArgumentType.getInteger(ctx, "value"))))))
                    .then(literal("village").then(argument("player", EntityArgument.player()).then(argument("dimension", StringArgumentType.word())
                        .then(argument("x", IntegerArgumentType.integer()).then(argument("z", IntegerArgumentType.integer()).then(argument("value", IntegerArgumentType.integer())
                            .executes(ctx -> {
                                try { return set(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), new VillageKey(StringArgumentType.getString(ctx, "dimension"),
                                        IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "z")), IntegerArgumentType.getInteger(ctx, "value")); }
                                catch (IllegalArgumentException invalid) { ctx.getSource().sendFailure(Component.translatable("reputation.village-quest.admin.invalid_village")); return 0; }
                            }))))))))
                .then(literal("pardon").then(argument("player", EntityArgument.player()).executes(ctx -> {
                    var source = ctx.getSource(); QuestState state = QuestState.get(source.getServer());
                    ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                    if (!ReparationService.pardon(state.getPlayerData(player.getUUID()).socialReputation(), state.socialServerTick())) return 0;
                    state.setDirty(); source.sendSuccess(() -> Component.translatable("reputation.village-quest.admin.pardoned", player.getName()), true); return 1;
                })));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> previewCommands() {
        var command = literal("uitest");
        for (String mode : new String[]{"neutral", "reliable", "respected", "blocked", "probation", "major"})
            command.then(literal(mode).executes(ctx -> {
                de.quest.reputation.ReputationPreview.open(ctx.getSource().getPlayerOrException(), mode); return 1;
            }));
        return command;
    }
    private static int inspect(CommandSourceStack source, ServerPlayer player) {
        QuestState state = QuestState.get(source.getServer());
        SocialReputationData data = state.getPlayersView().getOrDefault(player.getUUID(), new PlayerQuestData()).socialReputation();
        source.sendSuccess(() -> Component.translatable("reputation.village-quest.admin.inspect", player.getName(), data.guildTrust(),
                Component.translatable("screen.village-quest.reputation.rank." + SocialReputationRules.rankFor(data.guildTrust()).name().toLowerCase(java.util.Locale.ROOT)), data.localTrustView().size(),
                data.activeCase() == null ? "—" : data.activeCase().id().toString(), data.probationRemaining(), data.writable()), false); return 1;
    }
    private static int set(CommandSourceStack source, ServerPlayer player, VillageKey village, int requested) {
        QuestState state = QuestState.get(source.getServer()); var data = state.getPlayerData(player.getUUID()).socialReputation();
        if (!data.writable()) { source.sendFailure(Component.translatable("reputation.village-quest.admin.readonly")); return 0; }
        int before = data.guildTrust(); int localBefore = village == null ? 0 : data.localTrust(village);
        if (village == null) data.setGuildTrust(requested); else data.setLocalTrust(village, requested);
        data.appendAdministrativeHistory(UUID.randomUUID(), before, village, localBefore, state.socialServerTick());
        state.setDirty(); source.sendSuccess(() -> Component.translatable("reputation.village-quest.admin.set", player.getName(),
                village == null ? "guild" : village.toString(), village == null ? data.guildTrust() : data.localTrust(village)), true); return 1;
    }
}
