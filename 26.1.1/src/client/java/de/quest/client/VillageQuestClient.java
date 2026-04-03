package de.quest.client;

import de.quest.client.network.ClientQuestNetworking;
import de.quest.client.hud.QuestTrackerHud;
import de.quest.client.render.PilgrimEntityRenderer;
import de.quest.client.render.QuestMasterEntityRenderer;
import de.quest.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class VillageQuestClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModelLayerRegistry.registerModelLayer(
                PilgrimEntityRenderer.PILGRIM_LAYER,
                PilgrimEntityRenderer::createModelData
        );
        ModelLayerRegistry.registerModelLayer(
                QuestMasterEntityRenderer.QUEST_MASTER_LAYER,
                QuestMasterEntityRenderer::createModelData
        );
        EntityRenderers.register(ModEntities.PILGRIM, PilgrimEntityRenderer::new);
        EntityRenderers.register(ModEntities.QUEST_MASTER, QuestMasterEntityRenderer::new);
        ClientQuestNetworking.register();
        QuestTrackerHud.register();
    }
}
