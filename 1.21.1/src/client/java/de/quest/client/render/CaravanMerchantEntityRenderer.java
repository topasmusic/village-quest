package de.quest.client.render;

import de.quest.VillageQuest;
import de.quest.entity.CaravanMerchantEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

public final class CaravanMerchantEntityRenderer extends MobEntityRenderer<CaravanMerchantEntity, PlayerEntityModel<CaravanMerchantEntity>> {
    public static final EntityModelLayer CARAVAN_MERCHANT_LAYER =
            new EntityModelLayer(Identifier.of(VillageQuest.MOD_ID, "caravan_merchant"), "main");
    private static final Identifier[] TEXTURES = {
            texture("caravan_burgundy.png"),
            texture("caravan_forest.png"),
            texture("caravan.png"),
            texture("caravan_ochre.png"),
            texture("caravan_violet.png")
    };

    public CaravanMerchantEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new PlayerEntityModel<>(context.getPart(CARAVAN_MERCHANT_LAYER), false), 0.5f);
        this.addFeature(new HeldItemFeatureRenderer<>(this, context.getHeldItemRenderer()));
    }

    public static TexturedModelData createModelData() {
        return TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE, false), 64, 64);
    }

    @Override
    public Identifier getTexture(CaravanMerchantEntity entity) {
        return TEXTURES[Math.floorMod(entity.getLiveryIndex(), TEXTURES.length)];
    }

    @Override
    protected void setupTransforms(CaravanMerchantEntity entity, net.minecraft.client.util.math.MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta, float scale) {
        if (!entity.getMainHandStack().isEmpty()) {
            this.getModel().rightArmPose = BipedEntityModel.ArmPose.ITEM;
        }
        super.setupTransforms(entity, matrices, animationProgress, bodyYaw, tickDelta, scale);
    }

    private static Identifier texture(String filename) {
        return Identifier.of(VillageQuest.MOD_ID, "textures/entity/" + filename);
    }
}
