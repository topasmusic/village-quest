package de.quest.shrine;

import de.quest.economy.DeliveryEconomy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Exact main-inventory consumption; does not mutate rewards, equipment, or world drops. */
final class VillageIntroInventoryPlan {
    static final List<Item> TIMBER = List.of(Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG,
            Items.JUNGLE_LOG, Items.ACACIA_LOG, Items.DARK_OAK_LOG, Items.MANGROVE_LOG,
            Items.CHERRY_LOG, Items.PALE_OAK_LOG);
    private static final int SLOTS = 36;
    private final VillageIntroRequestType type;
    private final List<ItemStack> before, after;
    private final int paidReward;
    private final CompoundTag retained;
    private VillageIntroInventoryPlan(VillageIntroRequestType type, List<ItemStack> before,
                                      List<ItemStack> after, int reward, CompoundTag retained) {
        this.type = type; this.before = copies(before); this.after = copies(after);
        this.paidReward = reward; this.retained = retained == null ? null : retained.copy();
    }
    static Optional<VillageIntroInventoryPlan> plan(Container bag, VillageIntroRequestType type) {
        if (bag == null || bag.getContainerSize() < SLOTS || type == null) return Optional.empty();
        var before = new ArrayList<ItemStack>(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            var item = bag.getItem(i);
            if (item == null || !item.isEmpty() && (item.getCount() < 1 || item.getCount() > item.getMaxStackSize())) return Optional.empty();
            before.add(item.copy());
        }
        var after = copies(before);
        int remaining = type == VillageIntroRequestType.HARVEST_HELP ? 0 : type.target();
        var used = new LinkedHashMap<Item, Integer>();
        for (int i = 0; i < SLOTS && remaining > 0; i++) {
            var item = after.get(i);
            if (!accepts(type, item)) continue;
            int take = Math.min(remaining, item.getCount());
            used.merge(item.getItem(), take, Integer::sum); item.shrink(take); remaining -= take;
            if (item.isEmpty()) after.set(i, ItemStack.EMPTY);
        }
        if (remaining != 0) return Optional.empty();
        int reward = 2;
        for (var entry : used.entrySet()) reward += DeliveryEconomy.materialValue(entry.getKey(), entry.getValue());
        return Optional.of(new VillageIntroInventoryPlan(type, before, after, reward, null));
    }
    static boolean accepts(VillageIntroRequestType type, ItemStack item) {
        if (item.isEmpty() || !ItemStack.isSameItemSameComponents(item, new ItemStack(item.getItem()))) return false;
        return type == VillageIntroRequestType.REPAIR_TIMBER ? TIMBER.contains(item.getItem())
                : type == VillageIntroRequestType.PATH_STONE && item.is(Items.COBBLESTONE);
    }
    int paidReward() { return paidReward; }
    VillageIntroRequestType type() { return type; }
    boolean matchesBefore(Container bag) { return matches(bag, before); }
    boolean matchesAfter(Container bag) { return matches(bag, after); }
    boolean apply(Container bag) {
        if (!matchesBefore(bag)) return false;
        for (int i = 0; i < SLOTS; i++) if (!ItemStack.matches(before.get(i), after.get(i))) bag.setItem(i, after.get(i).copy());
        bag.setChanged(); return matchesAfter(bag);
    }
    CompoundTag toNbt(HolderLookup.Provider registries) {
        if (retained != null) return retained.copy();
        var tag = new CompoundTag(); tag.putInt("schema", 1); tag.putString("offer", type.id()); tag.putInt("reward", paidReward);
        var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        var first = new ListTag(); var last = new ListTag();
        for (int i = 0; i < SLOTS; i++) {
            first.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops, before.get(i)).getOrThrow());
            last.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops, after.get(i)).getOrThrow());
        }
        tag.put("before", first); tag.put("after", last); return tag;
    }
    static Optional<VillageIntroInventoryPlan> fromNbt(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag == null || registries == null || !(tag.get("schema") instanceof IntTag schema) || schema.intValue() != 1
                || !(tag.get("reward") instanceof IntTag reward)) return Optional.empty();
        var type = VillageIntroRequestType.fromId(tag.getStringOr("offer", ""));
        if (type == null) return Optional.empty();
        try {
            var before = decode(tag, "before", registries); var after = decode(tag, "after", registries);
            var bag = new SimpleContainer(SLOTS);
            for (int i = 0; i < SLOTS; i++) bag.setItem(i, before.get(i).copy());
            // Validate the exact saved postimage using catalogue rules, without recalculating its frozen price.
            var expected = plan(bag, type);
            if (expected.isEmpty() || !ItemStack.listMatches(expected.get().after, after) || reward.intValue() < 2 || reward.intValue() > 18
                    || type == VillageIntroRequestType.HARVEST_HELP && reward.intValue() != 2) return Optional.empty();
            return Optional.of(new VillageIntroInventoryPlan(type, before, after, reward.intValue(), tag));
        } catch (RuntimeException invalid) { return Optional.empty(); }
    }
    private static List<ItemStack> decode(CompoundTag tag, String key, HolderLookup.Provider registries) {
        if (!(tag.get(key) instanceof ListTag list) || list.size() != SLOTS) throw new IllegalArgumentException("Invalid inventory proof");
        var ops = RegistryOps.create(NbtOps.INSTANCE, registries); var result = new ArrayList<ItemStack>(SLOTS);
        for (var value : list) {
            if (!(value instanceof CompoundTag)) throw new IllegalArgumentException("Invalid inventory slot");
            var stack = ItemStack.OPTIONAL_CODEC.parse(ops, value).getOrThrow();
            if (!stack.isEmpty() && (stack.getCount() < 1 || stack.getCount() > stack.getMaxStackSize())) throw new IllegalArgumentException("Invalid count");
            result.add(stack);
        }
        return result;
    }
    private static List<ItemStack> copies(List<ItemStack> items) { return new ArrayList<>(items.stream().map(ItemStack::copy).toList()); }
    private static boolean matches(Container bag, List<ItemStack> image) {
        if (bag == null || bag.getContainerSize() < SLOTS) return false;
        for (int i = 0; i < SLOTS; i++) if (!ItemStack.matches(bag.getItem(i), image.get(i))) return false;
        return true;
    }
}
