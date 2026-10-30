package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.*;

final class VillageIntroTransactionTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @BeforeEach void components() {
        Items.COBBLESTONE.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
    }
    static net.minecraft.core.HolderLookup.Provider registries() { return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY); }
    VillageIntroTransaction transaction(int paidMask) {
        var bag = new SimpleContainer(36); bag.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        return VillageIntroTransaction.prepare(UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(),
                VillageIntroInventoryPlan.plan(bag, VillageIntroRequestType.PATH_STONE).orElseThrow(), 0, 10, 7, 0, paidMask, registries()).orElseThrow();
    }
    @Test void exactIdentityFrozenEffectsAndNativePlanSurviveReloadAndCopies() {
        var original = transaction(0); var encoded = original.toNbt();
        var loaded = VillageIntroTransaction.fromNbt(encoded, registries()).orElseThrow();
        assertTrue(original.sameTransaction(loaded)); assertEquals(5, loaded.reward());
        assertEquals(15, loaded.balanceAfter()); assertEquals(12, loaded.prosperityAfter());
        assertEquals(1, loaded.completionsAfter());
        encoded.putLong("balanceBefore", 900); assertEquals(10, loaded.balanceBefore());
        var after = loaded.withStage(VillageIntroDeliveryCoordinator.Stage.AFTER);
        assertTrue(loaded.sameTransaction(after)); assertEquals(VillageIntroDeliveryCoordinator.Stage.BEFORE, loaded.stage());
    }
    @Test void alreadyPaidOfferOrExhaustedGlobalBudgetProducesNoReward() {
        assertEquals(0, transaction(VillageIntroRequestType.PATH_STONE.bit()).reward());
        var exhausted = transaction(VillageIntroRequestType.REPAIR_TIMBER.bit() | VillageIntroRequestType.HARVEST_HELP.bit());
        assertEquals(0, exhausted.reward()); assertEquals(10, exhausted.balanceAfter()); assertEquals(7, exhausted.prosperityAfter());
    }
    @Test void acknowledgementIsTerminalOnlyAndDoesNotChangeTheFrozenClaimIdentity() {
        var tx = transaction(0);
        assertThrows(IllegalStateException.class,tx::acknowledgedImage);
        var invalid = tx.toNbt(); invalid.putBoolean("acknowledged",true);
        assertTrue(VillageIntroTransaction.fromNbt(invalid,registries()).isEmpty());
        for (var stage : java.util.List.of(VillageIntroDeliveryCoordinator.Stage.COMMITTED,VillageIntroDeliveryCoordinator.Stage.CANCELLED)) {
            var terminal = tx.withStage(stage);
            var acknowledged = VillageIntroTransaction.fromNbt(terminal.acknowledgedImage(),registries()).orElseThrow();
            assertTrue(acknowledged.acknowledged()); assertTrue(terminal.sameTransaction(acknowledged));
            assertFalse(acknowledged.withStage(stage).acknowledged());
            var wrongType = terminal.acknowledgedImage(); wrongType.putString("acknowledged","true");
            assertTrue(VillageIntroTransaction.fromNbt(wrongType,registries()).isEmpty());
            wrongType = terminal.acknowledgedImage(); wrongType.putByte("acknowledged",(byte)2);
            assertTrue(VillageIntroTransaction.fromNbt(wrongType,registries()).isEmpty());
        }
    }
    @Test void compiledMarketAdmissionChecksIntroBeforeNativeLedgerOrInventoryWork() throws Exception {
        var calls = new java.util.ArrayList<String>();
        try (var input = getClass().getResourceAsStream("/de/quest/kingdom/KingdomMarketTransferService.class")) {
            assertNotNull(input);
            new org.objectweb.asm.ClassReader(input).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                @Override public org.objectweb.asm.MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
                    if (!name.equals("execute")) return null;
                    return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean itf) {
                            calls.add(owner+"."+method);
                        }
                        @Override public void visitFieldInsn(int opcode,String owner,String field,String descriptor) {
                            if (owner.equals("de/quest/kingdom/MarketTradeCoordinator$Result")) calls.add(field);
                        }
                    };
                }
            },org.objectweb.asm.ClassReader.SKIP_DEBUG|org.objectweb.asm.ClassReader.SKIP_FRAMES);
        }
        int gate = calls.indexOf("de/quest/shrine/VillageIntroRequestService.blocksActions");
        assertTrue(gate >= 0);
        assertTrue(calls.indexOf("HELD") > gate);
        assertTrue(calls.indexOf("de/quest/kingdom/LivingKingdomsState.get") > calls.indexOf("HELD"));
    }
    @Test void journalFreezesResetAndFinancialActionsUntilVerifiedTerminalAndRechecksResetAfterReload() {
        var tx = transaction(0); var owner = tx.actor();
        var state = VillageBondMigrationTest.load(new net.minecraft.nbt.CompoundTag());
        var data = state.getPlayerData(owner);
        assertTrue(state.compareIntroTransaction(owner,null,tx.toNbt()));
        assertThrows(IllegalStateException.class,data::personalRestart);
        assertThrows(IllegalStateException.class,() -> state.restartPersonalProgress(owner));
        assertThrows(IllegalStateException.class,() -> state.replacePersonalProgress(owner,new de.quest.data.PlayerQuestData()));
        assertThrows(IllegalStateException.class,state::resetAllProgress);
        var server = org.mockito.Mockito.mock(net.minecraft.server.MinecraftServer.class);
        var world = org.mockito.Mockito.mock(net.minecraft.server.level.ServerLevel.class);
        var player = org.mockito.Mockito.mock(net.minecraft.server.level.ServerPlayer.class);
        org.mockito.Mockito.when(world.getServer()).thenReturn(server);
        org.mockito.Mockito.when(world.registryAccess()).thenReturn((net.minecraft.core.RegistryAccess)registries());
        org.mockito.Mockito.when(player.level()).thenReturn(world);
        org.mockito.Mockito.when(player.getUUID()).thenReturn(owner);
        try (var states = org.mockito.Mockito.mockStatic(de.quest.data.QuestState.class)) {
            states.when(() -> de.quest.data.QuestState.get(server)).thenReturn(state);
            assertTrue(VillageIntroRequestService.blocksActions(world,owner));
            assertFalse(de.quest.economy.CurrencyService.canAfford(world,owner,1));
            assertFalse(de.quest.quest.daily.DailyQuestService.completeIfEligible(world,player));
            assertFalse(de.quest.quest.weekly.WeeklyQuestService.completeIfEligible(world,player));
            assertFalse(de.quest.quest.story.StoryQuestService.claimFromQuestMaster(world,player,de.quest.quest.story.StoryArcType.LIVING_KINGDOMS));
            assertEquals(0,de.quest.reward.VanillaRewardService.deliverItems(world,player));
            org.mockito.Mockito.verify(player,org.mockito.Mockito.never()).getInventory();
            var terminal = tx.withStage(VillageIntroDeliveryCoordinator.Stage.COMMITTED);
            assertTrue(state.compareIntroTransaction(owner,tx.toNbt(),terminal.toNbt()));
            assertTrue(VillageIntroRequestService.blocksActions(world,owner));
            assertThrows(IllegalStateException.class,data::personalRestart);
            var ack = terminal.acknowledgedImage();
            assertTrue(state.compareIntroTransaction(owner,terminal.toNbt(),ack));
            assertFalse(VillageIntroRequestService.blocksActions(world,owner));
            assertThrows(IllegalStateException.class,data::personalRestart);
            assertTrue(state.confirmIntroTerminal(owner,ack));
            assertEquals(2,data.personalRestart().progressGeneration());
            var malformed = new net.minecraft.nbt.CompoundTag(); malformed.putInt("schema",99);
            assertTrue(state.compareIntroTransaction(owner,ack,malformed));
            assertTrue(VillageIntroRequestService.blocksActions(world,owner));
            assertThrows(IllegalStateException.class,data::personalRestart);
            assertTrue(state.compareIntroTransaction(owner,malformed,ack));
        }
        // Encode outside the static QuestState mock: the real codec calls its static native writer.
        var ops = net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE,registries());
        var root = (net.minecraft.nbt.CompoundTag)de.quest.data.QuestState.TYPE.codec().encodeStart(ops,state).getOrThrow();
        var loaded = VillageBondMigrationTest.load(root);
        assertThrows(IllegalStateException.class,() -> loaded.getPlayerData(owner).personalRestart());
        var ack = tx.withStage(VillageIntroDeliveryCoordinator.Stage.COMMITTED).acknowledgedImage();
        assertTrue(loaded.confirmIntroTerminal(owner,ack));
        assertEquals(2,loaded.getPlayerData(owner).personalRestart().progressGeneration());
    }
    @Test void malformedFutureForeignOrInconsistentPostimagesCannotBecomeProofs() {
        var original = transaction(0);
        for (String key : java.util.List.of("generation", "revision", "schema")) {
            var encoded = original.toNbt(); encoded.putInt(key, 99);
            if (key.equals("generation")) { encoded.putInt(key, -1); }
            assertTrue(VillageIntroTransaction.fromNbt(encoded, registries()).isEmpty(), key);
        }
        var encoded = original.toNbt(); encoded.putLong("balanceAfter", 100);
        assertTrue(VillageIntroTransaction.fromNbt(encoded, registries()).isEmpty());
        encoded = original.toNbt(); encoded.putString("actor", "unknown");
        assertTrue(VillageIntroTransaction.fromNbt(encoded, registries()).isEmpty());
        encoded = original.toNbt(); encoded.putString("actor", UUID.randomUUID().toString());
        assertFalse(original.sameTransaction(VillageIntroTransaction.fromNbt(encoded, registries()).orElseThrow()));
    }
}
