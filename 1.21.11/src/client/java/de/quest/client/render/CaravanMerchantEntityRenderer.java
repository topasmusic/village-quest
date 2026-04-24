package de.quest.client.render;

import de.quest.VillageQuest;
import de.quest.entity.CaravanMerchantEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

public final class CaravanMerchantEntityRenderer extends MobEntityRenderer<CaravanMerchantEntity, PlayerEntityRenderState, PlayerEntityModel> {
    public static final EntityModelLayer CARAVAN_MERCHANT_LAYER =
            new EntityModelLayer(Identifier.of(VillageQuest.MOD_ID, "caravan_merchant"), "main");
    private static final Identifier TEXTURE = Identifier.of(VillageQuest.MOD_ID, "textures/entity/caravan.png");
    private static final AssetInfo.TextureAsset CARAVAN_TEXTURE_ASSET = new AssetInfo.TextureAsset() {
        @Override
        public Identifier id() {
            return TEXTURE;
        }

        @Override
        public Identifier texturePath() {
            return TEXTURE;
        }
    };
    private static final SkinTextures CARAVAN_SKIN =
            SkinTextures.create(CARAVAN_TEXTURE_ASSET, null, null, PlayerSkinType.WIDE);
    private final ItemModelManager itemModelManager;

    public CaravanMerchantEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new PlayerEntityModel(context.getPart(CARAVAN_MERCHANT_LAYER), false), 0.5f);
        this.itemModelManager = context.getItemModelManager();
        this.addFeature(new HeldItemFeatureRenderer<>(this));
    }

    public static net.minecraft.client.model.TexturedModelData createModelData() {
        return net.minecraft.client.model.TexturedModelData.of(
                PlayerEntityModel.getTexturedModelData(Dilation.NONE, false),
                64,
                64
        );
    }

    @Override
    public PlayerEntityRenderState createRenderState() {
        return new PlayerEntityRenderState();
    }

    @Override
    public void updateRenderState(CaravanMerchantEntity entity, PlayerEntityRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        ArmedEntityRenderState.updateRenderState(entity, state, this.itemModelManager, tickDelta);
        if (!entity.getMainHandStack().isEmpty()) {
            state.rightArmPose = BipedEntityModel.ArmPose.BLOCK;
        }
        state.skinTextures = CARAVAN_SKIN;
    }

    @Override
    public Identifier getTexture(PlayerEntityRenderState state) {
        return TEXTURE;
    }
}
