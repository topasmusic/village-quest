package de.quest.registry;

import de.quest.VillageQuest;
import de.quest.entity.PilgrimEntity;
import de.quest.entity.QuestMasterEntity;
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
    private static final RegistryKey<EntityType<?>> PILGRIM_KEY = RegistryKey.of(RegistryKeys.ENTITY_TYPE, PILGRIM_ID);
    private static final RegistryKey<EntityType<?>> QUEST_MASTER_KEY = RegistryKey.of(RegistryKeys.ENTITY_TYPE, QUEST_MASTER_ID);

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

    private ModEntities() {}

    public static void register() {
        FabricDefaultAttributeRegistry.register(PILGRIM, PilgrimEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(QUEST_MASTER, QuestMasterEntity.createAttributes());
        VillageQuest.LOGGER.info("Registered entities");
    }
}
