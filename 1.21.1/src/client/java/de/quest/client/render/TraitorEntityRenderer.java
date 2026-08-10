package de.quest.client.render;

import de.quest.VillageQuest;
import de.quest.entity.TraitorEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

public final class TraitorEntityRenderer extends MobEntityRenderer<TraitorEntity, PlayerEntityModel<TraitorEntity>> {
    public static final EntityModelLayer TRAITOR_LAYER =
            new EntityModelLayer(Identifier.of(VillageQuest.MOD_ID, "traitor"), "main");
    private static final Identifier TEXTURE = Identifier.of(VillageQuest.MOD_ID, "textures/entity/traitor.png");

    public TraitorEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new PlayerEntityModel<>(context.getPart(TRAITOR_LAYER), false), 0.5f);
        this.addFeature(new HeldItemFeatureRenderer<>(this, context.getHeldItemRenderer()));
    }

    public static TexturedModelData createModelData() {
        return TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE, false), 64, 64);
    }

    @Override
    public Identifier getTexture(TraitorEntity entity) {
        return TEXTURE;
    }
}
