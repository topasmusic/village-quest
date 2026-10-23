package de.quest.client.hud;

import de.quest.client.compat.GuiGraphics;
import de.quest.client.config.VillageQuestClientConfig;
import de.quest.client.ui.KingdomUiAssets;
import de.quest.client.ui.VillageUiTheme;
import de.quest.kingdom.NpcSpeechBuffer;
import de.quest.network.NpcSpeechPayload;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Camera-projected world bubbles, occluded by line of sight; no gameplay or persistent world state. */
public final class NpcSpeechHud {
    private NpcSpeechHud() {}
    private static final NpcSpeechBuffer BUFFER=new NpcSpeechBuffer();
    private static long tick; private static Object level;
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(level!=client.level) { BUFFER.clear(); level=client.level; tick=0; }
            if(!client.isPaused()) tick++;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> { BUFFER.clear(); level=null; tick=0; });
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR,Identifier.fromNamespaceAndPath("village-quest","npc_speech"),
                (extractor,delta) -> render(new GuiGraphics(extractor)));
    }
    public static void receive(NpcSpeechPayload speech) {
        var client=Minecraft.getInstance(); if(client.level==null) return;
        if(level!=client.level) { BUFFER.clear(); level=client.level; tick=0; }
        BUFFER.offer(speech,client.level.dimension().identifier().toString(),tick);
    }
    private record Box(int left,int top,int width,int height) {
        boolean overlaps(Box b) { return left<b.left+b.width && left+width>b.left && top<b.top+b.height && top+height>b.top; }
    }
    private static void render(GuiGraphics g) {
        var client=Minecraft.getInstance();
        if(client.level==null || client.player==null || client.gui.hud.isHidden() || client.gui.screen()!=null || !VillageQuestClientConfig.get().speechBubblesEnabled()) return;
        var entries=BUFFER.active(client.level.dimension().identifier().toString(),tick).stream().sorted(Comparator.comparingDouble(e -> {
            var entity=client.level.getEntity(e.speech().entity()); return entity==null?Double.MAX_VALUE:client.player.distanceToSqr(entity);
        })).toList();
        var boxes=new ArrayList<Box>(); int width=Math.min(NpcSpeechLayout.WIDTH,g.guiWidth()-16); if(width<80) return;
        for(var entry:entries) {
            if(boxes.size()>=3) break;
            var speech=entry.speech(); var entity=client.level.getEntity(speech.entity());
            if(entity==null || !entity.getUUID().equals(speech.body()) || !entity.isAlive() || entity.isInvisibleTo(client.player)
                    || client.player.distanceToSqr(entity)>16*16 || !client.player.hasLineOfSight(entity)) continue;
            var point=client.gameRenderer.projectPointToScreen(entity.getEyePosition().add(0,0.55,0));
            // Camera projection returns normalized device coordinates. Points behind the camera are rejected separately.
            var camera=client.gameRenderer.mainCamera();
            var forward=net.minecraft.world.phys.Vec3.directionFromRotation(camera.xRot(),camera.yRot());
            if(entity.getEyePosition().subtract(camera.position()).dot(forward)<=0 || !Double.isFinite(point.x) || !Double.isFinite(point.y)
                    || Math.abs(point.x)>1 || Math.abs(point.y)>1) continue;
            if(client.level.clip(new net.minecraft.world.level.ClipContext(camera.position(),entity.getEyePosition(),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,client.player))
                    .getType()!=net.minecraft.world.phys.HitResult.Type.MISS) continue;
            float scale=NpcSpeechLayout.scale(entity.getEyePosition().distanceTo(camera.position())); if(scale<=0) continue;
            String body=NpcSpeechLayout.body(speech.name().getString(),speech.line().getString());
            var lines=client.font.split(net.minecraft.network.chat.Component.literal(body),width-24);
            int pages=Math.max(1,(lines.size()+NpcSpeechLayout.LINES-1)/NpcSpeechLayout.LINES);
            int page=NpcSpeechLayout.page(lines.size(),tick-(entry.until()-speech.ticks()),speech.ticks());
            int first=page*NpcSpeechLayout.LINES,shown=Math.min(NpcSpeechLayout.LINES,lines.size()-first);
            int height=NpcSpeechLayout.height(shown,pages>1),screenWidth=(int)Math.ceil(width*scale),screenHeight=(int)Math.ceil(height*scale);
            int x=(int)((point.x+1)*g.guiWidth()/2)-screenWidth/2, y=(int)((1-point.y)*g.guiHeight()/2)-screenHeight;
            // Never pin a distant/offscreen speaker's balloon to an unrelated screen edge.
            if(x<4 || y<4 || x+screenWidth>g.guiWidth()-4 || y+screenHeight>g.guiHeight()-4) continue;
            var box=new Box(x,y,screenWidth,screenHeight);
            if(boxes.stream().anyMatch(box::overlaps)) continue; boxes.add(box);
            var pose=g.pose(); pose.pushMatrix();
            try {
                pose.translate(x,y); pose.scale(scale,scale);
                KingdomUiAssets.speechFrame(g,0,0,width,height);
                g.drawString(client.font,VillageUiTheme.ellipsize(client.font,speech.name().getString(),width-24),12,10,VillageUiTheme.TEAL,false);
                for(int i=0;i<shown;i++) g.drawString(client.font,lines.get(first+i),12,NpcSpeechLayout.BODY_Y+i*NpcSpeechLayout.LINE_STEP,VillageUiTheme.INK,false);
                if(pages>1) g.drawString(client.font,(page+1)+" / "+pages,width-42,height-30,VillageUiTheme.MUTED,false);
            } finally { pose.popMatrix(); }
        }
    }
}
