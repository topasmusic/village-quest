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
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

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
    private static final String FLAG_COMPLETED_PREFIX = "pilgrim_contract_completed.";

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
            Text title,
            Text status,
            List<Text> descriptionLines,
            List<Text> objectiveLines,
            List<Text> rewardLines,
            Text actionLabel,
            boolean actionEnabled,
            ItemStack previewStack
    ) {}

    public record PilgrimContractStatus(Text title, List<Text> lines) {}

    private record PilgrimContractCompletion(
            Text title,
            Text completionLine1,
            Text completionLine2,
            Text completionLine3,
            long currencyReward,
            ReputationService.ReputationTrack reputationTrack,
            int reputationAmount,
            int levels,
            Text specialRewardLine,
            boolean unlocksCompassStructures
    ) {}

    @FunctionalInterface
    private interface KillMatcher {
        boolean matches(ServerWorld world, Entity entity);
    }

    private record KillObjective(String progressKey, int target, KillMatcher matcher) {}

    private interface PilgrimContractDefinition {
        Text title();

        Text offerParagraph1();

        Text offerParagraph2();

        List<Text> progressLines(ServerWorld world, ServerPlayerEntity player);

        boolean isComplete(ServerWorld world, ServerPlayerEntity player);

        PilgrimContractCompletion buildCompletion();

        ItemStack previewStack();

        default boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
            return true;
        }

        default void onAccepted(ServerWorld world, ServerPlayerEntity player) {}

        default void onEntityUse(ServerWorld world, ServerPlayerEntity player, Entity entity, ItemStack stack) {}

        default void onVillagerTrade(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {}

        default void onAnimalLove(ServerWorld world, ServerPlayerEntity player, AnimalEntity animal) {}

        default void onBeeNestInteract(ServerWorld world, ServerPlayerEntity player, BlockState state, ItemStack stack) {}

        default void onFurnaceOutput(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {}

        default void onMonsterKill(ServerWorld world, ServerPlayerEntity player, Entity killedEntity) {}
    }

    public static List<PilgrimContractView> buildViews(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return List.of();
        }

        PlayerQuestData data = data(world, player.getUuid());
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
                    Text.translatable(
                            ready
                                    ? "screen.village-quest.pilgrim.contract.status.ready"
                                    : "screen.village-quest.pilgrim.contract.status.active"
                    ).formatted(ready ? Formatting.GOLD : Formatting.GREEN),
                    List.of(Text.translatable(
                            ready
                                    ? "screen.village-quest.pilgrim.contract.ready"
                                    : "screen.village-quest.pilgrim.contract.active"
                    ).formatted(Formatting.GRAY)),
                    definition.progressLines(world, player),
                    rewardLines(world, player.getUuid(), definition.buildCompletion()),
                    Text.translatable(
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
                    Text.translatable("screen.village-quest.pilgrim.contract.status.available").formatted(Formatting.YELLOW),
                    List.of(definition.offerParagraph1(), definition.offerParagraph2()),
                    definition.progressLines(world, player),
                    rewardLines(world, player.getUuid(), definition.buildCompletion()),
                    Text.translatable("screen.village-quest.pilgrim.contract.action.accept"),
                    true,
                    definition.previewStack()
            ));
        }
        return List.copyOf(views);
    }

    public static PilgrimContractStatus openStatus(ServerWorld world, UUID playerId) {
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
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
        if (player == null) {
            return null;
        }
        return new PilgrimContractStatus(definition.title(), definition.progressLines(world, player));
    }

    public static boolean handleContractAction(ServerWorld world, ServerPlayerEntity player, String contractId) {
        if (world == null || player == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
        PilgrimContractType activeType = activeType(data);
        if (activeType != null) {
            PilgrimContractDefinition definition = definition(activeType);
            if (definition == null || !definition.isComplete(world, player)) {
                return false;
            }
            if (!definition.consumeCompletionRequirements(world, player)) {
                return false;
            }
            completeContract(world, player, activeType, definition.buildCompletion());
            return true;
        }

        PilgrimContractType offeredType = PilgrimContractType.fromId(contractId);
        if (offeredType == null || !ensureOfferedContracts(world, player).contains(offeredType)) {
            return false;
        }
        acceptContract(world, player, offeredType);
        return true;
    }

    public static void adminSetRumorUnlocked(ServerWorld world, ServerPlayerEntity player, boolean enabled) {
        if (world == null || player == null) {
            return;
        }
        PlayerQuestData data = data(world, player.getUuid());
        data.setPilgrimFlag(FLAG_ADMIN_UNLOCK, enabled);
        if (activeType(data) == null) {
            data.setOfferedPilgrimContractId(null);
            data.setOfferedPilgrimContractAltId(null);
            data.setPilgrimOfferDay(PlayerQuestData.UNSET_DAY);
        }
        markDirty(world);
        refreshUi(world, player);
    }

    public static boolean rerollOffer(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
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
        ServerWorld world = server.getOverworld();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            maybeUpdateReadyState(world, player);
        }
    }

    public static void onEntityUse(ServerWorld world, ServerPlayerEntity player, Entity entity, ItemStack stack) {
        PilgrimContractDefinition definition = activeDefinition(world, player);
        if (definition != null) {
            definition.onEntityUse(world, player, entity, stack);
        }
    }

    public static void onVillagerTrade(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
        PilgrimContractDefinition definition = activeDefinition(world, player);
        if (definition != null) {
            definition.onVillagerTrade(world, player, stack);
        }
    }

    public static void onAnimalLove(ServerWorld world, ServerPlayerEntity player, AnimalEntity animal) {
        PilgrimContractDefinition definition = activeDefinition(world, player);
        if (definition != null) {
            definition.onAnimalLove(world, player, animal);
        }
    }

    public static void onBeeNestInteract(ServerWorld world, ServerPlayerEntity player, BlockState state, ItemStack stack) {
        PilgrimContractDefinition definition = activeDefinition(world, player);
        if (definition != null) {
            definition.onBeeNestInteract(world, player, state, stack);
        }
    }

    public static void onFurnaceOutput(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
        PilgrimContractDefinition definition = activeDefinition(world, player);
        if (definition != null) {
            definition.onFurnaceOutput(world, player, stack);
        }
    }

    public static void onMonsterKill(ServerWorld world, ServerPlayerEntity player, Entity killedEntity) {
        PilgrimContractDefinition definition = activeDefinition(world, player);
        if (definition != null) {
            definition.onMonsterKill(world, player, killedEntity);
        }
    }

    private static PlayerQuestData data(ServerWorld world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    private static Map<PilgrimContractType, PilgrimContractDefinition> createDefinitions() {
        Map<PilgrimContractType, PilgrimContractDefinition> definitions = new EnumMap<>(PilgrimContractType.class);
        definitions.put(PilgrimContractType.ROADMARKS_FOR_THE_COMPASS, createCompassContract());
        definitions.put(PilgrimContractType.QUENCH_FOR_THE_HALL, createMonsterContract(
                PilgrimContractType.QUENCH_FOR_THE_HALL,
                Formatting.GREEN,
                new ItemStack(Items.LANTERN),
                CurrencyService.CROWN,
                10,
                6,
                new KillObjective(
                        KEY_LANTERN_SKELETONS,
                        LANTERN_SKELETON_TARGET,
                        (world, entity) -> world != null
                                && world.getRegistryKey() == World.OVERWORLD
                                && world.isNight()
                                && entity instanceof net.minecraft.entity.mob.SkeletonEntity
                                && !(entity instanceof net.minecraft.entity.mob.WitherSkeletonEntity)
                )
        ));
        definitions.put(PilgrimContractType.WOOL_BEFORE_RAIN, createMonsterContract(
                PilgrimContractType.WOOL_BEFORE_RAIN,
                Formatting.GREEN,
                new ItemStack(Items.CREEPER_HEAD),
                CurrencyService.SILVERMARK * 8L,
                10,
                6,
                new KillObjective(
                        KEY_SMOKE_CREEPERS,
                        SMOKE_CREEPER_TARGET,
                        (world, entity) -> world != null
                                && world.getRegistryKey() == World.OVERWORLD
                                && world.isNight()
                                && entity instanceof net.minecraft.entity.mob.CreeperEntity
                )
        ));
        definitions.put(PilgrimContractType.TRACKS_IN_THE_DARK, createMonsterContract(
                PilgrimContractType.TRACKS_IN_THE_DARK,
                Formatting.GREEN,
                new ItemStack(Items.ZOMBIE_HEAD),
                CurrencyService.SILVERMARK * 8L,
                10,
                6,
                new KillObjective(
                        KEY_TRACKS_ZOMBIES,
                        TRACKS_ZOMBIE_TARGET,
                        (world, entity) -> world != null
                                && world.getRegistryKey() == World.OVERWORLD
                                && world.isNight()
                                && entity instanceof net.minecraft.entity.mob.ZombieEntity
                )
        ));
        definitions.put(PilgrimContractType.FANGS_BY_THE_HEDGEROW, createMonsterContract(
                PilgrimContractType.FANGS_BY_THE_HEDGEROW,
                Formatting.GREEN,
                new ItemStack(Items.SPIDER_EYE),
                CurrencyService.SILVERMARK * 8L,
                10,
                6,
                new KillObjective(
                        KEY_FANGS_SPIDERS,
                        FANGS_SPIDER_TARGET,
                        (world, entity) -> world != null
                                && world.getRegistryKey() == World.OVERWORLD
                                && world.isNight()
                                && entity instanceof net.minecraft.entity.mob.SpiderEntity
                )
        ));
        definitions.put(PilgrimContractType.ASH_ON_THE_PASS, createMonsterContract(
                PilgrimContractType.ASH_ON_THE_PASS,
                Formatting.RED,
                new ItemStack(Items.BLAZE_ROD),
                CurrencyService.CROWN * 2L,
                14,
                10,
                new KillObjective(
                        KEY_ASH_BLAZES,
                        ASH_BLAZE_TARGET,
                        (world, entity) -> world != null
                                && world.getRegistryKey() == World.NETHER
                                && entity instanceof net.minecraft.entity.mob.BlazeEntity
                ),
                new KillObjective(
                        KEY_ASH_WITHER_SKELETONS,
                        ASH_WITHER_SKELETON_TARGET,
                        (world, entity) -> world != null
                                && world.getRegistryKey() == World.NETHER
                                && entity instanceof net.minecraft.entity.mob.WitherSkeletonEntity
                )
        ));
        definitions.put(PilgrimContractType.SMOKE_OVER_BLACKSTONE, createMonsterContract(
                PilgrimContractType.SMOKE_OVER_BLACKSTONE,
                Formatting.RED,
                new ItemStack(Items.GHAST_TEAR),
                CurrencyService.CROWN * 2L,
                14,
                10,
                new KillObjective(
                        KEY_BLACKSTONE_MAGMA_CUBES,
                        BLACKSTONE_MAGMA_CUBE_TARGET,
                        (world, entity) -> world != null
                                && world.getRegistryKey() == World.NETHER
                                && entity instanceof net.minecraft.entity.mob.MagmaCubeEntity
                ),
                new KillObjective(
                        KEY_BLACKSTONE_GHASTS,
                        BLACKSTONE_GHAST_TARGET,
                        (world, entity) -> world != null
                                && world.getRegistryKey() == World.NETHER
                                && entity instanceof net.minecraft.entity.mob.GhastEntity
                )
        ));
        definitions.put(PilgrimContractType.STILLNESS_BEYOND_THE_GATE, createMonsterContract(
                PilgrimContractType.STILLNESS_BEYOND_THE_GATE,
                Formatting.LIGHT_PURPLE,
                new ItemStack(Items.SHULKER_SHELL),
                CurrencyService.CROWN * 3L,
                20,
                14,
                new KillObjective(
                        KEY_STILLNESS_ENDERMEN,
                        STILLNESS_ENDERMAN_TARGET,
                        (world, entity) -> world != null
                                && world.getRegistryKey() == World.END
                                && entity instanceof net.minecraft.entity.mob.EndermanEntity
                ),
                new KillObjective(
                        KEY_STILLNESS_SHULKERS,
                        STILLNESS_SHULKER_TARGET,
                        (world, entity) -> world != null
                                && world.getRegistryKey() == World.END
                                && entity instanceof net.minecraft.entity.mob.ShulkerEntity
                )
        ));
        return definitions;
    }

    private static PilgrimContractDefinition createCompassContract() {
        return new PilgrimContractDefinition() {
            @Override
            public Text title() {
                return Text.translatable("quest.village-quest.pilgrim.contract.roadmarks_for_the_compass.title");
            }

            @Override
            public Text offerParagraph1() {
                return Text.translatable("quest.village-quest.pilgrim.contract.roadmarks_for_the_compass.offer.1").formatted(Formatting.GRAY);
            }

            @Override
            public Text offerParagraph2() {
                return Text.translatable("quest.village-quest.pilgrim.contract.roadmarks_for_the_compass.offer.2").formatted(Formatting.GRAY);
            }

            @Override
            public List<Text> progressLines(ServerWorld world, ServerPlayerEntity player) {
                return SurveyorCompassQuestService.roadmarkProgressLines(world, player.getUuid());
            }

            @Override
            public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
                return SurveyorCompassQuestService.hasRoadmarksReady(world, player);
            }

            @Override
            public PilgrimContractCompletion buildCompletion() {
                return new PilgrimContractCompletion(
                        title(),
                        Text.translatable("quest.village-quest.pilgrim.contract.roadmarks_for_the_compass.complete.1").formatted(Formatting.GRAY),
                        Text.translatable("quest.village-quest.pilgrim.contract.roadmarks_for_the_compass.complete.2").formatted(Formatting.GRAY),
                        Text.translatable("quest.village-quest.pilgrim.contract.roadmarks_for_the_compass.complete.3").formatted(Formatting.GRAY),
                        0L,
                        null,
                        0,
                        0,
                        Text.translatable("screen.village-quest.pilgrim.contract.reward.surveyor_compass_structures").formatted(Formatting.AQUA),
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
            public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
                return hasInventoryItems(player, ModItems.SURVEYORS_COMPASS, 1);
            }
        };
    }

    private static PilgrimContractDefinition createMonsterContract(PilgrimContractType type,
                                                                   Formatting titleColor,
                                                                   ItemStack previewStack,
                                                                   long currencyReward,
                                                                   int reputationReward,
                                                                   int levelReward,
                                                                   KillObjective... objectives) {
        return new PilgrimContractDefinition() {
            @Override
            public Text title() {
                return Text.translatable("quest.village-quest.pilgrim.contract." + type.id() + ".title").formatted(titleColor);
            }

            @Override
            public Text offerParagraph1() {
                return Text.translatable("quest.village-quest.pilgrim.contract." + type.id() + ".offer.1").formatted(Formatting.GRAY);
            }

            @Override
            public Text offerParagraph2() {
                return Text.translatable("quest.village-quest.pilgrim.contract." + type.id() + ".offer.2").formatted(Formatting.GRAY);
            }

            @Override
            public List<Text> progressLines(ServerWorld world, ServerPlayerEntity player) {
                return List.of(progressLine(type, world, player.getUuid(), Formatting.GRAY, objectives));
            }

            @Override
            public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
                UUID playerId = player.getUuid();
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
                        Text.translatable("quest.village-quest.pilgrim.contract." + type.id() + ".complete.1").formatted(Formatting.GRAY),
                        Text.translatable("quest.village-quest.pilgrim.contract." + type.id() + ".complete.2").formatted(Formatting.GRAY),
                        Text.translatable("quest.village-quest.pilgrim.contract." + type.id() + ".complete.3").formatted(Formatting.GRAY),
                        currencyReward,
                        ReputationService.ReputationTrack.MONSTER_HUNTING,
                        reputationReward,
                        levelReward,
                        Text.empty(),
                        false
                );
            }

            @Override
            public ItemStack previewStack() {
                return previewStack.copy();
            }

            @Override
            public void onMonsterKill(ServerWorld world, ServerPlayerEntity player, Entity killedEntity) {
                if (world == null || player == null || killedEntity == null) {
                    return;
                }
                UUID playerId = player.getUuid();
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
                        progressLine(type, world, playerId, Formatting.GOLD, objectives),
                        completedStep
                );
            }
        };
    }

    private static PilgrimContractDefinition definition(PilgrimContractType type) {
        return type == null ? null : DEFINITIONS.get(type);
    }

    private static PilgrimContractDefinition activeDefinition(ServerWorld world, ServerPlayerEntity player) {
        return world == null || player == null ? null : definition(activeType(data(world, player.getUuid())));
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

    private static List<PilgrimContractType> ensureOfferedContracts(ServerWorld world, ServerPlayerEntity player) {
        PlayerQuestData data = data(world, player.getUuid());
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

    private static void acceptContract(ServerWorld world, ServerPlayerEntity player, PilgrimContractType type) {
        PlayerQuestData data = data(world, player.getUuid());
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
        player.sendMessage(Texts.acceptedTitle(
                definition == null ? Text.translatable("screen.village-quest.pilgrim.contract.header") : definition.title(),
                Formatting.GOLD
        ), false);
        QuestTrackerService.enableForAcceptedQuest(world, player);
        maybeUpdateReadyState(world, player);
        refreshUi(world, player);
    }

    public static boolean hasCompletedContract(ServerWorld world, UUID playerId, PilgrimContractType type) {
        if (world == null || playerId == null || type == null) {
            return false;
        }
        return data(world, playerId).hasMilestoneFlag(completedFlag(type));
    }

    public static int completedCombatContractCount(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return 0;
        }
        int count = 0;
        for (PilgrimContractType type : PilgrimContractType.values()) {
            if (isCombatContract(type) && hasCompletedContract(world, playerId, type)) {
                count++;
            }
        }
        return count;
    }

    private static void completeContract(ServerWorld world,
                                         ServerPlayerEntity player,
                                         PilgrimContractType type,
                                         PilgrimContractCompletion completion) {
        long actualCurrencyReward = completion.currencyReward() + VillageProjectService.bonusCurrency(world, player.getUuid(), completion.reputationTrack());
        if (actualCurrencyReward > 0L) {
            CurrencyService.addBalance(world, player.getUuid(), actualCurrencyReward);
        }
        int actualReputation = 0;
        if (completion.reputationTrack() != null && completion.reputationAmount() > 0) {
            actualReputation = VillageProjectService.applyReputationReward(world, player.getUuid(), completion.reputationTrack(), completion.reputationAmount());
        }
        int actualLevelReward = completion.levels() + VillageProjectService.bonusLevels(world, player.getUuid(), completion.reputationTrack());
        if (actualLevelReward > 0) {
            player.addExperienceLevels(actualLevelReward);
        }
        boolean unlockedCompassStructures = completion.unlocksCompassStructures()
                && SurveyorCompassQuestService.unlockStructureModes(world, player);

        PlayerQuestData data = data(world, player.getUuid());
        if (isCombatContract(type)) {
            data.setMilestoneFlag(completedFlag(type), true);
        }
        data.clearPilgrimProgress();
        data.setPilgrimFlag(FLAG_READY, false);
        data.setPilgrimFlag(FLAG_SUPPRESS_OFFER, true);
        data.setActivePilgrimContractId(null);
        data.setOfferedPilgrimContractId(null);
        data.setOfferedPilgrimContractAltId(null);
        data.setPilgrimOfferDay(TimeUtil.currentDay());
        markDirty(world);

        Text divider = Text.literal("------------------------------").formatted(Formatting.GRAY);
        Text rewardsTitle = Text.translatable("text.village-quest.daily.rewards").formatted(Formatting.GRAY);
        MutableText rewardBody = Text.empty()
                .append(divider.copy()).append(Text.literal("\n"))
                .append(Texts.completedTitle(completion.title(), Formatting.GOLD)).append(Text.literal("\n\n"))
                .append(completion.completionLine1()).append(Text.literal("\n"))
                .append(completion.completionLine2()).append(Text.literal("\n\n"))
                .append(completion.completionLine3()).append(Text.literal("\n\n"))
                .append(rewardsTitle).append(Text.literal(":\n\n"));

        appendCurrencyRewardLine(rewardBody, actualCurrencyReward);
        if (completion.reputationTrack() != null && actualReputation > 0) {
            appendTextRewardLine(rewardBody, ReputationService.formatRewardLine(completion.reputationTrack(), actualReputation));
        }
        appendTextRewardLine(rewardBody, VillageProjectService.formatBonusRewardLine(world, player.getUuid(), completion.reputationTrack()));
        appendTextRewardLine(rewardBody, VillageProjectService.formatRewardEchoLine(world, player.getUuid(), completion.reputationTrack()));
        if (!completion.specialRewardLine().getString().isEmpty() && (!completion.unlocksCompassStructures() || unlockedCompassStructures)) {
            appendTextRewardLine(rewardBody, completion.specialRewardLine());
        }
        if (actualLevelReward > 0) {
            appendTextRewardLine(rewardBody, Text.translatable("screen.village-quest.questmaster.reward.levels", actualLevelReward).formatted(Formatting.GREEN));
        }
        rewardBody.append(divider.copy());

        player.sendMessage(rewardBody, false);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.85f, 1.05f);
        refreshUi(world, player);
    }

    private static void maybeUpdateReadyState(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return;
        }
        PlayerQuestData data = data(world, player.getUuid());
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
            player.sendMessage(Text.translatable("message.village-quest.pilgrim.contract.ready", definition.title()).formatted(Formatting.GOLD), false);
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.25f, 1.7f);
        }
        refreshUi(world, player);
    }

    private static void finishProgressUpdate(ServerWorld world, ServerPlayerEntity player, Text actionbar, boolean completedStep) {
        if (world == null || player == null || actionbar == null) {
            maybeUpdateReadyState(world, player);
            return;
        }
        markDirty(world);
        player.sendMessage(actionbar, true);
        if (completedStep) {
            player.sendMessage(Text.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).formatted(Formatting.GOLD), false);
        }
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, completedStep ? 0.28f : 0.18f, completedStep ? 1.7f : 1.35f);
        maybeUpdateReadyState(world, player);
        refreshUi(world, player);
    }

    private static List<Text> rewardLines(ServerWorld world, UUID playerId, PilgrimContractCompletion completion) {
        List<Text> rewards = new ArrayList<>();
        if (completion == null) {
            return rewards;
        }
        if (completion.currencyReward() > 0L) {
            rewards.add(Text.translatable("screen.village-quest.questmaster.reward.currency", CurrencyService.formatBalance(completion.currencyReward())).formatted(Formatting.GOLD));
        }
        if (completion.reputationTrack() != null && completion.reputationAmount() > 0) {
            rewards.add(ReputationService.formatRewardLine(completion.reputationTrack(), completion.reputationAmount()));
            Text echo = VillageProjectService.formatQuestEchoLine(world, playerId, completion.reputationTrack());
            if (!echo.getString().isEmpty()) {
                rewards.add(echo);
            }
        }
        if (!completion.specialRewardLine().getString().isEmpty()) {
            rewards.add(completion.specialRewardLine());
        }
        if (completion.levels() > 0) {
            rewards.add(Text.translatable("screen.village-quest.questmaster.reward.levels", completion.levels()).formatted(Formatting.GREEN));
        }
        return rewards;
    }

    private static List<PilgrimContractType> eligibleContracts(ServerWorld world, ServerPlayerEntity player) {
        List<PilgrimContractType> eligible = new ArrayList<>();
        if (world == null || player == null) {
            return eligible;
        }
        UUID playerId = player.getUuid();
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

    private static PilgrimContractType priorityContract(ServerWorld world, ServerPlayerEntity player) {
        if (isExtraEligible(world, player, PilgrimContractType.ROADMARKS_FOR_THE_COMPASS)
                && (hasAdminUnlock(data(world, player.getUuid()))
                || VillageProjectService.isUnlocked(world, player.getUuid(), PilgrimContractType.ROADMARKS_FOR_THE_COMPASS.requiredProject()))) {
            return PilgrimContractType.ROADMARKS_FOR_THE_COMPASS;
        }
        return null;
    }

    private static boolean isExtraEligible(ServerWorld world, ServerPlayerEntity player, PilgrimContractType type) {
        if (world == null || player == null || type == null) {
            return false;
        }
        return switch (type) {
            case ROADMARKS_FOR_THE_COMPASS -> !SurveyorCompassQuestService.hasStructureModesUnlocked(world, player.getUuid())
                    && SurveyorCompassQuestService.hasBiomeModesUnlocked(world, player.getUuid())
                    && SurveyorCompassQuestService.isCompleted(world, player.getUuid());
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

    private static List<PilgrimContractType> rollContracts(ServerWorld world,
                                                           ServerPlayerEntity player,
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

        return List.of(combatPool.get(world.random.nextInt(combatPool.size())));
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

    private static int pilgrimInt(ServerWorld world, UUID playerId, String key) {
        return data(world, playerId).getPilgrimInt(key);
    }

    private static void markDirty(ServerWorld world) {
        if (world != null && world.getServer() != null) {
            QuestState.get(world.getServer()).markDirty();
        }
    }

    private static void refreshUi(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return;
        }
        QuestBookHelper.refreshQuestBook(world, player);
        QuestTrackerService.refresh(world, player);
        PilgrimService.refreshIfTrading(world, player);
    }

    private static void appendCurrencyRewardLine(MutableText body, long amount) {
        if (body == null || amount <= 0L) {
            return;
        }
        body.append(Text.empty().append(Text.literal("    ")).append(CurrencyService.formatDelta(amount))).append(Text.literal("\n"));
    }

    private static void appendTextRewardLine(MutableText body, Text line) {
        if (body == null || line == null || line.getString().isEmpty()) {
            return;
        }
        body.append(Text.empty().append(Text.literal("    ")).append(line)).append(Text.literal("\n"));
    }

    private static boolean hasInventoryItems(ServerPlayerEntity player, Item item, int count) {
        return player != null && item != null && DailyQuestService.countInventoryItem(player, item) >= count;
    }

    private static boolean isCombatContract(PilgrimContractType type) {
        return type != null && type != PilgrimContractType.ROADMARKS_FOR_THE_COMPASS;
    }

    private static String completedFlag(PilgrimContractType type) {
        return FLAG_COMPLETED_PREFIX + type.id();
    }

    private static Text progressLine(PilgrimContractType type,
                                     ServerWorld world,
                                     UUID playerId,
                                     Formatting color,
                                     KillObjective... objectives) {
        String baseKey = "quest.village-quest.pilgrim.contract." + type.id() + ".progress.1";
        return switch (objectives.length) {
            case 1 -> Text.translatable(
                    baseKey,
                    pilgrimInt(world, playerId, objectives[0].progressKey()),
                    objectives[0].target()
            ).formatted(color);
            case 2 -> Text.translatable(
                    baseKey,
                    pilgrimInt(world, playerId, objectives[0].progressKey()),
                    objectives[0].target(),
                    pilgrimInt(world, playerId, objectives[1].progressKey()),
                    objectives[1].target()
            ).formatted(color);
            default -> Text.empty();
        };
    }
}
