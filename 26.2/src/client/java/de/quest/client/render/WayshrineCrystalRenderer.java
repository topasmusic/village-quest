package de.quest.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.quest.VillageQuest;
import de.quest.content.block.GuildWayshrineBlock;
import de.quest.content.block.GuildWayshrineBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/** Client-only visual renderer; the block entity has no ticker or networked animation state. */
public final class WayshrineCrystalRenderer
        implements BlockEntityRenderer<GuildWayshrineBlockEntity, WayshrineCrystalRenderState> {
    private static final SpriteId ACTIVE_TEXTURE = sprite("shrine_crystal_active");
    private static final SpriteId INACTIVE_TEXTURE = sprite("shrine_crystal_inactive");
    private static final SpriteId ACTIVE_GLOW_TEXTURE = sprite("shrine_crystal_active_e");
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final float STATIC_ANGLE = Mth.PI / 4.0f;
    private static final float ROTATION_PERIOD_TICKS = 240.0f;
    private static final int[][] BASE_TINTS = {
            {220, 245, 255, 255},
            {185, 220, 255, 255},
            {215, 185, 255, 255},
            {170, 195, 235, 255}
    };
    private static final int[][] GLOW_TINTS = {
            {135, 230, 255, 165},
            {110, 190, 255, 155},
            {185, 135, 255, 160},
            {130, 165, 240, 150}
    };
    private static final ShardGeometry[] SHARDS = {
            ShardGeometry.create(0.0f, 0.0f, 0.0f,
                    0.056f, 0.047f, 0.155f, 0.082f, -0.092f, -0.155f, 0),
            ShardGeometry.create(-0.115f, 0.035f, 0.024f,
                    0.0165f, 0.0135f, 0.058f, 0.027f, -0.025f, -0.054f, 5),
            ShardGeometry.create(0.105f, -0.040f, -0.052f,
                    0.0135f, 0.0115f, 0.043f, 0.018f, -0.018f, -0.040f, 10)
    };

    private final SpriteGetter sprites;

    public WayshrineCrystalRenderer(BlockEntityRendererProvider.Context context) {
        sprites = context.sprites();
    }

    @Override
    public WayshrineCrystalRenderState createRenderState() {
        return new WayshrineCrystalRenderState();
    }

    @Override
    public void extractRenderState(GuildWayshrineBlockEntity blockEntity,
                                   WayshrineCrystalRenderState state,
                                   float partialTick,
                                   Vec3 cameraPosition,
                                   CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderState.extractBase(blockEntity, state, crumblingOverlay);
        var blockState = blockEntity.getBlockState();
        state.visible = blockState.hasProperty(GuildWayshrineBlock.HALF)
                && blockState.getValue(GuildWayshrineBlock.HALF) == DoubleBlockHalf.LOWER;
        state.active = blockState.hasProperty(GuildWayshrineBlock.ACTIVE)
                && blockState.getValue(GuildWayshrineBlock.ACTIVE);
        // Keep the fractional frame time in double precision. Converting a long-running
        // world's game time to float first eventually discards partialTick completely,
        // which makes the crystal advance in visible 20 TPS steps.
        double animationTime = blockEntity.getLevel() == null
                ? partialTick
                : blockEntity.getLevel().getGameTime() + (double) partialTick;
        double rotationPhase = animationTime % ROTATION_PERIOD_TICKS;
        double bobPhase = (animationTime * 0.09d) % (Math.PI * 2.0d);
        state.rotation = state.active
                ? STATIC_ANGLE + (float) (rotationPhase * (Mth.TWO_PI / ROTATION_PERIOD_TICKS))
                : STATIC_ANGLE;
        state.bob = state.active ? (float) (Math.sin(bobPhase) * 0.025d) : 0.0f;
    }

    @Override
    public void submit(WayshrineCrystalRenderState state,
                       PoseStack poseStack,
                       SubmitNodeCollector submitNodes,
                       CameraRenderState cameraState) {
        if (!state.visible) return;

        SpriteId baseTexture = state.active ? ACTIVE_TEXTURE : INACTIVE_TEXTURE;
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.775f + state.bob, 0.5f);
        poseStack.mulPose(Axis.YP.rotation(state.rotation));
        poseStack.mulPose(Axis.ZP.rotation(0.075f));
        poseStack.scale(1.15f, 1.15f, 1.15f);
        TextureAtlasSprite baseSprite = sprites.get(baseTexture);
        submitNodes.submitCustomGeometry(poseStack, baseTexture.renderType(RenderTypes::entityCutout),
                (pose, consumer) -> renderCrystalCluster(
                        pose, consumer, baseSprite, state.lightCoords, OverlayTexture.NO_OVERLAY, false));

        if (state.active) {
            poseStack.scale(1.018f, 1.018f, 1.018f);
            TextureAtlasSprite glowSprite = sprites.get(ACTIVE_GLOW_TEXTURE);
            submitNodes.submitCustomGeometry(poseStack,
                    ACTIVE_GLOW_TEXTURE.renderType(RenderTypes::entityTranslucentEmissive),
                    (pose, consumer) -> renderCrystalCluster(
                            pose, consumer, glowSprite, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, true));
        }
        poseStack.popPose();
    }

    private static void renderCrystalCluster(PoseStack.Pose pose,
                                             VertexConsumer consumer,
                                             TextureAtlasSprite sprite,
                                             int light,
                                             int overlay,
                                             boolean glow) {
        for (ShardGeometry shard : SHARDS) {
            renderShard(pose, consumer, sprite, light, overlay, glow, shard);
        }
    }

    private static void renderShard(PoseStack.Pose pose,
                                    VertexConsumer consumer,
                                    TextureAtlasSprite sprite,
                                    int light,
                                    int overlay,
                                    boolean glow,
                                    ShardGeometry shard) {
        for (int side = 0; side < 4; side++) {
            int next = (side + 1) & 3;
            float u0 = (shard.uvShift + side * 3.0f) % 12.0f;
            float u1 = u0 + 4.0f;
            int[] tint = glow ? GLOW_TINTS[side] : BASE_TINTS[side];

            emitQuad(pose, consumer, sprite, light, overlay, tint,
                    shard.topTip, shard.upperRing[side], shard.upperRing[next], shard.topTip,
                    u0 + 2.0f, 0.5f, u0, 5.0f, u1, 5.0f, u0 + 2.0f, 0.5f);
            emitQuad(pose, consumer, sprite, light, overlay, tint,
                    shard.upperRing[side], shard.lowerRing[side], shard.lowerRing[next], shard.upperRing[next],
                    u0, 4.0f, u0, 13.0f, u1, 13.0f, u1, 4.0f);
            emitQuad(pose, consumer, sprite, light, overlay, tint,
                    shard.lowerRing[side], shard.bottomTip, shard.bottomTip, shard.lowerRing[next],
                    u0, 11.0f, u0 + 2.0f, 15.5f, u0 + 2.0f, 15.5f, u1, 11.0f);
        }
    }

    private static void emitQuad(PoseStack.Pose pose,
                                 VertexConsumer consumer,
                                 TextureAtlasSprite sprite,
                                 int light,
                                 int overlay,
                                 int[] color,
                                 float[] a,
                                 float[] b,
                                 float[] c,
                                 float[] d,
                                 float au,
                                 float av,
                                 float bu,
                                 float bv,
                                 float cu,
                                 float cv,
                                 float du,
                                 float dv) {
        float abx = b[0] - a[0];
        float aby = b[1] - a[1];
        float abz = b[2] - a[2];
        float acx = c[0] - a[0];
        float acy = c[1] - a[1];
        float acz = c[2] - a[2];
        float nx = aby * acz - abz * acy;
        float ny = abz * acx - abx * acz;
        float nz = abx * acy - aby * acx;
        float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 0.00001f) {
            nx /= length;
            ny /= length;
            nz /= length;
        }

        emitVertex(pose, consumer, sprite, light, overlay, color, a, au, av, nx, ny, nz);
        emitVertex(pose, consumer, sprite, light, overlay, color, b, bu, bv, nx, ny, nz);
        emitVertex(pose, consumer, sprite, light, overlay, color, c, cu, cv, nx, ny, nz);
        emitVertex(pose, consumer, sprite, light, overlay, color, d, du, dv, nx, ny, nz);
    }

    private static void emitVertex(PoseStack.Pose pose,
                                   VertexConsumer consumer,
                                   TextureAtlasSprite sprite,
                                   int light,
                                   int overlay,
                                   int[] color,
                                   float[] position,
                                   float u,
                                   float v,
                                   float nx,
                                   float ny,
                                   float nz) {
        consumer.addVertex(pose, position[0], position[1], position[2])
                .setColor(color[0], color[1], color[2], color[3])
                .setUv(sprite.getU(u / 16.0f), sprite.getV(v / 16.0f))
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    private record ShardGeometry(float[][] upperRing,
                                 float[][] lowerRing,
                                 float[] topTip,
                                 float[] bottomTip,
                                 int uvShift) {
        private static ShardGeometry create(float cx,
                                            float cy,
                                            float cz,
                                            float radiusX,
                                            float radiusZ,
                                            float top,
                                            float upper,
                                            float lower,
                                            float bottom,
                                            int uvShift) {
            float[][] upperRing = {
                    {cx - radiusX * 0.78f, cy + upper, cz - radiusZ * 0.58f},
                    {cx + radiusX, cy + upper * 0.96f, cz - radiusZ * 0.44f},
                    {cx + radiusX * 0.70f, cy + upper, cz + radiusZ},
                    {cx - radiusX, cy + upper * 0.94f, cz + radiusZ * 0.62f}
            };
            float[][] lowerRing = {
                    {cx - radiusX * 0.66f, cy + lower, cz - radiusZ * 0.54f},
                    {cx + radiusX * 0.84f, cy + lower * 0.97f, cz - radiusZ * 0.38f},
                    {cx + radiusX * 0.60f, cy + lower, cz + radiusZ * 0.82f},
                    {cx - radiusX * 0.82f, cy + lower * 0.95f, cz + radiusZ * 0.54f}
            };
            float[] topTip = {cx + radiusX * 0.18f, cy + top, cz - radiusZ * 0.12f};
            float[] bottomTip = {cx - radiusX * 0.10f, cy + bottom, cz + radiusZ * 0.08f};
            return new ShardGeometry(upperRing, lowerRing, topTip, bottomTip, uvShift);
        }
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    private static SpriteId sprite(String path) {
        return new SpriteId(TextureAtlas.LOCATION_BLOCKS,
                Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "block/" + path));
    }
}
