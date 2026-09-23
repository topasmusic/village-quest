package de.quest.commands;

import de.quest.config.VillageQuestServerConfig;
import de.quest.economy.CurrencyService;
import de.quest.network.Payloads;
import de.quest.network.VillageNetworkPayloads;
import de.quest.pilgrim.PilgrimService;
import de.quest.questmaster.QuestMasterService;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.shrine.VillageBondLevel;
import de.quest.shrine.VillageBondType;
import de.quest.shrine.VillageRequestType;
import de.quest.village.VillageCondition;
import de.quest.village.VillageNeed;
import de.quest.village.VillageRequestGenerator;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Non-persistent UI fixtures kept out of the command registry and gameplay services. */
final class AdminUiPreviewService {
    private AdminUiPreviewService() {}

    static int openQuestMaster(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return missingPlayer(source);
        ServerLevel world = source.getServer().overworld();
        var questMaster = QuestMasterService.findNearbyQuestMaster(
                world, player.getX(), player.getY(), player.getZ());
        if (questMaster == null) questMaster = QuestMasterService.spawnNearPlayer(world, player);
        if (questMaster == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questmaster.spawn.failed")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        QuestMasterUiService.open(world, player, questMaster);
        return 1;
    }

    static int openPilgrim(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return missingPlayer(source);
        ServerLevel world = source.getServer().overworld();
        var pilgrim = PilgrimService.findActivePilgrim(world);
        if (pilgrim == null) pilgrim = PilgrimService.spawnNearPlayer(world, player, true);
        if (pilgrim != null && player.distanceToSqr(pilgrim) > 16.0d) {
            pilgrim.setPos(player.getX() + 1.5d, player.getY(), player.getZ() + 1.5d);
        }
        if (pilgrim == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.pilgrim.spawn.failed")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        PilgrimService.openTrade(world, player, pilgrim);
        return 1;
    }

    static int openWayshrine(CommandSourceStack source, boolean owner) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return missingPlayer(source);
        int x = player.getBlockX();
        int y = player.getBlockY();
        int z = player.getBlockZ();
        int currentIndex = 10_000;
        int priceMultiplier = owner ? 1 : 2;
        List<Payloads.TradeRouteShrineData> destinations = List.of(
                new Payloads.TradeRouteShrineData(currentIndex, x, y, z,
                        Component.translatable("text.village-quest.wayshrine.homestead", 1),
                        true, 0, VillageBondLevel.TRUSTED.id(), 1, 300),
                new Payloads.TradeRouteShrineData(currentIndex + 1, x + 144, y, z - 48,
                        Component.translatable("text.village-quest.wayshrine.village", 2,
                                VillageBondType.ARCHIVE.label()), false, 4 * priceMultiplier,
                        VillageBondLevel.KNOWN.id(), 2, 600),
                new Payloads.TradeRouteShrineData(currentIndex + 2, x - 128, y, z + 80,
                        Component.translatable("text.village-quest.wayshrine.village", 3,
                                VillageBondType.FORGE.label()), false, 2 * priceMultiplier,
                        VillageBondLevel.TRUSTED.id(), 1, 300),
                new Payloads.TradeRouteShrineData(currentIndex + 3, x + 96, y, z + 144,
                        Component.translatable("text.village-quest.wayshrine.village", 4,
                                VillageBondType.PASTURE.label()), false, 2 * priceMultiplier,
                        VillageBondLevel.ALLIED.id(), 1, 240),
                new Payloads.TradeRouteShrineData(currentIndex + 4, x - 160, y, z - 112,
                        Component.translatable("text.village-quest.wayshrine.village", 5,
                                VillageBondType.APIARY.label()), false, 2 * priceMultiplier,
                        VillageBondLevel.TRUSTED.id(), 1, 300));
        ServerPlayNetworking.send(player, new VillageNetworkPayloads.WayshrinePayload(
                currentIndex, destinations, owner ? player.getGameProfile().name() : "Guild Tester",
                owner, priceMultiplier, 0, 240L, 14, 5, 50));
        return 1;
    }

    static int openNoticeBoard(CommandSourceStack source, VillageBondLevel level) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return missingPlayer(source);
        VillageBondType type;
        VillageRequestType request;
        int inventoryAmount;
        int completions;
        int nextThreshold;
        Component nextLevel;
        Component nextPerk;
        if (level == VillageBondLevel.KNOWN) {
            type = VillageBondType.ARCHIVE;
            request = VillageRequestType.ARCHIVE_BOOKS;
            inventoryAmount = 5;
            completions = 0;
            nextThreshold = 2;
            nextLevel = VillageBondLevel.TRUSTED.label();
            nextPerk = Component.translatable("screen.village-quest.notice_board.perk.trusted");
        } else if (level == VillageBondLevel.TRUSTED) {
            type = VillageBondType.FORGE;
            request = VillageRequestType.FORGE_IRON;
            inventoryAmount = request.amount();
            completions = 2;
            nextThreshold = 8;
            nextLevel = VillageBondLevel.ALLIED.label();
            nextPerk = Component.translatable("screen.village-quest.notice_board.perk.allied");
        } else {
            type = VillageBondType.PASTURE;
            request = VillageRequestType.PASTURE_WOOL;
            inventoryAmount = request.amount();
            completions = 8;
            nextThreshold = 0;
            nextLevel = Component.translatable("screen.village-quest.notice_board.max_level");
            nextPerk = Component.translatable("screen.village-quest.notice_board.perk.complete");
        }

        ServerLevel world = source.getServer().overworld();
        VillageNeed previewNeed = VillageNeed.forVillage(type, 0);
        var previewOffers = VillageRequestGenerator.generate(type, previewNeed, request, null, level,
                VillageCondition.STABLE, VillageQuestServerConfig.AdventureProfile.STANDARD,
                0, 0, 1.0);
        ServerPlayNetworking.send(player, new VillageNetworkPayloads.NoticeBoardPayload(
                player.getBlockX(), player.getBlockY(), player.getBlockZ(),
                type.label(), level.label(), VillageCondition.STABLE.label(), previewNeed.label(), 50,
                request.title(), new ItemStack(request.item()), request.amount(), inventoryAmount, request.reward(),
                CurrencyService.getBalance(world, player.getUUID()), completions, level.id(), nextThreshold,
                nextLevel, nextPerk, true, inventoryAmount >= request.amount(),
                previewOffers.stream().map(offer -> new VillageNetworkPayloads.NoticeBoardOfferData(
                        offer.id(), offer.request().title(), new ItemStack(offer.request().item()),
                        offer.amount(), inventoryAmount, offer.reward(), offer.support(),
                        offer.primaryNeed(), inventoryAmount >= offer.amount())).toList(),
                Component.translatable("text.village-quest.adventure_profile.standard")));
        return 1;
    }

    /** Read-only board states for visual QA; never advances the player's saved story. */
    static int openNoticeJourney(CommandSourceStack source, String preview) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return missingPlayer(source);
        int stage = switch (preview) {
            case "available" -> VillageNetworkPayloads.NoticeJourneyPayload.AVAILABLE;
            case "active", "forge", "pasture", "apiary", "archive", "connected" -> VillageNetworkPayloads.NoticeJourneyPayload.ACTIVE;
            case "choice" -> VillageNetworkPayloads.NoticeJourneyPayload.CHOOSE;
            case "ready" -> VillageNetworkPayloads.NoticeJourneyPayload.READY;
            case "remembered" -> VillageNetworkPayloads.NoticeJourneyPayload.REMEMBERED;
            case "paused" -> VillageNetworkPayloads.NoticeJourneyPayload.PAUSED;
            case "away" -> VillageNetworkPayloads.NoticeJourneyPayload.AWAY;
            case "welcome" -> VillageNetworkPayloads.NoticeJourneyPayload.QUESTMASTER;
            default -> VillageNetworkPayloads.NoticeJourneyPayload.INTRO;
        };
        int first = switch (preview) {
            case "greeting" -> 2;
            case "active", "connected", "forge", "archive" -> 2;
            case "pasture" -> 1;
            case "apiary" -> 3;
            case "choice", "ready", "remembered" -> 24;
            default -> 0;
        };
        int firstTarget = preview.equals("greeting") ? 3
                : stage == VillageNetworkPayloads.NoticeJourneyPayload.INTRO
                || stage == VillageNetworkPayloads.NoticeJourneyPayload.QUESTMASTER ? 0
                : switch (preview) {
                    case "forge" -> 12;
                    case "pasture" -> 2;
                    case "apiary" -> 6;
                    case "archive" -> 3;
                    default -> 24;
                };
        int second = stage == VillageNetworkPayloads.NoticeJourneyPayload.READY
                || stage == VillageNetworkPayloads.NoticeJourneyPayload.REMEMBERED ? 1 : 0;
        String storyKey = switch (preview) {
            case "forge" -> "sparks_for_the_road";
            case "pasture" -> "long_drive";
            case "apiary" -> "lanterns_in_bloom";
            case "archive" -> "ink_between_villages";
            default -> "shared_table";
        };
        Component title = stage == VillageNetworkPayloads.NoticeJourneyPayload.INTRO
                || stage == VillageNetworkPayloads.NoticeJourneyPayload.QUESTMASTER
                ? Component.translatable("screen.village-quest.notice_journey.welcome")
                : Component.translatable("quest.village-quest.guild_town.story." + storyKey + ".title");
        Component detail = switch (preview) {
            case "greeting" -> Component.translatable("message.village-quest.guild_notice_post.teaser.greeting_progress", 2, 3);
            case "available" -> Component.translatable("screen.village-quest.notice_journey.available");
            case "active" -> Component.translatable("message.village-quest.guild_town.story.shared_table.progress",
                    Component.translatable("quest.village-quest.guild_town.variant.preventive"), 12, 24, 0, 1);
            case "connected" -> Component.translatable("message.village-quest.guild_town.story.shared_table.progress",
                    Component.translatable("quest.village-quest.guild_town.variant.preventive"), 2, 24, 0, 1);
            case "forge", "pasture", "apiary", "archive" -> Component.translatable(
                    "message.village-quest.guild_town.story." + storyKey + ".progress",
                    Component.translatable("quest.village-quest.guild_town.variant.preventive"),
                    first, firstTarget, 0, switch (preview) {
                        case "forge" -> 6;
                        case "pasture" -> 2;
                        case "apiary" -> 4;
                        default -> 12;
                    });
            case "choice" -> Component.translatable("message.village-quest.guild_town.story.shared_table.choose");
            case "ready" -> Component.translatable("screen.village-quest.notice_journey.ready");
            case "remembered" -> Component.translatable("message.village-quest.guild_town.story.remembered", title);
            case "paused" -> Component.translatable("message.village-quest.guild_town.story.paused_line", title);
            case "away" -> Component.translatable("message.village-quest.guild_town.story.return_to_village");
            default -> Component.translatable("message.village-quest.guild_notice_post.teaser.first_favor");
        };
        int secondTarget = switch (preview) {
            case "forge" -> 6;
            case "pasture" -> 2;
            case "apiary" -> 4;
            case "archive" -> 12;
            default -> firstTarget == 0 ? 0 : stage == VillageNetworkPayloads.NoticeJourneyPayload.INTRO ? 0 : 1;
        };
        VillageNetworkPayloads.NoticeBoardPayload requests = preview.equals("connected")
                ? previewJourneyRequests(player) : null;
        ServerPlayNetworking.send(player, new VillageNetworkPayloads.NoticeJourneyPayload(
                player.getBlockX(), player.getBlockY(), player.getBlockZ(), stage,
                Component.translatable("text.village-quest.village_bond.type.granary"), title, detail,
                first, firstTarget, second, secondTarget,
                stage != VillageNetworkPayloads.NoticeJourneyPayload.INTRO
                        && stage != VillageNetworkPayloads.NoticeJourneyPayload.QUESTMASTER,
                stage == VillageNetworkPayloads.NoticeJourneyPayload.READY ? new ItemStack(net.minecraft.world.item.Items.BREAD)
                        : ItemStack.EMPTY,
                stage == VillageNetworkPayloads.NoticeJourneyPayload.READY ? 8 : 0, requests, true));
        return 1;
    }

    private static VillageNetworkPayloads.NoticeBoardPayload previewJourneyRequests(ServerPlayer player) {
        VillageRequestType request = VillageRequestType.GRANARY_WHEAT;
        ItemStack stack = new ItemStack(request.item());
        return new VillageNetworkPayloads.NoticeBoardPayload(
                player.getBlockX(), player.getBlockY(), player.getBlockZ(),
                VillageBondType.GRANARY.label(), VillageBondLevel.KNOWN.label(),
                VillageCondition.STABLE.label(), VillageNeed.forVillage(VillageBondType.GRANARY, 0).label(), 50,
                request.title(), stack, request.amount(), 0, request.reward(), 0L, 0, 0, 2,
                VillageBondLevel.TRUSTED.label(), Component.translatable("screen.village-quest.notice_board.perk.trusted"),
                true, false, List.of(new VillageNetworkPayloads.NoticeBoardOfferData(
                        request.id(), request.title(), stack, request.amount(), 0, request.reward(), 1, true, false)),
                Component.translatable("text.village-quest.adventure_profile.standard"));
    }

    private static int missingPlayer(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required")
                .withStyle(ChatFormatting.RED), false);
        return 0;
    }
}
