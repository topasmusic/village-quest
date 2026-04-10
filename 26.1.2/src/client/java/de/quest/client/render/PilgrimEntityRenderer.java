package de.quest.client.render;

import de.quest.VillageQuest;
import de.quest.entity.PilgrimEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
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

public final class PilgrimEntityRenderer extends MobRenderer<PilgrimEntity, AvatarRenderState, PlayerModel> {
    public static final ModelLayerLocation PILGRIM_LAYER =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "pilgrim"), "main");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "textures/entity/pilgrim.png");
    private static final ClientAsset.Texture PILGRIM_TEXTURE_ASSET = new ClientAsset.Texture() {
        @Override
        public Identifier id() {
            return TEXTURE;
        }

        @Override
        public Identifier texturePath() {
            return TEXTURE;
        }
    };
    private static final PlayerSkin PILGRIM_SKIN =
            PlayerSkin.insecure(PILGRIM_TEXTURE_ASSET, null, null, PlayerModelType.WIDE);
    private final ItemModelResolver itemModelManager;

    public PilgrimEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel(context.bakeLayer(PILGRIM_LAYER), false), 0.5f);
        this.itemModelManager = context.getItemModelResolver();
        this.addLayer(new ItemInHandLayer<>(this));
    }

    public static net.minecraft.client.model.geom.builders.LayerDefinition createModelData() {
        return net.minecraft.client.model.geom.builders.LayerDefinition.create(
                PlayerModel.createMesh(CubeDeformation.NONE, false),
                64,
                64
        );
    }

    @Override
    public AvatarRenderState createRenderState() {
        return new AvatarRenderState();
    }

    @Override
    public void extractRenderState(PilgrimEntity entity, AvatarRenderState state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        ArmedEntityRenderState.extractArmedEntityRenderState(entity, state, this.itemModelManager, tickDelta);
        if (entity.getMainHandItem().getItem() == Items.TORCH) {
            state.rightArmPose = HumanoidModel.ArmPose.BLOCK;
        }
        state.skin = PILGRIM_SKIN;
    }

    @Override
    public Identifier getTextureLocation(AvatarRenderState state) {
        return TEXTURE;
    }
}
