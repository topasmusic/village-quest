package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.RegistryOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/** Checks independently read-back save documents, never a world or player entity. */
final class MarketSavedCheckpointTest {
    private static final UUID ACTOR=UUID.randomUUID();
    private static final UUID RECEIPT=UUID.randomUUID(),VISIT=UUID.randomUUID(),TRADER=UUID.randomUUID();
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @BeforeEach void components() { Items.WHEAT.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build()); }
    private static net.minecraft.core.RegistryAccess access() { return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY); }
    private static CompoundTag marker() {
        var transfer=new CompoundTag(); transfer.putString("id",RECEIPT.toString()); transfer.putString("actor",ACTOR.toString());
        transfer.putString("visit",VISIT.toString()); transfer.putString("trader",TRADER.toString()); transfer.putString("sku","grain");
        transfer.putLong("price",4); transfer.putLong("beforeBalance",100); transfer.putLong("afterBalance",96);
        transfer.put("plan",MarketInventoryPlan.plan(new SimpleContainer(36),new ItemStack(Items.WHEAT),KingdomMarketLedger.Side.BUY,32).orElseThrow().toNbt(access()));
        return new MarketTransferMarker(transfer,MarketTransferMarker.Stage.AFTER).toNbt();
    }
    private static CompoundTag market() {
        var root=new CompoundTag(); var data=new CompoundTag(); data.putInt("schema",1); data.putInt("reservedStock",1); root.put("markets",data); return root;
    }
    private static CompoundTag wallet() {
        var root=new CompoundTag(); var owners=new CompoundTag(); var journal=new CompoundTag(); journal.putInt("schema",1);
        journal.put("marker",marker()); owners.put(ACTOR.toString(),journal); root.put("marketTransfers",owners);
        var manager=new CompoundTag(); var balances=new ListTag(); var row=new CompoundTag(); row.putString("id",ACTOR.toString()); row.putLong("v",96);
        balances.add(row); manager.put("currencyBalance",balances); root.put("questManager",manager); return root;
    }
    private static CompoundTag player() {
        var root=new CompoundTag(); var attachments=new CompoundTag(); var proof=marker();
        attachments.put("village-quest:market_transfer",proof); root.put(AttachmentTarget.NBT_ATTACHMENT_KEY,attachments);
        var inventory=new ListTag(); inventory.add(stack(0,new ItemStack(Items.WHEAT,32))); root.put("Inventory",inventory); return root;
    }
    private static Tag stack(int slot,ItemStack item) { return ItemStackWithSlot.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE,access()),new ItemStackWithSlot(slot,item)).getOrThrow(); }
    private static boolean matches(CompoundTag actualMarket,CompoundTag actualWallet,CompoundTag actualPlayer) {
        return MarketSavedCheckpoint.matches(ACTOR,market(),wallet(),player(),actualMarket,actualWallet,actualPlayer);
    }
    @Test void identicalReadBackDocumentsConfirmTheStoredReservationWalletProofAndInventory() { assertTrue(matches(market(),wallet(),player())); }
    @Test void aMissingOrStalePreparedStoryContextCannotConfirmTheBeforeCheckpoint() {
        var expected=wallet(); var strings=new ListTag(); var row=new CompoundTag();
        row.putString("id",ACTOR.toString()); row.putString("key","kingdoms.market.story.proof"); row.putString("v","prepared context");
        strings.add(row); expected.getCompoundOrEmpty("questManager").put("tradeRouteStrings",strings);
        assertFalse(MarketSavedCheckpoint.matches(ACTOR,market(),expected,player(),market(),wallet(),player()));
        var stale=expected.copy(); stale.getCompoundOrEmpty("questManager").getListOrEmpty("tradeRouteStrings").getCompoundOrEmpty(0).putString("v","old context");
        assertFalse(MarketSavedCheckpoint.matches(ACTOR,market(),expected,player(),market(),stale,player()));
        assertTrue(MarketSavedCheckpoint.matches(ACTOR,market(),expected,player(),market(),expected.copy(),player()));
    }
    @Test void sameProofWithOlderWalletBalanceCannotAcknowledgeTheNewSave() {
        var actual=wallet(); actual.getCompoundOrEmpty("questManager").getListOrEmpty("currencyBalance").getCompoundOrEmpty(0).putLong("v",100);
        assertFalse(matches(market(),actual,player()));
    }
    @Test void staleSharedStockOrShiftedOrDecoratedSavedStackCannotBeAcknowledged() {
        var m=market(); m.getCompoundOrEmpty("markets").putInt("reservedStock",2); assertFalse(matches(m,wallet(),player()));
        var p=player(); p.getListOrEmpty("Inventory").set(0,stack(1,new ItemStack(Items.WHEAT,32))); assertFalse(matches(market(),wallet(),p));
        p=player(); var decorated=new ItemStack(Items.WHEAT,32); decorated.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Other wheat"));
        p.getListOrEmpty("Inventory").set(0,stack(0,decorated));
        assertFalse(matches(market(),wallet(),p));
    }
    @Test void missingProofOrUntypedDuplicateBalanceRowsFailClosed() {
        var p=player(); p.getCompoundOrEmpty(AttachmentTarget.NBT_ATTACHMENT_KEY).remove("village-quest:market_transfer"); assertFalse(matches(market(),wallet(),p));
        var w=wallet(); w.getCompoundOrEmpty("questManager").getListOrEmpty("currencyBalance").add(w.getCompoundOrEmpty("questManager").getListOrEmpty("currencyBalance").get(0).copy());
        assertFalse(matches(market(),w,player()));
        w=wallet(); w.getCompoundOrEmpty("questManager").getListOrEmpty("currencyBalance").getCompoundOrEmpty(0).putInt("v",96); assertFalse(matches(market(),w,player()));
    }
    @Test void independentlySavedAppliedAndTerminalProofsCanBeCheckpointedBeforeReconcileDecidesTheEffect() {
        var w=wallet(); var journal=w.getCompoundOrEmpty("marketTransfers").getCompoundOrEmpty(ACTOR.toString());
        journal.put("marker",MarketTransferMarker.fromNbt(journal.get("marker")).orElseThrow().withStage(MarketTransferMarker.Stage.COMMITTED).toNbt());
        var p=player();
        assertTrue(MarketSavedCheckpoint.matches(ACTOR,market(),w,p,market(),w.copy(),p.copy()));
        // This confirms written images only. The coordinator still requires the exact balance/inventory/receipt to resolve.
    }
}
