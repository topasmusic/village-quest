package de.quest.data;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Plans changes on copies before touching inventory, ender chest or personal data. */
public final class PersonalResetItems {
    private PersonalResetItems() {}
    public static boolean isVq(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("village-quest")) return true;
        var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if (tag.contains("village_quest_map") || tag.contains("vq_archive_id") || tag.contains("watchOwner") || tag.getBooleanOr("wayfarersToken",false)) return true;
        return named(stack.get(DataComponents.ITEM_NAME)) || named(stack.get(DataComponents.CUSTOM_NAME));
    }
    private static boolean named(Component name) {
        return name != null && name.getContents() instanceof TranslatableContents text
                && (text.getKey().startsWith("item.village-quest.") || text.getKey().startsWith("quest.village-quest."));
    }
    private static final class Scan {
        int nodes;
        final List<ItemStack> rescued=new ArrayList<>();
        ItemStack clean(ItemStack input,int depth) {
            if (++nodes>4096 || depth>32) throw new IllegalStateException("Portable storage exceeds safe scan size");
            if (input.isEmpty()) return ItemStack.EMPTY;
            ItemStack result=input.copy();
            var container=result.get(DataComponents.CONTAINER);
            if(container!=null) result.set(DataComponents.CONTAINER,net.minecraft.world.item.component.ItemContainerContents.fromItems(container.allItemsCopyStream().map(s -> clean(s,depth+1)).toList()));
            var bundle=result.get(DataComponents.BUNDLE_CONTENTS);
            if(bundle!=null) result.set(DataComponents.BUNDLE_CONTENTS,new net.minecraft.world.item.component.BundleContents(bundle.itemCopyStream().map(s -> clean(s,depth+1)).filter(s -> !s.isEmpty()).map(net.minecraft.world.item.ItemStackTemplate::fromNonEmptyStack).toList()));
            if (!isVq(result)) return result;
            var kept=result.get(DataComponents.CONTAINER);
            if(kept!=null) kept.nonEmptyItemCopyStream().forEach(rescued::add);
            var keptBundle=result.get(DataComponents.BUNDLE_CONTENTS);
            if(keptBundle!=null) keptBundle.itemCopyStream().filter(s -> !s.isEmpty()).forEach(rescued::add);
            return ItemStack.EMPTY;
        }
    }
    public static final class Plan {
        private final UUID owner;
        private final Image before, after;
        private Plan(UUID owner, Image before, Image after) { this.owner=owner; this.before=before; this.after=after; }
        public List<ItemStack> inventory() { return copies(after.inventory); }
        public List<ItemStack> ender() { return copies(after.ender); }
        public List<ItemStack> crafting() { return copies(after.crafting); }
        public ItemStack cursor() { return after.cursor.copy(); }
        public boolean matchesBefore(ServerPlayer player) { return Objects.equals(owner,player.getUUID()) && before.matches(Image.capture(player)); }
        public boolean matchesAfter(ServerPlayer player) { return Objects.equals(owner,player.getUUID()) && after.matches(Image.capture(player)); }
        public net.minecraft.nbt.CompoundTag toNbt(net.minecraft.core.HolderLookup.Provider registries) {
            var tag=new net.minecraft.nbt.CompoundTag();tag.putInt("schema",1);tag.putString("owner",owner.toString());
            tag.put("before",before.toNbt(registries));tag.put("after",after.toNbt(registries));return tag;
        }
        public void apply(ServerPlayer player) {
            if (!Objects.equals(owner,player.getUUID()) || !before.matches(Image.capture(player)))
                throw new IllegalStateException("Personal inventory changed after preflight");
            for(int i=0;i<after.inventory.size();i++) player.getInventory().setItem(i,after.inventory.get(i).copy());
            for(int i=0;i<after.ender.size();i++) player.getEnderChestInventory().setItem(i,after.ender.get(i).copy());
            for(int i=0;i<4;i++) player.inventoryMenu.getSlot(i+1).set(after.crafting.get(i).copy());
            player.inventoryMenu.setCarried(after.cursor.copy());
            player.getInventory().setChanged(); player.getEnderChestInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
        }
    }
    private static List<ItemStack> copies(List<ItemStack> items) { return items.stream().map(ItemStack::copy).toList(); }
    private record Image(List<ItemStack> inventory,List<ItemStack> ender,List<ItemStack> crafting,ItemStack cursor) {
        Image { inventory=copies(inventory); ender=copies(ender); crafting=copies(crafting); cursor=cursor.copy(); }
        boolean matches(Image other) {
            return ItemStack.listMatches(inventory,other.inventory) && ItemStack.listMatches(ender,other.ender)
                    && ItemStack.listMatches(crafting,other.crafting) && ItemStack.matches(cursor,other.cursor);
        }
        net.minecraft.nbt.CompoundTag toNbt(net.minecraft.core.HolderLookup.Provider registries) {
            var tag=new net.minecraft.nbt.CompoundTag();var ops=net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE,registries);
            tag.put("inventory",encode(inventory,ops));tag.put("ender",encode(ender,ops));tag.put("crafting",encode(crafting,ops));
            tag.put("cursor",ItemStack.OPTIONAL_CODEC.encodeStart(ops,cursor).getOrThrow());return tag;
        }
        static Image fromNbt(net.minecraft.nbt.CompoundTag tag,net.minecraft.core.HolderLookup.Provider registries) {
            var ops=net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE,registries);
            var bag=decode(tag,"inventory",36,128,ops);var chest=decode(tag,"ender",27,27,ops);var table=decode(tag,"crafting",4,4,ops);
            if(!(tag.get("cursor") instanceof net.minecraft.nbt.CompoundTag))throw new IllegalArgumentException("Invalid cursor image");
            return new Image(bag,chest,table,ItemStack.OPTIONAL_CODEC.parse(ops,tag.get("cursor")).getOrThrow());
        }
        private static net.minecraft.nbt.ListTag encode(List<ItemStack> stacks,net.minecraft.resources.RegistryOps<net.minecraft.nbt.Tag> ops) {
            var list=new net.minecraft.nbt.ListTag();for(var stack:stacks)list.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops,stack).getOrThrow());return list;
        }
        private static List<ItemStack> decode(net.minecraft.nbt.CompoundTag tag,String key,int min,int max,net.minecraft.resources.RegistryOps<net.minecraft.nbt.Tag> ops) {
            if(!(tag.get(key) instanceof net.minecraft.nbt.ListTag list) || list.size()<min || list.size()>max)throw new IllegalArgumentException("Invalid storage image");
            var result=new ArrayList<ItemStack>();
            for(var entry:list) {
                if(!(entry instanceof net.minecraft.nbt.CompoundTag))throw new IllegalArgumentException("Invalid stack image");
                var stack=ItemStack.OPTIONAL_CODEC.parse(ops,entry).getOrThrow();
                if(!stack.isEmpty() && (stack.getCount()<1 || stack.getCount()>stack.getMaxStackSize()))throw new IllegalArgumentException("Invalid stack count");
                result.add(stack);
            }
            return result;
        }
        static Image capture(ServerPlayer player) {
            if(player.containerMenu!=player.inventoryMenu) throw new IllegalStateException("Close the open container first");
            if(player.getInventory().getContainerSize()<36) throw new IllegalStateException("Unsupported inventory");
            var inventory=new ArrayList<ItemStack>(); var ender=new ArrayList<ItemStack>(); var crafting=new ArrayList<ItemStack>();
            for(int i=0;i<player.getInventory().getContainerSize();i++) inventory.add(player.getInventory().getItem(i));
            for(int i=0;i<player.getEnderChestInventory().getContainerSize();i++) ender.add(player.getEnderChestInventory().getItem(i));
            for(int i=1;i<=4;i++) crafting.add(player.inventoryMenu.getSlot(i).getItem());
            return new Image(inventory,ender,crafting,player.inventoryMenu.getCarried());
        }
    }
    public static Plan plan(ServerPlayer player) {
        return plan(player.getUUID(),Image.capture(player));
    }
    /** Move the complete transient contents, including VQ items, into native saved slots.
     * No removal, dropping or partial insertion is allowed. Caller must save/read back before reset. */
    static void normalizeTransient(ServerPlayer player) {
        var before=Image.capture(player);var main=new SimpleContainer(36);
        for(int i=0;i<36;i++)main.setItem(i,before.inventory.get(i).copy());
        var transientItems=new ArrayList<>(before.crafting);transientItems.add(before.cursor);
        for(var stack:transientItems)if(!main.addItem(stack.copy()).isEmpty())
            throw new IllegalStateException("Make room for cursor and crafting contents");
        if(!before.matches(Image.capture(player)))throw new IllegalStateException("Inventory changed during normalization");
        for(int i=0;i<36;i++)player.getInventory().setItem(i,main.getItem(i).copy());
        for(int i=1;i<=4;i++)player.inventoryMenu.getSlot(i).set(ItemStack.EMPTY);
        player.inventoryMenu.setCarried(ItemStack.EMPTY);player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }
    static boolean nativeStorageOnly(ServerPlayer player) {
        var image=Image.capture(player);
        return image.cursor.isEmpty() && image.crafting.stream().allMatch(ItemStack::isEmpty);
    }
    public static Optional<Plan> fromNbt(net.minecraft.nbt.Tag raw,net.minecraft.core.HolderLookup.Provider registries) {
        if(!(raw instanceof net.minecraft.nbt.CompoundTag tag) || !(tag.get("schema") instanceof net.minecraft.nbt.IntTag)
                || tag.getIntOr("schema",0)!=1 || !(tag.get("before") instanceof net.minecraft.nbt.CompoundTag first)
                || !(tag.get("after") instanceof net.minecraft.nbt.CompoundTag last) || registries==null)return Optional.empty();
        try {
            var owner=UUID.fromString(tag.getStringOr("owner",""));
            if(owner.equals(new UUID(0,0)))return Optional.empty();
            var before=Image.fromNbt(first,registries);var after=Image.fromNbt(last,registries);
            var validated=plan(owner,before);
            if(!validated.after.matches(after))return Optional.empty();
            return Optional.of(validated);
        } catch(RuntimeException invalid) { return Optional.empty(); }
    }
    private static Plan plan(UUID owner,Image before) {
        var scan=new Scan(); var inventory=new ArrayList<ItemStack>(); var ender=new ArrayList<ItemStack>(); var crafting=new ArrayList<ItemStack>();
        for(var item:before.inventory) inventory.add(scan.clean(item,0));
        for(var item:before.ender) ender.add(scan.clean(item,0));
        for(var item:before.crafting) crafting.add(scan.clean(item,0));
        var cursor=scan.clean(before.cursor,0);
        var main=new SimpleContainer(36);
        for(int i=0;i<36;i++) main.setItem(i,inventory.get(i).copy());
        for(var rescued:scan.rescued) if(!main.addItem(rescued.copy()).isEmpty()) throw new IllegalStateException("Make room for contents of VQ storage items");
        for(int i=0;i<36;i++) inventory.set(i,main.getItem(i));
        return new Plan(owner,before,new Image(inventory,ender,crafting,cursor));
    }
    static ItemStack cleanForTest(ItemStack input) { return new Scan().clean(input,0); }
}
