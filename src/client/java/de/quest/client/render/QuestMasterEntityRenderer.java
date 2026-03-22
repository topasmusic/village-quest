package de.quest.client.render;

import de.quest.VillageQuest;
import de.quest.entity.QuestMasterEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

public final class QuestMasterEntityRenderer extends MobEntityRenderer<QuestMasterEntity, PlayerEntityRenderState, PlayerEntityModel> {
    public static final EntityModelLayer QUEST_MASTER_LAYER =
            new EntityModelLayer(Identifier.of(VillageQuest.MOD_ID, "quest_master"), "main");
    private static final Identifier TEXTURE = Identifier.of(VillageQuest.MOD_ID, "textures/entity/quest_master.png");
    private static final AssetInfo.TextureAsset QUEST_MASTER_TEXTURE_ASSET = new AssetInfo.TextureAsset() {
        @Override
        public Identifier id() {
            return TEXTURE;
        }

        @Override
        public Identifier texturePath() {
            return TEXTURE;
        }
    };
    private static final SkinTextures QUEST_MASTER_SKIN =
            SkinTextures.create(QUEST_MASTER_TEXTURE_ASSET, null, null, PlayerSkinType.WIDE);

    public QuestMasterEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new PlayerEntityModel(context.getPart(QUEST_MASTER_LAYER), false), 0.5f);
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
    public void updateRenderState(QuestMasterEntity entity, PlayerEntityRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.skinTextures = QUEST_MASTER_SKIN;
    }

    @Override
    public Identifier getTexture(PlayerEntityRenderState state) {
        return TEXTURE;
    }
}
