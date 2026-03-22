package de.quest.client;

import de.quest.client.network.ClientQuestNetworking;
import de.quest.client.hud.QuestTrackerHud;
import de.quest.client.render.PilgrimEntityRenderer;
import de.quest.client.render.QuestMasterEntityRenderer;
import de.quest.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.client.render.entity.EntityRendererFactories;

public class VillageQuestClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(
                PilgrimEntityRenderer.PILGRIM_LAYER,
                PilgrimEntityRenderer::createModelData
        );
        EntityModelLayerRegistry.registerModelLayer(
                QuestMasterEntityRenderer.QUEST_MASTER_LAYER,
                QuestMasterEntityRenderer::createModelData
        );
        EntityRendererFactories.register(ModEntities.PILGRIM, PilgrimEntityRenderer::new);
        EntityRendererFactories.register(ModEntities.QUEST_MASTER, QuestMasterEntityRenderer::new);
        ClientQuestNetworking.register();
        QuestTrackerHud.register();
    }
}
