package de.quest.content.story;

import de.quest.VillageQuest;
import de.quest.archive.GuildArchiveService;
import de.quest.archive.GuildArchiveService.ArchiveItem;
import de.quest.caravan.TradeRouteService;
import de.quest.data.QuestState;
import de.quest.economy.CurrencyService;
import de.quest.quest.QuestCompletionMode;
import de.quest.quest.story.StoryArcDefinition;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryChapterCompletion;
import de.quest.quest.story.StoryChapterDefinition;
import de.quest.quest.story.StoryQuestKeys;
import de.quest.quest.story.StoryQuestService;
import de.quest.quest.story.VillageProjectType;
import de.quest.registry.ModBlocks;
import de.quest.registry.ModItems;
import de.quest.reputation.ReputationService;
import de.quest.shrine.VillageBondService;
import de.quest.util.QuestSiteLocator;
import de.quest.util.QuestMapHelper;
import de.quest.util.WildernessSiteValidator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class ShrinesBetweenRoadsStoryArc implements StoryArcDefinition {
    private static final String RESERVED_RUIN_X = "reserved_heartstone_ruin_x";
    private static final String RESERVED_RUIN_Y = "reserved_heartstone_ruin_y";
    private static final String RESERVED_RUIN_Z = "reserved_heartstone_ruin_z";
    private static final String RESERVED_RUIN_PLACED = "reserved_heartstone_ruin_placed";
    private final List<StoryChapterDefinition> chapters = List.of(
            new StonesThatRemember(), new BrokenHeartstone(), new ThreeHands(),
            new FirstFlame(), new ChainOfWelcome(), new LastRelay());

    @Override public StoryArcType type() { return StoryArcType.SHRINES_BETWEEN_ROADS; }
    @Override public Component title() { return Component.translatable("quest.village-quest.story.shrines_between_roads.title"); }
    @Override public int chapterCount() { return chapters.size(); }
    @Override public StoryChapterDefinition chapter(int index) { return index < 0 || index >= chapters.size() ? null : chapters.get(index); }
    @Override public boolean isUnlocked(ServerLevel world, UUID playerId) {
        return StoryQuestService.isCompleted(world, playerId, StoryArcType.THE_EMPTY_CARAVAN)
                && TradeRouteService.routeCount(world, playerId) >= 2
                && TradeRouteService.completedGuildContracts(world, playerId) >= 3;
    }
    @Override public boolean shouldShowLockedEntry(ServerLevel world, UUID playerId) {
        return StoryQuestService.isCompleted(world, playerId, StoryArcType.THE_EMPTY_CARAVAN) && !isUnlocked(world, playerId);
    }
    @Override public Component lockedEntryBody(ServerLevel world, UUID playerId) {
        return Component.translatable("screen.village-quest.questmaster.story.shrines_between_roads.locked",
                TradeRouteService.routeCount(world, playerId), 2,
                TradeRouteService.completedGuildContracts(world, playerId), 3);
    }

    public static boolean canBreakActiveRuinMilestone(ServerLevel world, ServerPlayer player,
                                                       BlockPos pos, BlockState state) {
        if (world == null || player == null || pos == null || state == null
                || !state.is(ModBlocks.GUILD_MILESTONE)) {
            return true;
        }
        for (UUID owner : QuestState.get(world.getServer()).getPlayersView().keySet()) {
            if (!StoryQuestService.isActive(world, owner, StoryArcType.SHRINES_BETWEEN_ROADS)
                    || StoryQuestService.chapterIndex(world, owner, StoryArcType.SHRINES_BETWEEN_ROADS) != 1
                    || !StoryQuestService.hasStoryFlag(world, owner, StoryQuestKeys.SHRINES_RUIN_PLACED)
                    || StoryQuestService.hasStoryFlag(world, owner, StoryQuestKeys.SHRINES_CORE_RECOVERED)) {
                continue;
            }
            BlockPos target = new BlockPos(
                    StoryQuestService.getQuestInt(world, owner, StoryQuestKeys.SHRINES_TARGET_X),
                    StoryQuestService.getQuestInt(world, owner, StoryQuestKeys.SHRINES_TARGET_Y),
                    StoryQuestService.getQuestInt(world, owner, StoryQuestKeys.SHRINES_TARGET_Z));
            if (pos.distSqr(target) <= 36.0) {
                if (owner.equals(player.getUUID())) {
                    BrokenHeartstone.recoverCore(world, player, pos);
                    return false;
                }
                if (player.isCreative()) return true;
                player.sendSystemMessage(Component.translatable(
                        "message.village-quest.shrines.milestone_protected").withStyle(ChatFormatting.RED), true);
                return false;
            }
        }
        return true;
    }

    public static boolean abandonHeartstoneTrail(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null
                || !StoryQuestService.isActive(world, player.getUUID(), StoryArcType.SHRINES_BETWEEN_ROADS)
                || StoryQuestService.chapterIndex(world, player.getUUID(), StoryArcType.SHRINES_BETWEEN_ROADS) != 1
                || StoryQuestService.hasStoryFlag(world, player.getUUID(), StoryQuestKeys.SHRINES_CORE_RECOVERED)) {
            return false;
        }
        var data = QuestState.get(world.getServer()).getPlayerData(player.getUUID());
        if (StoryQuestService.hasStoryFlag(world, player.getUUID(), StoryQuestKeys.SHRINES_RUIN_PLACED)) {
            data.setTradeRouteInt(RESERVED_RUIN_X, StoryQuestService.getQuestInt(world, player.getUUID(), StoryQuestKeys.SHRINES_TARGET_X));
            data.setTradeRouteInt(RESERVED_RUIN_Y, StoryQuestService.getQuestInt(world, player.getUUID(), StoryQuestKeys.SHRINES_TARGET_Y));
            data.setTradeRouteInt(RESERVED_RUIN_Z, StoryQuestService.getQuestInt(world, player.getUUID(), StoryQuestKeys.SHRINES_TARGET_Z));
            data.setTradeRouteInt(RESERVED_RUIN_PLACED, 1);
        }
        QuestMapHelper.removeTaggedMaps(player, "broken_heartstone");
        data.clearStoryProgress();
        data.setActiveStoryArc(null);
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.shrines.trail_abandoned")
                .withStyle(ChatFormatting.YELLOW), false);
        return true;
    }

    private abstract static class ShrineChapter implements StoryChapterDefinition {
        protected int progress(ServerLevel world, UUID playerId, String key) { return StoryQuestService.getQuestInt(world, playerId, key); }
        protected boolean flag(ServerLevel world, UUID playerId, String key) { return StoryQuestService.hasStoryFlag(world, playerId, key); }
        protected Component title(int chapter) { return Component.translatable(key(chapter, "title")); }
        protected Component offer(int chapter, int paragraph) { return Component.translatable(key(chapter, "offer." + paragraph)).withStyle(ChatFormatting.GRAY); }
        protected StoryChapterCompletion completion(int chapter, long currency, int levels, ReputationService.ReputationTrack track, int reputation, VillageProjectType project) {
            String prefix = key(chapter, "");
            return new StoryChapterCompletion(title(chapter), Component.translatable(prefix + ".complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable(prefix + ".complete.2").withStyle(ChatFormatting.GRAY), Component.translatable(prefix + ".complete.3").withStyle(ChatFormatting.GRAY),
                    currency, levels, track, reputation, project);
        }
        private static String key(int chapter, String suffix) {
            return "quest.village-quest.story.shrines_between_roads.chapter_" + chapter
                    + (suffix.isEmpty() ? "" : "." + suffix);
        }
        protected void give(ServerPlayer player, ItemStack stack) { if (!player.getInventory().add(stack)) player.drop(stack, false); player.inventoryMenu.broadcastChanges(); }
    }

    private static final class StonesThatRemember extends ShrineChapter {
        @Override public Component title() { return title(1); }
        @Override public Component offerParagraph1() { return offer(1, 1); }
        @Override public Component offerParagraph2() { return offer(1, 2); }
        @Override public void onAccepted(ServerLevel world, ServerPlayer player) { give(player, new ItemStack(ModItems.CARTOGRAPHERS_LENS)); }
        @Override public List<Component> progressLines(ServerLevel world, UUID id) { return List.of(Component.translatable("quest.village-quest.story.shrines_between_roads.chapter_1.progress", progress(world, id, StoryQuestKeys.SHRINES_VILLAGES_INSPECTED), 2).withStyle(ChatFormatting.GRAY)); }
        @Override public boolean isComplete(ServerLevel world, ServerPlayer player) { return progress(world, player.getUUID(), StoryQuestKeys.SHRINES_VILLAGES_INSPECTED) >= 2; }
        @Override public void onClaimed(ServerLevel world, ServerPlayer player) { VillageBondService.installLensInLedger(world, player); }
        @Override public StoryChapterCompletion buildCompletion() { return completion(1, CurrencyService.CROWN + 4, 9, ReputationService.ReputationTrack.TRADE, 12, null); }
    }

    private static final class BrokenHeartstone extends ShrineChapter {
        private static final Identifier RUIN_TEMPLATE = Identifier.fromNamespaceAndPath(
                VillageQuest.MOD_ID, "broken_heartstone_ruin");
        private static final int MIN_RUIN_DISTANCE = 900;
        private static final int MAX_RUIN_DISTANCE = 1900;
        private static final int MAX_TARGET_ATTEMPTS = 64;
        private static final int RUIN_REVEAL_DISTANCE = 96;
        private static final int RUIN_SEARCH_RADIUS = 20;
        private static final int RUIN_SEARCH_INTERVAL = 100;
        private static final int RUIN_HALF_SIZE = 3;
        private static final int RUIN_HEIGHT = 3;

        @Override public Component title() { return title(2); }
        @Override public Component offerParagraph1() { return offer(2, 1); }
        @Override public Component offerParagraph2() { return offer(2, 2); }
        @Override public QuestCompletionMode completionMode() { return QuestCompletionMode.AUTOMATIC; }
        @Override public void onAccepted(ServerLevel world, ServerPlayer player) {
            var playerData = QuestState.get(world.getServer()).getPlayerData(player.getUUID());
            if (playerData.getTradeRouteInt(RESERVED_RUIN_PLACED) > 0) {
                BlockPos target = new BlockPos(playerData.getTradeRouteInt(RESERVED_RUIN_X),
                        playerData.getTradeRouteInt(RESERVED_RUIN_Y), playerData.getTradeRouteInt(RESERVED_RUIN_Z));
                StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SHRINES_TARGET_X, target.getX());
                StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SHRINES_TARGET_Y, target.getY());
                StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SHRINES_TARGET_Z, target.getZ());
                StoryQuestService.setStoryFlag(world, player.getUUID(), StoryQuestKeys.SHRINES_RUIN_PLACED, true);
                give(player, createHeartstoneMap(world, target));
                return;
            }
            BlockPos target = QuestSiteLocator.findDistantLandTarget(world, player.blockPosition(),
                    MIN_RUIN_DISTANCE, MAX_RUIN_DISTANCE, MAX_TARGET_ATTEMPTS);
            if (target == null) {
                target = player.blockPosition().offset(160, 0, 96);
            }
            StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SHRINES_TARGET_X, target.getX());
            StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SHRINES_TARGET_Z, target.getZ());
            give(player, createHeartstoneMap(world, target));
        }
        @Override public void onServerTick(ServerLevel world, ServerPlayer player) {
            if (flag(world, player.getUUID(), StoryQuestKeys.SHRINES_CORE_RECOVERED)) return;
            if ((world.getGameTime() + player.getId()) % RUIN_SEARCH_INTERVAL != 0L) return;
            int x = progress(world, player.getUUID(), StoryQuestKeys.SHRINES_TARGET_X), z = progress(world, player.getUUID(), StoryQuestKeys.SHRINES_TARGET_Z);
            BlockPos horizontal = new BlockPos(x, player.getBlockY(), z);
            boolean followingTrail = flag(world, player.getUUID(), StoryQuestKeys.SHRINES_RUIN_SEARCH_ACTIVE);
            if (!flag(world, player.getUUID(), StoryQuestKeys.SHRINES_RUIN_PLACED)
                    && (followingTrail || player.blockPosition().distSqr(horizontal) <= RUIN_REVEAL_DISTANCE * RUIN_REVEAL_DISTANCE)) {
                BlockPos center = followingTrail
                        ? resolveRuinSite(world, player.getUUID(), player.getBlockX(), player.getBlockZ())
                        : resolveRuinSite(world, player.getUUID(), x, z);
                if (center == null && !followingTrail) {
                    center = resolveRuinSite(world, player.getUUID(), player.getBlockX(), player.getBlockZ());
                }
                if (center != null && placeRuin(world, center)) {
                    StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SHRINES_TARGET_X, center.getX());
                    StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SHRINES_TARGET_Y, center.getY());
                    StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SHRINES_TARGET_Z, center.getZ());
                    StoryQuestService.setStoryFlag(world, player.getUUID(), StoryQuestKeys.SHRINES_RUIN_PLACED, true);
                    StoryQuestService.setStoryFlag(world, player.getUUID(), StoryQuestKeys.SHRINES_RUIN_SEARCH_ACTIVE, false);
                    player.sendSystemMessage(Component.translatable("message.village-quest.shrines.ruin_revealed")
                            .withStyle(ChatFormatting.AQUA), false);
                } else if (!followingTrail) {
                    StoryQuestService.setStoryFlag(world, player.getUUID(), StoryQuestKeys.SHRINES_RUIN_SEARCH_ACTIVE, true);
                    player.sendSystemMessage(Component.translatable("message.village-quest.shrines.seek_dry_ground")
                            .withStyle(ChatFormatting.YELLOW), false);
                }
            }
        }
        private static ItemStack createHeartstoneMap(ServerLevel world, BlockPos target) {
            ItemStack map = MapItem.create(world, target.getX(), target.getZ(), (byte) 2, true, true);
            MapItem.renderBiomePreviewMap(world, map);
            MapItemSavedData.addTargetDecoration(map,
                    new BlockPos(target.getX(), world.getSeaLevel(), target.getZ()),
                    "broken_heartstone", MapDecorationTypes.RED_X);
            map.set(DataComponents.ITEM_NAME,
                    Component.translatable("item.village-quest.broken_heartstone_map").withStyle(ChatFormatting.AQUA));
            map.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.translatable("item.village-quest.broken_heartstone_map.lore").withStyle(ChatFormatting.GRAY)
            )));
            QuestMapHelper.tag(map, "broken_heartstone");
            return map;
        }

        private static void recoverCore(ServerLevel world, ServerPlayer player, BlockPos pos) {
            if (StoryQuestService.hasStoryFlag(world, player.getUUID(), StoryQuestKeys.SHRINES_CORE_RECOVERED)) return;
            StoryQuestService.setStoryFlag(world, player.getUUID(), StoryQuestKeys.SHRINES_CORE_RECOVERED, true);
            var playerData = QuestState.get(world.getServer()).getPlayerData(player.getUUID());
            playerData.setTradeRouteInt(RESERVED_RUIN_PLACED, 0);
            QuestMapHelper.removeTaggedMaps(player, "broken_heartstone");
            ItemStack core = new ItemStack(ModItems.CRACKED_SHRINE_CORE);
            if (!player.getInventory().add(core)) player.drop(core, false);
            player.inventoryMenu.broadcastChanges();
            world.playSound(null, pos, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 0.7f);
            world.destroyBlock(pos, false, player);
            QuestState.get(world.getServer()).markTerrainModified(pos);
            StoryQuestService.completeIfEligible(world, player);
        }

        private static BlockPos resolveRuinSite(ServerLevel world, UUID playerId, int targetX, int targetZ) {
            for (int radius = 0; radius <= RUIN_SEARCH_RADIUS; radius++) {
                for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                    for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                        if (radius > 0 && Math.abs(xOffset) != radius && Math.abs(zOffset) != radius) {
                            continue;
                        }
                        BlockPos site = validateRuinSite(world, playerId, targetX + xOffset, targetZ + zOffset);
                        if (site != null) {
                            return site;
                        }
                    }
                }
            }
            return null;
        }

        private static BlockPos validateRuinSite(ServerLevel world, UUID playerId, int centerX, int centerZ) {
            BlockPos center = WildernessSiteValidator.findNaturalFlatSite(world, centerX, centerZ,
                    RUIN_HALF_SIZE, 8, 1, 2, 96, false);
            if (center == null) return null;
            for (int xOffset = -RUIN_HALF_SIZE; xOffset <= RUIN_HALF_SIZE; xOffset++) {
                for (int zOffset = -RUIN_HALF_SIZE; zOffset <= RUIN_HALF_SIZE; zOffset++) {
                    for (int yOffset = 0; yOffset < RUIN_HEIGHT; yOffset++) {
                        BlockState state = world.getBlockState(center.offset(xOffset, yOffset, zOffset));
                        if (!state.canBeReplaced()) {
                            return null;
                        }
                    }
                }
            }
            return center;
        }

        private static boolean placeRuin(ServerLevel world, BlockPos center) {
            StructureTemplate template = world.getStructureManager().get(RUIN_TEMPLATE).orElse(null);
            if (template == null) {
                return false;
            }
            Vec3i size = template.getSize();
            // Sink the template floor into the natural surface instead of placing
            // a raised platform on top of it. Non-air template blocks replace the
            // existing terrain while structure void and air remain ignored.
            BlockPos origin = center.offset(-size.getX() / 2, -1, -size.getZ() / 2);
            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setMirror(Mirror.NONE)
                    .setRotation(Rotation.getRandom(world.getRandom()))
                    .setRotationPivot(new BlockPos(size.getX() / 2, 0, size.getZ() / 2))
                    .setIgnoreEntities(true)
                    .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
            return template.placeInWorld(world, origin, origin, settings, world.getRandom(), 3);
        }
        @Override public List<Component> progressLines(ServerLevel world, UUID id) { return List.of(
                Component.translatable("quest.village-quest.story.shrines_between_roads.chapter_2.progress",
                        flag(world, id, StoryQuestKeys.SHRINES_CORE_RECOVERED) ? 1 : 0, 1).withStyle(ChatFormatting.GRAY)); }
        @Override public boolean isComplete(ServerLevel world, ServerPlayer player) { return flag(world, player.getUUID(), StoryQuestKeys.SHRINES_CORE_RECOVERED); }
        @Override public StoryChapterCompletion buildCompletion() { return completion(2, 18, 10, ReputationService.ReputationTrack.MONSTER_HUNTING, 12, null); }
    }

    private static final class ThreeHands extends ShrineChapter {
        @Override public Component title() { return title(3); }
        @Override public Component offerParagraph1() { return offer(3, 1); }
        @Override public Component offerParagraph2() { return offer(3, 2); }
        @Override public void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack inHand) {
            if (!(entity instanceof Villager villager) || !villager.isAlive()) return;
            Holder<VillagerProfession> profession = villager.getVillagerData().profession();
            String key = profession.is(VillagerProfession.CARTOGRAPHER) ? "cartographer" : profession.is(VillagerProfession.CLERIC) ? "cleric"
                    : profession.is(VillagerProfession.TOOLSMITH) ? "toolsmith" : null;
            if (key == null || flag(world, player.getUUID(), StoryQuestKeys.SHRINES_CRAFTSPERSON_PREFIX + key)) return;
            StoryQuestService.setStoryFlag(world, player.getUUID(), StoryQuestKeys.SHRINES_CRAFTSPERSON_PREFIX + key, true);
            StoryQuestService.addQuestIntClamped(world, player.getUUID(), StoryQuestKeys.SHRINES_CRAFTSPEOPLE, 1, 3);
            StoryQuestService.completeIfEligible(world, player);
        }
        @Override public List<Component> progressLines(ServerLevel world, UUID id) { return List.of(
                Component.translatable("quest.village-quest.story.shrines_between_roads.chapter_3.people", progress(world, id, StoryQuestKeys.SHRINES_CRAFTSPEOPLE), 3).withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.story.shrines_between_roads.chapter_3.materials").withStyle(ChatFormatting.GRAY)); }
        @Override public boolean isComplete(ServerLevel world, ServerPlayer player) { return progress(world, player.getUUID(), StoryQuestKeys.SHRINES_CRAFTSPEOPLE) >= 3
                && StoryQuestService.countCompletionItem(world, player.getUUID(), Items.AMETHYST_SHARD) >= 16
                && StoryQuestService.countCompletionItem(world, player.getUUID(), Items.GOLD_INGOT) >= 8
                && StoryQuestService.countCompletionItem(world, player.getUUID(), Items.LAPIS_LAZULI) >= 16
                && StoryQuestService.countCompletionItem(world, player.getUUID(), ModItems.CRACKED_SHRINE_CORE) >= 1; }
        @Override public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) { return StoryQuestService.consumeCompletionItems(world, player.getUUID(), Map.of(
                Items.AMETHYST_SHARD, 16, Items.GOLD_INGOT, 8, Items.LAPIS_LAZULI, 16, ModItems.CRACKED_SHRINE_CORE, 1)); }
        @Override public void onClaimed(ServerLevel world, ServerPlayer player) { give(player, new ItemStack(ModItems.RESTORED_SHRINE_CORE)); }
        @Override public StoryChapterCompletion buildCompletion() { return completion(3, CurrencyService.CROWN * 2, 11, ReputationService.ReputationTrack.CRAFTING, 16, null); }
    }

    private static final class FirstFlame extends ShrineChapter {
        @Override public Component title() { return title(4); }
        @Override public Component offerParagraph1() { return offer(4, 1); }
        @Override public Component offerParagraph2() { return offer(4, 2); }
        @Override public void onAccepted(ServerLevel world, ServerPlayer player) {
            VillageBondService.grantSigil(world, player);
        }
        @Override public void onServerTick(ServerLevel world, ServerPlayer player) { StoryQuestService.setQuestIntQuietly(world, player.getUUID(), StoryQuestKeys.SHRINES_ACTIVATED, VillageBondService.shrineCount(world, player.getUUID())); StoryQuestService.completeIfEligible(world, player); }
        @Override public List<Component> progressLines(ServerLevel world, UUID id) { return List.of(Component.translatable("quest.village-quest.story.shrines_between_roads.chapter_4.progress", progress(world, id, StoryQuestKeys.SHRINES_ACTIVATED), 1).withStyle(ChatFormatting.GRAY)); }
        @Override public boolean isComplete(ServerLevel world, ServerPlayer player) { return VillageBondService.shrineCount(world, player.getUUID()) >= 1; }
        @Override public StoryChapterCompletion buildCompletion() { return completion(4, 18, 11, ReputationService.ReputationTrack.CRAFTING, 16, null); }
    }

    private static final class ChainOfWelcome extends ShrineChapter {
        @Override public Component title() { return title(5); }
        @Override public Component offerParagraph1() { return offer(5, 1); }
        @Override public Component offerParagraph2() { return offer(5, 2); }
        @Override public void onAccepted(ServerLevel world, ServerPlayer player) { give(player, new ItemStack(ModItems.GUILD_NOTICE_POST, 2)); give(player, new ItemStack(ModItems.GUILD_WAYSHRINE, 2)); }
        @Override public void onServerTick(ServerLevel world, ServerPlayer player) { StoryQuestService.setQuestIntQuietly(world, player.getUUID(), StoryQuestKeys.SHRINES_ACTIVATED, VillageBondService.shrineCount(world, player.getUUID())); }
        @Override public List<Component> progressLines(ServerLevel world, UUID id) { return List.of(
                Component.translatable("quest.village-quest.story.shrines_between_roads.chapter_5.trust", progress(world, id, StoryQuestKeys.SHRINES_TRUSTED_VILLAGES), 2).withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.story.shrines_between_roads.chapter_5.shrines", progress(world, id, StoryQuestKeys.SHRINES_ACTIVATED), 3).withStyle(ChatFormatting.GRAY)); }
        @Override public boolean isComplete(ServerLevel world, ServerPlayer player) { return progress(world, player.getUUID(), StoryQuestKeys.SHRINES_TRUSTED_VILLAGES) >= 2 && VillageBondService.shrineCount(world, player.getUUID()) >= 3; }
        @Override public StoryChapterCompletion buildCompletion() { return completion(5, CurrencyService.CROWN * 2 + 5, 12, ReputationService.ReputationTrack.TRADE, 20, null); }
    }

    private static final class LastRelay extends ShrineChapter {
        @Override public Component title() { return title(6); }
        @Override public Component offerParagraph1() { return offer(6, 1); }
        @Override public Component offerParagraph2() { return offer(6, 2); }
        @Override public void onAccepted(ServerLevel world, ServerPlayer player) {
            StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SHRINES_RELAY_CONTRACT_BASELINE, TradeRouteService.completedGuildContracts(world, player.getUUID()));
            StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SHRINES_RELAY_SUCCESS_BASELINE, TradeRouteService.totalRouteSuccesses(world, player.getUUID()));
        }
        @Override public void onServerTick(ServerLevel world, ServerPlayer player) {
            boolean complete = TradeRouteService.completedGuildContracts(world, player.getUUID()) > progress(world, player.getUUID(), StoryQuestKeys.SHRINES_RELAY_CONTRACT_BASELINE)
                    && TradeRouteService.totalRouteSuccesses(world, player.getUUID()) > progress(world, player.getUUID(), StoryQuestKeys.SHRINES_RELAY_SUCCESS_BASELINE);
            StoryQuestService.setStoryFlag(world, player.getUUID(), StoryQuestKeys.SHRINES_RELAY_READY, complete);
            StoryQuestService.completeIfEligible(world, player);
        }
        @Override public List<Component> progressLines(ServerLevel world, UUID id) { return List.of(Component.translatable("quest.village-quest.story.shrines_between_roads.chapter_6.progress", flag(world, id, StoryQuestKeys.SHRINES_RELAY_READY) ? 1 : 0, 1).withStyle(ChatFormatting.GRAY)); }
        @Override public boolean isComplete(ServerLevel world, ServerPlayer player) { return flag(world, player.getUUID(), StoryQuestKeys.SHRINES_RELAY_READY); }
        @Override public void onClaimed(ServerLevel world, ServerPlayer player) {
            give(player, GuildArchiveService.issueInitial(world, player, ArchiveItem.GUILD_COURIERS_SATCHEL,
                    new ItemStack(ModItems.GUILD_COURIERS_SATCHEL)));
        }
        @Override public StoryChapterCompletion buildCompletion() { return completion(6, CurrencyService.CROWN * 4, 14, ReputationService.ReputationTrack.TRADE, 25, VillageProjectType.WAYSHRINE_NETWORK); }
    }
}
