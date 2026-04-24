package de.quest.registry;

import de.quest.VillageQuest;
import de.quest.entity.CaravanMerchantEntity;
import de.quest.entity.PilgrimEntity;
import de.quest.entity.QuestMasterEntity;
import de.quest.entity.TraitorEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModEntities {
    private static final Identifier PILGRIM_ID = Identifier.of(VillageQuest.MOD_ID, "pilgrim");
    private static final Identifier QUEST_MASTER_ID = Identifier.of(VillageQuest.MOD_ID, "quest_master");
    private static final Identifier CARAVAN_MERCHANT_ID = Identifier.of(VillageQuest.MOD_ID, "caravan_merchant");
    private static final Identifier TRAITOR_ID = Identifier.of(VillageQuest.MOD_ID, "traitor");
    private static final RegistryKey<EntityType<?>> PILGRIM_KEY = RegistryKey.of(RegistryKeys.ENTITY_TYPE, PILGRIM_ID);
    private static final RegistryKey<EntityType<?>> QUEST_MASTER_KEY = RegistryKey.of(RegistryKeys.ENTITY_TYPE, QUEST_MASTER_ID);
    private static final RegistryKey<EntityType<?>> CARAVAN_MERCHANT_KEY = RegistryKey.of(RegistryKeys.ENTITY_TYPE, CARAVAN_MERCHANT_ID);
    private static final RegistryKey<EntityType<?>> TRAITOR_KEY = RegistryKey.of(RegistryKeys.ENTITY_TYPE, TRAITOR_ID);

    public static final EntityType<PilgrimEntity> PILGRIM = Registry.register(
            Registries.ENTITY_TYPE,
            PILGRIM_ID,
            EntityType.Builder.<PilgrimEntity>create(PilgrimEntity::new, SpawnGroup.CREATURE)
                    .dimensions(0.6f, 1.8f)
                    .build(PILGRIM_KEY)
    );
    public static final EntityType<QuestMasterEntity> QUEST_MASTER = Registry.register(
            Registries.ENTITY_TYPE,
            QUEST_MASTER_ID,
            EntityType.Builder.<QuestMasterEntity>create(QuestMasterEntity::new, SpawnGroup.CREATURE)
                    .dimensions(0.6f, 1.8f)
                    .build(QUEST_MASTER_KEY)
    );
    public static final EntityType<CaravanMerchantEntity> CARAVAN_MERCHANT = Registry.register(
            Registries.ENTITY_TYPE,
            CARAVAN_MERCHANT_ID,
            EntityType.Builder.<CaravanMerchantEntity>create(CaravanMerchantEntity::new, SpawnGroup.CREATURE)
                    .dimensions(0.6f, 1.8f)
                    .build(CARAVAN_MERCHANT_KEY)
    );
    public static final EntityType<TraitorEntity> TRAITOR = Registry.register(
            Registries.ENTITY_TYPE,
            TRAITOR_ID,
            EntityType.Builder.<TraitorEntity>create(TraitorEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.6f, 1.95f)
                    .build(TRAITOR_KEY)
    );

    private ModEntities() {}

    public static void register() {
        FabricDefaultAttributeRegistry.register(PILGRIM, PilgrimEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(QUEST_MASTER, QuestMasterEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(CARAVAN_MERCHANT, CaravanMerchantEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(TRAITOR, TraitorEntity.createAttributes());
        VillageQuest.LOGGER.info("Registered entities");
    }
}
