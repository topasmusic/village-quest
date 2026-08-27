package de.quest.archive;

import de.quest.caravan.TradeRouteService;
import de.quest.content.story.ShadowsTradeRoadEncounterService;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.economy.CurrencyService;
import de.quest.quest.special.RelicQuestStage;
import de.quest.quest.special.ShardRelicQuestStage;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryQuestKeys;
import de.quest.quest.story.StoryQuestService;
import de.quest.registry.ModItems;
import de.quest.reputation.ReputationService;
import de.quest.shrine.VillageBondService;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * Server-authoritative replacement ledger for unique guild tools.
 *
 * <p>Every issued tool carries an owner and generation. Reissuing increments the
 * generation, so a copy hidden in a chest, backpack mod, or unloaded chunk becomes
 * inert without scanning other inventories or forcing chunks to load.</p>
 */
public final class GuildArchiveService {
    public static final String ENTRY_PREFIX = "guild_archive:";
    private static final String TAG_ID = "vq_archive_id";
    private static final String TAG_OWNER = "vq_archive_owner";
    private static final String TAG_GENERATION = "vq_archive_generation";
    private static final String TAG_SUPERSEDED = "vq_archive_superseded";
    private static final long CONFIRM_TICKS = 20L * 10L;
    private static final int CARRIED_ISSUE_REFRESH_TICKS = 5;
    private static final Map<PendingKey, Long> PENDING = new HashMap<>();

    private GuildArchiveService() {}

    public enum ArchiveItem {
        STARREACH_RING("starreach_ring", 8),
        MERCHANT_SEAL("merchant_seal", 8),
        SHEPHERD_FLUTE("shepherd_flute", 8),
        APIARISTS_SMOKER("apiarists_smoker", 8),
        SURVEYORS_COMPASS("surveyors_compass", 8),
        CARAVAN_LEDGER("caravan_ledger", 4),
        ROADWARDEN_HORN("roadwarden_horn", 8),
        WAYFARERS_SIGIL("wayfarers_sigil", 4),
        GUILD_COURIERS_SATCHEL("guild_couriers_satchel", 4);

        private final String id;
        private final long repeatCost;

        ArchiveItem(String id, long repeatCost) {
            this.id = id;
            this.repeatCost = repeatCost;
        }

        public String id() { return id; }
        public long repeatCost() { return repeatCost; }

        public static ArchiveItem byId(String id) {
            if (id == null) return null;
            for (ArchiveItem value : values()) if (value.id.equals(id)) return value;
            return null;
        }
    }

    public record Offer(String id, Component title, Component status,
                        List<Component> description, Component actionLabel, boolean enabled) {}

    private record PendingKey(UUID playerId, String itemId) {}

    private static PlayerQuestData data(ServerLevel world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    private static String generationKey(ArchiveItem type) { return "archive.gen." + type.id; }
    private static String reissueKey(ArchiveItem type) { return "archive.reissues." + type.id; }
    private static String cooldownKey(ArchiveItem type) { return "archive.next_day." + type.id; }

    public static Item item(ArchiveItem type) {
        if (type == null) return null;
        return switch (type) {
            case STARREACH_RING -> ModItems.STARREACH_RING;
            case MERCHANT_SEAL -> ModItems.MERCHANT_SEAL;
            case SHEPHERD_FLUTE -> ModItems.SHEPHERD_FLUTE;
            case APIARISTS_SMOKER -> ModItems.APIARISTS_SMOKER;
            case SURVEYORS_COMPASS -> ModItems.SURVEYORS_COMPASS;
            case CARAVAN_LEDGER -> ModItems.CARAVAN_LEDGER;
            case ROADWARDEN_HORN -> ModItems.ROADWARDEN_HORN;
            case WAYFARERS_SIGIL -> ModItems.WAYFARERS_SIGIL;
            case GUILD_COURIERS_SATCHEL -> ModItems.GUILD_COURIERS_SATCHEL;
        };
    }

    /** Tags a newly awarded story/reputation tool before it can be duplicated. */
    public static ItemStack issueInitial(ServerLevel world, ServerPlayer player,
                                         ArchiveItem type, ItemStack stack) {
        if (world == null || player == null || type == null || stack == null || stack.isEmpty()) return stack;
        return issueInitial(world, player.getUUID(), player.getGameProfile().name(), type, stack);
    }

    public static ItemStack issueInitial(ServerLevel world, UUID owner,
                                         ArchiveItem type, ItemStack stack) {
        if (world == null || owner == null || type == null || stack == null || stack.isEmpty()) return stack;
        return issueInitial(world, owner, owner.toString().substring(0, 8), type, stack);
    }

    private static ItemStack issueInitial(ServerLevel world, UUID owner, String ownerLabel,
                                          ArchiveItem type, ItemStack stack) {
        PlayerQuestData playerData = data(world, owner);
        int generation = Math.max(1, playerData.getTradeRouteInt(generationKey(type)));
        playerData.setTradeRouteInt(generationKey(type), generation);
        writeIssueTag(stack, owner, ownerLabel, type, generation);
        QuestState.get(world.getServer()).setDirty();
        return stack;
    }

    /** Checks owner/generation and lazily binds genuine pre-archive saves. */
    public static boolean validateUse(ServerLevel world, ServerPlayer player,
                                      ItemStack stack, ArchiveItem type) {
        if (world == null || player == null || stack == null || type == null || !stack.is(item(type))) return false;
        PlayerQuestData playerData = data(world, player.getUUID());
        Issue issue = readIssue(stack);
        int currentGeneration = playerData.getTradeRouteInt(generationKey(type));
        if (issue == null && currentGeneration <= 0) {
            issueInitial(world, player, type, stack);
            return true;
        }
        if (issue != null && issue.type == type && issue.owner.equals(player.getUUID())
                && issue.generation == currentGeneration && currentGeneration > 0) {
            return true;
        }
        markSuperseded(stack, type);
        player.sendSystemMessage(Component.translatable("message.village-quest.guild_archive.superseded")
                .withStyle(ChatFormatting.RED), true);
        return false;
    }

    public static boolean isValidOwnedStack(ServerLevel world, ServerPlayer player,
                                            ItemStack stack, ArchiveItem type) {
        if (world == null || player == null || stack == null || type == null || !stack.is(item(type))) return false;
        Issue issue = readIssue(stack);
        int currentGeneration = data(world, player.getUUID()).getTradeRouteInt(generationKey(type));
        if (issue == null) return currentGeneration <= 0;
        return issue.type == type && issue.owner.equals(player.getUUID())
                && issue.generation == currentGeneration && currentGeneration > 0;
    }

    /**
     * Marks recovered old serials as soon as they return to the owner's carried
     * inventory. The item remains inert authoritatively even before this visual
     * refresh; this pass adds the red name and invalidated-serial lore without
     * loading chunks or scanning external storage.
     */
    public static void onServerTick(MinecraftServer server) {
        if (server == null || server.getTickCount() % CARRIED_ISSUE_REFRESH_TICKS != 0) return;
        ServerLevel world = server.overworld();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refreshCarriedIssues(world, player);
        }
    }

    public static boolean isSuperseded(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return !custom.isEmpty() && custom.copyTag().getBooleanOr(TAG_SUPERSEDED, false);
    }

    private static void refreshCarriedIssues(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) return;
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            ArchiveItem type = archiveType(stack);
            if (type != null && !isValidOwnedStack(world, player, stack, type)) {
                changed |= markSuperseded(stack, type);
            }
        }
        if (changed) {
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
        }
    }

    private static ArchiveItem archiveType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (ArchiveItem type : ArchiveItem.values()) {
            if (stack.is(item(type))) return type;
        }
        return null;
    }

    public static boolean hasValidInInventory(ServerLevel world, ServerPlayer player, ArchiveItem type) {
        if (world == null || player == null || item(type) == null) return false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (isValidOwnedStack(world, player, player.getInventory().getItem(slot), type)) return true;
        }
        return false;
    }

    /** Applies save migration and binds one genuine legacy copy on login. */
    public static void migrateInventoryOnJoin(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) return;
        migrateRemovedPrototypeItems(world, player);
        for (ArchiveItem type : ArchiveItem.values()) {
            boolean boundLegacy = false;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.is(item(type))) continue;
                Issue issue = readIssue(stack);
                int generation = data(world, player.getUUID()).getTradeRouteInt(generationKey(type));
                if (issue == null && generation <= 0 && !boundLegacy) {
                    issueInitial(world, player, type, stack);
                    boundLegacy = true;
                } else if (!isValidOwnedStack(world, player, stack, type)) {
                    markSuperseded(stack, type);
                }
            }
        }
        player.inventoryMenu.broadcastChanges();
    }

    private static void migrateRemovedPrototypeItems(ServerLevel world, ServerPlayer player) {
        if (ModItems.LEGACY_ROADMENDERS_MALLET == null) return;
        int waystones = 0;
        boolean malletFound = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.LEGACY_ROADMENDERS_MALLET)) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
                malletFound = true;
            } else if (stack.is(ModItems.LEGACY_DORMANT_WAYSTONE)
                    || stack.is(ModItems.LEGACY_ATTUNED_WAYSTONE)) {
                waystones += stack.getCount();
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
        if (malletFound && VillageBondService.hasSigil(world, player.getUUID())
                && !hasRawItem(player, ModItems.WAYFARERS_SIGIL)) {
            giveOrDrop(player, issueInitial(world, player, ArchiveItem.WAYFARERS_SIGIL,
                    new ItemStack(ModItems.WAYFARERS_SIGIL)));
        }
        if (waystones > 0 && ModItems.GUILD_WAYSHRINE != null) {
            while (waystones > 0) {
                int count = Math.min(waystones, ModItems.GUILD_WAYSHRINE.getDefaultMaxStackSize());
                giveOrDrop(player, new ItemStack(ModItems.GUILD_WAYSHRINE, count));
                waystones -= count;
            }
        }
    }

    public static List<Offer> offers(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) return List.of();
        List<Offer> offers = new ArrayList<>();
        for (ArchiveItem type : ArchiveItem.values()) {
            if (eligible(world, player, type) && !hasValidInInventory(world, player, type)) {
                offers.add(buildPermanentOffer(world, player, type));
            }
        }
        addTemporaryOffers(world, player, offers);
        return List.copyOf(offers);
    }

    public static boolean handleEntryAction(ServerLevel world, ServerPlayer player, String entryId) {
        if (world == null || player == null || entryId == null || !entryId.startsWith(ENTRY_PREFIX)) return false;
        String id = entryId.substring(ENTRY_PREFIX.length());
        ArchiveItem type = ArchiveItem.byId(id);
        if (type != null) return requestReissue(world, player, type);
        return switch (id) {
            case "cartographers_lens" -> requestTemporary(world, player, id, ModItems.CARTOGRAPHERS_LENS);
            case "cracked_shrine_core" -> requestTemporary(world, player, id, ModItems.CRACKED_SHRINE_CORE);
            case "restored_shrine_core" -> requestRestoredCore(world, player);
            case "guild_warning_letter" -> requestWarningLetter(world, player);
            default -> false;
        };
    }

    private static Offer buildPermanentOffer(ServerLevel world, ServerPlayer player, ArchiveItem type) {
        PlayerQuestData playerData = data(world, player.getUUID());
        int reissues = playerData.getTradeRouteInt(reissueKey(type));
        int days = cooldownDays(world, playerData, type);
        boolean confirming = isPending(world, player, type.id);
        long cost = reissues == 0 ? 0L : type.repeatCost;
        Component status = days > 0
                ? Component.translatable("screen.village-quest.guild_archive.status.cooldown", days).withStyle(ChatFormatting.RED)
                : confirming
                ? Component.translatable("screen.village-quest.guild_archive.status.confirm").withStyle(ChatFormatting.GOLD)
                : cost == 0
                ? Component.translatable("screen.village-quest.guild_archive.status.first_free").withStyle(ChatFormatting.GREEN)
                : Component.translatable("screen.village-quest.guild_archive.status.cost", CurrencyService.formatBalance(cost))
                        .withStyle(ChatFormatting.YELLOW);
        List<Component> description = List.of(
                Component.translatable("screen.village-quest.guild_archive.description.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("screen.village-quest.guild_archive.description.2").withStyle(ChatFormatting.GRAY));
        return new Offer(type.id, new ItemStack(item(type)).getHoverName(), status, description,
                Component.translatable(confirming
                        ? "screen.village-quest.guild_archive.action.confirm"
                        : "screen.village-quest.guild_archive.action.reissue"), days <= 0);
    }

    private static void addTemporaryOffers(ServerLevel world, ServerPlayer player, List<Offer> offers) {
        UUID id = player.getUUID();
        boolean shrineStoryActive = StoryQuestService.isActive(world, id, StoryArcType.SHRINES_BETWEEN_ROADS);
        int chapter = StoryQuestService.chapterIndex(world, id, StoryArcType.SHRINES_BETWEEN_ROADS);
        if (shrineStoryActive && chapter == 0
                && !hasRawItem(player, ModItems.CARTOGRAPHERS_LENS)) {
            offers.add(buildTemporaryOffer(world, player, "cartographers_lens", ModItems.CARTOGRAPHERS_LENS));
        }
        if (shrineStoryActive && chapter >= 1 && chapter <= 2
                && StoryQuestService.hasStoryFlag(world, id, StoryQuestKeys.SHRINES_CORE_RECOVERED)
                && !hasRawItem(player, ModItems.CRACKED_SHRINE_CORE)) {
            offers.add(buildTemporaryOffer(world, player, "cracked_shrine_core", ModItems.CRACKED_SHRINE_CORE));
        }
        boolean coreUnlocked = StoryQuestService.isCompleted(world, id, StoryArcType.SHRINES_BETWEEN_ROADS)
                || shrineStoryActive && chapter >= 3;
        if (coreUnlocked && !hasRawItem(player, ModItems.RESTORED_SHRINE_CORE)) {
            offers.add(buildCoreOffer(world, player));
        }
        boolean warningNeeded = StoryQuestService.isActive(world, id, StoryArcType.SHADOWS_ON_THE_TRADE_ROAD)
                && StoryQuestService.chapterIndex(world, id, StoryArcType.SHADOWS_ON_THE_TRADE_ROAD) == 4
                && StoryQuestService.getQuestInt(world, id, StoryQuestKeys.SHADOWS_LETTER_RECEIVED) > 0
                && !ShadowsTradeRoadEncounterService.hasGuildWarningLetter(player);
        if (warningNeeded) {
            boolean confirming = isPending(world, player, "guild_warning_letter");
            offers.add(new Offer("guild_warning_letter",
                    Component.translatable("item.village-quest.guild_warning_letter"),
                    Component.translatable(confirming
                            ? "screen.village-quest.guild_archive.status.confirm"
                            : "screen.village-quest.guild_archive.status.story_free")
                            .withStyle(confirming ? ChatFormatting.GOLD : ChatFormatting.GREEN),
                    List.of(Component.translatable("screen.village-quest.guild_archive.temporary")
                            .withStyle(ChatFormatting.GRAY)),
                    Component.translatable(confirming
                            ? "screen.village-quest.guild_archive.action.confirm"
                            : "screen.village-quest.guild_archive.action.restore"), true));
        }
    }

    private static Offer buildTemporaryOffer(ServerLevel world, ServerPlayer player, String id, Item item) {
        boolean confirming = isPending(world, player, id);
        return new Offer(id, new ItemStack(item).getHoverName(),
                Component.translatable(confirming
                        ? "screen.village-quest.guild_archive.status.confirm"
                        : "screen.village-quest.guild_archive.status.story_free")
                        .withStyle(confirming ? ChatFormatting.GOLD : ChatFormatting.GREEN),
                List.of(Component.translatable("screen.village-quest.guild_archive.temporary").withStyle(ChatFormatting.GRAY)),
                Component.translatable(confirming
                        ? "screen.village-quest.guild_archive.action.confirm"
                        : "screen.village-quest.guild_archive.action.restore"), true);
    }

    private static Offer buildCoreOffer(ServerLevel world, ServerPlayer player) {
        boolean confirming = isPending(world, player, "restored_shrine_core");
        boolean firstCore = firstCoreRecovery(world, player);
        List<Component> lines = firstCore
                ? List.of(Component.translatable("screen.village-quest.guild_archive.core.first").withStyle(ChatFormatting.GRAY))
                : List.of(Component.translatable("screen.village-quest.guild_archive.core.materials")
                        .withStyle(ChatFormatting.GRAY));
        return new Offer("restored_shrine_core", new ItemStack(ModItems.RESTORED_SHRINE_CORE).getHoverName(),
                Component.translatable(confirming
                        ? "screen.village-quest.guild_archive.status.confirm"
                        : firstCore
                        ? "screen.village-quest.guild_archive.status.story_free"
                        : "screen.village-quest.guild_archive.status.recommission")
                        .withStyle(confirming ? ChatFormatting.GOLD : ChatFormatting.AQUA),
                lines, Component.translatable(confirming
                        ? "screen.village-quest.guild_archive.action.confirm"
                        : "screen.village-quest.guild_archive.action.recommission"), true);
    }

    private static boolean requestReissue(ServerLevel world, ServerPlayer player, ArchiveItem type) {
        if (!eligible(world, player, type) || hasValidInInventory(world, player, type)) return false;
        PlayerQuestData playerData = data(world, player.getUUID());
        if (cooldownDays(world, playerData, type) > 0) {
            player.sendSystemMessage(Component.translatable("message.village-quest.guild_archive.cooldown")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (!confirm(world, player, type.id)) return false;
        int reissues = playerData.getTradeRouteInt(reissueKey(type));
        long cost = reissues == 0 ? 0L : type.repeatCost;
        if (!CurrencyService.removeBalance(world, player.getUUID(), cost)) {
            player.sendSystemMessage(Component.translatable("message.village-quest.guild_archive.cost_missing",
                    CurrencyService.formatBalance(cost)).withStyle(ChatFormatting.RED), false);
            return false;
        }
        int generation = Math.max(1, playerData.getTradeRouteInt(generationKey(type)) + 1);
        playerData.setTradeRouteInt(generationKey(type), generation);
        playerData.setTradeRouteInt(reissueKey(type), reissues + 1);
        playerData.setTradeRouteInt(cooldownKey(type), currentDay(world) + 1);
        ItemStack replacement = new ItemStack(item(type));
        writeIssueTag(replacement, player.getUUID(), player.getGameProfile().name(), type, generation);
        giveOrDrop(player, replacement);
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.guild_archive.reissued",
                replacement.getHoverName()).withStyle(ChatFormatting.GREEN), false);
        return true;
    }

    private static boolean requestTemporary(ServerLevel world, ServerPlayer player, String id, Item item) {
        if (item == null || hasRawItem(player, item) || offers(world, player).stream().noneMatch(o -> o.id.equals(id))) return false;
        if (!confirm(world, player, id)) return false;
        ItemStack replacement = new ItemStack(item);
        giveOrDrop(player, replacement);
        player.sendSystemMessage(Component.translatable("message.village-quest.guild_archive.restored",
                replacement.getHoverName()).withStyle(ChatFormatting.GREEN), false);
        return true;
    }

    private static boolean requestRestoredCore(ServerLevel world, ServerPlayer player) {
        if (ModItems.RESTORED_SHRINE_CORE == null || hasRawItem(player, ModItems.RESTORED_SHRINE_CORE)
                || offers(world, player).stream().noneMatch(o -> o.id.equals("restored_shrine_core"))) return false;
        if (!confirm(world, player, "restored_shrine_core")) return false;
        PlayerQuestData playerData = data(world, player.getUUID());
        if (firstCoreRecovery(world, player)) {
            playerData.setTradeRouteFlag("archive.first_core_restored", true);
        } else if (!consumeCoreMaterials(player)) {
            player.sendSystemMessage(Component.translatable("message.village-quest.guild_archive.core_materials_missing")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        ItemStack core = new ItemStack(ModItems.RESTORED_SHRINE_CORE);
        giveOrDrop(player, core);
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.guild_archive.restored",
                core.getHoverName()).withStyle(ChatFormatting.GREEN), false);
        return true;
    }

    private static boolean requestWarningLetter(ServerLevel world, ServerPlayer player) {
        if (ShadowsTradeRoadEncounterService.hasGuildWarningLetter(player)
                || offers(world, player).stream().noneMatch(o -> o.id.equals("guild_warning_letter"))) return false;
        if (!confirm(world, player, "guild_warning_letter")) return false;
        boolean restored = ShadowsTradeRoadEncounterService.restoreGuildWarningLetter(world, player);
        if (restored) {
            player.sendSystemMessage(Component.translatable("message.village-quest.guild_archive.restored",
                    Component.translatable("item.village-quest.guild_warning_letter"))
                    .withStyle(ChatFormatting.GREEN), false);
        }
        return restored;
    }

    private static boolean firstCoreRecovery(ServerLevel world, ServerPlayer player) {
        PlayerQuestData playerData = data(world, player.getUUID());
        return VillageBondService.shrineCount(world, player.getUUID()) == 0
                && !playerData.hasTradeRouteFlag("archive.first_core_restored")
                && !StoryQuestService.isCompleted(world, player.getUUID(), StoryArcType.SHRINES_BETWEEN_ROADS);
    }

    private static boolean consumeCoreMaterials(ServerPlayer player) {
        Map<Item, Integer> costs = Map.of(
                Items.AMETHYST_SHARD, 4,
                Items.GOLD_INGOT, 2,
                Items.LAPIS_LAZULI, 8,
                Items.CHISELED_STONE_BRICKS, 1);
        for (var entry : costs.entrySet()) if (count(player, entry.getKey()) < entry.getValue()) return false;
        for (var entry : costs.entrySet()) remove(player, entry.getKey(), entry.getValue());
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    private static boolean eligible(ServerLevel world, ServerPlayer player, ArchiveItem type) {
        PlayerQuestData playerData = data(world, player.getUUID());
        return switch (type) {
            case STARREACH_RING -> playerData.getShardRelicQuestStage() == ShardRelicQuestStage.COMPLETED;
            case MERCHANT_SEAL -> playerData.getMerchantSealQuestStage() == RelicQuestStage.COMPLETED;
            case SHEPHERD_FLUTE -> playerData.getShepherdFluteQuestStage() == RelicQuestStage.COMPLETED;
            case APIARISTS_SMOKER -> playerData.getApiaristSmokerQuestStage() == RelicQuestStage.COMPLETED;
            case SURVEYORS_COMPASS -> playerData.getSurveyorCompassQuestStage() == RelicQuestStage.COMPLETED;
            case CARAVAN_LEDGER -> TradeRouteService.hasRouteAccess(world, player.getUUID());
            case ROADWARDEN_HORN -> ReputationService.get(world, player.getUUID(),
                    ReputationService.ReputationTrack.MONSTER_HUNTING) >= ReputationService.MASTERY_START;
            case WAYFARERS_SIGIL -> VillageBondService.hasSigil(world, player.getUUID());
            case GUILD_COURIERS_SATCHEL -> StoryQuestService.isCompleted(
                    world, player.getUUID(), StoryArcType.SHRINES_BETWEEN_ROADS);
        };
    }

    private static boolean confirm(ServerLevel world, ServerPlayer player, String itemId) {
        PendingKey key = new PendingKey(player.getUUID(), itemId);
        long now = world.getGameTime();
        Long until = PENDING.get(key);
        if (until == null || until < now) {
            PENDING.put(key, now + CONFIRM_TICKS);
            player.sendSystemMessage(Component.translatable("message.village-quest.guild_archive.confirm", 10)
                    .withStyle(ChatFormatting.YELLOW), false);
            return false;
        }
        PENDING.remove(key);
        return true;
    }

    private static boolean isPending(ServerLevel world, ServerPlayer player, String itemId) {
        Long until = PENDING.get(new PendingKey(player.getUUID(), itemId));
        return until != null && until >= world.getGameTime();
    }

    private static int cooldownDays(ServerLevel world, PlayerQuestData playerData, ArchiveItem type) {
        return Math.max(0, playerData.getTradeRouteInt(cooldownKey(type)) - currentDay(world));
    }

    private static int currentDay(ServerLevel world) {
        return (int) Math.max(0L, world.getOverworldClockTime() / 24000L);
    }

    public static void resetTransientState() {
        PENDING.clear();
    }

    private static void writeIssueTag(ItemStack stack, UUID owner, String ownerLabel,
                                      ArchiveItem type, int generation) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(TAG_ID, type.id);
            tag.putString(TAG_OWNER, owner.toString());
            tag.putInt(TAG_GENERATION, generation);
            tag.remove(TAG_SUPERSEDED);
        });
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        List<Component> lines = new ArrayList<>(lore.lines());
        lines.removeIf(line -> line.getString().startsWith("Guild issue:")
                || line.getString().startsWith("Gildenausgabe:")
                || line.getString().startsWith("Emisión del gremio:"));
        lines.add(Component.translatable("item.village-quest.guild_archive.registered", ownerLabel)
                .withStyle(ChatFormatting.DARK_AQUA));
        stack.set(DataComponents.LORE, new ItemLore(List.copyOf(lines)));
    }

    private static Issue readIssue(ItemStack stack) {
        CustomData custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (custom.isEmpty()) return null;
        CompoundTag tag = custom.copyTag();
        String rawId = tag.getStringOr(TAG_ID, "");
        String rawOwner = tag.getStringOr(TAG_OWNER, "");
        int generation = tag.getIntOr(TAG_GENERATION, 0);
        ArchiveItem type = ArchiveItem.byId(rawId);
        try {
            return type == null || generation <= 0 ? null : new Issue(type, UUID.fromString(rawOwner), generation);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean markSuperseded(ItemStack stack, ArchiveItem type) {
        CustomData custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (!custom.copyTag().getBooleanOr(TAG_SUPERSEDED, false)) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(TAG_SUPERSEDED, true));
            stack.set(DataComponents.CUSTOM_NAME, Component.translatable(
                    "item.village-quest.guild_archive.superseded", new ItemStack(item(type)).getHoverName())
                    .withStyle(ChatFormatting.RED));
            stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable(
                    "item.village-quest.guild_archive.superseded.lore").withStyle(ChatFormatting.DARK_RED))));
            return true;
        }
        return false;
    }

    private record Issue(ArchiveItem type, UUID owner, int generation) {}

    private static boolean hasRawItem(ServerPlayer player, Item item) {
        if (player == null || item == null) return false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(item)) return true;
        }
        return false;
    }

    private static int count(ServerPlayer player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static void remove(ServerPlayer player, Item item, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(item)) continue;
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
    }

    private static void removeAll(ServerPlayer player, Item item) {
        if (item == null) return;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(item)) player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        player.inventoryMenu.broadcastChanges();
    }
}
