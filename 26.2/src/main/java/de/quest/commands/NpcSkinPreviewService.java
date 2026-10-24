package de.quest.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import de.quest.entity.KingdomNpcEntity;
import de.quest.kingdom.NpcSkinCatalog;
import de.quest.registry.ModEntities;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import static net.minecraft.commands.Commands.literal;

/** Explicit operator tool: loaded empty space only, inert bodies, no canonical people or progress. */
final class NpcSkinPreviewService {
    private NpcSkinPreviewService() {}
    private static final String TAG="vq_skin_preview";
    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return literal("skinpreview").requires(AdminCommands::canManageRespawn)
                .executes(ctx -> spawn(ctx.getSource()))
                .then(literal("clear").executes(ctx -> clear(ctx.getSource())));
    }
    private static int spawn(CommandSourceStack source) {
        var actor=source.getPlayer(); if(actor==null) return 0;
        var world=actor.level(); int count=NpcSkinCatalog.FILES.size();
        // A straight east-west row, centered nearby; raised so normal ground is preserved.
        var origin=actor.blockPosition().offset(-count,4,4);
        var floor=new ArrayList<BlockPos>();
        for(int x=0;x<=count*2;x++) for(int z=-3;z<=2;z++) {
            var p=origin.offset(x,0,z);
            if(!world.getWorldBorder().isWithinBounds(p) || p.getY()<world.getMinY() || p.getY()+4>=world.getMaxY() || !world.hasChunkAt(p)
                    || !world.isPositionEntityTicking(p)) return fail(source,"space");
            for(int y=0;y<=4;y++) if(!world.isEmptyBlock(p.above(y))) return fail(source,"space");
            if(!world.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new AABB(p).expandTowards(0,4,0)).isEmpty()) return fail(source,"space");
            floor.add(p);
        }
        var bodies=new ArrayList<KingdomNpcEntity>();
        for(int i=0;i<count;i++) {
            var npc=ModEntities.KINGDOM_NPC.create(world,EntitySpawnReason.COMMAND);
            if(npc==null) return fail(source,"failed");
            String file=NpcSkinCatalog.FILES.get(i);
            Component name=i>=14 && i<18?de.quest.caravan.NpcNames.guest(de.quest.caravan.NpcNames.GUESTS.get(i-14))
                    :Component.literal(file.substring(file.lastIndexOf('/')+1));
            npc.preview(i,name); npc.previewOwner(actor.getUUID());
            npc.snapTo(origin.getX()+i*2+1.5,origin.getY()+1,origin.getZ()+0.5,180,0);
            bodies.add(npc);
        }
        for(var p:floor) world.setBlock(p,Blocks.SMOOTH_STONE.defaultBlockState(),3);
        for(var npc:bodies) if(!world.addFreshEntity(npc)) {
            bodies.forEach(KingdomNpcEntity::discard);
            for(var p:floor) if(world.getBlockState(p).is(Blocks.SMOOTH_STONE)) world.setBlock(p,Blocks.AIR.defaultBlockState(),3);
            return fail(source,"failed");
        }
        actor.teleportTo(actor.getX(),origin.getY()+1,origin.getZ()-1.5);
        actor.setYRot(0); actor.setXRot(0);
        var server=source.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"tick freeze");
        source.sendSuccess(() -> Component.translatable("command.village-quest.skinpreview.ready",count,origin.getX(),origin.getY(),origin.getZ()),false);
        return count;
    }
    private static int clear(CommandSourceStack source) {
        var actor=source.getPlayer(); if(actor==null) return 0;
        var world=actor.level();
        var bodies=world.getEntitiesOfClass(KingdomNpcEntity.class,actor.getBoundingBox().inflate(128),
                npc -> npc.isPreview() && npc.previewOwner().equals(actor.getUUID().toString()));
        bodies.forEach(KingdomNpcEntity::discard);
        source.sendSuccess(() -> Component.translatable("command.village-quest.skinpreview.cleared",bodies.size()),false);
        return bodies.size();
    }
    private static int fail(CommandSourceStack source,String key) {
        source.sendFailure(Component.translatable("command.village-quest.skinpreview."+key)); return 0;
    }
}
