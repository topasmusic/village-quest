package de.quest.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.quest.VillageQuest;
import de.quest.client.compat.ClientModCompat;
import de.quest.entity.KingdomNpcEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.Items;

/** Rank changes clothing and hat; the original speaker head is rendered separately and remains identical. */
public final class KingdomNpcEntityRenderer extends MobRenderer<KingdomNpcEntity,AvatarRenderState,QuestNpcPlayerModel> {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID,"kingdom_npc"),"main");
    private static final String[] FILES=de.quest.kingdom.NpcSkinCatalog.FILES.toArray(String[]::new);
    private static final PlayerSkin[] SKINS=java.util.Arrays.stream(FILES).map(KingdomNpcEntityRenderer::skin).toArray(PlayerSkin[]::new);
    private final ItemModelResolver items;
    private final boolean standardHands;
    public KingdomNpcEntityRenderer(EntityRendererProvider.Context context) {
        super(context,new KingdomModel(context.bakeLayer(LAYER),false),0.5f);
        items=context.getItemModelResolver(); standardHands=!ClientModCompat.shouldUseSafeNpcHeldItemFallback();
        if (standardHands) addLayer(new ItemInHandLayer<>(this)); else addLayer(new QuestNpcHeldItemLayer(this));
        addLayer(new OriginalFaceLayer(this,new KingdomModel(context.bakeLayer(LAYER),true)));
    }
    @Override public AvatarRenderState createRenderState() { return new KingdomRenderState(); }
    @Override public void extractRenderState(KingdomNpcEntity entity,AvatarRenderState state,float delta) {
        super.extractRenderState(entity,state,delta);
        if (standardHands) ArmedEntityRenderState.extractArmedEntityRenderState(entity,state,items,delta);
        else QuestNpcHeldItemStateHelper.extractSafeHeldItemState(entity,state,items);
        if (entity.getMainHandItem().is(Items.TORCH)) state.rightArmPose=HumanoidModel.ArmPose.BLOCK;
        int skin=entity.skinIndex(); state.skin=SKINS[skin];
        var kingdom=(KingdomRenderState)state; kingdom.promoted=skin==2 || skin==3;
        kingdom.face=SKINS[skin==3?1:0].body().texturePath();
    }
    @Override public Identifier getTextureLocation(AvatarRenderState state) { return state.skin==null?SKINS[0].body().texturePath():state.skin.body().texturePath(); }
    private static PlayerSkin skin(String file) {
        var texture=Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID,"textures/entity/"+file+".png");
        ClientAsset.Texture asset=new ClientAsset.Texture() {
            public Identifier id() { return texture; }
            public Identifier texturePath() { return texture; }
        };
        return PlayerSkin.insecure(asset,null,null,PlayerModelType.WIDE);
    }
    private static final class KingdomRenderState extends AvatarRenderState { boolean promoted; Identifier face; }
    private static final class KingdomModel extends QuestNpcPlayerModel {
        private final boolean faceOnly;
        KingdomModel(ModelPart root,boolean faceOnly) { super(root,false); this.faceOnly=faceOnly; }
        @Override public void setupAnim(AvatarRenderState state) {
            super.setupAnim(state); head.skipDraw=!faceOnly && state instanceof KingdomRenderState kingdom && kingdom.promoted;
            if (faceOnly) { root().getAllParts().forEach(part -> part.visible=false); root().visible=true; head.visible=true; hat.visible=false; head.skipDraw=false; }
        }
    }
    private static final class OriginalFaceLayer extends RenderLayer<AvatarRenderState,QuestNpcPlayerModel> {
        private final KingdomModel face;
        OriginalFaceLayer(RenderLayerParent<AvatarRenderState,QuestNpcPlayerModel> parent,KingdomModel face) { super(parent); this.face=face; }
        @Override public void submit(PoseStack poses,SubmitNodeCollector collector,int light,AvatarRenderState state,float y,float x) {
            if (!(state instanceof KingdomRenderState kingdom) || !kingdom.promoted || state.isInvisible) return;
            collector.submitModel(face,state,poses,RenderTypes.entityCutout(kingdom.face),light,
                    net.minecraft.client.renderer.entity.LivingEntityRenderer.getOverlayCoords(state,0),state.outlineColor,null);
        }
    }
}
