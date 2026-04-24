package de.quest.client;

import de.quest.client.network.ClientQuestNetworking;
import de.quest.client.hud.QuestTrackerHud;
import de.quest.client.render.CaravanMerchantEntityRenderer;
import de.quest.client.render.PilgrimEntityRenderer;
import de.quest.client.render.QuestMasterEntityRenderer;
import de.quest.client.render.TraitorEntityRenderer;
import de.quest.client.ui.InventoryJournalTutorialState;
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
        EntityModelLayerRegistry.registerModelLayer(
                CaravanMerchantEntityRenderer.CARAVAN_MERCHANT_LAYER,
                CaravanMerchantEntityRenderer::createModelData
        );
        EntityModelLayerRegistry.registerModelLayer(
                TraitorEntityRenderer.TRAITOR_LAYER,
                TraitorEntityRenderer::createModelData
        );
        EntityRendererFactories.register(ModEntities.PILGRIM, PilgrimEntityRenderer::new);
        EntityRendererFactories.register(ModEntities.QUEST_MASTER, QuestMasterEntityRenderer::new);
        EntityRendererFactories.register(ModEntities.CARAVAN_MERCHANT, CaravanMerchantEntityRenderer::new);
        EntityRendererFactories.register(ModEntities.TRAITOR, TraitorEntityRenderer::new);
        ClientQuestNetworking.register();
        QuestTrackerHud.register();
        InventoryJournalTutorialState.bootstrap();
    }
}
