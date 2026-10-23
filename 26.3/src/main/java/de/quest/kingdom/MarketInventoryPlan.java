package de.quest.kingdom;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Exact native main-inventory transfer; wallet and shared stock are handled by the market coordinator. */
public final class MarketInventoryPlan {
    private static final int SLOTS=36;
    private final ItemStack commodity;
    private final KingdomMarketLedger.Side side;
    private final int quantity;
    private final List<ItemStack> before,after;
    private final int[] limits;
    private final CompoundTag retained;

    private MarketInventoryPlan(ItemStack commodity,KingdomMarketLedger.Side side,int quantity,
                                List<ItemStack> before,List<ItemStack> after,int[] limits,CompoundTag retained) {
        this.commodity=commodity.copy();this.side=side;this.quantity=quantity;
        this.before=copy(before);this.after=copy(after);this.limits=limits.clone();
        this.retained=retained==null?null:retained.copy();
    }
    public static Optional<MarketInventoryPlan> plan(Container bag,ItemStack commodity,KingdomMarketLedger.Side side,int quantity) {
        if(bag==null || bag.getContainerSize()<SLOTS || !plain(commodity) || side==null || quantity<1 || quantity>128) return Optional.empty();
        var before=new ArrayList<ItemStack>(SLOTS);var limits=new int[SLOTS];
        for(int slot=0;slot<SLOTS;slot++) {
            var stack=bag.getItem(slot);
            if(stack==null || !stack.isEmpty() && (stack.getCount()<1 || stack.getCount()>stack.getMaxStackSize())) return Optional.empty();
            before.add(stack.copy());
            limits[slot]=bag.canPlaceItem(slot,commodity)?Math.max(0,Math.min(64,Math.min(commodity.getMaxStackSize(),bag.getMaxStackSize(commodity)))):0;
        }
        var after=distribute(before,commodity,side,quantity,limits);
        return after.map(stacks -> new MarketInventoryPlan(commodity,side,quantity,before,stacks,limits,null));
    }
    public int beforeCount() { return count(before,commodity); }
    public int afterCount() { return count(after,commodity); }
    public KingdomMarketLedger.Side side() { return side; }
    public int quantity() { return quantity; }
    public ItemStack commodity() { return commodity.copy(); }
    public boolean matchesBefore(Container bag) { return matches(bag,before); }
    public boolean matchesAfter(Container bag) { return matches(bag,after); }

    /** Called only on the server thread after the shared reservation. Never drops overflow into the world. */
    public boolean apply(Container bag) {
        if(!matchesBefore(bag)) return false;
        // Permissions/capacity can change after a quote; reject before touching any slot.
        for(int slot=0;slot<SLOTS;slot++) {
            var next=after.get(slot);
            if(side==KingdomMarketLedger.Side.BUY && !ItemStack.matches(before.get(slot),next)
                    && (!bag.canPlaceItem(slot,next) || next.getCount()>bag.getMaxStackSize(next))) return false;
        }
        for(int slot=0;slot<SLOTS;slot++) if(!ItemStack.matches(before.get(slot),after.get(slot))) bag.setItem(slot,after.get(slot).copy());
        bag.setChanged();return matchesAfter(bag);
    }
    public CompoundTag toNbt(HolderLookup.Provider registries) {
        if(retained!=null) return retained.copy();
        var ops=RegistryOps.create(NbtOps.INSTANCE,registries);var root=new CompoundTag();
        root.putInt("schema",1);root.putString("side",side.name());root.putInt("quantity",quantity);
        root.put("commodity",ItemStack.CODEC.encodeStart(ops,commodity).getOrThrow());
        var b=new ListTag();var a=new ListTag();var capacity=new ListTag();
        for(int slot=0;slot<SLOTS;slot++) {
            b.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops,before.get(slot)).getOrThrow());
            a.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops,after.get(slot)).getOrThrow());
            capacity.add(IntTag.valueOf(limits[slot]));
        }
        root.put("before",b);root.put("after",a);root.put("limits",capacity);return root;
    }
    /** Rejects malformed/future plans without inventing inventory state; the receipt retains the raw payload. */
    public static Optional<MarketInventoryPlan> fromNbt(CompoundTag root,HolderLookup.Provider registries) {
        if(root==null || registries==null || !(root.get("schema") instanceof IntTag) || root.getIntOr("schema",-1)!=1
                || !(root.get("quantity") instanceof IntTag)) return Optional.empty();
        try {
            var ops=RegistryOps.create(NbtOps.INSTANCE,registries);
            var commodity=ItemStack.CODEC.parse(ops,root.get("commodity")).getOrThrow();
            var side=KingdomMarketLedger.Side.valueOf(root.getStringOr("side",""));int quantity=root.getIntOr("quantity",0);
            if(!plain(commodity) || quantity<1 || quantity>128) return Optional.empty();
            var before=decode(root,"before",registries);var after=decode(root,"after",registries);
            if(!(root.get("limits") instanceof ListTag list) || list.size()!=SLOTS) return Optional.empty();
            var limits=new int[SLOTS];
            for(int slot=0;slot<SLOTS;slot++) {
                if(!(list.get(slot) instanceof IntTag number) || number.intValue()<0 || number.intValue()>Math.min(64,commodity.getMaxStackSize())) return Optional.empty();
                limits[slot]=number.intValue();
            }
            var expected=distribute(before,commodity,side,quantity,limits);
            if(expected.isEmpty() || !ItemStack.listMatches(expected.get(),after)) return Optional.empty();
            return Optional.of(new MarketInventoryPlan(commodity,side,quantity,before,after,limits,root));
        } catch(RuntimeException invalid) { return Optional.empty(); }
    }
    private static List<ItemStack> decode(CompoundTag root,String key,HolderLookup.Provider registries) {
        if(!(root.get(key) instanceof ListTag list) || list.size()!=SLOTS) throw new IllegalArgumentException("Invalid inventory snapshot");
        var ops=RegistryOps.create(NbtOps.INSTANCE,registries);var result=new ArrayList<ItemStack>(SLOTS);
        for(var tag:list) {
            if(!(tag instanceof CompoundTag)) throw new IllegalArgumentException("Invalid stack row");
            var stack=ItemStack.OPTIONAL_CODEC.parse(ops,tag).getOrThrow();
            if(!stack.isEmpty() && (stack.getCount()<1 || stack.getCount()>stack.getMaxStackSize())) throw new IllegalArgumentException("Oversized saved stack");
            result.add(stack);
        }
        return result;
    }
    private static Optional<List<ItemStack>> distribute(List<ItemStack> before,ItemStack commodity,KingdomMarketLedger.Side side,int quantity,int[] limits) {
        var after=copy(before);int remaining=quantity;
        if(side==KingdomMarketLedger.Side.SELL) {
            for(int slot=0;slot<SLOTS && remaining>0;slot++) {
                var stack=after.get(slot);if(!same(stack,commodity)) continue;
                int take=Math.min(remaining,stack.getCount());stack.shrink(take);remaining-=take;
                if(stack.isEmpty()) after.set(slot,ItemStack.EMPTY);
            }
        } else {
            // Fill matching partial stacks first, then empty main slots. Decorated stacks never mix.
            for(int pass=0;pass<2 && remaining>0;pass++) for(int slot=0;slot<SLOTS && remaining>0;slot++) {
                var stack=after.get(slot);
                if(pass==0?!same(stack,commodity):!stack.isEmpty()) continue;
                int old=stack.isEmpty()?0:stack.getCount();int add=Math.min(remaining,Math.max(0,limits[slot]-old));
                if(add>0) { after.set(slot,commodity.copyWithCount(old+add));remaining-=add; }
            }
        }
        return remaining==0?Optional.of(after):Optional.empty();
    }
    private static boolean plain(ItemStack stack) {
        return stack!=null && !stack.isEmpty() && stack.getCount()==1
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft")
                && ItemStack.isSameItemSameComponents(stack,new ItemStack(stack.getItem()));
    }
    private static boolean same(ItemStack stack,ItemStack commodity) { return !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack,commodity); }
    private static int count(List<ItemStack> stacks,ItemStack commodity) { return stacks.stream().filter(s -> same(s,commodity)).mapToInt(ItemStack::getCount).sum(); }
    private static List<ItemStack> copy(List<ItemStack> stacks) { return new ArrayList<>(stacks.stream().map(ItemStack::copy).toList()); }
    private static boolean matches(Container bag,List<ItemStack> snapshot) {
        if(bag==null || bag.getContainerSize()<SLOTS) return false;
        for(int slot=0;slot<SLOTS;slot++) if(!ItemStack.matches(bag.getItem(slot),snapshot.get(slot))) return false;
        return true;
    }
}
