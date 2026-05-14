package de.quest.client.render;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;

public final class QuestNpcHeldItemFeatureRenderer extends HeldItemFeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    private static final float HAND_ITEM_X = 1.0f / 16.0f;
    private static final float HAND_ITEM_Y = 2.0f / 16.0f;
    private static final float HAND_ITEM_Z = -10.0f / 16.0f;

    public QuestNpcHeldItemFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        renderItemSafely(state, matrices, queue, light, Arm.RIGHT);
        renderItemSafely(state, matrices, queue, light, Arm.LEFT);
    }

    private void renderItemSafely(PlayerEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, Arm arm) {
        if (arm == Arm.RIGHT) {
            if (state.rightHandItemState.isEmpty() || state.rightHandItem.isEmpty()) {
                return;
            }
        } else if (state.leftHandItemState.isEmpty() || state.leftHandItem.isEmpty()) {
            return;
        }

        matrices.push();
        this.getContextModel().setArmAngle(state, arm, matrices);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0f));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));
        matrices.translate(arm == Arm.LEFT ? -HAND_ITEM_X : HAND_ITEM_X, HAND_ITEM_Y, HAND_ITEM_Z);

        if (arm == Arm.RIGHT && state.rightArmPose == BipedEntityModel.ArmPose.BLOCK) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-18.0f));
            matrices.translate(0.0f, -2.0f / 16.0f, 0.0f);
        } else if (arm == Arm.LEFT && state.leftArmPose == BipedEntityModel.ArmPose.BLOCK) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-18.0f));
            matrices.translate(0.0f, -2.0f / 16.0f, 0.0f);
        }

        if (arm == Arm.RIGHT) {
            state.rightHandItemState.render(matrices, queue, light, OverlayTexture.DEFAULT_UV, state.outlineColor);
        } else {
            state.leftHandItemState.render(matrices, queue, light, OverlayTexture.DEFAULT_UV, state.outlineColor);
        }
        matrices.pop();
    }
}
