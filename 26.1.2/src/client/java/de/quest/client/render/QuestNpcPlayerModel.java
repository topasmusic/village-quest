package de.quest.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.world.entity.HumanoidArm;

public final class QuestNpcPlayerModel extends PlayerModel {
    private final boolean slimArms;

    public QuestNpcPlayerModel(ModelPart root, boolean slimArms) {
        super(root, slimArms);
        this.slimArms = slimArms;
    }

    public void translateToQuestHand(HumanoidArm arm, PoseStack poseStack) {
        this.root().translateAndRotate(poseStack);
        ModelPart armPart = this.getArm(arm);
        if (this.slimArms) {
            float armOffset = 0.5f * (arm == HumanoidArm.RIGHT ? 1.0f : -1.0f);
            armPart.x += armOffset;
            armPart.translateAndRotate(poseStack);
            armPart.x -= armOffset;
            return;
        }
        armPart.translateAndRotate(poseStack);
    }
}
