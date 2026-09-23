package de.quest.client.render;

import de.quest.VillageQuest;
import de.quest.client.compat.ClientModCompat;
import de.quest.entity.QuestMasterEntity;
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

public final class QuestMasterEntityRenderer extends MobRenderer<QuestMasterEntity, AvatarRenderState, QuestNpcPlayerModel> {
    public static final ModelLayerLocation QUEST_MASTER_LAYER =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "quest_master"), "main");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "textures/entity/quest_master.png");
    private static final ClientAsset.Texture QUEST_MASTER_TEXTURE_ASSET = new ClientAsset.Texture() {
        @Override
        public Identifier id() {
            return TEXTURE;
        }

        @Override
        public Identifier texturePath() {
            return TEXTURE;
        }
    };
    private static final PlayerSkin QUEST_MASTER_SKIN =
            PlayerSkin.insecure(QUEST_MASTER_TEXTURE_ASSET, null, null, PlayerModelType.WIDE);
    private final ItemModelResolver itemModelManager;
    private final boolean heldItemRenderingEnabled;

    public QuestMasterEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new QuestNpcPlayerModel(context.bakeLayer(QUEST_MASTER_LAYER), false), 0.5f);
        this.itemModelManager = context.getItemModelResolver();
        this.heldItemRenderingEnabled = !ClientModCompat.shouldUseSafeNpcHeldItemFallback();
        if (this.heldItemRenderingEnabled) {
            this.addLayer(new ItemInHandLayer<>(this));
        } else {
            this.addLayer(new QuestNpcHeldItemLayer(this));
        }
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
    public void extractRenderState(QuestMasterEntity entity, AvatarRenderState state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        if (this.heldItemRenderingEnabled) {
            ArmedEntityRenderState.extractArmedEntityRenderState(entity, state, this.itemModelManager, tickDelta);
        } else {
            QuestNpcHeldItemStateHelper.extractSafeHeldItemState(entity, state, this.itemModelManager);
        }
        if (entity.getMainHandItem().getItem() == Items.TORCH) {
            state.rightArmPose = HumanoidModel.ArmPose.BLOCK;
        }
        state.skin = QUEST_MASTER_SKIN;
    }

    @Override
    public Identifier getTextureLocation(AvatarRenderState state) {
        return TEXTURE;
    }
}
