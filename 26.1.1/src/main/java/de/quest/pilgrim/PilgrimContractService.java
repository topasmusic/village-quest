package de.quest.pilgrim;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.economy.CurrencyService;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.special.SurveyorCompassQuestService;
import de.quest.quest.story.VillageProjectService;
import de.quest.quest.story.VillageProjectType;
import de.quest.reputation.ReputationService;
import de.quest.registry.ModItems;
import de.quest.util.Texts;
import de.quest.util.TimeUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PilgrimContractService {
    public static final String ACTION_ENTRY_ID = "__pilgrim_contract__";

    private static final String FLAG_ADMIN_UNLOCK = "pilgrim_contract_admin_unlock";
    private static final String FLAG_READY = "pilgrim_contract_ready";
    private static final String FLAG_SUPPRESS_OFFER = "pilgrim_contract_suppress_offer";

    private static final String KEY_LANTERN_SKELETONS = "pilgrim_lantern_skeletons";
    private static final String KEY_SMOKE_CREEPERS = "pilgrim_smoke_creepers";
    private static final String KEY_TRACKS_ZOMBIES = "pilgrim_tracks_zombies";
    private static final String KEY_FANGS_SPIDERS = "pilgrim_fangs_spiders";
    private static final String KEY_ASH_BLAZES = "pilgrim_ash_blazes";
    private static final String KEY_ASH_WITHER_SKELETONS = "pilgrim_ash_wither_skeletons";
    private static final String KEY_BLACKSTONE_MAGMA_CUBES = "pilgrim_blackstone_magma_cubes";
    private static final String KEY_BLACKSTONE_GHASTS = "pilgrim_blackstone_ghasts";
    private static final String KEY_STILLNESS_ENDERMEN = "pilgrim_stillness_endermen";
    private static final String KEY_STILLNESS_SHULKERS = "pilgrim_stillness_shulkers";

    private static final int LANTERN_SKELETON_TARGET = 6;
    private static final int SMOKE_CREEPER_TARGET = 3;
    private static final int TRACKS_ZOMBIE_TARGET = 8;
    private static final int FANGS_SPIDER_TARGET = 6;
    private static final int ASH_BLAZE_TARGET = 4;
    private static final int ASH_WITHER_SKELETON_TARGET = 3;
    private static final int BLACKSTONE_MAGMA_CUBE_TARGET = 5;
    private static final int BLACKSTONE_GHAST_TARGET = 2;
    private static final int STILLNESS_ENDERMAN_TARGET = 6;
    private static final int STILLNESS_SHULKER_TARGET = 3;
    private static final Map<PilgrimContractType, PilgrimContractDefinition> DEFINITIONS = createDefinitions();

    private PilgrimContractService() {}

    public record PilgrimContractView(
            String contractId,
            Component title,
            Component status,
            List<Component> descriptionLines,
            List<Component> objectiveLines,
            List<Component> rewardLines,
            Component actionLabel,
            boolean actionEnabled,
            ItemStack previewStack
    ) {}

    public record PilgrimContractStatus(Component title, List<Component> lines) {}

    private record PilgrimContractCompletion(
            Component title,
            Component completionLine1,
            Component completionLine2,
            Component completionLine3,
            long currencyReward,
            ReputationService.ReputationTrack reputationTrack,
            int reputationAmount,
            int levels,
            Component specialRewardLine,
            boolean unlocksCompassStructures
    ) {}

    @FunctionalInterface
    private interface KillMatcher {
        boolean matches(ServerLevel world, Entity entity);
    }

    private record KillObjective(String progressKey, int target, KillMatcher matcher) {}

    private interface PilgrimContractDefinition {
        Component title();

        Component offerParagraph1();

        Component offerParagraph2();

        List<Component> progressLines(ServerLevel world, ServerPlayer player);

        boolean isComplete(ServerLevel world, ServerPlayer player);

        PilgrimContractCompletion buildCompletion();

        ItemStack previewStack();

        default boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
            return true;
        }

        default void onAccepted(ServerLevel world, ServerPlayer player) {}

        default void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack stack) {}

        default void onVillagerTrade(ServerLevel world, ServerPlayer player, ItemStack stack) {}

        default void onAnimalLove(ServerLevel world, ServerPlayer player, Animal animal) {}

        default void onBeeNestInteract(ServerLevel world, ServerPlayer player, BlockState state, ItemStack stack) {}

        default void onFurnaceOutput(ServerLevel world, ServerPlayer player, ItemStack stack) {}

        default void onMonsterKill(ServerLevel world, ServerPlayer player, Entity killedEntity) {}
    }

    public static List<PilgrimContractView> buildViews(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return List.of();
        }

        PlayerQuestData data = data(world, player.getUUID());
        PilgrimContractType activeType = activeType(data);
        if (activeType != null) {
            PilgrimContractDefinition definition = definition(activeType);
            if (definition == null) {
                return List.of();
            }
            boolean ready = definition.isComplete(world, player);
            return List.of(new PilgrimContractView(
                    activeType.id(),
                    definition.title(),
                    Component.translatable(
                            ready
                                    ? "screen.village-quest.pilgrim.contract.status.ready"
                                    : "screen.village-quest.pilgrim.contract.status.active"
                    ).withStyle(ready ? ChatFormatting.GOLD : ChatFormatting.GREEN),
                    List.of(Component.translatable(
                            ready
                                    ? "screen.village-quest.pilgrim.contract.ready"
                                    : "screen.village-quest.pilgrim.contract.active"
                    ).withStyle(ChatFormatting.GRAY)),
                    definition.progressLines(world, player),
                    rewardLines(world, player.getUUID(), definition.buildCompletion()),
                    Component.translatable(
                            ready
                                    ? "screen.village-quest.pilgrim.contract.action.claim"
                                    : "screen.village-quest.pilgrim.contract.action.in_progress"
                    ),
                    ready,
                    definition.previewStack()
            ));
        }

        List<PilgrimContractType> offeredTypes = ensureOfferedContracts(world, player);
        if (offeredTypes.isEmpty()) {
            return List.of();
        }

        List<PilgrimContractView> views = new ArrayList<>(offeredTypes.size());
        for (PilgrimContractType offeredType : offeredTypes) {
            PilgrimContractDefinition definition = definition(offeredType);
            if (definition == null) {
                continue;
            }
            views.add(new PilgrimContractView(
                    offeredType.id(),
                    definition.title(),
                    Component.translatable("screen.village-quest.pilgrim.contract.status.available").withStyle(ChatFormatting.YELLOW),
                    List.of(definition.offerParagraph1(), definition.offerParagraph2()),
                    definition.progressLines(world, player),
                    rewardLines(world, player.getUUID(), definition.buildCompletion()),
                    Component.translatable("screen.village-quest.pilgrim.contract.action.accept"),
                    true,
                    definition.previewStack()
            ));
        }
        return List.copyOf(views);
    }

    public static PilgrimContractStatus openStatus(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return null;
        }
        PlayerQuestData data = data(world, playerId);
        PilgrimContractType activeType = activeType(data);
        if (activeType == null) {
            return null;
        }
        PilgrimContractDefinition definition = definition(activeType);
        if (definition == null) {
            return null;
        }
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
        if (player == null) {
            return null;
        }
        return new PilgrimContractStatus(definition.title(), definition.progressLines(world, player));
    }

    public static boolean handleContractAction(ServerLevel world, ServerPlayer player, String contractId) {
        if (world == null || player == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        PilgrimContractType activeType = activeType(data);
        if (activeType != null) {
            PilgrimContractDefinition definition = definition(activeType);
            if (definition == null || !definition.isComplete(world, player)) {
                return false;
            }
            if (!definition.consumeCompletionRequirements(world, player)) {
                return false;
            }
            completeContract(world, player, definition.buildCompletion());
            return true;
        }

        PilgrimContractType offeredType = PilgrimContractType.fromId(contractId);
        if (offeredType == null || !ensureOfferedContracts(world, player).contains(offeredType)) {
            return false;
        }
        acceptContract(world, player, offeredType);
        return true;
    }

    public static void adminSetRumorUnlocked(ServerLevel world, ServerPlayer player, boolean enabled) {
        if (world == null || player == null) {
            return;
        }
        PlayerQuestData data = data(world, player.getUUID());
        data.setPilgrimFlag(FLAG_ADMIN_UNLOCK, enabled);
        if (activeType(data) == null) {
            data.setOfferedPilgrimContractId(null);
            data.setOfferedPilgrimContractAltId(null);
            data.setPilgrimOfferDay(PlayerQuestData.UNSET_DAY);
        }
        markDirty(world);
        refreshUi(world, player);
    }

    public static boolean rerollOffer(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        normalizeOfferState(data);
        if (activeType(data) != null || (data.hasPilgrimFlag(FLAG_SUPPRESS_OFFER) && !hasAdminUnlock(data))) {
            return false;
        }

        List<PilgrimContractType> eligible = eligibleContracts(world, player);
        if (eligible.isEmpty()) {
            return false;
        }

        List<PilgrimContractType> current = offeredTypes(data);
        List<PilgrimContractType> rolled = rollContracts(world, player, eligible, List.of());
        if (rolled.isEmpty()) {
            return false;
        }

        setOfferedContracts(data, rolled);
        data.setPilgrimOfferDay(TimeUtil.currentDay());
        markDirty(world);
        refreshUi(world, player);
        return !current.equals(rolled);
    }

    public static void onServerTick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        ServerLevel world = server.overworld();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            maybeUpdateReadyState(world, player);
        }
    }

    public static void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack stack) {
        PilgrimContractDefinition definition = activeDefinition(world, player);
        if (definition != null) {
            definition.onEntityUse(world, player, entity, stack);
        }
    }

    public static void onVillagerTrade(ServerLevel world, ServerPlayer player, ItemStack stack) {
        PilgrimContractDefinition definition = activeDefinition(world, player);
        if (definition != null) {
            definition.onVillagerTrade(world, player, stack);
        }
    }

    public static void onAnimalLove(ServerLevel world, ServerPlayer player, Animal animal) {
        PilgrimContractDefinition definition = activeDefinition(world, player);
        if (definition != null) {
            definition.onAnimalLove(world, player, animal);
        }
    }

    public static void onBeeNestInteract(ServerLevel world, ServerPlayer player, BlockState state, ItemStack stack) {
        PilgrimContractDefinition definition = activeDefinition(world, player);
        if (definition != null) {
            definition.onBeeNestInteract(world, player, state, stack);
        }
    }

    public static void onFurnaceOutput(ServerLevel world, ServerPlayer player, ItemStack stack) {
        PilgrimContractDefinition definition = activeDefinition(world, player);
        if (definition != null) {
            definition.onFurnaceOutput(world, player, stack);
        }
    }

    public static void onMonsterKill(ServerLevel world, ServerPlayer player, Entity killedEntity) {
        PilgrimContractDefinition definition = activeDefinition(world, player);
        if (definition != null) {
            definition.onMonsterKill(world, player, killedEntity);
        }
    }

    private static PlayerQuestData data(ServerLevel world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    private static Map<PilgrimContractType, PilgrimContractDefinition> createDefinitions() {
        Map<PilgrimContractType, PilgrimContractDefinition> definitions = new EnumMap<>(PilgrimContractType.class);
        definitions.put(PilgrimContractType.ROADMARKS_FOR_THE_COMPASS, createCompassContract());
        definitions.put(PilgrimContractType.QUENCH_FOR_THE_HALL, createMonsterContract(
                PilgrimContractType.QUENCH_FOR_THE_HALL,
                ChatFormatting.GREEN,
                new ItemStack(Items.LANTERN),
                CurrencyService.CROWN,
                10,
                6,
                new KillObjective(
                        KEY_LANTERN_SKELETONS,
                        LANTERN_SKELETON_TARGET,
                        (world, entity) -> world != null
                                && world.dimension() == Level.OVERWORLD
                                && world.isDarkOutside()
                                && entity instanceof net.minecraft.world.entity.monster.skeleton.Skeleton
                                && !(entity instanceof net.minecraft.world.entity.monster.skeleton.WitherSkeleton)
                )
        ));
        definitions.put(PilgrimContractType.WOOL_BEFORE_RAIN, createMonsterContract(
                PilgrimContractType.WOOL_BEFORE_RAIN,
                ChatFormatting.GREEN,
                new ItemStack(Items.CREEPER_HEAD),
                CurrencyService.SILVERMARK * 8L,
                10,
                6,
                new KillObjective(
                        KEY_SMOKE_CREEPERS,
                        SMOKE_CREEPER_TARGET,
                        (world, entity) -> world != null
                                && world.dimension() == Level.OVERWORLD
                                && world.isDarkOutside()
                                && entity instanceof net.minecraft.world.entity.monster.Creeper
                )
        ));
        definitions.put(PilgrimContractType.TRACKS_IN_THE_DARK, createMonsterContract(
                PilgrimContractType.TRACKS_IN_THE_DARK,
                ChatFormatting.GREEN,
                new ItemStack(Items.ZOMBIE_HEAD),
                CurrencyService.SILVERMARK * 8L,
                10,
                6,
                new KillObjective(
                        KEY_TRACKS_ZOMBIES,
                        TRACKS_ZOMBIE_TARGET,
                        (world, entity) -> world != null
                                && world.dimension() == Level.OVERWORLD
                                && world.isDarkOutside()
                                && entity instanceof net.minecraft.world.entity.monster.zombie.Zombie
                )
        ));
        definitions.put(PilgrimContractType.FANGS_BY_THE_HEDGEROW, createMonsterContract(
                PilgrimContractType.FANGS_BY_THE_HEDGEROW,
                ChatFormatting.GREEN,
                new ItemStack(Items.SPIDER_EYE),
                CurrencyService.SILVERMARK * 8L,
                10,
                6,
                new KillObjective(
                        KEY_FANGS_SPIDERS,
                        FANGS_SPIDER_TARGET,
                        (world, entity) -> world != null
                                && world.dimension() == Level.OVERWORLD
                                && world.isDarkOutside()
                                && entity instanceof net.minecraft.world.entity.monster.spider.Spider
                )
        ));
        definitions.put(PilgrimContractType.ASH_ON_THE_PASS, createMonsterContract(
                PilgrimContractType.ASH_ON_THE_PASS,
                ChatFormatting.RED,
                new ItemStack(Items.BLAZE_ROD),
                CurrencyService.CROWN * 2L,
                14,
                10,
                new KillObjective(
                        KEY_ASH_BLAZES,
                        ASH_BLAZE_TARGET,
                        (world, entity) -> world != null
                                && world.dimension() == Level.NETHER
                                && entity instanceof net.minecraft.world.entity.monster.Blaze
                ),
                new KillObjective(
                        KEY_ASH_WITHER_SKELETONS,
                        ASH_WITHER_SKELETON_TARGET,
                        (world, entity) -> world != null
                                && world.dimension() == Level.NETHER
                                && entity instanceof net.minecraft.world.entity.monster.skeleton.WitherSkeleton
                )
        ));
        definitions.put(PilgrimContractType.SMOKE_OVER_BLACKSTONE, createMonsterContract(
                PilgrimContractType.SMOKE_OVER_BLACKSTONE,
                ChatFormatting.RED,
                new ItemStack(Items.GHAST_TEAR),
                CurrencyService.CROWN * 2L,
                14,
                10,
                new KillObjective(
                        KEY_BLACKSTONE_MAGMA_CUBES,
                        BLACKSTONE_MAGMA_CUBE_TARGET,
                        (world, entity) -> world != null
                                && world.dimension() == Level.NETHER
                                && entity instanceof net.minecraft.world.entity.monster.MagmaCube
                ),
                new KillObjective(
                        KEY_BLACKSTONE_GHASTS,
                        BLACKSTONE_GHAST_TARGET,
                        (world, entity) -> world != null
                                && world.dimension() == Level.NETHER
                                && entity instanceof net.minecraft.world.entity.monster.Ghast
                )
        ));
        definitions.put(PilgrimContractType.STILLNESS_BEYOND_THE_GATE, createMonsterContract(
                PilgrimContractType.STILLNESS_BEYOND_THE_GATE,
                ChatFormatting.LIGHT_PURPLE,
                new ItemStack(Items.SHULKER_SHELL),
                CurrencyService.CROWN * 3L,
                20,
                14,
                new KillObjective(
                        KEY_STILLNESS_ENDERMEN,
                        STILLNESS_ENDERMAN_TARGET,
                        (world, entity) -> world != null
                                && world.dimension() == Level.END
                                && entity instanceof net.minecraft.world.entity.monster.EnderMan
                ),
                new KillObjective(
                        KEY_STILLNESS_SHULKERS,
                        STILLNESS_SHULKER_TARGET,
                        (world, entity) -> world != null
                                && world.dimension() == Level.END
                                && entity instanceof net.minecraft.world.entity.monster.Shulker
                )
        ));
        return definitions;
    }

    private static PilgrimContractDefinition createCompassContract() {
        return new PilgrimContractDefinition() {
            @Override
            public Component title() {
                return Component.translatable("quest.village-quest.pilgrim.contract.roadmarks_for_the_compass.title");
            }

            @Override
            public Component offerParagraph1() {
                return Component.translatable("quest.village-quest.pilgrim.contract.roadmarks_for_the_compass.offer.1").withStyle(ChatFormatting.GRAY);
            }

            @Override
            public Component offerParagraph2() {
                return Component.translatable("quest.village-quest.pilgrim.contract.roadmarks_for_the_compass.offer.2").withStyle(ChatFormatting.GRAY);
            }

            @Override
            public List<Component> progressLines(ServerLevel world, ServerPlayer player) {
                return SurveyorCompassQuestService.roadmarkProgressLines(world, player.getUUID());
            }

            @Override
            public boolean isComplete(ServerLevel world, ServerPlayer player) {
                return SurveyorCompassQuestService.hasRoadmarksReady(world, player);
            }

            @Override
            public PilgrimContractCompletion buildCompletion() {
                return new PilgrimContractCompletion(
                        title(),
                        Component.translatable("quest.village-quest.pilgrim.contract.roadmarks_for_the_compass.complete.1").withStyle(ChatFormatting.GRAY),
                        Component.translatable("quest.village-quest.pilgrim.contract.roadmarks_for_the_compass.complete.2").withStyle(ChatFormatting.GRAY),
                        Component.translatable("quest.village-quest.pilgrim.contract.roadmarks_for_the_compass.complete.3").withStyle(ChatFormatting.GRAY),
                        0L,
                        null,
                        0,
                        0,
                        Component.translatable("screen.village-quest.pilgrim.contract.reward.surveyor_compass_structures").withStyle(ChatFormatting.AQUA),
                        true
                );
            }

            @Override
            public ItemStack previewStack() {
                return ModItems.SURVEYORS_COMPASS == null
                        ? new ItemStack(Items.COMPASS)
                        : new ItemStack(ModItems.SURVEYORS_COMPASS);
            }

            @Override
            public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
                return hasInventoryItems(player, ModItems.SURVEYORS_COMPASS, 1);
            }
        };
    }

    private static PilgrimContractDefinition createMonsterContract(PilgrimContractType type,
                                                                   ChatFormatting titleColor,
                                                                   ItemStack previewStack,
                                                                   long currencyReward,
                                                                   int reputationReward,
                                                                   int levelReward,
                                                                   KillObjective... objectives) {
        return new PilgrimContractDefinition() {
            @Override
            public Component title() {
                return Component.translatable("quest.village-quest.pilgrim.contract." + type.id() + ".title").withStyle(titleColor);
            }

            @Override
            public Component offerParagraph1() {
                return Component.translatable("quest.village-quest.pilgrim.contract." + type.id() + ".offer.1").withStyle(ChatFormatting.GRAY);
            }

            @Override
            public Component offerParagraph2() {
                return Component.translatable("quest.village-quest.pilgrim.contract." + type.id() + ".offer.2").withStyle(ChatFormatting.GRAY);
            }

            @Override
            public List<Component> progressLines(ServerLevel world, ServerPlayer player) {
                return List.of(progressLine(type, world, player.getUUID(), ChatFormatting.GRAY, objectives));
            }

            @Override
            public boolean isComplete(ServerLevel world, ServerPlayer player) {
                UUID playerId = player.getUUID();
                for (KillObjective objective : objectives) {
                    if (pilgrimInt(world, playerId, objective.progressKey()) < objective.target()) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public PilgrimContractCompletion buildCompletion() {
                return new PilgrimContractCompletion(
                        title(),
                        Component.translatable("quest.village-quest.pilgrim.contract." + type.id() + ".complete.1").withStyle(ChatFormatting.GRAY),
                        Component.translatable("quest.village-quest.pilgrim.contract." + type.id() + ".complete.2").withStyle(ChatFormatting.GRAY),
                        Component.translatable("quest.village-quest.pilgrim.contract." + type.id() + ".complete.3").withStyle(ChatFormatting.GRAY),
                        currencyReward,
                        ReputationService.ReputationTrack.MONSTER_HUNTING,
                        reputationReward,
                        levelReward,
                        Component.empty(),
                        false
                );
            }

            @Override
            public ItemStack previewStack() {
                return previewStack.copy();
            }

            @Override
            public void onMonsterKill(ServerLevel world, ServerPlayer player, Entity killedEntity) {
                if (world == null || player == null || killedEntity == null) {
                    return;
                }
                UUID playerId = player.getUUID();
                boolean completedStep = false;
                boolean updated = false;
                for (KillObjective objective : objectives) {
                    if (!objective.matcher().matches(world, killedEntity)) {
                        continue;
                    }
                    int before = pilgrimInt(world, playerId, objective.progressKey());
                    if (before < objective.target()) {
                        data(world, playerId).setPilgrimInt(objective.progressKey(), Math.min(objective.target(), before + 1));
                        completedStep = completedStep || pilgrimInt(world, playerId, objective.progressKey()) >= objective.target();
                        updated = true;
                    }
                }
                if (!updated) {
                    return;
                }
                finishProgressUpdate(
                        world,
                        player,
                        progressLine(type, world, playerId, ChatFormatting.GOLD, objectives),
                        completedStep
                );
            }
        };
    }

    private static PilgrimContractDefinition definition(PilgrimContractType type) {
        return type == null ? null : DEFINITIONS.get(type);
    }

    private static PilgrimContractDefinition activeDefinition(ServerLevel world, ServerPlayer player) {
        return world == null || player == null ? null : definition(activeType(data(world, player.getUUID())));
    }

    private static PilgrimContractType activeType(PlayerQuestData data) {
        return data == null ? null : PilgrimContractType.fromId(data.getActivePilgrimContractId());
    }

    private static List<PilgrimContractType> offeredTypes(PlayerQuestData data) {
        if (data == null) {
            return List.of();
        }
        List<PilgrimContractType> offered = new ArrayList<>(2);
        PilgrimContractType primary = PilgrimContractType.fromId(data.getOfferedPilgrimContractId());
        PilgrimContractType secondary = PilgrimContractType.fromId(data.getOfferedPilgrimContractAltId());
        if (primary != null) {
            offered.add(primary);
        }
        if (secondary != null && secondary != primary) {
            offered.add(secondary);
        }
        return List.copyOf(offered);
    }

    private static void setOfferedContracts(PlayerQuestData data, List<PilgrimContractType> offered) {
        if (data == null || offered == null || offered.isEmpty()) {
            if (data != null) {
                data.setOfferedPilgrimContractId(null);
                data.setOfferedPilgrimContractAltId(null);
            }
            return;
        }

        PilgrimContractType primary = offered.getFirst();
        PilgrimContractType secondary = offered.size() > 1 ? offered.get(1) : null;
        data.setOfferedPilgrimContractId(primary == null ? null : primary.id());
        data.setOfferedPilgrimContractAltId(secondary == null ? null : secondary.id());
    }

    private static List<PilgrimContractType> ensureOfferedContracts(ServerLevel world, ServerPlayer player) {
        PlayerQuestData data = data(world, player.getUUID());
        normalizeOfferState(data);
        if (activeType(data) != null || (data.hasPilgrimFlag(FLAG_SUPPRESS_OFFER) && !hasAdminUnlock(data))) {
            return List.of();
        }

        List<PilgrimContractType> eligible = eligibleContracts(world, player);
        if (eligible.isEmpty()) {
            if (!offeredTypes(data).isEmpty()) {
                setOfferedContracts(data, List.of());
                markDirty(world);
            }
            return List.of();
        }

        List<PilgrimContractType> current = offeredTypes(data).stream()
                .filter(eligible::contains)
                .toList();
        List<PilgrimContractType> rolled = rollContracts(world, player, eligible, current);
        if (rolled.isEmpty()) {
            return List.of();
        }
        if (!current.equals(rolled)) {
            setOfferedContracts(data, rolled);
            data.setPilgrimOfferDay(TimeUtil.currentDay());
            markDirty(world);
        }
        return rolled;
    }

    private static void acceptContract(ServerLevel world, ServerPlayer player, PilgrimContractType type) {
        PlayerQuestData data = data(world, player.getUUID());
        data.clearPilgrimProgress();
        data.setPilgrimFlag(FLAG_READY, false);
        data.setPilgrimFlag(FLAG_SUPPRESS_OFFER, false);
        data.setActivePilgrimContractId(type.id());
        data.setOfferedPilgrimContractId(null);
        data.setOfferedPilgrimContractAltId(null);
        data.setPilgrimOfferDay(TimeUtil.currentDay());
        PilgrimContractDefinition definition = definition(type);
        if (definition != null) {
            definition.onAccepted(world, player);
        }
        markDirty(world);
        player.sendSystemMessage(Texts.acceptedTitle(
                definition == null ? Component.translatable("screen.village-quest.pilgrim.contract.header") : definition.title(),
                ChatFormatting.GOLD
        ), false);
        QuestTrackerService.enableForAcceptedQuest(world, player);
        maybeUpdateReadyState(world, player);
        refreshUi(world, player);
    }

    private static void completeContract(ServerLevel world, ServerPlayer player, PilgrimContractCompletion completion) {
        long actualCurrencyReward = completion.currencyReward() + VillageProjectService.bonusCurrency(world, player.getUUID(), completion.reputationTrack());
        if (actualCurrencyReward > 0L) {
            CurrencyService.addBalance(world, player.getUUID(), actualCurrencyReward);
        }
        int actualReputation = 0;
        if (completion.reputationTrack() != null && completion.reputationAmount() > 0) {
            actualReputation = VillageProjectService.applyReputationReward(world, player.getUUID(), completion.reputationTrack(), completion.reputationAmount());
        }
        int actualLevelReward = completion.levels() + VillageProjectService.bonusLevels(world, player.getUUID(), completion.reputationTrack());
        if (actualLevelReward > 0) {
            player.giveExperienceLevels(actualLevelReward);
        }
        boolean unlockedCompassStructures = completion.unlocksCompassStructures()
                && SurveyorCompassQuestService.unlockStructureModes(world, player);

        PlayerQuestData data = data(world, player.getUUID());
        data.clearPilgrimProgress();
        data.setPilgrimFlag(FLAG_READY, false);
        data.setPilgrimFlag(FLAG_SUPPRESS_OFFER, true);
        data.setActivePilgrimContractId(null);
        data.setOfferedPilgrimContractId(null);
        data.setOfferedPilgrimContractAltId(null);
        data.setPilgrimOfferDay(TimeUtil.currentDay());
        markDirty(world);

        Component divider = Component.literal("------------------------------").withStyle(ChatFormatting.GRAY);
        Component rewardsTitle = Component.translatable("text.village-quest.daily.rewards").withStyle(ChatFormatting.GRAY);
        MutableComponent rewardBody = Component.empty()
                .append(divider.copy()).append(Component.literal("\n"))
                .append(Texts.completedTitle(completion.title(), ChatFormatting.GOLD)).append(Component.literal("\n\n"))
                .append(completion.completionLine1()).append(Component.literal("\n"))
                .append(completion.completionLine2()).append(Component.literal("\n\n"))
                .append(completion.completionLine3()).append(Component.literal("\n\n"))
                .append(rewardsTitle).append(Component.literal(":\n\n"));

        appendCurrencyRewardLine(rewardBody, actualCurrencyReward);
        if (completion.reputationTrack() != null && actualReputation > 0) {
            appendTextRewardLine(rewardBody, ReputationService.formatRewardLine(completion.reputationTrack(), actualReputation));
        }
        appendTextRewardLine(rewardBody, VillageProjectService.formatBonusRewardLine(world, player.getUUID(), completion.reputationTrack()));
        appendTextRewardLine(rewardBody, VillageProjectService.formatRewardEchoLine(world, player.getUUID(), completion.reputationTrack()));
        if (!completion.specialRewardLine().getString().isEmpty() && (!completion.unlocksCompassStructures() || unlockedCompassStructures)) {
            appendTextRewardLine(rewardBody, completion.specialRewardLine());
        }
        if (actualLevelReward > 0) {
            appendTextRewardLine(rewardBody, Component.translatable("screen.village-quest.questmaster.reward.levels", actualLevelReward).withStyle(ChatFormatting.GREEN));
        }
        rewardBody.append(divider.copy());

        player.sendSystemMessage(rewardBody, false);
        world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.85f, 1.05f);
        refreshUi(world, player);
    }

    private static void maybeUpdateReadyState(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return;
        }
        PlayerQuestData data = data(world, player.getUUID());
        PilgrimContractType activeType = activeType(data);
        if (activeType == null) {
            if (data.hasPilgrimFlag(FLAG_READY)) {
                data.setPilgrimFlag(FLAG_READY, false);
                markDirty(world);
            }
            return;
        }

        PilgrimContractDefinition definition = definition(activeType);
        if (definition == null) {
            return;
        }
        boolean readyNow = definition.isComplete(world, player);
        boolean readyBefore = data.hasPilgrimFlag(FLAG_READY);
        if (readyNow == readyBefore) {
            return;
        }
        data.setPilgrimFlag(FLAG_READY, readyNow);
        markDirty(world);
        if (readyNow) {
            player.sendSystemMessage(Component.translatable("message.village-quest.pilgrim.contract.ready", definition.title()).withStyle(ChatFormatting.GOLD), false);
            world.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.25f, 1.7f);
        }
        refreshUi(world, player);
    }

    private static void finishProgressUpdate(ServerLevel world, ServerPlayer player, Component actionbar, boolean completedStep) {
        if (world == null || player == null || actionbar == null) {
            maybeUpdateReadyState(world, player);
            return;
        }
        markDirty(world);
        player.sendSystemMessage(actionbar, true);
        if (completedStep) {
            player.sendSystemMessage(Component.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).withStyle(ChatFormatting.GOLD), false);
        }
        world.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, completedStep ? 0.28f : 0.18f, completedStep ? 1.7f : 1.35f);
        maybeUpdateReadyState(world, player);
        refreshUi(world, player);
    }

    private static List<Component> rewardLines(ServerLevel world, UUID playerId, PilgrimContractCompletion completion) {
        List<Component> rewards = new ArrayList<>();
        if (completion == null) {
            return rewards;
        }
        if (completion.currencyReward() > 0L) {
            rewards.add(Component.translatable("screen.village-quest.questmaster.reward.currency", CurrencyService.formatBalance(completion.currencyReward())).withStyle(ChatFormatting.GOLD));
        }
        if (completion.reputationTrack() != null && completion.reputationAmount() > 0) {
            rewards.add(ReputationService.formatRewardLine(completion.reputationTrack(), completion.reputationAmount()));
            Component echo = VillageProjectService.formatQuestEchoLine(world, playerId, completion.reputationTrack());
            if (!echo.getString().isEmpty()) {
                rewards.add(echo);
            }
        }
        if (!completion.specialRewardLine().getString().isEmpty()) {
            rewards.add(completion.specialRewardLine());
        }
        if (completion.levels() > 0) {
            rewards.add(Component.translatable("screen.village-quest.questmaster.reward.levels", completion.levels()).withStyle(ChatFormatting.GREEN));
        }
        return rewards;
    }

    private static List<PilgrimContractType> eligibleContracts(ServerLevel world, ServerPlayer player) {
        List<PilgrimContractType> eligible = new ArrayList<>();
        if (world == null || player == null) {
            return eligible;
        }
        UUID playerId = player.getUUID();
        PlayerQuestData data = data(world, playerId);
        boolean adminUnlocked = hasAdminUnlock(data);
        for (PilgrimContractType type : PilgrimContractType.values()) {
            VillageProjectType project = type.requiredProject();
            if ((adminUnlocked || (project != null && VillageProjectService.isUnlocked(world, playerId, project)))
                    && isExtraEligible(world, player, type)) {
                eligible.add(type);
            }
        }
        return eligible;
    }

    private static PilgrimContractType priorityContract(ServerLevel world, ServerPlayer player) {
        if (isExtraEligible(world, player, PilgrimContractType.ROADMARKS_FOR_THE_COMPASS)
                && (hasAdminUnlock(data(world, player.getUUID()))
                || VillageProjectService.isUnlocked(world, player.getUUID(), PilgrimContractType.ROADMARKS_FOR_THE_COMPASS.requiredProject()))) {
            return PilgrimContractType.ROADMARKS_FOR_THE_COMPASS;
        }
        return null;
    }

    private static boolean isExtraEligible(ServerLevel world, ServerPlayer player, PilgrimContractType type) {
        if (world == null || player == null || type == null) {
            return false;
        }
        return switch (type) {
            case ROADMARKS_FOR_THE_COMPASS -> !SurveyorCompassQuestService.hasStructureModesUnlocked(world, player.getUUID())
                    && SurveyorCompassQuestService.hasBiomeModesUnlocked(world, player.getUUID())
                    && SurveyorCompassQuestService.isCompleted(world, player.getUUID());
            case QUENCH_FOR_THE_HALL,
                 WOOL_BEFORE_RAIN,
                 TRACKS_IN_THE_DARK,
                 FANGS_BY_THE_HEDGEROW,
                 ASH_ON_THE_PASS,
                 SMOKE_OVER_BLACKSTONE,
                 STILLNESS_BEYOND_THE_GATE -> true;
        };
    }

    private static boolean hasAdminUnlock(PlayerQuestData data) {
        return data != null && data.hasPilgrimFlag(FLAG_ADMIN_UNLOCK);
    }

    private static List<PilgrimContractType> rollContracts(ServerLevel world,
                                                           ServerPlayer player,
                                                           List<PilgrimContractType> eligible,
                                                           List<PilgrimContractType> current) {
        if (world == null || player == null || eligible == null || eligible.isEmpty()) {
            return List.of();
        }

        PilgrimContractType priority = priorityContract(world, player);
        if (priority != null && eligible.contains(priority)) {
            return List.of(priority);
        }

        List<PilgrimContractType> combatPool = eligible.stream()
                .filter(PilgrimContractService::isCombatContract)
                .toList();
        if (combatPool.isEmpty()) {
            return List.of();
        }

        if (current != null) {
            for (PilgrimContractType type : current) {
                if (type != null && combatPool.contains(type)) {
                    return List.of(type);
                }
            }
        }

        return List.of(combatPool.get(world.getRandom().nextInt(combatPool.size())));
    }

    private static void normalizeOfferState(PlayerQuestData data) {
        long currentDay = TimeUtil.currentDay();
        if (data.getPilgrimOfferDay() == currentDay) {
            return;
        }
        data.setOfferedPilgrimContractId(null);
        data.setOfferedPilgrimContractAltId(null);
        data.setPilgrimFlag(FLAG_SUPPRESS_OFFER, false);
        data.setPilgrimOfferDay(PlayerQuestData.UNSET_DAY);
    }

    private static int pilgrimInt(ServerLevel world, UUID playerId, String key) {
        return data(world, playerId).getPilgrimInt(key);
    }

    private static void markDirty(ServerLevel world) {
        if (world != null && world.getServer() != null) {
            QuestState.get(world.getServer()).setDirty();
        }
    }

    private static void refreshUi(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return;
        }
        QuestBookHelper.refreshQuestBook(world, player);
        QuestTrackerService.refresh(world, player);
        PilgrimService.refreshIfTrading(world, player);
    }

    private static void appendCurrencyRewardLine(MutableComponent body, long amount) {
        if (body == null || amount <= 0L) {
            return;
        }
        body.append(Component.empty().append(Component.literal("    ")).append(CurrencyService.formatDelta(amount))).append(Component.literal("\n"));
    }

    private static void appendTextRewardLine(MutableComponent body, Component line) {
        if (body == null || line == null || line.getString().isEmpty()) {
            return;
        }
        body.append(Component.empty().append(Component.literal("    ")).append(line)).append(Component.literal("\n"));
    }

    private static boolean hasInventoryItems(ServerPlayer player, Item item, int count) {
        return player != null && item != null && DailyQuestService.countInventoryItem(player, item) >= count;
    }

    private static boolean isCombatContract(PilgrimContractType type) {
        return type != null && type != PilgrimContractType.ROADMARKS_FOR_THE_COMPASS;
    }

    private static Component progressLine(PilgrimContractType type,
                                     ServerLevel world,
                                     UUID playerId,
                                     ChatFormatting color,
                                     KillObjective... objectives) {
        String baseKey = "quest.village-quest.pilgrim.contract." + type.id() + ".progress.1";
        return switch (objectives.length) {
            case 1 -> Component.translatable(
                    baseKey,
                    pilgrimInt(world, playerId, objectives[0].progressKey()),
                    objectives[0].target()
            ).withStyle(color);
            case 2 -> Component.translatable(
                    baseKey,
                    pilgrimInt(world, playerId, objectives[0].progressKey()),
                    objectives[0].target(),
                    pilgrimInt(world, playerId, objectives[1].progressKey()),
                    objectives[1].target()
            ).withStyle(color);
            default -> Component.empty();
        };
    }
}
