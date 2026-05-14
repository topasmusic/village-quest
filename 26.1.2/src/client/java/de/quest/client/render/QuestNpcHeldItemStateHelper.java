package de.quest.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwingAnimationType;

public final class QuestNpcHeldItemStateHelper {
    private QuestNpcHeldItemStateHelper() {}

    public static void extractSafeHeldItemState(LivingEntity entity, AvatarRenderState state, ItemModelResolver itemModelResolver) {
        state.mainArm = entity.getMainArm();
        state.attackArm = state.mainArm;
        state.attackTime = 0.0f;
        state.swingAnimationType = SwingAnimationType.WHACK;
        state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        state.leftArmPose = HumanoidModel.ArmPose.EMPTY;

        state.rightHandItemState.clear();
        state.leftHandItemState.clear();

        ItemStack rightHand = entity.getItemHeldByArm(HumanoidArm.RIGHT);
        ItemStack leftHand = entity.getItemHeldByArm(HumanoidArm.LEFT);
        itemModelResolver.updateForLiving(state.rightHandItemState, rightHand, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
        itemModelResolver.updateForLiving(state.leftHandItemState, leftHand, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, entity);
        state.rightHandItemStack = rightHand.copy();
        state.leftHandItemStack = leftHand.copy();
    }
}
