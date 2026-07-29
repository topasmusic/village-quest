package de.quest.client.render;

import de.quest.VillageQuest;
import de.quest.client.compat.ClientModCompat;
import de.quest.entity.CaravanMerchantEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.Items;

public final class CaravanMerchantEntityRenderer extends MobRenderer<CaravanMerchantEntity, AvatarRenderState, QuestNpcPlayerModel> {
    public static final ModelLayerLocation CARAVAN_MERCHANT_LAYER =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "caravan_merchant"), "main");
    private static final Identifier[] TEXTURES = {
            texture("caravan_burgundy.png"),
            texture("caravan_forest.png"),
            texture("caravan.png"),
            texture("caravan_ochre.png"),
            texture("caravan_violet.png")
    };
    private static final PlayerSkin[] SKINS = {
            skin(TEXTURES[0]), skin(TEXTURES[1]), skin(TEXTURES[2]), skin(TEXTURES[3]), skin(TEXTURES[4])
    };
    private final ItemModelResolver itemModelManager;
    private final boolean heldItemRenderingEnabled;

    public CaravanMerchantEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new QuestNpcPlayerModel(context.bakeLayer(CARAVAN_MERCHANT_LAYER), false), 0.5f);
        this.itemModelManager = context.getItemModelResolver();
        this.heldItemRenderingEnabled = !ClientModCompat.shouldUseSafeNpcHeldItemFallback();
        if (this.heldItemRenderingEnabled) {
            this.addLayer(new ItemInHandLayer<>(this));
        } else {
            this.addLayer(new QuestNpcHeldItemLayer(this));
        }
    }

    public static LayerDefinition createModelData() {
        return LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64);
    }

    @Override
    public AvatarRenderState createRenderState() {
        return new AvatarRenderState();
    }

    @Override
    public void extractRenderState(CaravanMerchantEntity entity, AvatarRenderState state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        if (this.heldItemRenderingEnabled) {
            ArmedEntityRenderState.extractArmedEntityRenderState(entity, state, this.itemModelManager, tickDelta);
        } else {
            QuestNpcHeldItemStateHelper.extractSafeHeldItemState(entity, state, this.itemModelManager);
        }
        if (entity.getMainHandItem().getItem() == Items.TORCH) {
            state.rightArmPose = HumanoidModel.ArmPose.BLOCK;
        }
        // Every member of one route shares an outfit. This makes a caravan readable
        // at a glance and matches the route color used by the ledger and minimap.
        state.skin = SKINS[Math.floorMod(entity.getLiveryIndex(), SKINS.length)];
    }

    @Override
    public Identifier getTextureLocation(AvatarRenderState state) {
        return state.skin == null ? TEXTURES[0] : state.skin.body().texturePath();
    }

    private static Identifier texture(String filename) {
        return Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "textures/entity/" + filename);
    }

    private static PlayerSkin skin(Identifier texture) {
        ClientAsset.Texture asset = new ClientAsset.Texture() {
            @Override
            public Identifier id() {
                return texture;
            }

            @Override
            public Identifier texturePath() {
                return texture;
            }
        };
        return PlayerSkin.insecure(asset, null, null, PlayerModelType.WIDE);
    }
}
