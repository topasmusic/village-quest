package de.wolkensprung.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import de.wolkensprung.entity.QuestGiverEntity;
import de.wolkensprung.quest.WolkensprungQuest;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class WolkensprungCommands {
    private static final double NPC_SEARCH_RADIUS = 3.0;

    private WolkensprungCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("wolkensprung")
                    .then(literal("cancel").executes(ctx -> cancelActive(ctx.getSource())))
                    .then(buildRespawnCommand())
                    .then(buildAreaCommand())
                    .then(buildCheckpointCommand()));

            dispatcher.register(literal("npcconfig")
                    .requires(WolkensprungCommands::canManage)
                    .executes(ctx -> showNpcConfig(ctx.getSource()))
                    .then(argument("radius", IntegerArgumentType.integer(0, 64))
                            .executes(ctx -> setNpcRadius(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))));

            dispatcher.register(literal("npcdelete")
                    .requires(WolkensprungCommands::canManage)
                    .executes(ctx -> deleteNearestNpc(ctx.getSource())));

            dispatcher.register(literal("npcdeleteid")
                    .requires(WolkensprungCommands::canManage)
                    .then(argument("uuid", StringArgumentType.word())
                            .executes(ctx -> deleteNpcById(ctx.getSource(), StringArgumentType.getString(ctx, "uuid")))));

            dispatcher.register(literal("quest")
                    .then(literal("accept").executes(ctx -> acceptQuest(ctx.getSource())))
                    .then(literal("decline").executes(ctx -> declineQuest(ctx.getSource()))));
        });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildRespawnCommand() {
        return literal("respawn")
                .requires(WolkensprungCommands::canManage)
                .then(literal("set")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            if (player == null) {
                                return 0;
                            }
                            ServerWorld world = ctx.getSource().getWorld();
                            BlockPos pos = player.getBlockPos();
                            WolkensprungQuest.setGlobalRespawn(world, pos);
                            player.sendMessage(Text.literal("Respawn gesetzt auf: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()).formatted(Formatting.GRAY), false);
                            return 1;
                        })
                        .then(argument("x", IntegerArgumentType.integer())
                                .then(argument("y", IntegerArgumentType.integer())
                                        .then(argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> {
                                                    ServerWorld world = ctx.getSource().getWorld();
                                                    int x = IntegerArgumentType.getInteger(ctx, "x");
                                                    int y = IntegerArgumentType.getInteger(ctx, "y");
                                                    int z = IntegerArgumentType.getInteger(ctx, "z");
                                                    BlockPos pos = new BlockPos(x, y, z);
                                                    WolkensprungQuest.setGlobalRespawn(world, pos);
                                                    ctx.getSource().sendFeedback(() -> Text.literal("Respawn gesetzt auf: " + x + " " + y + " " + z).formatted(Formatting.GRAY), false);
                                                    return 1;
                                                })))))
                .then(literal("clear").executes(ctx -> {
                    WolkensprungQuest.clearGlobalRespawn(ctx.getSource().getWorld());
                    ctx.getSource().sendFeedback(() -> Text.literal("Respawn geloescht.").formatted(Formatting.GRAY), false);
                    return 1;
                }))
                .then(literal("height")
                        .then(argument("y", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    int y = IntegerArgumentType.getInteger(ctx, "y");
                                    WolkensprungQuest.setFallYThreshold(ctx.getSource().getWorld(), y);
                                    ctx.getSource().sendFeedback(() -> Text.literal("Fall-Hoehe gesetzt auf: " + y).formatted(Formatting.GRAY), false);
                                    return 1;
                                })))
                .then(literal("info").executes(ctx -> {
                    BlockPos pos = WolkensprungQuest.getGlobalRespawnPos();
                    int y = WolkensprungQuest.getFallYThreshold();
                    if (pos == null || WolkensprungQuest.getGlobalRespawnDim() == null) {
                        ctx.getSource().sendFeedback(() -> Text.literal("Respawn nicht gesetzt.").formatted(Formatting.GRAY), false);
                    } else {
                        ctx.getSource().sendFeedback(() -> Text.literal("Respawn: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()).formatted(Formatting.GRAY), false);
                    }
                    if (y == Integer.MIN_VALUE) {
                        ctx.getSource().sendFeedback(() -> Text.literal("Fall-Hoehe nicht gesetzt.").formatted(Formatting.GRAY), false);
                    } else {
                        ctx.getSource().sendFeedback(() -> Text.literal("Fall-Hoehe: " + y).formatted(Formatting.GRAY), false);
                    }
                    return 1;
                }));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildAreaCommand() {
        return literal("area")
                .requires(WolkensprungCommands::canManage)
                .then(literal("wand").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) {
                        return 0;
                    }
                    boolean enabled = WolkensprungQuest.toggleWand(player.getUuid());
                    player.sendMessage(Text.literal(enabled
                            ? "Area-Wand aktiv: Klicke mit einem Stick auf Bloecke, um Spawnpunkte hinzuzufuegen."
                            : "Area-Wand deaktiviert.").formatted(Formatting.GRAY), false);
                    return 1;
                }))
                .then(literal("add").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) {
                        return 0;
                    }
                    ServerWorld world = ctx.getSource().getWorld();
                    BlockPos pos = player.getBlockPos();
                    boolean added = WolkensprungQuest.addCustomSpawnChecked(world, player, pos);
                    if (added) {
                        int count = WolkensprungQuest.getCustomSpawnCount(world);
                        player.sendMessage(Text.literal("Position hinzugefuegt. Gesamt: " + count).formatted(Formatting.GRAY), false);
                    }
                    return 1;
                }))
                .then(literal("removeall").executes(ctx -> {
                    ServerWorld world = ctx.getSource().getWorld();
                    int removed = WolkensprungQuest.clearCustomSpawns(world);
                    ctx.getSource().sendFeedback(() -> Text.literal("Custom-Spawnliste geleert (" + removed + " Eintraege).").formatted(Formatting.GRAY), false);
                    return 1;
                }))
                .then(literal("list").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) {
                        return 0;
                    }
                    WolkensprungQuest.openSpawnListScreen(ctx.getSource().getWorld(), player);
                    return 1;
                }))
                .then(literal("set")
                        .then(argument("x", IntegerArgumentType.integer())
                                .then(argument("y", IntegerArgumentType.integer())
                                        .then(argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> {
                                                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                                                    if (player == null) {
                                                        return 0;
                                                    }
                                                    ServerWorld world = ctx.getSource().getWorld();
                                                    int x = IntegerArgumentType.getInteger(ctx, "x");
                                                    int y = IntegerArgumentType.getInteger(ctx, "y");
                                                    int z = IntegerArgumentType.getInteger(ctx, "z");
                                                    boolean added = WolkensprungQuest.addCustomSpawnChecked(world, player, new BlockPos(x, y, z));
                                                    if (added) {
                                                        int count = WolkensprungQuest.getCustomSpawnCount(world);
                                                        player.sendMessage(Text.literal("Position gesetzt. Gesamt: " + count).formatted(Formatting.GRAY), false);
                                                    }
                                                    return 1;
                                                })))))
                .then(literal("sethere").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) {
                        return 0;
                    }
                    ServerWorld world = ctx.getSource().getWorld();
                    BlockPos pos = player.getBlockPos().down();
                    boolean added = WolkensprungQuest.addCustomSpawnChecked(world, player, pos);
                    if (added) {
                        int count = WolkensprungQuest.getCustomSpawnCount(world);
                        player.sendMessage(Text.literal("Position (aktuell) gesetzt. Gesamt: " + count).formatted(Formatting.GRAY), false);
                    }
                    return 1;
                }))
                .then(literal("remove")
                        .then(argument("x", IntegerArgumentType.integer())
                                .then(argument("y", IntegerArgumentType.integer())
                                        .then(argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> {
                                                    ServerWorld world = ctx.getSource().getWorld();
                                                    int x = IntegerArgumentType.getInteger(ctx, "x");
                                                    int y = IntegerArgumentType.getInteger(ctx, "y");
                                                    int z = IntegerArgumentType.getInteger(ctx, "z");
                                                    boolean removed = WolkensprungQuest.removeCustomSpawn(world, new BlockPos(x, y, z));
                                                    if (removed) {
                                                        int count = WolkensprungQuest.getCustomSpawnCount(world);
                                                        ctx.getSource().sendFeedback(() -> Text.literal("Spawnpunkt entfernt. Verbleibend: " + count).formatted(Formatting.GRAY), false);
                                                    } else {
                                                        ctx.getSource().sendFeedback(() -> Text.literal("Spawnpunkt nicht gefunden.").formatted(Formatting.RED), false);
                                                    }
                                                    return 1;
                                                })))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildCheckpointCommand() {
        return literal("checkpoint")
                .requires(WolkensprungCommands::canManage)
                .then(literal("wand").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) {
                        return 0;
                    }
                    boolean enabled = WolkensprungQuest.toggleCheckpointWand(player.getUuid());
                    player.sendMessage(Text.literal(enabled
                            ? "Checkpoint-Wand aktiv: Klicke mit einem Stick auf Bloecke, um Checkpoints zu setzen."
                            : "Checkpoint-Wand deaktiviert.").formatted(Formatting.GRAY), false);
                    return 1;
                }))
                .then(literal("list").executes(ctx -> {
                    List<BlockPos> checkpoints = WolkensprungQuest.getCheckpoints(ctx.getSource().getWorld());
                    if (checkpoints.isEmpty()) {
                        ctx.getSource().sendFeedback(() -> Text.literal("Keine Checkpoints gesetzt.").formatted(Formatting.GRAY), false);
                        return 1;
                    }
                    ctx.getSource().sendFeedback(() -> Text.literal("Checkpoints (" + checkpoints.size() + "):").formatted(Formatting.GRAY), false);
                    for (BlockPos pos : checkpoints) {
                        ctx.getSource().sendFeedback(() -> Text.literal(" - " + pos.getX() + " " + pos.getY() + " " + pos.getZ()).formatted(Formatting.GRAY), false);
                    }
                    return 1;
                }))
                .then(literal("set")
                        .then(argument("x", IntegerArgumentType.integer())
                                .then(argument("y", IntegerArgumentType.integer())
                                        .then(argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> {
                                                    ServerWorld world = ctx.getSource().getWorld();
                                                    int x = IntegerArgumentType.getInteger(ctx, "x");
                                                    int y = IntegerArgumentType.getInteger(ctx, "y");
                                                    int z = IntegerArgumentType.getInteger(ctx, "z");
                                                    boolean added = WolkensprungQuest.addCheckpointManual(world, new BlockPos(x, y, z));
                                                    ctx.getSource().sendFeedback(() -> Text.literal(added
                                                                    ? "Checkpoint gesetzt."
                                                                    : "Checkpoint existiert bereits.")
                                                            .formatted(Formatting.GRAY), false);
                                                    return 1;
                                                })))))
                .then(literal("removeall").executes(ctx -> {
                    int removed = WolkensprungQuest.clearCheckpoints(ctx.getSource().getWorld());
                    ctx.getSource().sendFeedback(() -> Text.literal("Checkpoints entfernt (" + removed + ").").formatted(Formatting.GRAY), false);
                    return 1;
                }))
                .then(literal("remove")
                        .then(argument("x", IntegerArgumentType.integer())
                                .then(argument("y", IntegerArgumentType.integer())
                                        .then(argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> {
                                                    ServerWorld world = ctx.getSource().getWorld();
                                                    int x = IntegerArgumentType.getInteger(ctx, "x");
                                                    int y = IntegerArgumentType.getInteger(ctx, "y");
                                                    int z = IntegerArgumentType.getInteger(ctx, "z");
                                                    boolean removed = WolkensprungQuest.removeCheckpoint(world, new BlockPos(x, y, z));
                                                    ctx.getSource().sendFeedback(() -> Text.literal(removed
                                                                    ? "Checkpoint entfernt."
                                                                    : "Checkpoint nicht gefunden.")
                                                            .formatted(Formatting.GRAY), false);
                                                    return 1;
                                                })))));
    }

    public static boolean canManage(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return true;
        }
        return source.getServer().getPlayerManager().isOperator(player.getPlayerConfigEntry());
    }

    private static int acceptQuest(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        ServerWorld world = source.getWorld();
        WolkensprungQuest.OfferType offer = WolkensprungQuest.consumeOffer(player.getUuid());
        if (offer == null) {
            player.sendMessage(Text.literal("Kein offenes Wolkensprung-Angebot.").formatted(Formatting.RED), false);
            return 0;
        }
        if (offer == WolkensprungQuest.OfferType.TIMER) {
            WolkensprungQuest.acceptTimer(world, player);
        } else {
            WolkensprungQuest.accept(world, player);
        }
        return 1;
    }

    private static int declineQuest(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        WolkensprungQuest.OfferType offer = WolkensprungQuest.consumeOffer(player.getUuid());
        if (offer == null) {
            player.sendMessage(Text.literal("Kein offenes Wolkensprung-Angebot.").formatted(Formatting.RED), false);
            return 0;
        }
        if (offer == WolkensprungQuest.OfferType.TIMER) {
            WolkensprungQuest.declineTimer(player);
        } else {
            WolkensprungQuest.declineOffer(player);
        }
        return 1;
    }

    private static int cancelActive(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        boolean cancelled = WolkensprungQuest.cancel(source.getWorld(), player);
        if (!cancelled) {
            player.sendMessage(Text.literal("Keine aktive Wolkensprung-Quest.").formatted(Formatting.RED), false);
            return 0;
        }
        player.sendMessage(Text.literal("Wolkensprung abgebrochen.").formatted(Formatting.GRAY), false);
        return 1;
    }

    private static int showNpcConfig(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        QuestGiverEntity npc = findNearestNpc(source.getWorld(), player);
        if (npc == null) {
            player.sendMessage(Text.literal("Kein Quest-NPC im 3-Block-Radius.").formatted(Formatting.RED), false);
            return 0;
        }
        player.sendMessage(Text.literal("NPC-Radius: " + npc.getWanderRadius() + " Bloecke.").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int setNpcRadius(ServerCommandSource source, int radius) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        QuestGiverEntity npc = findNearestNpc(source.getWorld(), player);
        if (npc == null) {
            player.sendMessage(Text.literal("Kein Quest-NPC im 3-Block-Radius.").formatted(Formatting.RED), false);
            return 0;
        }
        npc.setWanderRadius(radius);
        player.sendMessage(Text.literal("NPC-Radius gesetzt auf " + radius + " Bloecke.").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int deleteNearestNpc(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        QuestGiverEntity npc = findNearestNpc(source.getWorld(), player);
        if (npc == null) {
            player.sendMessage(Text.literal("Kein Quest-NPC im 3-Block-Radius.").formatted(Formatting.RED), false);
            return 0;
        }
        npc.discard();
        player.sendMessage(Text.literal("Quest-NPC entfernt.").formatted(Formatting.GRAY), false);
        return 1;
    }

    private static int deleteNpcById(ServerCommandSource source, String rawUuid) {
        UUID id;
        try {
            id = UUID.fromString(rawUuid);
        } catch (IllegalArgumentException ex) {
            source.sendFeedback(() -> Text.literal("Ungueltige NPC-ID.").formatted(Formatting.RED), false);
            return 0;
        }

        for (ServerWorld world : source.getServer().getWorlds()) {
            if (world.getEntity(id) instanceof QuestGiverEntity npc) {
                npc.discard();
                source.sendFeedback(() -> Text.literal("Quest-NPC entfernt.").formatted(Formatting.GRAY), false);
                return 1;
            }
        }

        source.sendFeedback(() -> Text.literal("Kein Quest-NPC mit dieser ID gefunden.").formatted(Formatting.RED), false);
        return 0;
    }

    private static QuestGiverEntity findNearestNpc(ServerWorld world, ServerPlayerEntity player) {
        Box range = player.getBoundingBox().expand(NPC_SEARCH_RADIUS);
        return world.getEntitiesByClass(QuestGiverEntity.class, range, entity -> true)
                .stream()
                .min((a, b) -> Double.compare(a.squaredDistanceTo(player), b.squaredDistanceTo(player)))
                .orElse(null);
    }
}
