package de.quest.client.render;

import de.quest.VillageQuest;
import de.quest.entity.TraitorEntity;
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

public final class TraitorEntityRenderer extends MobRenderer<TraitorEntity, AvatarRenderState, PlayerModel> {
    public static final ModelLayerLocation TRAITOR_LAYER =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "traitor"), "main");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "textures/entity/traitor.png");
    private static final ClientAsset.Texture TEXTURE_ASSET = new ClientAsset.Texture() {
        @Override
        public Identifier id() {
            return TEXTURE;
        }

        @Override
        public Identifier texturePath() {
            return TEXTURE;
        }
    };
    private static final PlayerSkin SKIN = PlayerSkin.insecure(TEXTURE_ASSET, null, null, PlayerModelType.WIDE);
    private final ItemModelResolver itemModelManager;

    public TraitorEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel(context.bakeLayer(TRAITOR_LAYER), false), 0.55f);
        this.itemModelManager = context.getItemModelResolver();
        this.addLayer(new ItemInHandLayer<>(this));
    }

    public static LayerDefinition createModelData() {
        return LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64);
    }

    @Override
    public AvatarRenderState createRenderState() {
        return new AvatarRenderState();
    }

    @Override
    public void extractRenderState(TraitorEntity entity, AvatarRenderState state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        ArmedEntityRenderState.extractArmedEntityRenderState(entity, state, this.itemModelManager, tickDelta);
        state.skin = SKIN;
    }

    @Override
    public Identifier getTextureLocation(AvatarRenderState state) {
        return TEXTURE;
    }
}
