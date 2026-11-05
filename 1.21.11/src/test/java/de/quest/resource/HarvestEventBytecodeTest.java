package de.quest.resource;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

/** Checks real pinned Vanilla bytecode and registered production hooks; never applies Mixins or starts Minecraft. */
final class HarvestEventBytecodeTest {
    private static ClassNode node(String name) throws Exception {
        try(var in=HarvestEventBytecodeTest.class.getResourceAsStream("/"+name+".class")) {
            assertNotNull(in,name);var n=new ClassNode();new ClassReader(in).accept(n,0);return n;
        }
    }
    private static MethodNode method(ClassNode n,String name) {
        return n.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();
    }
    @Test void bottleAndShearSuccessHooksPrecedeSmokeBranchInActualHiveCode() throws Exception {
        var nativeMethod=method(node("net/minecraft/world/level/block/BeehiveBlock"),"useItemOn");
        int shrink=-1,shears=-1,smoke=-1;
        for(int i=0;i<nativeMethod.instructions.size();i++)if(nativeMethod.instructions.get(i) instanceof MethodInsnNode call) {
            if(call.owner.equals("net/minecraft/world/item/ItemStack")&&call.name.equals("shrink"))shrink=i;
            if(call.owner.equals("net/minecraft/world/item/ItemStack")&&call.name.equals("hurtAndBreak"))shears=i;
            if(call.name.equals("isSmokeyPos"))smoke=i;
        }
        assertTrue(shrink>=0&&shears>=0&&smoke>shrink&&smoke>shears,"Harvest success must precede the smoke split");
        var hooks=node("de/quest/mixin/BeehiveBlockMixin");int bindings=0;
        for(var m:hooks.methods)if(m.visibleAnnotations!=null)for(var a:m.visibleAnnotations)
            if(a.desc.endsWith("/Inject;")) {
                assertTrue(a.values.toString().contains("useItemOn"));bindings++;
            }
        assertEquals(2,bindings);
        var fanout=method(hooks,"villageQuest$trackSuccessfulHarvest");
        var targets=new HashSet<String>();
        for(var insn:fanout.instructions)if(insn instanceof MethodInsnNode call)targets.add(call.owner);
        for(String owner:List.of("quest/daily/DailyQuestService","quest/story/StoryQuestService",
                "quest/special/SpecialQuestService","pilgrim/PilgrimContractService","guildtown/GuildTownService"))
            assertTrue(targets.contains("de/quest/"+owner));
    }
    @Test void paperCraftAndPickupHooksMatchNativeInventoryMutationBoundaries() throws Exception {
        var stack=node("net/minecraft/world/item/ItemStack");
        assertEquals("(Lnet/minecraft/world/entity/player/Player;I)V",method(stack,"onCraftedBy").desc);
        var result=method(node("net/minecraft/world/inventory/ResultSlot"),"checkTakeAchievements");
        assertTrue(Arrays.stream(result.instructions.toArray()).anyMatch(i->i instanceof MethodInsnNode c
                &&c.name.equals("onCraftedBy")&&c.desc.equals("(Lnet/minecraft/world/entity/player/Player;I)V")));
        var pickup=method(node("net/minecraft/world/entity/item/ItemEntity"),"playerTouch");
        int add=-1,take=-1;
        for(int i=0;i<pickup.instructions.size();i++)if(pickup.instructions.get(i) instanceof MethodInsnNode c) {
            if(c.owner.equals("net/minecraft/world/entity/player/Inventory")&&c.name.equals("add"))add=i;
            if(c.owner.equals("net/minecraft/world/entity/player/Player")&&c.name.equals("take"))take=i;
        }
        assertTrue(add>=0&&take>add);
        assertTrue(node("net/minecraft/world/entity/item/ItemEntity").fields.stream().anyMatch(f->
                f.name.equals("thrower")&&f.desc.equals("Lnet/minecraft/world/entity/EntityReference;")));
    }
}
