package de.quest.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * Safer fallback for modded clients that choke on the vanilla armed-player item layer.
 * It keeps quest NPC held items visible, but deliberately skips the more complex
 * attack/use-item animation path from the standard layer.
 */
public final class QuestNpcHeldItemLayer extends RenderLayer<AvatarRenderState, QuestNpcPlayerModel> {
    private static final float HAND_ITEM_X = 1.0f / 16.0f;
    private static final float HAND_ITEM_Y = 2.0f / 16.0f;
    private static final float HAND_ITEM_Z = -10.0f / 16.0f;

    public QuestNpcHeldItemLayer(RenderLayerParent<AvatarRenderState, QuestNpcPlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitCollector, int packedLight, AvatarRenderState state, float yRot, float xRot) {
        submitArmWithItem(state, state.rightHandItemState, state.rightHandItemStack, HumanoidArm.RIGHT, poseStack, submitCollector, packedLight);
        submitArmWithItem(state, state.leftHandItemState, state.leftHandItemStack, HumanoidArm.LEFT, poseStack, submitCollector, packedLight);
    }

    private void submitArmWithItem(
            AvatarRenderState state,
            ItemStackRenderState itemState,
            ItemStack itemStack,
            HumanoidArm arm,
            PoseStack poseStack,
            SubmitNodeCollector submitCollector,
            int packedLight
    ) {
        if (itemState.isEmpty() || itemStack.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        this.getParentModel().translateToQuestHand(arm, poseStack);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
        poseStack.translate(arm == HumanoidArm.LEFT ? -HAND_ITEM_X : HAND_ITEM_X, HAND_ITEM_Y, HAND_ITEM_Z);

        if (arm == HumanoidArm.RIGHT && state.rightArmPose == HumanoidModel.ArmPose.BLOCK) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-18.0f));
            poseStack.translate(0.0f, -2.0f / 16.0f, 0.0f);
        } else if (arm == HumanoidArm.LEFT && state.leftArmPose == HumanoidModel.ArmPose.BLOCK) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-18.0f));
            poseStack.translate(0.0f, -2.0f / 16.0f, 0.0f);
        }

        itemState.submit(poseStack, submitCollector, packedLight, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }
}
