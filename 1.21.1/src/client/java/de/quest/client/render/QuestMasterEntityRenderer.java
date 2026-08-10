package de.quest.client.render;

import de.quest.VillageQuest;
import de.quest.entity.QuestMasterEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

public final class QuestMasterEntityRenderer extends MobEntityRenderer<QuestMasterEntity, PlayerEntityModel<QuestMasterEntity>> {
    public static final EntityModelLayer QUEST_MASTER_LAYER =
            new EntityModelLayer(Identifier.of(VillageQuest.MOD_ID, "quest_master"), "main");
    private static final Identifier TEXTURE = Identifier.of(VillageQuest.MOD_ID, "textures/entity/quest_master.png");

    public QuestMasterEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new PlayerEntityModel<>(context.getPart(QUEST_MASTER_LAYER), false), 0.5f);
        this.addFeature(new HeldItemFeatureRenderer<>(this, context.getHeldItemRenderer()));
    }

    public static TexturedModelData createModelData() {
        return TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE, false), 64, 64);
    }

    @Override
    public Identifier getTexture(QuestMasterEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void setupTransforms(QuestMasterEntity entity, net.minecraft.client.util.math.MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta, float scale) {
        if (entity.getMainHandStack().isOf(Items.TORCH)) {
            this.getModel().rightArmPose = BipedEntityModel.ArmPose.BLOCK;
        }
        super.setupTransforms(entity, matrices, animationProgress, bodyYaw, tickDelta, scale);
    }
}
