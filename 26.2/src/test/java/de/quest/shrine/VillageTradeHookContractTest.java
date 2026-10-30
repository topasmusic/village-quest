package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

final class VillageTradeHookContractTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test void wanderingTraderAndClientPlayerNeverEnterEitherQuestTradePath() throws Exception {
        var callback=de.quest.mixin.TradeOutputSlotMixin.class.getDeclaredMethod("villageQuest$trackVillagerTrade",
                net.minecraft.world.entity.player.Player.class,net.minecraft.world.item.ItemStack.class,
                org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
        callback.setAccessible(true);
        var merchantField=de.quest.mixin.TradeOutputSlotMixin.class.getDeclaredField("merchant");
        merchantField.setAccessible(true);
        var hook=new Hook();
        try(var daily=org.mockito.Mockito.mockStatic(de.quest.quest.daily.DailyQuestService.class);
            var trust=org.mockito.Mockito.mockStatic(VillageTradeTrustService.class)) {
            merchantField.set(hook,org.mockito.Mockito.mock(net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader.class));
            callback.invoke(hook,org.mockito.Mockito.mock(net.minecraft.server.level.ServerPlayer.class),net.minecraft.world.item.ItemStack.EMPTY,null);
            merchantField.set(hook,org.mockito.Mockito.mock(net.minecraft.world.entity.npc.villager.Villager.class));
            callback.invoke(hook,org.mockito.Mockito.mock(net.minecraft.world.entity.player.Player.class),net.minecraft.world.item.ItemStack.EMPTY,null);
            daily.verifyNoInteractions(); trust.verifyNoInteractions();
        }
    }
    private static final class Hook extends de.quest.mixin.TradeOutputSlotMixin {}
    // Catches a duplicated hook or moving it to a failed output take or client packet.
    @Test void bytecodeBindsExactlyOneDailyAndTrustEventAfterSuccessfulNativeNotifyTrade() throws Exception {
        var daily=new AtomicInteger(); var trust=new AtomicInteger(); var receipts=new AtomicInteger();
        var atTarget=new AtomicInteger(); var atAfter=new AtomicInteger();
        try(var input=de.quest.mixin.TradeOutputSlotMixin.class.getResourceAsStream("TradeOutputSlotMixin.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
                    if(!name.equals("villageQuest$trackVillagerTrade")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean itf) {
                            if(owner.equals("de/quest/quest/daily/DailyQuestService") && name.equals("onVillagerTrade")) daily.incrementAndGet();
                            if(owner.equals("de/quest/shrine/VillageTradeTrustService") && name.equals("onCommittedTrade")) trust.incrementAndGet();
                            if(owner.equals("java/util/UUID") && name.equals("randomUUID")) receipts.incrementAndGet();
                        }
                        @Override public AnnotationVisitor visitAnnotation(String descriptor,boolean visible) {
                            return annotations(atTarget,atAfter);
                        }
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        assertEquals(1,daily.get()); assertEquals(1,trust.get()); assertEquals(1,receipts.get());
        assertEquals(1,atTarget.get()); assertEquals(1,atAfter.get());
        // The native commit call is a single site: stack quantity is not a count of trades.
        var nativeCommits=new AtomicInteger();
        try(var input=net.minecraft.world.inventory.MerchantResultSlot.class.getResourceAsStream("MerchantResultSlot.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
                    if(!name.equals("onTake")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean itf) {
                            if(owner.equals("net/minecraft/world/item/trading/Merchant") && name.equals("notifyTrade")) nativeCommits.incrementAndGet();
                        }
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        assertEquals(1,nativeCommits.get());
    }
    private static AnnotationVisitor annotations(AtomicInteger target,AtomicInteger after) {
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override public AnnotationVisitor visitArray(String name) { return annotations(target,after); }
            @Override public AnnotationVisitor visitAnnotation(String name,String descriptor) { return annotations(target,after); }
            @Override public void visit(String name,Object value) {
                if("target".equals(name) && value.equals("Lnet/minecraft/world/item/trading/Merchant;notifyTrade(Lnet/minecraft/world/item/trading/MerchantOffer;)V")) target.incrementAndGet();
            }
            @Override public void visitEnum(String name,String descriptor,String value) {
                if("shift".equals(name) && value.equals("AFTER")) after.incrementAndGet();
            }
        };
    }
}
