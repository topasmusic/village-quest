package de.quest.client.render;

import de.quest.VillageQuest;
import de.quest.client.compat.ClientModCompat;
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
    private static final Identifier[] TEXTURES = {
            texture("caravan_burgundy.png"),
            texture("caravan_forest.png"),
            texture("caravan.png"),
            texture("caravan_ochre.png"),
            texture("caravan_violet.png")
    };
    private static final SkinTextures[] SKINS = {
            skin(TEXTURES[0]), skin(TEXTURES[1]), skin(TEXTURES[2]), skin(TEXTURES[3]), skin(TEXTURES[4])
    };
    private final ItemModelManager itemModelManager;
    private final boolean heldItemRenderingEnabled;

    public CaravanMerchantEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new PlayerEntityModel(context.getPart(CARAVAN_MERCHANT_LAYER), false), 0.5f);
        this.itemModelManager = context.getItemModelManager();
        this.heldItemRenderingEnabled = !ClientModCompat.shouldUseSafeNpcHeldItemFallback();
        if (this.heldItemRenderingEnabled) {
            this.addFeature(new HeldItemFeatureRenderer<>(this));
        } else {
            this.addFeature(new QuestNpcHeldItemFeatureRenderer(this));
        }
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
        if (this.heldItemRenderingEnabled) {
            ArmedEntityRenderState.updateRenderState(entity, state, this.itemModelManager, tickDelta);
        } else {
            QuestNpcHeldItemStateHelper.updateSafeHeldItemState(entity, state, this.itemModelManager);
        }
        if (!entity.getMainHandStack().isEmpty()) {
            state.rightArmPose = BipedEntityModel.ArmPose.BLOCK;
        }
        state.skinTextures = SKINS[Math.floorMod(entity.getRouteIndex(), SKINS.length)];
    }

    @Override
    public Identifier getTexture(PlayerEntityRenderState state) {
        return state.skinTextures == null ? TEXTURES[0] : state.skinTextures.body().texturePath();
    }

    private static Identifier texture(String filename) {
        return Identifier.of(VillageQuest.MOD_ID, "textures/entity/" + filename);
    }

    private static SkinTextures skin(Identifier texture) {
        AssetInfo.TextureAsset asset = new AssetInfo.TextureAsset() {
            @Override
            public Identifier id() {
                return texture;
            }

            @Override
            public Identifier texturePath() {
                return texture;
            }
        };
        return SkinTextures.create(asset, null, null, PlayerSkinType.WIDE);
    }
}
