package de.quest.registry;

import de.quest.VillageQuest;
import de.quest.entity.CaravanMerchantEntity;
import de.quest.entity.CaravanPackMuleEntity;
import de.quest.entity.PilgrimEntity;
import de.quest.entity.QuestMasterEntity;
import de.quest.entity.TraitorEntity;
import de.quest.entity.KingdomNpcEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    private static final Identifier PILGRIM_ID = Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "pilgrim");
    private static final Identifier QUEST_MASTER_ID = Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "quest_master");
    private static final Identifier CARAVAN_MERCHANT_ID = Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "caravan_merchant");
    private static final Identifier CARAVAN_PACK_MULE_ID = Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "caravan_pack_mule");
    private static final Identifier TRAITOR_ID = Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "traitor");
    private static final ResourceKey<EntityType<?>> PILGRIM_KEY = ResourceKey.create(Registries.ENTITY_TYPE, PILGRIM_ID);
    private static final ResourceKey<EntityType<?>> QUEST_MASTER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, QUEST_MASTER_ID);
    private static final ResourceKey<EntityType<?>> CARAVAN_MERCHANT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, CARAVAN_MERCHANT_ID);
    private static final ResourceKey<EntityType<?>> CARAVAN_PACK_MULE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, CARAVAN_PACK_MULE_ID);
    private static final ResourceKey<EntityType<?>> TRAITOR_KEY = ResourceKey.create(Registries.ENTITY_TYPE, TRAITOR_ID);

    public static final EntityType<PilgrimEntity> PILGRIM = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            PILGRIM_ID,
            EntityType.Builder.<PilgrimEntity>of(PilgrimEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.8f)
                    .build(PILGRIM_KEY)
    );
    public static final EntityType<QuestMasterEntity> QUEST_MASTER = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            QUEST_MASTER_ID,
            EntityType.Builder.<QuestMasterEntity>of(QuestMasterEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.8f)
                    .build(QUEST_MASTER_KEY)
    );
    public static final EntityType<CaravanMerchantEntity> CARAVAN_MERCHANT = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            CARAVAN_MERCHANT_ID,
            EntityType.Builder.<CaravanMerchantEntity>of(CaravanMerchantEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.8f)
                    .build(CARAVAN_MERCHANT_KEY)
    );
    public static final EntityType<CaravanPackMuleEntity> CARAVAN_PACK_MULE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            CARAVAN_PACK_MULE_ID,
            EntityType.Builder.<CaravanPackMuleEntity>of(CaravanPackMuleEntity::new, MobCategory.CREATURE)
                    .sized(1.3965f, 1.6f)
                    .build(CARAVAN_PACK_MULE_KEY)
    );
    public static final EntityType<TraitorEntity> TRAITOR = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            TRAITOR_ID,
            EntityType.Builder.<TraitorEntity>of(TraitorEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .build(TRAITOR_KEY)
    );

    private ModEntities() {}

    public static final EntityType<de.quest.entity.AmbientAdventurerEntity> AMBIENT_ADVENTURER=Registry.register(
            BuiltInRegistries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID,"ambient_adventurer"),
            EntityType.Builder.<de.quest.entity.AmbientAdventurerEntity>of(de.quest.entity.AmbientAdventurerEntity::new,MobCategory.CREATURE)
                    .sized(.6f,1.8f).noSave().noLootTable().build(ResourceKey.create(Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID,"ambient_adventurer"))));

    public static final EntityType<KingdomNpcEntity> KINGDOM_NPC=Registry.register(BuiltInRegistries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID,"kingdom_npc"),
            EntityType.Builder.<KingdomNpcEntity>of(KingdomNpcEntity::new,MobCategory.CREATURE).sized(0.6f,1.8f)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID,"kingdom_npc"))));

    public static void register() {
        FabricDefaultAttributeRegistry.register(AMBIENT_ADVENTURER,de.quest.entity.AmbientAdventurerEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(PILGRIM, PilgrimEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(QUEST_MASTER, QuestMasterEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(CARAVAN_MERCHANT, CaravanMerchantEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(CARAVAN_PACK_MULE, CaravanPackMuleEntity.createBaseHorseAttributes());
        FabricDefaultAttributeRegistry.register(TRAITOR, TraitorEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(KINGDOM_NPC,KingdomNpcEntity.createAttributes());
        VillageQuest.LOGGER.info("Registered entities");
    }
}
