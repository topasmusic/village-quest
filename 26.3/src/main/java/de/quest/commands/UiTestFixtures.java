package de.quest.commands;

import de.quest.config.VillageQuestServerConfig;
import de.quest.network.Payloads;
import de.quest.network.VillageNetworkPayloads;
import de.quest.shrine.*;
import de.quest.village.*;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Pure preview snapshots: no server, entities, inventory or persistent state. */
public final class UiTestFixtures {
    private UiTestFixtures() {}
    public static VillageNetworkPayloads.CaravanMasterPayload caravan(String mode) {
        boolean stopped = mode.equals("paused") || mode.equals("incident");
        Component quote = switch (mode) {
            case "cargo" -> Component.translatable("screen.village-quest.caravan_master.quote_cargo");
            case "incident" -> Component.translatable("screen.village-quest.caravan_master.quote_incident");
            case "paused" -> Component.translatable("screen.village-quest.caravan_master.quote_paused");
            default -> Component.translatable("screen.village-quest.caravan_master.quote_safe");
        };
        return new VillageNetworkPayloads.CaravanMasterPayload(
                de.quest.network.ReputationPayloads.NONE, Component.literal("Master Rowan — UI Preview"),
                Component.literal("Oakfield — Granary"),
                Component.translatable("screen.village-quest.caravan_master.journey",
                        Component.translatable("text.village-quest.trade_route.node.homestead"),
                        Component.literal("Oakfield")),
                62, stopped ? -1 : 135, 84, de.quest.caravan.TradeRouteStatus.SECURED.label(),
                Component.translatable(stopped
                        ? "screen.village-quest.trade_route.availability." + (mode.equals("paused") ? "player_paused" : "incident")
                        : "screen.village-quest.caravan_master.active"),
                mode.equals("incident") ? de.quest.caravan.TradeRouteEventType.BROKEN_WHEEL.label() : Component.empty(),
                mode.equals("cargo") ? Component.literal("Regional Dispatch: 8 Bread → Forge / Ironridge")
                        : Component.translatable("screen.village-quest.caravan_master.no_cargo"),
                quote, true);
    }

    public static VillageNetworkPayloads.WayshrinePayload wayshrine(boolean owner) {
        int x = 0;
        int y = 64;
        int z = 0;
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
        return new VillageNetworkPayloads.WayshrinePayload(
                currentIndex, destinations, owner ? "Guild Tester" : "Guild Tester",
                owner, priceMultiplier, 0, 240L, 14, 5, 50);
    }

    public static VillageNetworkPayloads.NoticeBoardPayload noticeBoard(VillageBondLevel level) {
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

        VillageNeed previewNeed = VillageNeed.forVillage(type, 0);
        var previewOffers = VillageRequestGenerator.generate(type, previewNeed, request, null, level,
                VillageCondition.STABLE, VillageQuestServerConfig.AdventureProfile.STANDARD,
                0, 0, 1.0);
        return new VillageNetworkPayloads.NoticeBoardPayload(
                0, 64, 0,
                type.label(), level.label(), VillageCondition.STABLE.label(), previewNeed.label(), 50,
                request.title(), new ItemStack(request.item()), request.amount(), inventoryAmount, request.reward(),
                240L, completions, level.id(), nextThreshold,
                nextLevel, nextPerk, true, inventoryAmount >= request.amount(),
                previewOffers.stream().map(offer -> new VillageNetworkPayloads.NoticeBoardOfferData(
                        offer.id(), offer.request().title(), new ItemStack(offer.request().item()),
                        offer.amount(), inventoryAmount, offer.reward(), offer.support(),
                        offer.primaryNeed(), inventoryAmount >= offer.amount())).toList(),
                Component.translatable("text.village-quest.adventure_profile.standard"));
    }

    /** Read-only board states for visual QA; never advances the player's saved story. */
    public static VillageNetworkPayloads.NoticeJourneyPayload journey(String preview) {
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
                ? previewJourneyRequests() : null;
        return new VillageNetworkPayloads.NoticeJourneyPayload(
                0, 64, 0, stage,
                Component.translatable("text.village-quest.village_bond.type.granary"), title, detail,
                first, firstTarget, second, secondTarget,
                stage != VillageNetworkPayloads.NoticeJourneyPayload.INTRO
                        && stage != VillageNetworkPayloads.NoticeJourneyPayload.QUESTMASTER,
                stage == VillageNetworkPayloads.NoticeJourneyPayload.READY ? new ItemStack(net.minecraft.world.item.Items.BREAD)
                        : ItemStack.EMPTY,
                stage == VillageNetworkPayloads.NoticeJourneyPayload.READY ? 8 : 0, requests, true);
    }

    private static VillageNetworkPayloads.NoticeBoardPayload previewJourneyRequests() {
        VillageRequestType request = VillageRequestType.GRANARY_WHEAT;
        ItemStack stack = new ItemStack(request.item());
        return new VillageNetworkPayloads.NoticeBoardPayload(
                0, 64, 0,
                VillageBondType.GRANARY.label(), VillageBondLevel.KNOWN.label(),
                VillageCondition.STABLE.label(), VillageNeed.forVillage(VillageBondType.GRANARY, 0).label(), 50,
                request.title(), stack, request.amount(), 0, request.reward(), 0L, 0, 0, 2,
                VillageBondLevel.TRUSTED.label(), Component.translatable("screen.village-quest.notice_board.perk.trusted"),
                true, false, List.of(new VillageNetworkPayloads.NoticeBoardOfferData(
                        request.id(), request.title(), stack, request.amount(), 0, request.reward(), 1, true, false)),
                Component.translatable("text.village-quest.adventure_profile.standard"));
    }

}
