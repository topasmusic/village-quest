package de.quest.client.render;

import de.quest.VillageQuest;
import de.quest.caravan.CaravanRole;
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
    private static final String[] LIVERIES = {"burgundy", "forest", "neutral", "ochre", "violet"};
    private static final PlayerSkin[][] SKINS = createRoleSkins();
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
        // One livery identifies the route; each synchronized role has its own outfit.
        CaravanRole role = entity.isCourier() ? CaravanRole.COURIER : entity.getCrewRole();
        state.skin = SKINS[Math.floorMod(entity.getLiveryIndex(), SKINS.length)][role.ordinal()];
    }

    @Override
    public Identifier getTextureLocation(AvatarRenderState state) {
        return state.skin == null ? texture("caravan_burgundy_master.png")
                : state.skin.body().texturePath();
    }

    private static PlayerSkin[][] createRoleSkins() {
        PlayerSkin[][] skins = new PlayerSkin[LIVERIES.length][CaravanRole.values().length];
        for (int livery = 0; livery < LIVERIES.length; livery++) {
            for (CaravanRole role : CaravanRole.values()) {
                skins[livery][role.ordinal()] = skin(texture("caravan_" + LIVERIES[livery]
                        + "_" + role.name().toLowerCase(java.util.Locale.ROOT) + ".png"));
            }
        }
        return skins;
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
