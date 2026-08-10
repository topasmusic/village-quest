package de.quest.client;

import de.quest.client.compat.ClientModCompat;
import de.quest.client.config.VillageQuestClientConfig;
import de.quest.client.network.ClientQuestNetworking;
import de.quest.client.hud.QuestTrackerHud;
import de.quest.client.hud.TradeRouteMinimapHud;
import de.quest.client.render.CaravanMerchantEntityRenderer;
import de.quest.client.render.PilgrimEntityRenderer;
import de.quest.client.render.QuestMasterEntityRenderer;
import de.quest.client.render.TraitorEntityRenderer;
import de.quest.client.ui.InventoryJournalTutorialState;
import de.quest.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class VillageQuestClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        VillageQuestClientConfig.bootstrap();
        ClientModCompat.bootstrap();
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
        EntityRendererRegistry.register(ModEntities.PILGRIM, PilgrimEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.QUEST_MASTER, QuestMasterEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.CARAVAN_MERCHANT, CaravanMerchantEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.TRAITOR, TraitorEntityRenderer::new);
        ClientQuestNetworking.register();
        QuestTrackerHud.register();
        TradeRouteMinimapHud.register();
        InventoryJournalTutorialState.bootstrap();
    }
}
