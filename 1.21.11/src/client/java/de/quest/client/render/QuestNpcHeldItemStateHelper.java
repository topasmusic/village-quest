package de.quest.client.render;

import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.SwingAnimationType;

public final class QuestNpcHeldItemStateHelper {
    private QuestNpcHeldItemStateHelper() {}

    public static void updateSafeHeldItemState(LivingEntity entity, PlayerEntityRenderState state, ItemModelManager itemModelManager) {
        state.mainArm = entity.getMainArm();
        state.handSwingProgress = 0.0f;
        state.swingAnimationType = SwingAnimationType.WHACK;
        state.rightArmPose = BipedEntityModel.ArmPose.EMPTY;
        state.leftArmPose = BipedEntityModel.ArmPose.EMPTY;

        state.rightHandItemState.clear();
        state.leftHandItemState.clear();

        ItemStack rightHand = entity.getStackInArm(Arm.RIGHT);
        ItemStack leftHand = entity.getStackInArm(Arm.LEFT);
        itemModelManager.updateForLivingEntity(state.rightHandItemState, rightHand, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
        itemModelManager.updateForLivingEntity(state.leftHandItemState, leftHand, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, entity);
        state.rightHandItem = rightHand.copy();
        state.leftHandItem = leftHand.copy();
    }
}
