package de.quest.registry;

import de.quest.VillageQuest;
import de.quest.entity.PilgrimEntity;
import de.quest.entity.QuestMasterEntity;
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
    private static final ResourceKey<EntityType<?>> PILGRIM_KEY = ResourceKey.create(Registries.ENTITY_TYPE, PILGRIM_ID);
    private static final ResourceKey<EntityType<?>> QUEST_MASTER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, QUEST_MASTER_ID);

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

    private ModEntities() {}

    public static void register() {
        FabricDefaultAttributeRegistry.register(PILGRIM, PilgrimEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(QUEST_MASTER, QuestMasterEntity.createAttributes());
        VillageQuest.LOGGER.info("Registered entities");
    }
}
