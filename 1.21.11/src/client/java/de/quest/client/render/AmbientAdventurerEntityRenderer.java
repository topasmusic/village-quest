package de.quest.client.render;

import de.quest.VillageQuest;
import de.quest.caravan.NpcNames;
import de.quest.client.compat.ClientModCompat;
import de.quest.entity.AmbientAdventurerEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.Items;

public final class AmbientAdventurerEntityRenderer extends MobRenderer<AmbientAdventurerEntity,AvatarRenderState,QuestNpcPlayerModel> {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID,"ambient_adventurer"),"main");
    private static final PlayerSkin[] SKINS=NpcNames.ambientProfiles().stream().map(p -> skin(p.texture())).toArray(PlayerSkin[]::new);
    private final ItemModelResolver items;
    private final boolean standardHands;
    public AmbientAdventurerEntityRenderer(EntityRendererProvider.Context context) {
        super(context,new QuestNpcPlayerModel(context.bakeLayer(LAYER),false),.5f);
        items=context.getItemModelResolver(); standardHands=!ClientModCompat.shouldUseSafeNpcHeldItemFallback();
        if(standardHands) addLayer(new ItemInHandLayer<>(this)); else addLayer(new QuestNpcHeldItemLayer(this));
    }
    @Override public AvatarRenderState createRenderState() { return new AvatarRenderState(); }
    @Override public void extractRenderState(AmbientAdventurerEntity entity,AvatarRenderState state,float delta) {
        super.extractRenderState(entity,state,delta);
        if(standardHands) ArmedEntityRenderState.extractArmedEntityRenderState(entity,state,items,delta);
        else QuestNpcHeldItemStateHelper.extractSafeHeldItemState(entity,state,items);
        if(entity.getMainHandItem().is(Items.TORCH)) state.rightArmPose=HumanoidModel.ArmPose.BLOCK;
        state.skin=SKINS[entity.profileIndex()];
    }
    @Override public Identifier getTextureLocation(AvatarRenderState state) { return (state.skin==null?SKINS[0]:state.skin).body().texturePath(); }
    private static PlayerSkin skin(String file) {
        var texture=Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID,"textures/entity/"+file+".png");
        ClientAsset.Texture asset=new ClientAsset.Texture() {
            public Identifier id() { return texture; }
            public Identifier texturePath() { return texture; }
        };
        return PlayerSkin.insecure(asset,null,null,PlayerModelType.WIDE);
    }
}
