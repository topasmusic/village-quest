package de.quest.guildtown;

import net.minecraft.util.Prediction;
import de.quest.caravan.TradeRouteService;
import de.quest.content.story.ShadowsTradeRoadEncounterService;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.guild.VillageGuildService;
import de.quest.guild.VillageGuildState;
import de.quest.quest.QuestBookHelper;
import de.quest.registry.ModItems;
import de.quest.shrine.VillageBondService;
import de.quest.shrine.VillageBondType;
import de.quest.shrine.VillageWelcomeService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/** Integrated gameplay facade for local stories, pair commissions, Chronicle, projects and Concord. */
public final class GuildTownService {
    private static final int GRANARY_TARGET = 24;
    private static final int FORGE_TARGET = 12;
    private static final int PASTURE_ESCORT_SELECTION_TARGET = 2;
    private static final int PASTURE_ESCORT_DISTANCE_TARGET = 2;
    private static final int PASTURE_ESCORT_START_RADIUS = 24;
    private static final int PASTURE_ESCORT_PLAYER_RADIUS = 16;
    private static final int APIARY_HONEY_TARGET = 6;
    private static final int APIARY_LIGHT_TARGET = 4;
    private static final int ARCHIVE_TRADE_TARGET = 3;
    private static final int ARCHIVE_SUPPLY_TARGET = 12;

    private GuildTownService() {}

    public enum NoticePostStoryState {
        DORMANT,
        REMEMBERED,
        AVAILABLE,
        ACTIVE
    }

    enum CommissionProgressResult {
        NONE,
        PAUSED,
        PROGRESSED
    }

    public static int showStatus(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) return 0;
        for (Component line : statusLines(world, player)) player.sendSystemMessage(line, false);
        return 1;
    }

    public static int showChronicle(ServerLevel world, ServerPlayer player) {
        return showChronicle(world, player, 1);
    }

    public static int showChronicle(ServerLevel world, ServerPlayer player, int requestedPage) {
        if (world == null || player == null) return 0;
        List<GuildTownProgress.ChronicleEntry> entries = GuildTownProgress.personalChronicle(data(world, player.getUUID()));
        int pages = Math.max(1, (entries.size() + 11) / 12);
        int page = Math.max(1, Math.min(pages, requestedPage));
        int end = Math.max(0, entries.size() - (page - 1) * 12);
        int start = Math.max(0, end - 12);
        player.sendSystemMessage(Component.translatable("message.village-quest.guild_town.chronicle.header",
                page, pages, entries.size())
                .withStyle(ChatFormatting.GOLD), false);
        for (int i = start; i < end; i++) {
            GuildTownProgress.ChronicleEntry entry = entries.get(i);
            player.sendSystemMessage(Component.translatable("message.village-quest.guild_town.chronicle.line",
                    entry.sequence(), entry.village().x(), entry.village().z(), chronicleLabel(entry.eventId()))
                    .withStyle(entry.milestone() ? ChatFormatting.YELLOW : ChatFormatting.GRAY), false);
        }
        return entries.isEmpty() ? 0 : 1;
    }

    public static List<Component> statusLines(ServerLevel world, ServerPlayer player) {
        PlayerQuestData data = data(world, player.getUUID());
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("message.village-quest.guild_town.status",
                GuildTownProgress.completedStoryCount(data), GuildTownStory.values().length,
                GuildTownProgress.completedCommissionCount(data), GuildTownCommission.values().length,
                TradeRouteService.routeCount(world, player.getUUID())).withStyle(ChatFormatting.GOLD));
        GuildTownStory activeStory = storyById(GuildTownProgress.activeStoryId(data));
        GuildTownCommission activeCommission = GuildTownProgress.activeCommission(data);
        if (activeStory != null) lines.add(storyProgressLine(data, activeStory));
        if (activeCommission != null) lines.add(commissionProgressLine(data, activeCommission));
        if (activeStory == null && activeCommission == null) {
            lines.add(Component.translatable("message.village-quest.guild_town.status.idle")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (activeCommission == null) {
            int offered = 0;
            for (GuildTownCommission commission : GuildTownCommission.values()) {
                if (offered >= 3 || GuildTownProgress.commissionCompleted(data, commission)
                        || !GuildTownProgress.storyCompleted(data, GuildTownStory.forVillage(commission.first()))
                        || !GuildTownProgress.storyCompleted(data, GuildTownStory.forVillage(commission.second()))
                        || findConnectedPair(world, player.getUUID(), commission) == null) continue;
                lines.add(Component.translatable("message.village-quest.guild_town.commission.available", commission.title())
                        .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                                .withClickEvent(new ClickEvent.RunCommand(
                                        "/vq town commission accept " + commission.key()))));
                offered++;
            }
        }
        VillageGuildState.GuildSnapshot guild = VillageGuildService.guild(world, player.getUUID());
        GuildTownSharedState.ProjectSnapshot project = guild == null ? null
                : GuildTownSharedState.get(world.getServer()).project(guild.id()).orElse(null);
        if (project != null) {
            lines.add(Component.translatable(project.completed()
                    ? "message.village-quest.guild_town.project.ready"
                    : "message.village-quest.guild_town.project.progress",
                    project.type().title(), project.progress(), project.type().target())
                    .withStyle(project.completed() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        int pendingClaims = GuildTownSharedState.get(world.getServer()).pendingClaims(player.getUUID());
        Component pendingClaimLine = pendingClaimsLine(pendingClaims);
        if (pendingClaimLine != null) lines.add(pendingClaimLine);
        if (GuildTownProgress.hasReward(data, "concordant_title")) {
            lines.add(Component.translatable("message.village-quest.guild_town.title.unlocked")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (GuildTownProgress.hasReward(data, "concord_title")) {
            lines.add(Component.translatable("message.village-quest.guild_town.title.concord")
                    .withStyle(ChatFormatting.GOLD));
        }
        if (GuildTownProgress.hasReward(data, "pairing_mastery")) {
            lines.add(Component.translatable("message.village-quest.guild_town.pairing_mastery")
                    .withStyle(ChatFormatting.AQUA));
        }
        List<GuildTownProgress.ChronicleEntry> personalChronicle = GuildTownProgress.personalChronicle(data);
        if (!personalChronicle.isEmpty()) {
            GuildTownProgress.ChronicleEntry latest = personalChronicle.get(personalChronicle.size() - 1);
            lines.add(Component.translatable("message.village-quest.guild_town.chronicle.latest",
                    latest.sequence(), chronicleLabel(latest.eventId())).withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(GuildTownProgress.finaleClaimed(data)
                ? Component.translatable("message.village-quest.guild_town.concord.claimed")
                    .withStyle(ChatFormatting.GOLD)
                : finaleEligible(world, player.getUUID())
                ? Component.translatable("message.village-quest.guild_town.concord.ready")
                    .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                            .withClickEvent(new ClickEvent.RunCommand("/vq town concord claim")))
                : Component.translatable("message.village-quest.guild_town.concord.gate",
                        GuildTownProgress.completedStoryCount(data), GuildTownProgress.completedCommissionCount(data),
                        TradeRouteService.routeCount(world, player.getUUID())).withStyle(ChatFormatting.GRAY));
        return List.copyOf(lines);
    }

    static Component pendingClaimsLine(int pendingClaims) {
        if (pendingClaims <= 0) return null;
        return Component.translatable("message.village-quest.guild_town.project.pending_claims", pendingClaims)
                .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent.RunCommand("/vq town project claim")));
    }

    public static void onNoticePostUse(ServerLevel world, ServerPlayer player) {
        tryNoticePostUse(world, player);
    }

    /**
     * Handles the local story side of a notice-post interaction and reports
     * whether the post belongs to a known route or historical contact.
     */
    public static boolean tryNoticePostUse(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) return false;
        NoticePostStoryContext interaction = createNoticePostStoryContext(world, player);
        NoticePostStoryResolution resolution = resolveNoticePostStory(interaction);
        return presentNoticePostStory(player, interaction, resolution);
    }

    public static NoticePostStoryContext createNoticePostStoryContext(
            ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) return null;
        PlayerQuestData data = data(world, player.getUUID());
        ShadowsTradeRoadEncounterService.VillageMarker marker =
                ShadowsTradeRoadEncounterService.currentVillage(world, player.blockPosition());
        if (marker == null) return null;
        boolean registeredDestination = TradeRouteService.isRegisteredDestination(
                world, player.getUUID(), marker.centerX(), marker.centerZ());
        VillageBondService.VillageBondView route = registeredDestination
                ? VillageBondService.inspectCurrentVillage(world, player, false) : null;
        return new NoticePostStoryContext(data, marker, registeredDestination, route);
    }

    public static boolean presentNoticePostStory(
            ServerPlayer player,
            NoticePostStoryContext interaction,
            NoticePostStoryResolution resolution) {
        if (player == null || interaction == null || resolution == null) return false;
        PlayerQuestData data = interaction.data();
        GuildTownStory story = resolution.story();
        switch (resolution.state()) {
            case DORMANT -> player.sendSystemMessage(
                    Component.translatable("message.village-quest.guild_town.dormant.welcome")
                            .withStyle(ChatFormatting.GRAY), false);
            case REMEMBERED -> player.sendSystemMessage(
                    Component.translatable("message.village-quest.guild_town.story.remembered", story.title())
                            .withStyle(ChatFormatting.DARK_GREEN), false);
            case AVAILABLE -> player.sendSystemMessage(
                    Component.translatable("message.village-quest.guild_town.story.available", story.title())
                            .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                                    .withClickEvent(new ClickEvent.RunCommand("/vq town story accept"))), false);
            case ACTIVE -> player.sendSystemMessage(
                    storyProgressLine(data, storyById(GuildTownProgress.activeStoryId(data))), false);
        }
        return true;
    }

    public static NoticePostStoryResolution resolveNoticePostStory(
            NoticePostStoryContext interaction) {
        if (interaction == null) return null;
        return resolveNoticePostStory(
                interaction.data(), interaction.marker(), interaction.registeredDestination(),
                interaction.connectedRoute());
    }

    static NoticePostStoryResolution resolveNoticePostStory(
            PlayerQuestData data,
            ShadowsTradeRoadEncounterService.VillageMarker marker,
            boolean registeredDestination,
            VillageBondService.VillageBondView connectedRoute) {
        GuildTownStoryVillageResolver.StoryVillage village =
                GuildTownStoryVillageResolver.current(
                        data, marker, registeredDestination, connectedRoute);
        if (village == null) return null;
        GuildTownStory story = GuildTownStory.forVillage(village.type());
        return new NoticePostStoryResolution(
                village, story, noticePostStoryState(data, story));
    }

    static NoticePostStoryState noticePostStoryState(PlayerQuestData data, GuildTownStory story) {
        if (!VillageWelcomeService.isCompleted(data)) return NoticePostStoryState.DORMANT;
        if (GuildTownProgress.storyCompleted(data, story)) return NoticePostStoryState.REMEMBERED;
        return GuildTownProgress.activeStoryId(data) < 0
                ? NoticePostStoryState.AVAILABLE : NoticePostStoryState.ACTIVE;
    }

    public record NoticePostStoryContext(
            PlayerQuestData data,
            ShadowsTradeRoadEncounterService.VillageMarker marker,
            boolean registeredDestination,
            VillageBondService.VillageBondView connectedRoute) {}

    public record NoticePostStoryResolution(GuildTownStoryVillageResolver.StoryVillage village,
                                            GuildTownStory story,
                                            NoticePostStoryState state) {
        public int villageIndex() {
            return village.index();
        }

        public int villageX() {
            return village.x();
        }

        public int villageZ() {
            return village.z();
        }

        public boolean connectedRoute() {
            return village.connectedRoute();
        }
    }

    /** Read-only presentation data for the Notice Post; gameplay remains in the existing story methods. */
    public static NoticePostUiState noticePostUiState(PlayerQuestData data, NoticePostStoryResolution resolution) {
        GuildTownStory story = resolution.story();
        if (resolution.state() != NoticePostStoryState.ACTIVE) {
            return new NoticePostUiState(story.title(), Component.empty(), resolution.state().name(),
                    0, 0, 0, 0, ItemStack.EMPTY, 0, false);
        }
        GuildTownStory active = storyById(GuildTownProgress.activeStoryId(data));
        if (active == null) active = story;
        int firstTarget = switch (active) {
            case SHARED_TABLE -> GRANARY_TARGET;
            case SPARKS_FOR_THE_ROAD -> FORGE_TARGET;
            case LONG_DRIVE -> PASTURE_ESCORT_SELECTION_TARGET;
            case LANTERNS_IN_BLOOM -> APIARY_HONEY_TARGET;
            case INK_BETWEEN_VILLAGES -> ARCHIVE_TRADE_TARGET;
        };
        int secondTarget = switch (active) {
            case SHARED_TABLE -> 1;
            case SPARKS_FOR_THE_ROAD -> 6;
            case LONG_DRIVE -> PASTURE_ESCORT_DISTANCE_TARGET;
            case LANTERNS_IN_BLOOM -> APIARY_LIGHT_TARGET;
            case INK_BETWEEN_VILLAGES -> ARCHIVE_SUPPLY_TARGET;
        };
        int state = GuildTownProgress.storyState(data, active);
        Delivery delivery = state == GuildTownProgress.READY ? storyDelivery(data, active) : null;
        return new NoticePostUiState(active.title(), storyProgressLine(data, active),
                state == GuildTownProgress.READY ? "READY" : state == GuildTownProgress.PAUSED ? "PAUSED" : "ACTIVE",
                GuildTownProgress.storyProgress(data, active, 1), firstTarget,
                GuildTownProgress.storyProgress(data, active, 2), secondTarget,
                delivery == null ? ItemStack.EMPTY : new ItemStack(delivery.item()),
                delivery == null ? 0 : delivery.amount(), active == GuildTownStory.SHARED_TABLE);
    }

    public record NoticePostUiState(Component title, Component detail, String state,
                                    int first, int firstTarget, int second, int secondTarget,
                                    ItemStack delivery, int deliveryCount, boolean sharedTable) {}

    public static void onQuestmasterOpen(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null || !VillageWelcomeService.isCompleted(data(world, player.getUUID()))) return;
        player.sendSystemMessage(Component.translatable("message.village-quest.guild_town.questmaster_hint")
                .withStyle(style -> style.withColor(ChatFormatting.GRAY)
                        .withClickEvent(new ClickEvent.RunCommand("/vq town status"))), false);
    }

    public static int acceptCurrentStory(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) return 0;
        PlayerQuestData data = data(world, player.getUUID());
        GuildTownStoryVillageResolver.StoryVillage village =
                GuildTownStoryVillageResolver.current(world, player, data);
        if (village == null || !VillageWelcomeService.isCompleted(data)) {
            return fail(player, "message.village-quest.guild_town.story.no_contact");
        }
        GuildTownStory story = GuildTownStory.forVillage(village.type());
        int variant = village.needsRecovery() ? GuildTownProgress.RECOVERY : GuildTownProgress.PREVENTIVE;
        if (!GuildTownProgress.beginStory(data, story, village.index(), variant)) {
            return fail(player, "message.village-quest.guild_town.story.accept_failed");
        }
        QuestState.get(world.getServer()).setDirty();
        GuildTownProgress.addPersonalChronicle(data, identity(player.getUUID(), village),
                "story.accepted." + story.key(), false, world.getGameTime());
        player.sendSystemMessage(Component.translatable("message.village-quest.guild_town.story.accepted",
                story.title(), Component.translatable("quest.village-quest.guild_town.variant."
                        + (variant == GuildTownProgress.RECOVERY ? "recovery" : "preventive")))
                .withStyle(ChatFormatting.GREEN), false);
        refresh(world, player);
        return 1;
    }

    public static int chooseSharedTable(ServerLevel world, ServerPlayer player, String choice) {
        PlayerQuestData data = data(world, player.getUUID());
        GuildTownStory story = GuildTownStory.SHARED_TABLE;
        if (GuildTownProgress.activeStoryId(data) != story.id()
                || GuildTownProgress.storyState(data, story) != GuildTownProgress.ACTIVE
                || !isNearStoryVillage(world, player, data)
                || GuildTownProgress.storyProgress(data, story, 1) < GRANARY_TARGET) {
            return fail(player, "message.village-quest.guild_town.story.choice_locked");
        }
        int selected = "reserve".equalsIgnoreCase(choice) ? 1 : "share".equalsIgnoreCase(choice) ? 2 : 0;
        if (selected == 0) return fail(player, "message.village-quest.guild_town.story.choice_invalid");
        data.setStoryInt("guild_town.story.shared_table.p2", selected);
        GuildTownProgress.markStoryReady(data, story);
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.guild_town.story.choice_saved",
                Component.translatable("quest.village-quest.guild_town.story.shared_table.choice." + choice.toLowerCase()))
                .withStyle(ChatFormatting.AQUA), false);
        refresh(world, player);
        return 1;
    }

    public static int deliverStory(ServerLevel world, ServerPlayer player) {
        PlayerQuestData data = data(world, player.getUUID());
        GuildTownStory story = storyById(GuildTownProgress.activeStoryId(data));
        if (story == null || GuildTownProgress.storyState(data, story) != GuildTownProgress.READY) {
            return fail(player, "message.village-quest.guild_town.story.not_ready");
        }
        if (!isNearStoryVillage(world, player, data)) {
            return fail(player, "message.village-quest.guild_town.story.return_to_village");
        }
        GuildTownStoryVillageResolver.StoryVillage activeStoryVillage =
                GuildTownStoryVillageResolver.byIndex(world, player.getUUID(), data,
                        GuildTownProgress.activeStoryVillage(data));
        if (activeStoryVillage == null) {
            return fail(player, "message.village-quest.guild_town.story.return_to_village");
        }
        // Capture the already validated owner-qualified identity before completeStory clears
        // active_story_village. Never reconstruct it from optional legacy story metadata.
        GuildTownProgress.VillageIdentity chronicleVillage = identity(player.getUUID(), activeStoryVillage);
        Delivery delivery = storyDelivery(data, story);
        if (!consumeAtomic(player, delivery.item(), delivery.amount())) {
            return missing(player, delivery.item(), delivery.amount());
        }
        if (!completeStoryWithChronicle(data, story, chronicleVillage, world.getGameTime())) {
            return fail(player, "message.village-quest.guild_town.story.complete_failed");
        }
        giveStoryMemory(player, data, story);
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.guild_town.story.completed", story.title())
                .withStyle(ChatFormatting.GREEN), false);
        refresh(world, player);
        return 1;
    }

    public static int pauseStory(ServerLevel world, ServerPlayer player, boolean paused) {
        PlayerQuestData data = data(world, player.getUUID());
        if (!GuildTownProgress.setStoryPaused(data, paused)) {
            return fail(player, "message.village-quest.guild_town.story.pause_failed");
        }
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable(paused
                ? "message.village-quest.guild_town.story.paused"
                : "message.village-quest.guild_town.story.resumed").withStyle(ChatFormatting.GRAY), false);
        refresh(world, player);
        return 1;
    }

    public static int recoverLongDrive(ServerLevel world, ServerPlayer player) {
        PlayerQuestData data = data(world, player.getUUID());
        if (!GuildTownProgress.recoverLongDriveEscort(data)) {
            return fail(player, "message.village-quest.guild_town.story.long_drive.recover_failed");
        }
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.guild_town.story.long_drive.recovered")
                .withStyle(ChatFormatting.YELLOW), false);
        refresh(world, player);
        return 1;
    }

    public static int acceptCommission(ServerLevel world, ServerPlayer player, GuildTownCommission commission,
                                       UUID networkOwner) {
        return acceptCommission(world, player, commission, networkOwner, null);
    }

    public static int acceptCommission(ServerLevel world, ServerPlayer player, GuildTownCommission commission,
                                       UUID networkOwner, Component networkOwnerName) {
        if (world == null || player == null || commission == null) return 0;
        if (world != world.getServer().overworld()) return fail(player, "message.village-quest.guild_town.commission.return_to_pair");
        UUID owner = networkOwner == null ? player.getUUID() : networkOwner;
        if (!canUseNetwork(world, player.getUUID(), owner)) return fail(player, "message.village-quest.guild_town.commission.owner_denied");
        VillagePair pair = findConnectedPair(world, owner, commission);
        if (pair == null) return fail(player, "message.village-quest.guild_town.commission.routes_missing");
        PlayerQuestData data = data(world, player.getUUID());
        if (!GuildTownProgress.storyCompleted(data, GuildTownStory.forVillage(commission.first()))
                || !GuildTownProgress.storyCompleted(data, GuildTownStory.forVillage(commission.second()))) {
            return fail(player, "message.village-quest.guild_town.commission.stories_missing");
        }
        if (!GuildTownProgress.beginCommission(data, commission,
                identity(owner, pair.first()), pair.first().index(),
                identity(owner, pair.second()), pair.second().index())) {
            return fail(player, "message.village-quest.guild_town.commission.accept_failed");
        }
        QuestState.get(world.getServer()).setDirty();
        Component ownerLabel = owner.equals(player.getUUID()) ? player.getName()
                : networkOwnerName == null ? Component.literal(owner.toString()) : networkOwnerName;
        player.sendSystemMessage(Component.translatable("message.village-quest.guild_town.commission.accepted",
                commission.title(), ownerLabel)
                .withStyle(ChatFormatting.GREEN), false);
        refresh(world, player);
        return 1;
    }

    public static int pauseCommission(ServerLevel world, ServerPlayer player, boolean paused) {
        PlayerQuestData data = data(world, player.getUUID());
        if (!paused && GuildTownProgress.activeCommission(data) != null
                && !hasCommissionAccess(world, player.getUUID(), data)) {
            return fail(player, "message.village-quest.guild_town.commission.owner_denied");
        }
        if (!paused && GuildTownProgress.activeCommission(data) != null
                && !ensureCommissionIdentities(world, data)) {
            QuestState.get(world.getServer()).setDirty();
            return fail(player, "message.village-quest.guild_town.commission.return_to_pair");
        }
        if (!GuildTownProgress.setCommissionPaused(data, paused)) return fail(player, "message.village-quest.guild_town.commission.pause_failed");
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable(paused
                ? "message.village-quest.guild_town.commission.paused"
                : "message.village-quest.guild_town.commission.resumed").withStyle(ChatFormatting.GRAY), false);
        refresh(world, player);
        return 1;
    }

    public static int abandonCommission(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) return 0;
        PlayerQuestData data = data(world, player.getUUID());
        GuildTownCommission commission = GuildTownProgress.activeCommission(data);
        if (commission == null) return fail(player, "message.village-quest.guild_town.commission.none");
        if (!GuildTownProgress.abandonCommission(data)) {
            return fail(player, "message.village-quest.guild_town.commission.abandon_failed");
        }
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable(
                "message.village-quest.guild_town.commission.abandoned", commission.title()
        ).withStyle(ChatFormatting.YELLOW), false);
        refresh(world, player);
        return 1;
    }

    public static int deliverCommission(ServerLevel world, ServerPlayer player) {
        if (world != world.getServer().overworld()) return fail(player, "message.village-quest.guild_town.commission.return_to_pair");
        PlayerQuestData data = data(world, player.getUUID());
        GuildTownCommission commission = GuildTownProgress.activeCommission(data);
        if (commission == null || GuildTownProgress.commissionState(data, commission) != GuildTownProgress.READY) {
            return fail(player, "message.village-quest.guild_town.commission.not_ready");
        }
        if (!hasCommissionAccess(world, player.getUUID(), data)) {
            QuestState.get(world.getServer()).setDirty();
            return fail(player, "message.village-quest.guild_town.commission.owner_denied");
        }
        if (!ensureCommissionIdentities(world, data)) {
            QuestState.get(world.getServer()).setDirty();
            return fail(player, "message.village-quest.guild_town.commission.return_to_pair");
        }
        if (!isNearCommissionPair(player, data)) {
            return fail(player, "message.village-quest.guild_town.commission.return_to_pair");
        }
        if (!consumeAtomic(player, commission.deliveryItem(), commission.deliveryAmount())) {
            return missing(player, commission.deliveryItem(), commission.deliveryAmount());
        }
        GuildTownProgress.VillageIdentity first = GuildTownProgress.commissionFirstIdentity(data);
        GuildTownProgress.VillageIdentity second = GuildTownProgress.commissionSecondIdentity(data);
        if (!GuildTownProgress.completeCommission(data, commission)) {
            return fail(player, "message.village-quest.guild_town.commission.complete_failed");
        }
        if (first != null) GuildTownProgress.addPersonalChronicle(data, first,
                "commission.completed." + commission.key(), true, world.getGameTime());
        if (second != null) GuildTownProgress.addPersonalChronicle(data, second,
                "commission.completed." + commission.key(), true, world.getGameTime());
        giveCommissionSeal(player, data, commission);
        grantMilestoneRewards(player, data);
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.guild_town.commission.completed",
                commission.title()).withStyle(ChatFormatting.GREEN), false);
        refresh(world, player);
        return 1;
    }

    public static int acceptSharedProject(ServerLevel world, ServerPlayer player, GuildTownSharedProject project) {
        VillageGuildState.GuildSnapshot guild = VillageGuildService.guild(world, player.getUUID());
        if (guild == null || !guild.role(player.getUUID()).canChooseProject()) return fail(player, "message.village-quest.guild_town.project.denied");
        if (!GuildTownSharedState.get(world.getServer()).accept(guild.id(), project)) return fail(player, "message.village-quest.guild_town.project.accept_failed");
        player.sendSystemMessage(Component.translatable("message.village-quest.guild_town.project.accepted", project.title())
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    public static int contributeSharedProject(ServerLevel world, ServerPlayer player) {
        VillageGuildState.GuildSnapshot guild = VillageGuildService.guild(world, player.getUUID());
        GuildTownSharedState state = GuildTownSharedState.get(world.getServer());
        GuildTownSharedState.ProjectSnapshot project = guild == null ? null : state.project(guild.id()).orElse(null);
        if (project == null || project.completed()) return fail(player, "message.village-quest.guild_town.project.none");
        int remaining = project.type().target() - project.progress();
        int available = count(player, project.type().item());
        int amount = Math.min(remaining, available);
        if (amount <= 0) return missing(player, project.type().item(), Math.min(remaining, 1));
        if (!consumeAtomic(player, project.type().item(), amount)) return 0;
        GuildTownSharedState.ContributionResult result = state.contribute(guild.id(), player.getUUID(), UUID.randomUUID(), amount);
        if (result.applied() != amount) {
            give(player, new ItemStack(project.type().item(), amount));
            return fail(player, "message.village-quest.guild_town.project.changed");
        }
        player.sendSystemMessage(Component.translatable("message.village-quest.guild_town.project.contributed",
                amount, result.project().progress(), result.project().type().target()).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    public static int claimSharedProject(ServerLevel world, ServerPlayer player) {
        VillageGuildState.GuildSnapshot guild = VillageGuildService.guild(world, player.getUUID());
        GuildTownSharedState state = GuildTownSharedState.get(world.getServer());
        GuildTownSharedState.ClaimResult claim = state.claimForParticipant(
                player.getUUID(), guild == null ? null : guild.id());
        if (claim == null) return fail(player, "message.village-quest.guild_town.project.claim_failed");
        giveNamed(player, vanillaItem("light_blue_banner", Items.SHIELD), "item.village-quest.guild_project_standard",
                "item.village-quest.guild_project_standard.lore", claim.type().title());
        player.sendSystemMessage(Component.translatable("message.village-quest.guild_town.project.claimed")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    public static boolean finaleEligible(ServerLevel world, UUID playerId) {
        PlayerQuestData data = data(world, playerId);
        return GuildTownProgress.meetsConcordGate(data, TradeRouteService.routeCount(world, playerId));
    }

    public static int claimConcord(ServerLevel world, ServerPlayer player) {
        PlayerQuestData data = data(world, player.getUUID());
        if (!finaleEligible(world, player.getUUID())) return fail(player, "message.village-quest.guild_town.concord.locked");
        if (!GuildTownProgress.markFinaleClaimed(data)) return fail(player, "message.village-quest.guild_town.concord.claimed_already");
        giveNamed(player, ModItems.GUILD_MILESTONE, "item.village-quest.concord_plaque",
                "item.village-quest.concord_plaque.lore", Component.empty());
        giveNamed(player, vanillaItem("white_banner", Items.SHIELD), "item.village-quest.guild_standard",
                "item.village-quest.guild_standard.lore", Component.empty());
        GuildTownProgress.grantReward(data, "concord_title");
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.guild_town.concord.completed")
                .withStyle(ChatFormatting.GOLD), false);
        refresh(world, player);
        return 1;
    }

    public static Component journalNextAction(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) return Component.empty();
        PlayerQuestData data = data(world, playerId);
        GuildTownStory story = storyById(GuildTownProgress.activeStoryId(data));
        if (story != null) return Component.translatable("screen.village-quest.journal.guild_town.story",
                story.title(), storyProgressLine(data, story));
        GuildTownCommission commission = GuildTownProgress.activeCommission(data);
        if (commission != null) return Component.translatable("screen.village-quest.journal.guild_town.commission",
                commission.title(), commissionProgressLine(data, commission));
        if (finaleEligible(world, playerId) && !GuildTownProgress.finaleClaimed(data)) {
            return Component.translatable("screen.village-quest.journal.guild_town.concord");
        }
        if (GuildTownProgress.completedStoryCount(data) == 0) {
            return Component.translatable("screen.village-quest.journal.guild_town.find_story");
        }
        if (TradeRouteService.routeCount(world, playerId) < 2) {
            return Component.translatable("screen.village-quest.journal.guild_town.connect_villages");
        }
        return Component.translatable("screen.village-quest.journal.guild_town.find_commission");
    }

    public static void onTrackedItemPickup(ServerLevel world, ServerPlayer player, ItemStack stack, int count) {
        if (world == null || player == null || stack == null || stack.isEmpty() || count <= 0) return;
        PlayerQuestData data = data(world, player.getUUID());
        GuildTownStory story = storyById(GuildTownProgress.activeStoryId(data));
        if (story != null && !isNearStoryVillage(world, player, data)) story = null;
        if (story == GuildTownStory.SHARED_TABLE && isHarvest(stack)) addStory(world, player, data, story, 1, count, GRANARY_TARGET);
        if (story == GuildTownStory.INK_BETWEEN_VILLAGES && isArchiveSupply(stack)) addStory(world, player, data, story, 2, count, ARCHIVE_SUPPLY_TARGET);
        progressCommission(world, player, GuildTownCommission.Mechanic.HARVEST, isHarvest(stack) ? count : 0);
    }

    public static void onFurnaceOutput(ServerLevel world, ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isMetal(stack)) return;
        PlayerQuestData data = data(world, player.getUUID());
        GuildTownStory story = storyById(GuildTownProgress.activeStoryId(data));
        if (story != null && !isNearStoryVillage(world, player, data)) story = null;
        if (story == GuildTownStory.SPARKS_FOR_THE_ROAD) addStory(world, player, data, story, 1, stack.getCount(), FORGE_TARGET);
        progressCommission(world, player, GuildTownCommission.Mechanic.SMELT, stack.getCount());
    }

    public static void onAnimalLove(ServerLevel world, ServerPlayer player, Animal animal) {
        PlayerQuestData data = data(world, player.getUUID());
        progressCommission(world, player, GuildTownCommission.Mechanic.BREED, 1);
    }

    public static void onHoneyHarvest(ServerLevel world, ServerPlayer player) {
        PlayerQuestData data = data(world, player.getUUID());
        GuildTownStory story = storyById(GuildTownProgress.activeStoryId(data));
        if (story != null && !isNearStoryVillage(world, player, data)) story = null;
        if (story == GuildTownStory.LANTERNS_IN_BLOOM) addStory(world, player, data, story, 1, 1, APIARY_HONEY_TARGET);
        progressCommission(world, player, GuildTownCommission.Mechanic.HONEY, 1);
    }

    public static void onVillagerTrade(ServerLevel world, ServerPlayer player) {
        PlayerQuestData data = data(world, player.getUUID());
        GuildTownStory story = storyById(GuildTownProgress.activeStoryId(data));
        if (story != null && !isNearStoryVillage(world, player, data)) story = null;
        if (story == GuildTownStory.INK_BETWEEN_VILLAGES) addStory(world, player, data, story, 1, 1, ARCHIVE_TRADE_TARGET);
        progressCommission(world, player, GuildTownCommission.Mechanic.TRADE, 1);
    }

    public static void onUseEntity(ServerLevel world, ServerPlayer player, Entity entity, ItemStack held) {
        if (!(entity instanceof Animal animal)) return;
        PlayerQuestData data = data(world, player.getUUID());
        GuildTownStory story = storyById(GuildTownProgress.activeStoryId(data));
        if (story != null && !isNearStoryVillage(world, player, data)) story = null;
        if (story == GuildTownStory.LONG_DRIVE && isNearLongDriveStart(world, player.getUUID(), data, animal.blockPosition())
                && GuildTownProgress.registerLongDriveAnimal(data, animal.getUUID(), animal.blockPosition(),
                player.blockPosition(), world.getGameTime())) {
            QuestState.get(world.getServer()).setDirty();
            refresh(world, player);
        }
        GuildTownCommission activeCommission = GuildTownProgress.activeCommission(data);
        if (activeCommission != null && !hasCommissionAccess(world, player.getUUID(), data)) {
            QuestState.get(world.getServer()).setDirty();
            activeCommission = null;
        }
        if (activeCommission != null && !ensureCommissionIdentities(world, data)) {
            QuestState.get(world.getServer()).setDirty();
            activeCommission = null;
        }
        if (activeCommission != null && (GuildTownProgress.commissionState(data, activeCommission) != GuildTownProgress.ACTIVE
                || (activeCommission.firstMechanic() != GuildTownCommission.Mechanic.ANIMAL_CONTACT
                && activeCommission.secondMechanic() != GuildTownCommission.Mechanic.ANIMAL_CONTACT)
                || !hasCommissionAccess(world, player.getUUID(), data)
                || !isNearCommissionPair(player, data))) {
            activeCommission = null;
        }
        if (activeCommission == null) return;
        String seen = "guild_town.animal_seen.commission." + activeCommission.id() + "." + entity.getUUID();
        if (data.hasTradeRouteFlag(seen)) return;
        data.setTradeRouteFlag(seen, true);
        progressCommission(world, player, GuildTownCommission.Mechanic.ANIMAL_CONTACT, 1);
    }

    public static void onServerTick(MinecraftServer server) {
        if (server == null || server.getTickCount() % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel world)) continue;
            if (world != server.overworld()) continue;
            PlayerQuestData data = data(world, player.getUUID());
            if (GuildTownProgress.activeStoryId(data) != GuildTownStory.LONG_DRIVE.id()
                    || GuildTownProgress.storyState(data, GuildTownStory.LONG_DRIVE) != GuildTownProgress.ACTIVE) continue;
            boolean changed = false;
            for (UUID animalId : GuildTownProgress.longDriveAnimals(data)) {
                Entity loaded = world.getEntity(animalId);
                if (!(loaded instanceof Animal animal)) continue;
                boolean playerNear = animal.distanceToSqr(player)
                        <= (double) PASTURE_ESCORT_PLAYER_RADIUS * PASTURE_ESCORT_PLAYER_RADIUS;
                changed |= GuildTownProgress.trackLongDriveEscort(data, animalId, animal.blockPosition(),
                        player.blockPosition(), world.getGameTime(), playerNear).changed();
            }
            if (!changed) continue;
            maybeReadyStory(data, GuildTownStory.LONG_DRIVE);
            QuestState.get(server).setDirty();
            refresh(world, player);
        }
    }

    public static void handleDisconnect(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) return;
        PlayerQuestData data = data(world, playerId);
        if (GuildTownProgress.suspendLongDriveObservation(data)) QuestState.get(world.getServer()).setDirty();
    }

    public static void onPlaceBlock(ServerLevel world, ServerPlayer player, BlockPos pos, ItemStack stack) {
        if (world == null || player == null || stack == null || stack.isEmpty()) return;
        PlayerQuestData data = data(world, player.getUUID());
        GuildTownStory story = storyById(GuildTownProgress.activeStoryId(data));
        if (story != null && !isNearStoryVillage(world, player, data)) story = null;
        if (story == GuildTownStory.SPARKS_FOR_THE_ROAD && stack.is(Items.RAIL)) addStory(world, player, data, story, 2, 1, 6);
        if (story == GuildTownStory.LANTERNS_IN_BLOOM && (stack.is(Items.LANTERN) || stack.is(Items.CANDLE))) {
            addStory(world, player, data, story, 2, 1, APIARY_LIGHT_TARGET);
        }
        int plant = isSapling(stack) || isFlower(stack) ? 1 : 0;
        progressCommission(world, player, GuildTownCommission.Mechanic.PLANT, plant);
    }

    public static void onRouteArrival(ServerLevel world, UUID ownerId, int destinationX, int destinationZ) {
        if (world == null || ownerId == null) return;
        for (var entry : QuestState.get(world.getServer()).getPlayersView().entrySet()) {
            PlayerQuestData progress = entry.getValue();
            GuildTownCommission commission = GuildTownProgress.activeCommission(progress);
            if (commission == null) continue;
            UUID commissionOwner = GuildTownProgress.commissionOwner(progress);
            if (!ownerId.equals(commissionOwner)) continue;
            if (!hasCommissionAccess(world, entry.getKey(), progress)) continue;
            if (!ensureCommissionIdentities(world, progress)) continue;
            if (commission.secondMechanic() != GuildTownCommission.Mechanic.ROUTE_ARRIVAL
                    || !GuildTownProgress.routeBelongsToCommission(progress, ownerId, destinationX, destinationZ)) continue;
            GuildTownProgress.addCommissionProgress(progress, commission, 2, 1, commission.secondTarget());
            if (GuildTownProgress.commissionProgress(progress, commission, 1) >= commission.firstTarget()
                    && GuildTownProgress.commissionProgress(progress, commission, 2) >= commission.secondTarget()) {
                GuildTownProgress.markCommissionReady(progress, commission);
            }
            ServerPlayer online = world.getServer().getPlayerList().getPlayer(entry.getKey());
            if (online != null) refresh(world, online);
        }
        QuestState.get(world.getServer()).setDirty();
    }

    private static void addStory(ServerLevel world, ServerPlayer player, PlayerQuestData data,
                                 GuildTownStory story, int part, int amount, int target) {
        int before = GuildTownProgress.storyProgress(data, story, part);
        int now = GuildTownProgress.addStoryProgress(data, story, part, amount, target);
        if (now == before) return;
        maybeReadyStory(data, story);
        QuestState.get(world.getServer()).setDirty();
        refresh(world, player);
    }

    private static void maybeReadyStory(PlayerQuestData data, GuildTownStory story) {
        boolean ready = switch (story) {
            case SHARED_TABLE -> false; // the reserve/share decision is an explicit story beat
            case SPARKS_FOR_THE_ROAD -> GuildTownProgress.storyProgress(data, story, 1) >= FORGE_TARGET
                    && GuildTownProgress.storyProgress(data, story, 2) >= 6;
            case LONG_DRIVE -> GuildTownProgress.storyProgress(data, story, 1) >= PASTURE_ESCORT_SELECTION_TARGET
                    && GuildTownProgress.storyProgress(data, story, 2) >= PASTURE_ESCORT_DISTANCE_TARGET;
            case LANTERNS_IN_BLOOM -> GuildTownProgress.storyProgress(data, story, 1) >= APIARY_HONEY_TARGET
                    && GuildTownProgress.storyProgress(data, story, 2) >= APIARY_LIGHT_TARGET;
            case INK_BETWEEN_VILLAGES -> GuildTownProgress.storyProgress(data, story, 1) >= ARCHIVE_TRADE_TARGET
                    && GuildTownProgress.storyProgress(data, story, 2) >= ARCHIVE_SUPPLY_TARGET;
        };
        if (ready) GuildTownProgress.markStoryReady(data, story);
    }

    private static void progressCommission(ServerLevel world, ServerPlayer player,
                                           GuildTownCommission.Mechanic mechanic, int amount) {
        if (amount <= 0 || world != world.getServer().overworld()) return;
        PlayerQuestData data = data(world, player.getUUID());
        GuildTownCommission commission = GuildTownProgress.activeCommission(data);
        if (commission == null || GuildTownProgress.commissionState(data, commission) != GuildTownProgress.ACTIVE) return;
        if (!hasCommissionAccess(world, player.getUUID(), data)) {
            QuestState.get(world.getServer()).setDirty();
            return;
        }
        UUID owner = GuildTownProgress.commissionOwner(data);
        List<VillageBondService.VillageBondView> villages = owner == null
                ? List.of() : VillageBondService.villages(world, owner);
        CommissionProgressResult result = revalidateAndProgressCommission(
                data, mechanic, amount, ignored -> isNearCommissionPair(player, data), villages,
                (x, z) -> owner != null
                        && TradeRouteService.isRegisteredDestination(world, owner, x, z));
        if (result == CommissionProgressResult.PAUSED) {
            QuestState.get(world.getServer()).setDirty();
            return;
        }
        if (result != CommissionProgressResult.PROGRESSED) return;
        QuestState.get(world.getServer()).setDirty();
        refresh(world, player);
    }

    static CommissionProgressResult revalidateAndProgressCommission(
            PlayerQuestData data, GuildTownCommission.Mechanic mechanic, int amount,
            Predicate<PlayerQuestData> nearCommissionPair,
            List<VillageBondService.VillageBondView> villages,
            BiPredicate<Integer, Integer> registeredDestination) {
        if (data == null) return CommissionProgressResult.NONE;
        GuildTownCommission commission = GuildTownProgress.activeCommission(data);
        if (amount <= 0 || commission == null
                || GuildTownProgress.commissionState(data, commission) != GuildTownProgress.ACTIVE) {
            return CommissionProgressResult.NONE;
        }
        if (!ensureCommissionIdentities(data, villages, registeredDestination)) {
            return CommissionProgressResult.PAUSED;
        }
        if (nearCommissionPair == null || !nearCommissionPair.test(data)
                || commission.firstMechanic() != mechanic && commission.secondMechanic() != mechanic) {
            return CommissionProgressResult.NONE;
        }
        if (commission.firstMechanic() == mechanic) {
            GuildTownProgress.addCommissionProgress(data, commission, 1, amount, commission.firstTarget());
        }
        if (commission.secondMechanic() == mechanic) {
            GuildTownProgress.addCommissionProgress(data, commission, 2, amount, commission.secondTarget());
        }
        if (GuildTownProgress.commissionProgress(data, commission, 1) >= commission.firstTarget()
                && GuildTownProgress.commissionProgress(data, commission, 2) >= commission.secondTarget()) {
            GuildTownProgress.markCommissionReady(data, commission);
        }
        return CommissionProgressResult.PROGRESSED;
    }

    private static VillagePair findConnectedPair(ServerLevel world, UUID owner, GuildTownCommission commission) {
        VillageBondService.VillageBondView first = null;
        VillageBondService.VillageBondView second = null;
        for (VillageBondService.VillageBondView village : VillageBondService.villages(world, owner)) {
            if (!TradeRouteService.isRegisteredDestination(world, owner, village.x(), village.z())) continue;
            if (village.type() == commission.first() && first == null) first = village;
            if (village.type() == commission.second() && second == null) second = village;
        }
        return first != null && second != null ? new VillagePair(first, second) : null;
    }

    private static boolean canUseNetwork(ServerLevel world, UUID actor, UUID owner) {
        if (actor == null || owner == null) return false;
        if (actor.equals(owner)) return true;
        VillageGuildState.GuildSnapshot actorGuild = VillageGuildService.guild(world, actor);
        VillageGuildState.GuildSnapshot ownerGuild = VillageGuildService.guild(world, owner);
        return networkAccessAllowed(actor, owner, actorGuild == null ? null : actorGuild.id(),
                ownerGuild == null ? null : ownerGuild.id());
    }

    static boolean networkAccessAllowed(UUID actor, UUID owner, UUID actorGuild, UUID ownerGuild) {
        return actor != null && owner != null && (actor.equals(owner)
                || actorGuild != null && actorGuild.equals(ownerGuild));
    }

    private static boolean hasCommissionAccess(ServerLevel world, UUID actor, PlayerQuestData data) {
        UUID owner = GuildTownProgress.commissionOwner(data);
        VillageGuildState.GuildSnapshot actorGuild = actor == null ? null : VillageGuildService.guild(world, actor);
        VillageGuildState.GuildSnapshot ownerGuild = owner == null ? null : VillageGuildService.guild(world, owner);
        return revalidateCommissionAccess(data, actor, owner,
                actorGuild == null ? null : actorGuild.id(), ownerGuild == null ? null : ownerGuild.id());
    }

    static boolean revalidateCommissionAccess(PlayerQuestData data, UUID actor, UUID owner,
                                              UUID actorGuild, UUID ownerGuild) {
        if (networkAccessAllowed(actor, owner, actorGuild, ownerGuild)) return true;
        GuildTownCommission commission = GuildTownProgress.activeCommission(data);
        if (commission != null && GuildTownProgress.commissionState(data, commission) == GuildTownProgress.ACTIVE) {
            GuildTownProgress.setCommissionPaused(data, true);
        }
        return false;
    }

    private static boolean isNearStoryVillage(ServerLevel world, ServerPlayer player, PlayerQuestData data) {
        if (world == null || world != world.getServer().overworld()) return false;
        int target = GuildTownProgress.activeStoryVillage(data);
        if (target < 0) return false;
        GuildTownStoryVillageResolver.StoryVillage village =
                GuildTownStoryVillageResolver.byIndex(world, player.getUUID(), data, target);
        if (village == null) return false;
        long dx = (long) player.getBlockX() - village.x();
        long dz = (long) player.getBlockZ() - village.z();
        return dx * dx + dz * dz <= 128L * 128L;
    }

    private static boolean isNearLongDriveStart(ServerLevel world, UUID owner, PlayerQuestData data, BlockPos animalPos) {
        GuildTownStoryVillageResolver.StoryVillage village = GuildTownStoryVillageResolver.byIndex(
                world, owner, data, GuildTownProgress.activeStoryVillage(data));
        if (village == null || animalPos == null) return false;
        long dx = (long) animalPos.getX() - village.x();
        long dz = (long) animalPos.getZ() - village.z();
        return dx * dx + dz * dz <= (long) PASTURE_ESCORT_START_RADIUS * PASTURE_ESCORT_START_RADIUS;
    }

    private static boolean isNearCommissionPair(ServerPlayer player, PlayerQuestData data) {
        GuildTownProgress.VillageIdentity first = GuildTownProgress.commissionFirstIdentity(data);
        GuildTownProgress.VillageIdentity second = GuildTownProgress.commissionSecondIdentity(data);
        return isNear(player, first, 128) || isNear(player, second, 128);
    }

    private static boolean ensureCommissionIdentities(ServerLevel world, PlayerQuestData data) {
        GuildTownCommission commission = GuildTownProgress.activeCommission(data);
        UUID owner = GuildTownProgress.commissionOwner(data);
        if (world == null || commission == null || owner == null) return pauseInvalidCommission(data);
        return ensureCommissionIdentities(data, VillageBondService.villages(world, owner),
                (x, z) -> TradeRouteService.isRegisteredDestination(world, owner, x, z));
    }

    static boolean ensureCommissionIdentities(
            PlayerQuestData data, List<VillageBondService.VillageBondView> villages,
            BiPredicate<Integer, Integer> registeredDestination) {
        if (data == null) return false;
        GuildTownCommission commission = GuildTownProgress.activeCommission(data);
        UUID owner = GuildTownProgress.commissionOwner(data);
        if (commission == null || owner == null
                || villages == null || registeredDestination == null) {
            return pauseInvalidCommission(data);
        }
        GuildTownProgress.VillageIdentity storedFirst = GuildTownProgress.commissionFirstIdentity(data);
        GuildTownProgress.VillageIdentity storedSecond = GuildTownProgress.commissionSecondIdentity(data);
        VillageBondService.VillageBondView firstByIdentity = findVillageByIdentity(villages, owner, storedFirst);
        VillageBondService.VillageBondView secondByIdentity = findVillageByIdentity(villages, owner, storedSecond);
        if (validCommissionPair(commission, firstByIdentity, secondByIdentity, registeredDestination)) return true;

        // Missing or malformed .7 identity data may recover only from the real legacy indices
        // recorded when the commission was accepted. Never synthesize index 0/1 targets.
        VillageBondService.VillageBondView first = findVillageByIndex(villages,
                GuildTownProgress.commissionFirstVillage(data));
        VillageBondService.VillageBondView second = findVillageByIndex(villages,
                GuildTownProgress.commissionSecondVillage(data));
        if (!validCommissionPair(commission, first, second, registeredDestination)) {
            return pauseInvalidCommission(data);
        }
        return GuildTownProgress.attachCommissionIdentities(data, identity(owner, first), identity(owner, second));
    }

    private static boolean validCommissionPair(GuildTownCommission commission,
                                               VillageBondService.VillageBondView first,
                                               VillageBondService.VillageBondView second,
                                               BiPredicate<Integer, Integer> registeredDestination) {
        return first != null && second != null && first.index() != second.index()
                && first.type() == commission.first() && second.type() == commission.second()
                && registeredDestination.test(first.x(), first.z())
                && registeredDestination.test(second.x(), second.z());
    }

    static boolean pauseInvalidCommission(PlayerQuestData data) {
        GuildTownCommission commission = GuildTownProgress.activeCommission(data);
        if (commission != null && GuildTownProgress.commissionState(data, commission) == GuildTownProgress.ACTIVE) {
            GuildTownProgress.setCommissionPaused(data, true);
        }
        return false;
    }

    static boolean completeStoryWithChronicle(PlayerQuestData data, GuildTownStory story,
                                              GuildTownProgress.VillageIdentity capturedVillage,
                                              long gameTime) {
        if (data == null || story == null || capturedVillage == null
                || !GuildTownProgress.completeStory(data, story)) {
            return false;
        }
        GuildTownProgress.addPersonalChronicle(data, capturedVillage,
                "story.completed." + story.key(), true, gameTime);
        return true;
    }

    private static boolean isNear(ServerPlayer player, GuildTownProgress.VillageIdentity village, int radius) {
        if (player == null || village == null) return false;
        long dx = (long) player.getBlockX() - village.x();
        long dz = (long) player.getBlockZ() - village.z();
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    private static VillageBondService.VillageBondView findVillageByIndex(
            List<VillageBondService.VillageBondView> villages, int index) {
        for (VillageBondService.VillageBondView village : villages) {
            if (village.index() == index) return village;
        }
        return null;
    }

    private static VillageBondService.VillageBondView findVillageByIdentity(
            List<VillageBondService.VillageBondView> villages, UUID owner,
            GuildTownProgress.VillageIdentity identity) {
        if (identity == null || owner == null || !owner.equals(identity.ownerId())) return null;
        for (VillageBondService.VillageBondView village : villages) {
            if (village.x() == identity.x() && village.z() == identity.z()) return village;
        }
        return null;
    }

    private static GuildTownProgress.VillageIdentity identity(UUID owner, VillageBondService.VillageBondView village) {
        return new GuildTownProgress.VillageIdentity(owner, village.x(), village.z());
    }

    private static GuildTownProgress.VillageIdentity identity(
            UUID owner, GuildTownStoryVillageResolver.StoryVillage village) {
        return new GuildTownProgress.VillageIdentity(owner, village.x(), village.z());
    }

    private static Component storyProgressLine(PlayerQuestData data, GuildTownStory story) {
        int state = GuildTownProgress.storyState(data, story);
        if (state == GuildTownProgress.PAUSED) {
            return Component.translatable("message.village-quest.guild_town.story.paused_line", story.title())
                    .withStyle(ChatFormatting.GRAY);
        }
        if (state == GuildTownProgress.READY) {
            Delivery delivery = storyDelivery(data, story);
            return Component.translatable("message.village-quest.guild_town.story.ready", story.title(),
                    delivery.amount(), new ItemStack(delivery.item()).getHoverName()).withStyle(ChatFormatting.GREEN);
        }
        if (story == GuildTownStory.SHARED_TABLE && GuildTownProgress.storyProgress(data, story, 1) >= GRANARY_TARGET) {
            return Component.translatable("message.village-quest.guild_town.story.shared_table.choose")
                    .withStyle(ChatFormatting.AQUA);
        }
        int firstTarget = switch (story) {
            case SHARED_TABLE -> GRANARY_TARGET;
            case SPARKS_FOR_THE_ROAD -> FORGE_TARGET;
            case LONG_DRIVE -> PASTURE_ESCORT_SELECTION_TARGET;
            case LANTERNS_IN_BLOOM -> APIARY_HONEY_TARGET;
            case INK_BETWEEN_VILLAGES -> ARCHIVE_TRADE_TARGET;
        };
        int secondTarget = switch (story) {
            case SHARED_TABLE -> 1;
            case SPARKS_FOR_THE_ROAD -> 6;
            case LONG_DRIVE -> PASTURE_ESCORT_DISTANCE_TARGET;
            case LANTERNS_IN_BLOOM -> APIARY_LIGHT_TARGET;
            case INK_BETWEEN_VILLAGES -> ARCHIVE_SUPPLY_TARGET;
        };
        Component variant = Component.translatable("quest.village-quest.guild_town.variant."
                + (GuildTownProgress.storyVariant(data, story) == GuildTownProgress.RECOVERY
                ? "recovery" : "preventive"));
        return Component.translatable("message.village-quest.guild_town.story." + story.key() + ".progress",
                variant, GuildTownProgress.storyProgress(data, story, 1), firstTarget,
                GuildTownProgress.storyProgress(data, story, 2), secondTarget).withStyle(ChatFormatting.GRAY);
    }

    private static Component commissionProgressLine(PlayerQuestData data, GuildTownCommission commission) {
        int state = GuildTownProgress.commissionState(data, commission);
        if (state == GuildTownProgress.PAUSED) return Component.translatable("message.village-quest.guild_town.commission.paused_line",
                commission.title()).withStyle(ChatFormatting.GRAY);
        if (state == GuildTownProgress.READY) return Component.translatable("message.village-quest.guild_town.commission.ready",
                commission.title(), commission.deliveryAmount(), new ItemStack(commission.deliveryItem()).getHoverName())
                .withStyle(ChatFormatting.GREEN);
        return Component.translatable("message.village-quest.guild_town.commission.progress", commission.title(),
                commission.firstMechanic().label(), GuildTownProgress.commissionProgress(data, commission, 1),
                commission.firstTarget(), commission.secondMechanic().label(),
                GuildTownProgress.commissionProgress(data, commission, 2), commission.secondTarget())
                .withStyle(ChatFormatting.GRAY);
    }

    private static Delivery storyDelivery(PlayerQuestData data, GuildTownStory story) {
        int variant = GuildTownProgress.storyVariant(data, story);
        return switch (story) {
            case SHARED_TABLE -> {
                int choice = GuildTownProgress.storyProgress(data, story, 2);
                yield choice == 1 ? new Delivery(Items.HAY_BLOCK, variant == GuildTownProgress.RECOVERY ? 6 : 4)
                        : new Delivery(Items.BREAD, variant == GuildTownProgress.RECOVERY ? 12 : 8);
            }
            case SPARKS_FOR_THE_ROAD -> new Delivery(Items.RAIL, variant == GuildTownProgress.RECOVERY ? 12 : 8);
            case LONG_DRIVE -> new Delivery(Items.LEAD, 2);
            case LANTERNS_IN_BLOOM -> new Delivery(Items.CANDLE, variant == GuildTownProgress.RECOVERY ? 12 : 8);
            case INK_BETWEEN_VILLAGES -> new Delivery(Items.WRITABLE_BOOK, variant == GuildTownProgress.RECOVERY ? 3 : 2);
        };
    }

    private static void giveStoryMemory(ServerPlayer player, PlayerQuestData data, GuildTownStory story) {
        String reward = "story_memory_" + story.key();
        if (!GuildTownProgress.grantReward(data, reward)) return;
        Item item = switch (story) {
            case SHARED_TABLE -> ModItems.VILLAGE_LEDGER_PLAQUE;
            case SPARKS_FOR_THE_ROAD -> ModItems.FORGE_CHARTER_PLAQUE;
            case LONG_DRIVE -> ModItems.PASTURE_CHARTER_PLAQUE;
            case LANTERNS_IN_BLOOM -> ModItems.APIARY_CHARTER_PLAQUE;
            case INK_BETWEEN_VILLAGES -> ModItems.MARKET_CHARTER_PLAQUE;
        };
        giveNamed(player, item, "item.village-quest.guild_town.memory." + story.key(),
                "item.village-quest.guild_town.memory.lore", story.title());
    }

    private static void giveCommissionSeal(ServerPlayer player, PlayerQuestData data, GuildTownCommission commission) {
        String reward = "commission_seal_" + commission.key();
        if (!GuildTownProgress.grantReward(data, reward)) return;
        String[] patterns = {"flow_banner_pattern", "guster_banner_pattern", "flower_banner_pattern",
                "globe_banner_pattern", "mojang_banner_pattern", "creeper_banner_pattern",
                "skull_banner_pattern", "piglin_banner_pattern", "white_banner", "yellow_banner"};
        giveNamed(player, vanillaItem(patterns[commission.id()], Items.SHIELD),
                "item.village-quest.guild_town.seal." + commission.key(),
                "item.village-quest.guild_town.seal.lore", commission.title());
    }

    private static void grantMilestoneRewards(ServerPlayer player, PlayerQuestData data) {
        int count = GuildTownProgress.completedCommissionCount(data);
        if (count >= 1 && GuildTownProgress.grantReward(data, "guild_waymarker")) {
            giveNamed(player, ModItems.GUILD_NOTICE_POST, "item.village-quest.guild_waymarker",
                    "item.village-quest.guild_waymarker.lore", Component.empty());
        }
        if (count >= 3 && GuildTownProgress.grantReward(data, "atlas_illustration")) {
            giveNamed(player, Items.MAP, "item.village-quest.atlas_illustration",
                    "item.village-quest.atlas_illustration.lore", Component.empty());
        }
        if (count >= 5) GuildTownProgress.grantReward(data, "concordant_title");
        if (count >= GuildTownCommission.values().length) GuildTownProgress.grantReward(data, "pairing_mastery");
    }

    private static boolean isHarvest(ItemStack stack) {
        return stack.is(Items.WHEAT) || stack.is(Items.WHEAT_SEEDS) || stack.is(Items.CARROT)
                || stack.is(Items.POTATO) || stack.is(Items.BEETROOT) || stack.is(Items.BEETROOT_SEEDS);
    }

    private static boolean isArchiveSupply(ItemStack stack) {
        return stack.is(Items.PAPER) || stack.is(Items.INK_SAC) || stack.is(Items.FEATHER);
    }

    private static boolean isMetal(ItemStack stack) {
        return stack.is(Items.IRON_INGOT) || stack.is(Items.COPPER_INGOT) || stack.is(Items.GOLD_INGOT);
    }

    private static boolean isSapling(ItemStack stack) {
        return stack.is(Items.OAK_SAPLING) || stack.is(Items.BIRCH_SAPLING) || stack.is(Items.SPRUCE_SAPLING)
                || stack.is(Items.JUNGLE_SAPLING) || stack.is(Items.ACACIA_SAPLING) || stack.is(Items.DARK_OAK_SAPLING)
                || stack.is(Items.CHERRY_SAPLING) || stack.is(Items.MANGROVE_PROPAGULE);
    }

    private static boolean isFlower(ItemStack stack) {
        return stack.is(Items.DANDELION) || stack.is(Items.POPPY) || stack.is(Items.BLUE_ORCHID)
                || stack.is(Items.ALLIUM) || stack.is(Items.AZURE_BLUET) || stack.is(Items.OXEYE_DAISY)
                || stack.is(Items.CORNFLOWER) || stack.is(Items.LILY_OF_THE_VALLEY) || stack.is(Items.TORCHFLOWER);
    }

    private static Item vanillaItem(String path, Item fallback) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", path));
        return item == null || item == Items.AIR ? fallback : item;
    }

    private static boolean consumeAtomic(ServerPlayer player, Item item, int amount) {
        if (count(player, item) < amount) return false;
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(item)) continue;
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        player.inventoryMenu.broadcastChanges();
        return remaining == 0;
    }

    private static int count(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static void giveNamed(ServerPlayer player, Item item, String nameKey, String loreKey, Component argument) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable(nameKey).withStyle(ChatFormatting.GOLD));
        stack.set(DataComponents.LORE, new ItemLore(List.of(argument.getString().isBlank()
                ? Component.translatable(loreKey).withStyle(ChatFormatting.DARK_GRAY)
                : Component.translatable(loreKey, argument).withStyle(ChatFormatting.DARK_GRAY))));
        give(player, stack);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false, Prediction.SERVER_ONLY);
        player.inventoryMenu.broadcastChanges();
    }

    private static int missing(ServerPlayer player, Item item, int amount) {
        player.sendSystemMessage(Component.translatable("message.village-quest.guild_town.delivery_missing",
                amount, new ItemStack(item).getHoverName()).withStyle(ChatFormatting.RED), false);
        return 0;
    }

    private static int fail(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.RED), false);
        return 0;
    }

    private static PlayerQuestData data(ServerLevel world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    private static GuildTownStory storyById(int id) {
        for (GuildTownStory story : GuildTownStory.values()) if (story.id() == id) return story;
        return null;
    }

    private static Component chronicleLabel(String eventId) {
        if (eventId != null) {
            for (GuildTownStory story : GuildTownStory.values()) {
                if (eventId.equals("story.accepted." + story.key())) {
                    return Component.translatable("message.village-quest.guild_town.chronicle.story.accepted", story.title());
                }
                if (eventId.equals("story.completed." + story.key())) {
                    return Component.translatable("message.village-quest.guild_town.chronicle.story.completed", story.title());
                }
            }
            for (GuildTownCommission commission : GuildTownCommission.values()) {
                if (eventId.equals("commission.completed." + commission.key())) {
                    return Component.translatable("message.village-quest.guild_town.chronicle.commission.completed",
                            commission.title());
                }
            }
        }
        return Component.literal(eventId == null ? "" : eventId.replace('.', ' '));
    }

    private static void refresh(ServerLevel world, ServerPlayer player) {
        QuestBookHelper.refreshQuestBook(world, player);
    }

    private record Delivery(Item item, int amount) {}
    private record VillagePair(VillageBondService.VillageBondView first,
                               VillageBondService.VillageBondView second) {}
}
